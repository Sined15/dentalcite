package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.disponibilidad.service.FranjasCache;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * La cancelación desde recepción (HU-11 · RF-20, RF-21). Cada prueba fija una de
 * las decisiones que la distinguen de la cancelación del paciente (HU-15): sin
 * ventana, con motivo obligatorio y dejando rastro.
 */
@ExtendWith(MockitoExtension.class)
class CancelacionServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock private CitaRepository citaRepository;
    @Mock private CitaHistorialRepository historialRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private FranjasCache franjasCache;

    private CancelacionService servicio;
    private UUID recepcionistaId;

    @BeforeEach
    void inicializar() {
        servicio = new CancelacionService(citaRepository, historialRepository, usuarioRepository,
                franjasCache, "America/Lima");
        recepcionistaId = UUID.randomUUID();
        autenticarComoRecepcion();
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private void autenticarComoRecepcion() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(recepcionistaId.toString(), null,
                        List.of(new SimpleGrantedAuthority("SCOPE_RECEPCIONISTA"))));
    }

    private Usuario recepcionista() {
        return Usuario.builder().id(recepcionistaId).nombre("Recepcion")
                .correo("recepcion@dentalcite.com").rol("RECEPCIONISTA").build();
    }

    /** Una cita dentro de las próximas dos horas: la que HU-15 no dejaría cancelar. */
    private Cita citaInminente(String estado) {
        OffsetDateTime inicio = OffsetDateTime.now(LIMA).plusMinutes(30);
        return Cita.builder()
                .id(UUID.randomUUID())
                .codigo("CIT-000007")
                .ficha(Ficha.builder().id(UUID.randomUUID()).numeroHistoria("HC-00042").build())
                .odontologo(Odontologo.builder().id(UUID.randomUUID())
                        .nombres("Juan").apellidos("Perez").build())
                .tratamiento(Tratamiento.builder().id(UUID.randomUUID())
                        .nombre("Endodoncia").duracionMinutos(30).build())
                .consultorio(Consultorio.builder().id(UUID.randomUUID())
                        .nombre("Consultorio 2").build())
                .inicio(inicio)
                .fin(inicio.plusMinutes(30))
                .estado(estado)
                .build();
    }

    private Cita prepararConfirmada() {
        Cita cita = citaInminente(Cita.ESTADO_CONFIRMADA);
        when(citaRepository.findById(cita.getId())).thenReturn(Optional.of(cita));
        lenient().when(usuarioRepository.findById(recepcionistaId))
                .thenReturn(Optional.of(recepcionista()));
        return cita;
    }

    @Test
    void cancelar_citaConfirmada_pasaACanceladaConSuMotivo() {
        Cita cita = prepararConfirmada();

        CitaResponseDTO respuesta = servicio.cancelar(cita.getId(), "El paciente reprograma");

        assertEquals("CANCELADA", cita.getEstado());
        assertEquals("El paciente reprograma", cita.getMotivoCancelacion());
        assertEquals("CANCELADA", respuesta.getEstado());
        assertEquals("CIT-000007", respuesta.getCodigo());
        verify(citaRepository).save(cita);
    }

    @Test
    void cancelar_dentroDeLasVeinticuatroHoras_seCancelaIgual() {
        // El criterio es explicito: recepcion cancela «incluso dentro de las
        // veinticuatro horas previas». La ventana de RN-06 es de HU-15.
        Cita cita = prepararConfirmada();
        assertTrue(cita.getInicio().isBefore(OffsetDateTime.now(LIMA).plusHours(24)));

        servicio.cancelar(cita.getId(), "Urgencia del odontologo");

        assertEquals("CANCELADA", cita.getEstado());
    }

    @Test
    void cancelar_conservaLaFilaDeLaCitaIntegra() {
        // RN-12: nada se borra. La cita sigue con todos sus datos.
        Cita cita = prepararConfirmada();
        String codigo = cita.getCodigo();
        OffsetDateTime inicio = cita.getInicio();
        UUID odontologoId = cita.getOdontologo().getId();

        servicio.cancelar(cita.getId(), "El paciente reprograma");

        assertEquals(codigo, cita.getCodigo());
        assertEquals(inicio, cita.getInicio());
        assertEquals(odontologoId, cita.getOdontologo().getId());
        verify(citaRepository, never()).delete(any());
        verify(citaRepository, never()).deleteById(any());
    }

    @Test
    void cancelar_registraLaTransicionConFechaResponsableYMotivo() {
        Cita cita = prepararConfirmada();

        servicio.cancelar(cita.getId(), "El paciente reprograma");

        ArgumentCaptor<CitaHistorial> fila = ArgumentCaptor.forClass(CitaHistorial.class);
        verify(historialRepository).save(fila.capture());

        assertEquals(cita, fila.getValue().getCita());
        assertEquals("CONFIRMADA", fila.getValue().getEstadoAnterior());
        assertEquals("CANCELADA", fila.getValue().getEstadoNuevo());
        assertEquals("El paciente reprograma", fila.getValue().getMotivo());
        assertEquals(recepcionistaId, fila.getValue().getUsuario().getId());
    }

    @Test
    void cancelar_invalidaLaCacheDeFranjas() {
        // RN-06: «la franja volvera a ofrecerse de inmediato». Sin esto seguiria
        // sin ofrecerse hasta que caducara el TTL de la cache.
        Cita cita = prepararConfirmada();

        servicio.cancelar(cita.getId(), "El paciente reprograma");

        verify(franjasCache).invalidarTrasCommit();
    }

    @Test
    void cancelar_citaInexistente_lanza404() {
        UUID citaId = UUID.randomUUID();
        when(citaRepository.findById(citaId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.cancelar(citaId, "Motivo"));
        verify(franjasCache, never()).invalidarTrasCommit();
    }

    @Test
    void cancelar_citaYaCancelada_lanzaIllegalState() {
        // RN-09 no admite retornos: cancelar dos veces no es idempotente, es un
        // error de quien opera, y silenciarlo ocultaria que otro ya lo hizo.
        Cita cita = citaInminente(Cita.ESTADO_CANCELADA);
        when(citaRepository.findById(cita.getId())).thenReturn(Optional.of(cita));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> servicio.cancelar(cita.getId(), "Otro motivo"));

        assertTrue(error.getMessage().contains("CANCELADA"));
        verify(historialRepository, never()).save(any());
        verify(franjasCache, never()).invalidarTrasCommit();
    }

    @Test
    void cancelar_citaYaAtendida_lanzaIllegalState() {
        Cita cita = citaInminente("ATENDIDA");
        when(citaRepository.findById(cita.getId())).thenReturn(Optional.of(cita));

        assertThrows(IllegalStateException.class, () -> servicio.cancelar(cita.getId(), "Motivo"));
    }

    @Test
    void cancelar_sinAutenticacion_registraLaTransicionSinResponsable() {
        // Perder la bitacora entera por no poder nombrar al responsable seria
        // peor que registrarla sin el; la autorizacion ya la resolvio el filtro.
        SecurityContextHolder.clearContext();
        Cita cita = citaInminente(Cita.ESTADO_CONFIRMADA);
        when(citaRepository.findById(cita.getId())).thenReturn(Optional.of(cita));

        servicio.cancelar(cita.getId(), "Cancelacion de sistema");

        ArgumentCaptor<CitaHistorial> fila = ArgumentCaptor.forClass(CitaHistorial.class);
        verify(historialRepository).save(fila.capture());
        assertNull(fila.getValue().getUsuario());
        assertEquals("CANCELADA", cita.getEstado());
    }

    @Test
    void cancelar_devuelveLaHoraLocalDeLaClinica() {
        Cita cita = prepararConfirmada();
        OffsetDateTime local = cita.getInicio().atZoneSameInstant(LIMA).toOffsetDateTime();

        CitaResponseDTO respuesta = servicio.cancelar(cita.getId(), "El paciente reprograma");

        assertEquals(local.toLocalDate(), respuesta.getFecha());
        assertEquals(local.toLocalTime(), respuesta.getHora());
        assertEquals("America/Lima", respuesta.getZonaHoraria());
        assertEquals(30, respuesta.getDuracionMinutos());
    }

    @Test
    void cancelar_subjectQueNoEsUuid_registraSinResponsableEnVezDeFallar() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("no-es-un-uuid", null,
                        List.of(new SimpleGrantedAuthority("SCOPE_RECEPCIONISTA"))));
        Cita cita = citaInminente(Cita.ESTADO_CONFIRMADA);
        when(citaRepository.findById(cita.getId())).thenReturn(Optional.of(cita));

        servicio.cancelar(cita.getId(), "Motivo");

        ArgumentCaptor<CitaHistorial> fila = ArgumentCaptor.forClass(CitaHistorial.class);
        verify(historialRepository).save(fila.capture());
        assertNull(fila.getValue().getUsuario());
    }

    @Test
    void cancelar_citaDeOtroDia_tambienSeCancela() {
        // No hay ventana por arriba tampoco: recepcion cancela lo que haga falta.
        Cita cita = citaInminente(Cita.ESTADO_CONFIRMADA);
        OffsetDateTime lejos = ZonedDateTime.of(LocalDate.now().plusDays(60), LocalTime.of(9, 0), LIMA)
                .toOffsetDateTime();
        cita.setInicio(lejos);
        cita.setFin(lejos.plusMinutes(30));
        when(citaRepository.findById(cita.getId())).thenReturn(Optional.of(cita));
        lenient().when(usuarioRepository.findById(recepcionistaId))
                .thenReturn(Optional.of(recepcionista()));

        servicio.cancelar(cita.getId(), "El paciente reprograma");

        assertEquals("CANCELADA", cita.getEstado());
    }
}
