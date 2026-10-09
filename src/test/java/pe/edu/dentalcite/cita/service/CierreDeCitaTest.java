package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.plan.service.EnlaceDeSesiones;
import pe.edu.dentalcite.plan.service.EnlaceDeSesiones.SesionOcupada;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * Las reglas de HU-16, una por prueba. La autorización de propiedad se delega en
 * {@code OdontologoOwnershipGuard}, que ya tiene las suyas; aquí se fija que se
 * la consulte y en qué orden respecto del estado.
 */
@ExtendWith(MockitoExtension.class)
class CierreDeCitaTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    @Mock private CitaRepository citaRepository;
    @Mock private CitaHistorialRepository historialRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private EnlaceDeSesiones enlaceDeSesiones;

    private CierreDeCita servicio;

    private UUID recepcionistaId;
    private Ficha fichaDelOdontologo;
    private Odontologo odontologo;

    @BeforeEach
    void setUp() {
        servicio = new CierreDeCita(citaRepository, historialRepository, usuarioRepository,
                enlaceDeSesiones, "America/Lima");
        recepcionistaId = UUID.randomUUID();

        fichaDelOdontologo = Ficha.builder().id(UUID.randomUUID()).numeroHistoria("HC-00009")
                .nombres("Luis").apellidos("Perez").build();
        odontologo = Odontologo.builder().id(UUID.randomUUID()).cop("COP-1")
                .nombres("Luis").apellidos("Perez").ficha(fichaDelOdontologo).activo(true).build();

        autenticar(recepcionistaId, "SCOPE_RECEPCIONISTA");
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID quien, String autoridad) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(quien.toString(), null,
                        List.of(new SimpleGrantedAuthority(autoridad))));
    }

    /** @param terminoHace hace cuántas horas terminó; negativo para el futuro. */
    private Cita cita(String estado, long terminoHace) {
        OffsetDateTime fin = OffsetDateTime.now(LIMA).minusHours(terminoHace);
        Cita cita = Cita.builder()
                .id(UUID.randomUUID()).codigo("CIT-000001")
                .ficha(Ficha.builder().id(UUID.randomUUID()).numeroHistoria("HC-00001").build())
                .odontologo(odontologo)
                .tratamiento(Tratamiento.builder().id(UUID.randomUUID()).nombre("Endodoncia")
                        .duracionMinutos(30).build())
                .consultorio(Consultorio.builder().id(UUID.randomUUID()).nombre("Consultorio 2")
                        .inoperativo(false).build())
                .inicio(fin.minusMinutes(30)).fin(fin)
                .estado(estado)
                .build();
        lenient().when(citaRepository.findParaTransicion(cita.getId())).thenReturn(Optional.of(cita));
        return cita;
    }

    // ------------------------------------------------------------------
    // Criterios 1 y 2 · las dos transiciones de RN-09
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_atendida_dejaLaCitaAtendida() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);

        CitaResponseDTO respuesta = servicio.registrarResultado(cita.getId(), "ATENDIDA");

        assertEquals("ATENDIDA", respuesta.getEstado());
        assertEquals("ATENDIDA", cita.getEstado());
    }

    @Test
    void registrarResultado_noAsistio_dejaLaCitaEnNoAsistio() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);

        assertEquals("NO_ASISTIO", servicio.registrarResultado(cita.getId(), "NO_ASISTIO").getEstado());
    }

    @Test
    void registrarResultado_escribeLaTransicionConSuResponsable() {
        // RF-21: «cada transicion con fecha, usuario y motivo». La fecha la pone
        // la base con su valor por defecto; el usuario, esta prueba.
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);
        Usuario recepcionista = Usuario.builder().id(recepcionistaId).rol("RECEPCIONISTA")
                .correo("recepcion@dentalcite.com").build();
        when(usuarioRepository.findById(recepcionistaId)).thenReturn(Optional.of(recepcionista));

        servicio.registrarResultado(cita.getId(), "ATENDIDA");

        ArgumentCaptor<CitaHistorial> fila = ArgumentCaptor.forClass(CitaHistorial.class);
        verify(historialRepository).save(fila.capture());
        assertEquals("CONFIRMADA", fila.getValue().getEstadoAnterior());
        assertEquals("ATENDIDA", fila.getValue().getEstadoNuevo());
        assertEquals(recepcionistaId, fila.getValue().getUsuario().getId());
        // Sin motivo: RF-22 no pide ninguno y el resultado se explica solo.
        assertNull(fila.getValue().getMotivo());
    }

    // ------------------------------------------------------------------
    // La cita atendida ocupa una sesión de su plan
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_atendidaConPlan_devuelveLaSesionQueOcupa() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);
        UUID planId = UUID.randomUUID();
        when(enlaceDeSesiones.enlazarCitaAtendida(cita))
                .thenReturn(Optional.of(new SesionOcupada(planId, 2, "Endodoncia")));

        CitaResponseDTO respuesta = servicio.registrarResultado(cita.getId(), "ATENDIDA");

        assertEquals(planId, respuesta.getSesionEnlazada().getPlanId());
        assertEquals(2, respuesta.getSesionEnlazada().getNumero());
        assertEquals("Endodoncia", respuesta.getSesionEnlazada().getTratamiento());
    }

    @Test
    void registrarResultado_atendidaSinPlan_noTraeSesion() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);
        when(enlaceDeSesiones.enlazarCitaAtendida(cita)).thenReturn(Optional.empty());

        assertNull(servicio.registrarResultado(cita.getId(), "ATENDIDA").getSesionEnlazada());
    }

    @Test
    void registrarResultado_noAsistio_noIntentaOcuparNingunaSesion() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);

        assertNull(servicio.registrarResultado(cita.getId(), "NO_ASISTIO").getSesionEnlazada());
        verify(enlaceDeSesiones, never()).enlazarCitaAtendida(any());
    }

    @Test
    void registrarResultado_queNoPuedeRegistrarse_noIntentaOcuparNingunaSesion() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, -3);

        assertThrows(IllegalStateException.class,
                () -> servicio.registrarResultado(cita.getId(), "ATENDIDA"));
        verify(enlaceDeSesiones, never()).enlazarCitaAtendida(any());
    }

    // ------------------------------------------------------------------
    // Criterio 3 · un estado final no se reescribe (RN-09)
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_sobreUnaCitaCancelada_lanzaIllegalState() {
        Cita cita = cita(Cita.ESTADO_CANCELADA, 2);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> servicio.registrarResultado(cita.getId(), "ATENDIDA"));

        assertTrue(ex.getMessage().contains("final"), ex.getMessage());
        verify(historialRepository, never()).save(any());
    }

    @Test
    void registrarResultado_sobreUnaCitaYaAtendida_lanzaIllegalState() {
        // Ni siquiera para corregirse: RN-09 no admite retornos.
        Cita cita = cita(Cita.ESTADO_ATENDIDA, 2);

        assertThrows(IllegalStateException.class,
                () -> servicio.registrarResultado(cita.getId(), "NO_ASISTIO"));
    }

    // ------------------------------------------------------------------
    // La cita tiene que haber terminado
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_sobreUnaCitaQueNoHaTerminado_lanzaIllegalState() {
        // Sacarla de CONFIRMADA la retiraria de la restriccion de exclusion de
        // V13, que es parcial sobre ese estado, y su franja —todavia ocupada de
        // verdad— quedaria libre para que otra reserva se le pusiera encima.
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, -3);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> servicio.registrarResultado(cita.getId(), "ATENDIDA"));

        assertTrue(ex.getMessage().contains("todavia no ha terminado"), ex.getMessage());
        verify(citaRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // El resultado tiene que ser uno de los dos
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_conUnResultadoDesconocido_lanzaIllegalArgument() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarResultado(cita.getId(), "ATENDIDO"));

        assertTrue(ex.getMessage().contains("NO_ASISTIO"), ex.getMessage());
    }

    @Test
    void registrarResultado_seValidaAntesDeBuscarLaCita() {
        // Un cuerpo mal escrito es 400 aunque el identificador no exista: mirar
        // primero la base solo cambiaria el error por un 404 mas confuso.
        assertThrows(IllegalArgumentException.class,
                () -> servicio.registrarResultado(UUID.randomUUID(), "loquesea"));

        verify(citaRepository, never()).findById(any());
    }

    @Test
    void registrarResultado_admiteElResultadoEnMinusculas() {
        // Tolerar la caja no es laxitud: el valor sigue teniendo que ser uno de
        // los dos, y quien escribe «atendida» no ha cometido ningun error.
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);

        assertEquals("ATENDIDA", servicio.registrarResultado(cita.getId(), "atendida").getEstado());
    }

    // ------------------------------------------------------------------
    // La cita tiene que existir
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_sobreUnaCitaInexistente_lanzaResourceNotFound() {
        UUID inventada = UUID.randomUUID();
        when(citaRepository.findParaTransicion(inventada)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> servicio.registrarResultado(inventada, "ATENDIDA"));
    }

    // ------------------------------------------------------------------
    // Criterio 5 · RNF-04
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_comoOdontologoDeLaCita_loRegistra() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);
        UUID suUsuarioId = UUID.randomUUID();
        autenticar(suUsuarioId, "SCOPE_ODONTOLOGO");
        when(usuarioRepository.findById(suUsuarioId)).thenReturn(Optional.of(
                Usuario.builder().id(suUsuarioId).rol("ODONTOLOGO").ficha(fichaDelOdontologo).build()));

        assertEquals("ATENDIDA", servicio.registrarResultado(cita.getId(), "ATENDIDA").getEstado());
    }

    @Test
    void registrarResultado_comoOtroOdontologo_lanzaForbidden() {
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);
        UUID otroId = UUID.randomUUID();
        autenticar(otroId, "SCOPE_ODONTOLOGO");
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(
                Usuario.builder().id(otroId).rol("ODONTOLOGO")
                        .ficha(Ficha.builder().id(UUID.randomUUID()).build()).build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarResultado(cita.getId(), "ATENDIDA"));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
        verify(citaRepository, never()).save(any());
    }

    @Test
    void registrarResultado_comoPaciente_lanzaForbidden() {
        // Criterio 5: «incluida la mia». El servicio no se fia de que
        // SecurityConfig ya lo corte: su ficha no es la de ningun odontologo.
        Cita cita = cita(Cita.ESTADO_CONFIRMADA, 2);
        UUID pacienteId = UUID.randomUUID();
        autenticar(pacienteId, "SCOPE_PACIENTE");
        when(usuarioRepository.findById(pacienteId)).thenReturn(Optional.of(
                Usuario.builder().id(pacienteId).rol("PACIENTE")
                        .ficha(Ficha.builder().id(UUID.randomUUID()).build()).build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarResultado(cita.getId(), "ATENDIDA"));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void registrarResultado_laPropiedadSeCompruebaAntesQueElEstado() {
        // RNF-04: si el 409 saliera primero, un odontologo ajeno averiguaria por
        // el codigo de respuesta en que estado esta una cita que no es suya.
        Cita cita = cita(Cita.ESTADO_CANCELADA, 2);
        UUID otroId = UUID.randomUUID();
        autenticar(otroId, "SCOPE_ODONTOLOGO");
        when(usuarioRepository.findById(otroId)).thenReturn(Optional.of(
                Usuario.builder().id(otroId).rol("ODONTOLOGO")
                        .ficha(Ficha.builder().id(UUID.randomUUID()).build()).build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.registrarResultado(cita.getId(), "ATENDIDA"));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }
}
