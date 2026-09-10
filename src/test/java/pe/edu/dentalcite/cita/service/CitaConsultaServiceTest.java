package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pe.edu.dentalcite.cita.api.dto.CitaHistorialDTO;
import pe.edu.dentalcite.cita.api.dto.CitaResumenDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La consulta de la agenda (HU-11 · RF-18) y de la bitácora (RF-21). Lo que hay
 * que fijar aquí es la traducción del rango de días a instantes y el mapeo a
 * hora local: el filtrado en sí lo hace la base.
 */
@ExtendWith(MockitoExtension.class)
class CitaConsultaServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 12);

    @Mock private CitaRepository citaRepository;
    @Mock private CitaHistorialRepository historialRepository;
    @Mock private UsuarioRepository usuarioRepository;

    private CitaConsultaService servicio;

    @BeforeEach
    void inicializar() {
        servicio = new CitaConsultaService(citaRepository, historialRepository, usuarioRepository,
                new VentanaDeCancelacion(24, new ReglasDeReserva(2, 90, 3)), "America/Lima");
    }

    private static OffsetDateTime instante(LocalDate dia, int hora, int minuto) {
        return ZonedDateTime.of(dia, LocalTime.of(hora, minuto), LIMA).toOffsetDateTime();
    }

    private static Cita cita(LocalDate dia, int hora, String estado) {
        Ficha ficha = Ficha.builder()
                .id(UUID.randomUUID())
                .numeroHistoria("HC-00042")
                .nombres("Ana").apellidos("Torres")
                .build();
        return Cita.builder()
                .id(UUID.randomUUID())
                .codigo("CIT-000007")
                .ficha(ficha)
                .odontologo(Odontologo.builder().id(UUID.randomUUID())
                        .nombres("Juan").apellidos("Perez").build())
                .tratamiento(Tratamiento.builder().id(UUID.randomUUID())
                        .nombre("Endodoncia").duracionMinutos(30).build())
                .consultorio(Consultorio.builder().id(UUID.randomUUID())
                        .nombre("Consultorio 2").build())
                .inicio(instante(dia, hora, 0))
                .fin(instante(dia, hora, 30))
                .estado(estado)
                .build();
    }

    private void devolver(Cita... citas) {
        when(citaRepository.buscar(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(citas)));
    }

    @Test
    void consultar_rangoDeUnDia_abarcaTodoElDia() {
        devolver(cita(LUNES, 9, Cita.ESTADO_CONFIRMADA));

        servicio.consultar(LUNES, LUNES, null, null, Pageable.unpaged());

        ArgumentCaptor<OffsetDateTime> desde = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> hasta = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(citaRepository).buscar(desde.capture(), hasta.capture(), isNull(), isNull(), isNull(), any());

        // Pedir «el 12 al 12» tiene que incluir la cita de las 19:00 de ese dia:
        // el extremo superior se abre al dia siguiente, no al inicio del mismo.
        assertEquals(instante(LUNES, 0, 0), desde.getValue());
        assertEquals(instante(LUNES.plusDays(1), 0, 0), hasta.getValue());
    }

    @Test
    void consultar_rangoInvertido_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultar(LUNES.plusDays(1), LUNES, null, null, Pageable.unpaged()));
    }

    @Test
    void consultar_sinFechas_lanzaIllegalArgument() {
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultar(null, LUNES, null, null, Pageable.unpaged()));
        assertThrows(IllegalArgumentException.class,
                () -> servicio.consultar(LUNES, null, null, null, Pageable.unpaged()));
    }

    @Test
    void consultar_conFiltros_losTrasladaAlRepositorio() {
        UUID odontologoId = UUID.randomUUID();
        devolver(cita(LUNES, 9, Cita.ESTADO_CANCELADA));

        servicio.consultar(LUNES, LUNES, odontologoId, "cancelada", Pageable.unpaged());

        // El estado llega en minusculas desde la query string y se normaliza.
        verify(citaRepository).buscar(any(), any(), isNull(), eq(odontologoId), eq("CANCELADA"), any());
    }

    @Test
    void consultar_estadoDesconocido_lanzaIllegalArgument() {
        // Devolver una pagina vacia convertiria una errata en «no hay citas»,
        // que es la respuesta mas dificil de depurar.
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> servicio.consultar(LUNES, LUNES, null, "PENDIENTE", Pageable.unpaged()));
        assertTrue(error.getMessage().contains("PENDIENTE"));
    }

    @Test
    void consultar_estadoEnBlanco_equivaleATodos() {
        devolver(cita(LUNES, 9, Cita.ESTADO_CONFIRMADA));

        servicio.consultar(LUNES, LUNES, null, "  ", Pageable.unpaged());

        verify(citaRepository).buscar(any(), any(), isNull(), isNull(), isNull(), any());
    }

    @Test
    void consultar_devuelveHoraLocalDeClinicaYNoElInstanteUtc() {
        devolver(cita(LUNES, 9, Cita.ESTADO_CONFIRMADA));

        CitaResumenDTO fila = servicio.consultar(LUNES, LUNES, null, null, Pageable.unpaged())
                .getContent().get(0);

        assertEquals(LUNES, fila.getFecha());
        assertEquals(LocalTime.of(9, 0), fila.getHora());
        assertEquals("America/Lima", fila.getZonaHoraria());
    }

    @Test
    void consultar_devuelvePacienteHoraYConsultorio() {
        devolver(cita(LUNES, 9, Cita.ESTADO_CONFIRMADA));

        CitaResumenDTO fila = servicio.consultar(LUNES, LUNES, null, null, Pageable.unpaged())
                .getContent().get(0);

        // RF-18 nombra exactamente estos tres datos.
        assertEquals("Ana Torres", fila.getPaciente().getNombre());
        assertEquals("HC-00042", fila.getPaciente().getNumeroHistoria());
        assertEquals(LocalTime.of(9, 0), fila.getHora());
        assertEquals("Consultorio 2", fila.getConsultorio().getNombre());
        assertEquals("Juan Perez", fila.getOdontologo().getNombre());
        assertEquals(30, fila.getDuracionMinutos());
    }

    @Test
    void consultar_fichaSinNombre_identificaPorNumeroDeHistoria() {
        // RF-06 admite dar de alta la ficha solo con el documento.
        Cita sinNombre = cita(LUNES, 9, Cita.ESTADO_CONFIRMADA);
        sinNombre.getFicha().setNombres(null);
        sinNombre.getFicha().setApellidos(null);
        devolver(sinNombre);

        CitaResumenDTO fila = servicio.consultar(LUNES, LUNES, null, null, Pageable.unpaged())
                .getContent().get(0);

        assertEquals("HC-00042", fila.getPaciente().getNombre());
    }

    @Test
    void consultar_citaCancelada_llevaSuMotivo() {
        Cita cancelada = cita(LUNES, 9, Cita.ESTADO_CANCELADA);
        cancelada.setMotivoCancelacion("El paciente reprograma");
        devolver(cancelada);

        CitaResumenDTO fila = servicio.consultar(LUNES, LUNES, null, null, Pageable.unpaged())
                .getContent().get(0);

        assertEquals("CANCELADA", fila.getEstado());
        assertEquals("El paciente reprograma", fila.getMotivoCancelacion());
    }

    @Test
    void consultar_respetaElOrdenPorHoraQueLlegaEnElPageable() {
        Pageable porHora = PageRequest.of(0, 20, Sort.by("inicio"));
        devolver(cita(LUNES, 9, Cita.ESTADO_CONFIRMADA));

        servicio.consultar(LUNES, LUNES, null, null, porHora);

        verify(citaRepository).buscar(any(), any(), isNull(), isNull(), isNull(), eq(porHora));
    }

    @Test
    void historial_citaInexistente_lanza404() {
        UUID citaId = UUID.randomUUID();
        when(citaRepository.existsById(citaId)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> servicio.historial(citaId));
    }

    @Test
    void historial_devuelveLaTransicionConFechaResponsableYMotivo() {
        UUID citaId = UUID.randomUUID();
        Usuario recepcion = Usuario.builder()
                .id(UUID.randomUUID())
                .nombre("Recepcion")
                .correo("recepcion@dentalcite.com")
                .rol("RECEPCIONISTA")
                .build();
        OffsetDateTime cuando = instante(LUNES, 8, 0);
        when(citaRepository.existsById(citaId)).thenReturn(true);
        when(historialRepository.findByCitaIdOrderByOcurridoEnAsc(citaId)).thenReturn(List.of(
                CitaHistorial.builder().id(UUID.randomUUID())
                        .estadoNuevo(Cita.ESTADO_CONFIRMADA).ocurridoEn(cuando).build(),
                CitaHistorial.builder().id(UUID.randomUUID())
                        .estadoAnterior(Cita.ESTADO_CONFIRMADA)
                        .estadoNuevo(Cita.ESTADO_CANCELADA)
                        .motivo("El paciente reprograma")
                        .usuario(recepcion)
                        .ocurridoEn(cuando.plusHours(1)).build()));

        List<CitaHistorialDTO> historial = servicio.historial(citaId);

        assertEquals(2, historial.size());
        // Una transicion sin estado anterior ni responsable se mapea sin romperse:
        // hoy no la escribe nadie, pero el DTO tiene que admitirla.
        assertNull(historial.get(0).getEstadoAnterior());
        assertNull(historial.get(0).getResponsable());
        assertEquals("CONFIRMADA", historial.get(0).getEstadoNuevo());

        CitaHistorialDTO cancelacion = historial.get(1);
        assertEquals("CONFIRMADA", cancelacion.getEstadoAnterior());
        assertEquals("CANCELADA", cancelacion.getEstadoNuevo());
        assertEquals("El paciente reprograma", cancelacion.getMotivo());
        assertEquals(cuando.plusHours(1), cancelacion.getOcurridoEn());
        assertEquals("recepcion@dentalcite.com", cancelacion.getResponsable().getCorreo());
        assertEquals("RECEPCIONISTA", cancelacion.getResponsable().getRol());
    }
}
