package pe.edu.dentalcite.disponibilidad.service;

import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.odontologo.domain.Odontologo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El cálculo de HU-08 (RF-13, RF-14). Clase pura: recibe una {@link AgendaDelRango}
 * ya cargada y devuelve las franjas, sin Spring, sin repositorios y sin reloj
 * propio, de modo que sus pruebas son aritmética de calendario y no montan un
 * contexto.
 *
 * <p>Por cada odontólogo y cada día del rango recorre los tramos que declaró en
 * HU-07 en incrementos de quince minutos y propone todo bloque contiguo que quepa
 * (RN-04). Descarta el candidato que choque con una cita activa o un bloqueo del
 * odontólogo (RN-03), el que caiga en feriado (RN-03), el que no deje ningún
 * consultorio libre (RN-02, RF-14) y el que quede fuera de la ventana de reserva
 * (RN-05, cuyos umbrales llegan en la agenda porque RN-17 los quiere configurables).
 */
public final class MotorDeFranjas {

    /** RN-04: el motor recorre el horario en incrementos de quince minutos. */
    public static final int PASO_MINUTOS = 15;

    private MotorDeFranjas() {
    }

    /**
     * @return por odontólogo, los días con franjas y sus horas de inicio locales.
     *         Los días sin ninguna franja y los odontólogos sin ningún día no
     *         aparecen: la respuesta enumera lo que hay, no lo que falta.
     */
    public static Map<UUID, Map<LocalDate, List<LocalTime>>> calcular(AgendaDelRango agenda) {
        Map<UUID, Map<LocalDate, List<LocalTime>>> resultado = new LinkedHashMap<>();

        for (Odontologo odontologo : agenda.odontologos()) {
            List<HorarioAtencion> horarios =
                    agenda.horariosPorOdontologo().getOrDefault(odontologo.getId(), List.of());
            if (horarios.isEmpty()) {
                continue;
            }

            Map<LocalDate, List<LocalTime>> dias = new LinkedHashMap<>();
            for (LocalDate fecha = agenda.desde(); !fecha.isAfter(agenda.hasta()); fecha = fecha.plusDays(1)) {
                if (agenda.feriados().contains(fecha)) {
                    continue;
                }
                List<LocalTime> inicios = iniciosDelDia(agenda, odontologo.getId(), horarios, fecha);
                if (!inicios.isEmpty()) {
                    dias.put(fecha, inicios);
                }
            }

            if (!dias.isEmpty()) {
                resultado.put(odontologo.getId(), dias);
            }
        }
        return resultado;
    }

    private static List<LocalTime> iniciosDelDia(AgendaDelRango agenda, UUID odontologoId,
            List<HorarioAtencion> horarios, LocalDate fecha) {
        // V5 numera el día de 1 (lunes) a 7 (domingo), que es exactamente la
        // convención ISO-8601 de DayOfWeek: no hay traducción que hacer.
        Integer diaSemana = fecha.getDayOfWeek().getValue();
        List<LocalTime> inicios = new ArrayList<>();

        for (HorarioAtencion tramo : horarios) {
            if (!diaSemana.equals(tramo.getDiaSemana())) {
                continue;
            }
            // El barrido se ancla en la fecha en vez de sumar sobre LocalTime: un
            // tramo que termina cerca de medianoche haría que LocalTime diese la
            // vuelta y el candidato pareciera caber cuando en realidad se sale del día.
            LocalDateTime finTramo = LocalDateTime.of(fecha, tramo.getHoraFin());
            for (LocalDateTime candidato = LocalDateTime.of(fecha, tramo.getHoraInicio());
                    !candidato.plusMinutes(agenda.duracionMinutos()).isAfter(finTramo);
                    candidato = candidato.plusMinutes(PASO_MINUTOS)) {

                OffsetDateTime inicio = candidato.atZone(agenda.zona()).toOffsetDateTime();
                OffsetDateTime fin = inicio.plusMinutes(agenda.duracionMinutos());

                if (esOfrecible(agenda, odontologoId, inicio, fin)) {
                    inicios.add(candidato.toLocalTime());
                }
            }
        }

        // Dos tramos del mismo día (mañana y tarde) llegan en el orden que quiera
        // la base; el cliente espera la jornada en orden.
        inicios.sort(LocalTime::compareTo);
        return inicios;
    }

    private static boolean esOfrecible(AgendaDelRango agenda, UUID odontologoId,
            OffsetDateTime inicio, OffsetDateTime fin) {
        if (!agenda.reglas().dentroDeVentana(inicio, agenda.ahora())) {
            return false;
        }
        if (Intervalo.algunoSolapa(agenda.citasPorOdontologo().get(odontologoId), inicio, fin)) {
            return false;
        }
        if (Intervalo.algunoSolapa(agenda.bloqueosPorOdontologo().get(odontologoId), inicio, fin)) {
            return false;
        }
        return hayConsultorioLibre(agenda, inicio, fin);
    }

    /**
     * RN-02 y RF-14: la franja solo se ofrece si queda algún consultorio donde
     * alojarla. No se elige cuál —eso es de la reserva (HU-09)—: dos odontólogos
     * libres a la misma hora con un solo consultorio libre ven ambos la franja, y
     * es la exclusión de HU-10 la que decide quién se la queda.
     *
     * <p>El criterio vive en {@link ConsultoriosLibres} porque la reserva hace la
     * misma pregunta y debe obtener la misma respuesta.
     */
    private static boolean hayConsultorioLibre(AgendaDelRango agenda, OffsetDateTime inicio, OffsetDateTime fin) {
        return ConsultoriosLibres.hayAlguno(agenda.consultoriosOperativos(),
                agenda.citasPorConsultorio(), agenda.bloqueosPorConsultorio(), inicio, fin);
    }
}
