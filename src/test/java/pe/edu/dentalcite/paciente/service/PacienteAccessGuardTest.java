package pe.edu.dentalcite.paciente.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * RNF-06 y RF-08: quién puede leer la ficha de quién.
 *
 * <p>Es la clase que decide el cuarto criterio de HU-13, y sus cuatro caminos
 * son cuatro reglas distintas, no cuatro variantes de la misma: por eso se
 * prueban aquí, sin levantar el contexto, y no solo a través de HTTP.
 */
@ExtendWith(MockitoExtension.class)
class PacienteAccessGuardTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CitaRepository citaRepository;

    @InjectMocks
    private PacienteAccessGuard guard;

    private final UUID fichaDelPaciente = UUID.randomUUID();

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    /** El subject del JWT es el UUID del usuario, como lo emite JwtService. */
    private UUID autenticar(String autoridad) {
        UUID usuarioId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuarioId.toString(), "n/a",
                        List.of(new SimpleGrantedAuthority(autoridad))));
        return usuarioId;
    }

    private void cuentaCon(UUID usuarioId, UUID fichaId) {
        Usuario usuario = Usuario.builder().id(usuarioId).build();
        if (fichaId != null) {
            usuario.setFicha(Ficha.builder().id(fichaId).build());
        }
        lenient().when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    }

    @Test
    void verificarLectura_comoRecepcion_pasaSinConsultarNada() {
        autenticar("SCOPE_RECEPCIONISTA");

        assertDoesNotThrow(() -> guard.verificarLectura(fichaDelPaciente));

        // Ni siquiera resuelve la cuenta: recepción ve cualquier ficha.
        verifyNoInteractions(usuarioRepository, citaRepository);
    }

    @Test
    void verificarLectura_comoOdontologoQueLoAtendio_pasa() {
        UUID usuarioId = autenticar("SCOPE_ODONTOLOGO");
        UUID fichaDelOdontologo = UUID.randomUUID();
        cuentaCon(usuarioId, fichaDelOdontologo);
        when(citaRepository.atendioAPorFichaDelOdontologo(fichaDelOdontologo, fichaDelPaciente))
                .thenReturn(true);

        assertDoesNotThrow(() -> guard.verificarLectura(fichaDelPaciente));
    }

    /**
     * El criterio 4 de HU-13. Una cita CANCELADA no cuenta, y quien lo decide es
     * la consulta: aquí basta con que devuelva false para exigir el 403.
     */
    @Test
    void verificarLectura_comoOdontologoSinCitasConEsaPersona_devuelve403() {
        UUID usuarioId = autenticar("SCOPE_ODONTOLOGO");
        UUID fichaDelOdontologo = UUID.randomUUID();
        cuentaCon(usuarioId, fichaDelOdontologo);
        when(citaRepository.atendioAPorFichaDelOdontologo(fichaDelOdontologo, fichaDelPaciente))
                .thenReturn(false);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guard.verificarLectura(fichaDelPaciente));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void verificarLectura_comoPacienteSobreSuPropiaFicha_pasa() {
        UUID usuarioId = autenticar("SCOPE_PACIENTE");
        cuentaCon(usuarioId, fichaDelPaciente);

        assertDoesNotThrow(() -> guard.verificarLectura(fichaDelPaciente));
    }

    @Test
    void verificarLectura_comoPacienteSobreLaFichaDeOtro_devuelve403() {
        UUID usuarioId = autenticar("SCOPE_PACIENTE");
        cuentaCon(usuarioId, UUID.randomUUID());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guard.verificarLectura(fichaDelPaciente));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    /** Falla cerrado: sin credenciales no se acredita relación con nadie. */
    @Test
    void verificarLectura_sinAutenticacion_devuelve401() {
        SecurityContextHolder.clearContext();

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guard.verificarLectura(fichaDelPaciente));
        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void odontologoDelListado_comoAdministrador_noRestringeElListado() {
        autenticar("SCOPE_ADMINISTRADOR");

        assertNull(guard.odontologoDelListado());
    }

    @Test
    void odontologoDelListado_comoOdontologo_devuelveSuFicha() {
        UUID usuarioId = autenticar("SCOPE_ODONTOLOGO");
        UUID fichaDelOdontologo = UUID.randomUUID();
        cuentaCon(usuarioId, fichaDelOdontologo);

        assertEquals(fichaDelOdontologo, guard.odontologoDelListado());
    }

    /**
     * Una cuenta ODONTOLOGO sin ficha no resuelve a ningún registro. Devolver
     * {@code null} la trataría como recepción y le abriría todas las fichas, que
     * es lo contrario de lo que RF-07 le concede.
     */
    @Test
    void odontologoDelListado_conCuentaSinFicha_devuelve403() {
        UUID usuarioId = autenticar("SCOPE_ODONTOLOGO");
        cuentaCon(usuarioId, null);

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> guard.odontologoDelListado());
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }
}
