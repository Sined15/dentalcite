package pe.edu.dentalcite.publico.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.feriado.domain.Feriado;
import pe.edu.dentalcite.feriado.repository.FeriadoRepository;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.publico.api.dto.CalendarioPublicoDTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Qué días abre la clínica, tal como los necesita un calendario.
 *
 * <p>Lo que se fija aquí es que los días de la clínica son la **unión** de los
 * horarios declarados y los de cada odontólogo, los suyos: elegir a uno concreto
 * estrecha el calendario, y confundir las dos cosas deja marcar un día en que esa
 * persona no atiende.
 */
@ExtendWith(MockitoExtension.class)
class CalendarioPublicoServiceTest {

    @Mock
    private HorarioAtencionRepository horarioRepository;

    @Mock
    private FeriadoRepository feriadoRepository;

    @InjectMocks
    private CalendarioPublicoService servicio;

    private static final UUID MANIANAS = UUID.randomUUID();
    private static final UUID JUEVES = UUID.randomUUID();

    private static HorarioAtencion tramo(UUID odontologoId, int dia) {
        return HorarioAtencion.builder()
                .id(UUID.randomUUID())
                .odontologo(Odontologo.builder().id(odontologoId).activo(true).build())
                .diaSemana(dia)
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(13, 0))
                .build();
    }

    private CalendarioPublicoDTO conHorario(HorarioAtencion... tramos) {
        when(horarioRepository.findDeOdontologosActivos()).thenReturn(List.of(tramos));
        when(feriadoRepository.findAllByOrderByFechaAsc()).thenReturn(List.of());
        return servicio.consultar();
    }

    @Test
    void consultar_losDiasDeLaClinicaSonLaUnionDeLosHorarios() {
        CalendarioPublicoDTO calendario = conHorario(
                tramo(MANIANAS, 1), tramo(MANIANAS, 3), tramo(JUEVES, 4));

        // Nadie atiende los martes: ese día la clínica está cerrada.
        assertEquals(List.of(1, 3, 4), calendario.getDiasDeAtencion());
    }

    @Test
    void consultar_variosTramosElMismoDia_noRepitenElDia() {
        // Mañana y tarde del lunes son dos tramos y un solo día en el calendario.
        CalendarioPublicoDTO calendario = conHorario(tramo(MANIANAS, 1), tramo(MANIANAS, 1));

        assertEquals(List.of(1), calendario.getDiasDeAtencion());
    }

    @Test
    void consultar_cadaOdontologoLlevaSusPropiosDias() {
        CalendarioPublicoDTO calendario = conHorario(
                tramo(MANIANAS, 1), tramo(MANIANAS, 3), tramo(JUEVES, 4));

        assertEquals(2, calendario.getPorOdontologo().size());
        assertEquals(List.of(1, 3), diasDe(calendario, MANIANAS));
        // Y quien solo atiende jueves no hereda los días de los demás.
        assertEquals(List.of(4), diasDe(calendario, JUEVES));
    }

    @Test
    void consultar_devuelveLosFeriados() {
        when(horarioRepository.findDeOdontologosActivos()).thenReturn(List.of(tramo(MANIANAS, 1)));
        when(feriadoRepository.findAllByOrderByFechaAsc()).thenReturn(List.of(
                Feriado.builder().id(UUID.randomUUID())
                        .fecha(LocalDate.of(2026, 7, 28)).descripcion("Fiestas Patrias").build()));

        assertEquals(List.of(LocalDate.of(2026, 7, 28)), servicio.consultar().getFeriados());
    }

    @Test
    void consultar_sinHorarioDeclarado_noAnunciaNingunDiaAbierto() {
        // Preferible a suponer una semana laborable: un calendario que ofrece días
        // sin horario detrás devuelve listas vacías y parece roto.
        CalendarioPublicoDTO calendario = conHorario();

        assertTrue(calendario.getDiasDeAtencion().isEmpty());
        assertTrue(calendario.getPorOdontologo().isEmpty());
    }

    private static List<Integer> diasDe(CalendarioPublicoDTO calendario, UUID odontologoId) {
        return calendario.getPorOdontologo().stream()
                .filter(fila -> fila.getOdontologoId().equals(odontologoId))
                .findFirst()
                .orElseThrow()
                .getDias();
    }
}
