package pe.edu.dentalcite.disponibilidad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.dentalcite.cita.service.ReglasDeReserva;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.odontologo.domain.Odontologo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los criterios de aceptación de HU-08, ejercidos sobre el cálculo desnudo: sin
 * Spring, sin Mockito y sin base de datos. El motor no tiene reloj propio ni
 * repositorios justamente para que estas pruebas sean aritmética de calendario y
 * no dependan de qué día se ejecuten.
 */
class MotorDeFranjasTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    /**
     * RN-17: los umbrales son configuración, así que la prueba los construye en
     * vez de heredarlos de una constante. Estos son los valores por defecto.
     */
    private static final ReglasDeReserva REGLAS = new ReglasDeReserva(2, 90, 3);
    /** Un lunes cualquiera, elegido para que el día de la semana sea el del enunciado. */
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 14);

    private UUID odontologoId;
    private Odontologo odontologo;
    private List<UUID> consultorios;

    @BeforeEach
    void setUp() {
        odontologoId = UUID.randomUUID();
        odontologo = Odontologo.builder()
                .id(odontologoId)
                .cop("COP-90001")
                .nombres("Juan")
                .apellidos("Pérez")
                .activo(true)
                .build();
        consultorios = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    // ------------------------------------------------------------------
    // Utilidades de construcción
    // ------------------------------------------------------------------

    private HorarioAtencion tramo(int diaSemana, String inicio, String fin) {
        return HorarioAtencion.builder()
                .id(UUID.randomUUID())
                .odontologo(odontologo)
                .diaSemana(diaSemana)
                .horaInicio(LocalTime.parse(inicio))
                .horaFin(LocalTime.parse(fin))
                .build();
    }

    private OffsetDateTime enLima(LocalDate fecha, String hora) {
        return fecha.atTime(LocalTime.parse(hora)).atZone(ZONA).toOffsetDateTime();
    }

    private Intervalo intervalo(LocalDate fecha, String inicio, String fin) {
        return new Intervalo(enLima(fecha, inicio), enLima(fecha, fin));
    }

    /** Constructor con los valores por defecto del escenario del enunciado. */
    private AgendaBuilder agenda() {
        return new AgendaBuilder();
    }

    private final class AgendaBuilder {
        private int duracion = 30;
        private LocalDate desde = LUNES;
        private LocalDate hasta = LUNES;
        private List<HorarioAtencion> horarios = new ArrayList<>(List.of(tramo(1, "09:00", "13:00")));
        private final Map<UUID, List<Intervalo>> citasOdontologo = new HashMap<>();
        private final Map<UUID, List<Intervalo>> citasConsultorio = new HashMap<>();
        private final Map<UUID, List<Intervalo>> bloqueosOdontologo = new HashMap<>();
        private final Map<UUID, List<Intervalo>> bloqueosConsultorio = new HashMap<>();
        private Set<LocalDate> feriados = Set.of();
        private List<UUID> operativos = consultorios;
        /** Muy anterior al lunes de prueba: la ventana de RN-05 no estorba salvo que se pruebe. */
        private OffsetDateTime ahora = enLima(LUNES.minusDays(7), "08:00");

        AgendaBuilder duracion(int minutos) {
            this.duracion = minutos;
            return this;
        }

        AgendaBuilder rango(LocalDate desde, LocalDate hasta) {
            this.desde = desde;
            this.hasta = hasta;
            return this;
        }

        AgendaBuilder horarios(HorarioAtencion... tramos) {
            this.horarios = new ArrayList<>(List.of(tramos));
            return this;
        }

        AgendaBuilder citaDelOdontologo(Intervalo... intervalos) {
            citasOdontologo.put(odontologoId, List.of(intervalos));
            return this;
        }

        AgendaBuilder bloqueoDelOdontologo(Intervalo... intervalos) {
            bloqueosOdontologo.put(odontologoId, List.of(intervalos));
            return this;
        }

        AgendaBuilder consultorioOcupado(int indice, Intervalo... intervalos) {
            citasConsultorio.put(operativos.get(indice), List.of(intervalos));
            return this;
        }

        AgendaBuilder consultorioBloqueado(int indice, Intervalo... intervalos) {
            bloqueosConsultorio.put(operativos.get(indice), List.of(intervalos));
            return this;
        }

        AgendaBuilder consultoriosOperativos(List<UUID> operativos) {
            this.operativos = operativos;
            return this;
        }

        AgendaBuilder feriados(LocalDate... fechas) {
            this.feriados = Set.of(fechas);
            return this;
        }

        AgendaBuilder ahora(OffsetDateTime ahora) {
            this.ahora = ahora;
            return this;
        }

        Map<UUID, Map<LocalDate, List<LocalTime>>> calcular() {
            AgendaDelRango agenda = new AgendaDelRango(duracion, desde, hasta, ZONA, ahora, REGLAS,
                    List.of(odontologo), Map.of(odontologoId, horarios),
                    citasOdontologo, citasConsultorio, bloqueosOdontologo, bloqueosConsultorio,
                    operativos, feriados);
            return MotorDeFranjas.calcular(agenda);
        }

        List<LocalTime> iniciosDel(LocalDate fecha) {
            Map<LocalDate, List<LocalTime>> dias = calcular().get(odontologoId);
            return dias == null ? List.of() : dias.getOrDefault(fecha, List.of());
        }
    }

    private static List<LocalTime> horas(String... valores) {
        return List.of(valores).stream().map(LocalTime::parse).toList();
    }

    // ------------------------------------------------------------------
    // CA-1 · RN-04: barrido de quince minutos y bloques contiguos
    // ------------------------------------------------------------------

    @Test
    void calcular_conHorarioDeCuatroHorasYCitaDeTreintaMinutos_omiteSoloLosBloquesQueSolapan() {
        // El escenario literal del criterio: 09:00–13:00, cita de 30 min a las 10:00,
        // tratamiento de 30 min.
        List<LocalTime> inicios = agenda()
                .duracion(30)
                .citaDelOdontologo(intervalo(LUNES, "10:00", "10:30"))
                .iniciosDel(LUNES);

        // El barrido va de quince en quince desde las 09:00 y el último bloque de 30
        // que cabe empieza a las 12:30.
        assertEquals(LocalTime.parse("09:00"), inicios.get(0));
        assertEquals(LocalTime.parse("12:30"), inicios.get(inicios.size() - 1));

        // Solapan la cita los que empiezan a las 09:45, 10:00 y 10:15; ninguno más.
        assertFalse(inicios.contains(LocalTime.parse("09:45")));
        assertFalse(inicios.contains(LocalTime.parse("10:00")));
        assertFalse(inicios.contains(LocalTime.parse("10:15")));
        assertTrue(inicios.contains(LocalTime.parse("09:30")), "termina justo cuando empieza la cita");
        assertTrue(inicios.contains(LocalTime.parse("10:30")), "empieza justo cuando la cita termina");

        // 09:00 a 12:30 de quince en quince son quince candidatos, menos los tres que solapan.
        assertEquals(12, inicios.size());
    }

    @Test
    void calcular_conTratamientoQueNoCabeEnElTramo_noProponeNada() {
        List<LocalTime> inicios = agenda()
                .duracion(60)
                .horarios(tramo(1, "09:00", "09:45"))
                .iniciosDel(LUNES);

        assertTrue(inicios.isEmpty());
    }

    @Test
    void calcular_conTramoDeLaDuracionExacta_proponeUnaSolaFranja() {
        List<LocalTime> inicios = agenda()
                .duracion(30)
                .horarios(tramo(1, "09:00", "09:30"))
                .iniciosDel(LUNES);

        assertEquals(horas("09:00"), inicios);
    }

    @Test
    void calcular_conDosTramosElMismoDia_devuelveLaJornadaOrdenada() {
        // La base puede devolver la tarde antes que la mañana; el cliente espera orden.
        List<LocalTime> inicios = agenda()
                .duracion(60)
                .horarios(tramo(1, "15:00", "16:00"), tramo(1, "09:00", "10:00"))
                .iniciosDel(LUNES);

        assertEquals(horas("09:00", "15:00"), inicios);
    }

    // ------------------------------------------------------------------
    // CA-4 · RN-03: horario declarado y bloqueos del odontólogo
    // ------------------------------------------------------------------

    @Test
    void calcular_paraElDiaSinHorarioDeclarado_noProponeNada() {
        // Solo se declararon los lunes; el martes siguiente no tiene tramo.
        List<LocalTime> inicios = agenda()
                .rango(LUNES.plusDays(1), LUNES.plusDays(1))
                .iniciosDel(LUNES.plusDays(1));

        assertTrue(inicios.isEmpty());
    }

    @Test
    void calcular_conBloqueoDelOdontologo_noOfreceFranjasSobreEseRango() {
        List<LocalTime> inicios = agenda()
                .duracion(30)
                .bloqueoDelOdontologo(intervalo(LUNES, "10:00", "11:00"))
                .iniciosDel(LUNES);

        // Todo lo que toca 10:00–11:00 desaparece; el resto del tramo sigue ahí.
        assertFalse(inicios.contains(LocalTime.parse("09:45")));
        assertFalse(inicios.contains(LocalTime.parse("10:30")));
        assertTrue(inicios.contains(LocalTime.parse("09:30")));
        assertTrue(inicios.contains(LocalTime.parse("11:00")));
        // Y nada cae fuera del intervalo declarado.
        assertTrue(inicios.stream().allMatch(h -> !h.isBefore(LocalTime.parse("09:00"))
                && !h.isAfter(LocalTime.parse("12:30"))));
    }

    @Test
    void calcular_enFeriado_noProponeNingunaFranja() {
        // RN-03: el feriado es un bloqueo general, aunque el horario esté declarado.
        List<LocalTime> inicios = agenda()
                .feriados(LUNES)
                .iniciosDel(LUNES);

        assertTrue(inicios.isEmpty());
    }

    // ------------------------------------------------------------------
    // CA-5 y CA-6 · RN-02 y RF-14: el pool de consultorios
    // ------------------------------------------------------------------

    @Test
    void calcular_conUnConsultorioBloqueadoYOtrosLibres_siguieOfreciendoLaFranja() {
        List<LocalTime> inicios = agenda()
                .duracion(30)
                .consultorioBloqueado(0, intervalo(LUNES, "09:00", "13:00"))
                .iniciosDel(LUNES);

        assertTrue(inicios.contains(LocalTime.parse("09:00")), "quedan otros dos consultorios");
    }

    @Test
    void calcular_conLosTresConsultoriosOcupados_noOfreceEsaFranja() {
        Intervalo ocupacion = intervalo(LUNES, "10:00", "10:30");
        List<LocalTime> inicios = agenda()
                .duracion(30)
                .consultorioOcupado(0, ocupacion)
                .consultorioOcupado(1, ocupacion)
                .consultorioOcupado(2, ocupacion)
                .iniciosDel(LUNES);

        assertFalse(inicios.contains(LocalTime.parse("10:00")), "sin consultorio donde alojarla (RF-14)");
        assertTrue(inicios.contains(LocalTime.parse("10:30")), "fuera de la franja copada sí hay sitio");
    }

    @Test
    void calcular_conElUltimoConsultorioBloqueadoYLosOtrosOcupados_noOfreceEsaFranja() {
        // RN-02 y RN-03 se combinan: da igual si el consultorio está ocupado por una
        // cita o fuera de servicio por un bloqueo, deja de contar igual.
        Intervalo tramo = intervalo(LUNES, "09:00", "13:00");
        List<LocalTime> inicios = agenda()
                .consultorioOcupado(0, tramo)
                .consultorioOcupado(1, tramo)
                .consultorioBloqueado(2, tramo)
                .iniciosDel(LUNES);

        assertTrue(inicios.isEmpty());
    }

    @Test
    void calcular_sinNingunConsultorioOperativo_noProponeNada() {
        List<LocalTime> inicios = agenda()
                .consultoriosOperativos(List.of())
                .iniciosDel(LUNES);

        assertTrue(inicios.isEmpty());
    }

    // ------------------------------------------------------------------
    // RN-05: la ventana de reserva
    // ------------------------------------------------------------------

    @Test
    void calcular_conFranjasAMenosDeDosHoras_lasDescarta() {
        // Son las 09:00 del propio lunes: la primera franja reservable es la de las 11:00.
        List<LocalTime> inicios = agenda()
                .duracion(30)
                .ahora(enLima(LUNES, "09:00"))
                .iniciosDel(LUNES);

        assertFalse(inicios.contains(LocalTime.parse("10:45")));
        assertEquals(LocalTime.parse("11:00"), inicios.get(0));
    }

    @Test
    void calcular_conFranjasAMasDeNoventaDias_lasDescarta() {
        LocalDate lejano = LUNES.plusDays(120);
        List<LocalTime> inicios = agenda()
                .rango(lejano, lejano)
                .horarios(tramo(lejano.getDayOfWeek().getValue(), "09:00", "13:00"))
                .ahora(enLima(LUNES, "08:00"))
                .iniciosDel(lejano);

        assertTrue(inicios.isEmpty());
    }

    // ------------------------------------------------------------------
    // Forma del resultado
    // ------------------------------------------------------------------

    @Test
    void calcular_conOdontologoSinNingunTramo_noLoIncluyeEnElResultado() {
        AgendaDelRango agenda = new AgendaDelRango(30, LUNES, LUNES, ZONA,
                enLima(LUNES.minusDays(7), "08:00"), REGLAS, List.of(odontologo), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), consultorios, Set.of());

        assertTrue(MotorDeFranjas.calcular(agenda).isEmpty());
    }

    @Test
    void calcular_sobreVariosDias_omiteLosDiasSinFranjas() {
        // Rango de lunes a domingo con horario solo los lunes y los miércoles.
        Map<LocalDate, List<LocalTime>> dias = agenda()
                .duracion(60)
                .rango(LUNES, LUNES.plusDays(6))
                .horarios(tramo(1, "09:00", "10:00"), tramo(3, "09:00", "10:00"))
                .calcular()
                .get(odontologoId);

        assertEquals(Set.of(LUNES, LUNES.plusDays(2)), dias.keySet());
    }
}
