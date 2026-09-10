package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Quién puede reservar para quién (HU-09, HU-14 · RF-17 · RNF-04).
 *
 * <p>Es la tabla del javadoc de {@link AutorDeLaReserva}, con una prueba por
 * celda. Se prueba aquí y no en {@code CitaServiceTest} porque es una decisión
 * completa en sí misma: no necesita catálogo, ni franja, ni base de datos.
 */
@ExtendWith(MockitoExtension.class)
class AutorDeLaReservaTest {

    @Mock private UsuarioRepository usuarioRepository;
    @Mock private FichaRepository fichaRepository;

    private AutorDeLaReserva autor;

    private UUID usuarioId;
    private Ficha fichaPropia;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        autor = new AutorDeLaReserva(usuarioRepository, fichaRepository);
        usuarioId = UUID.randomUUID();
        fichaPropia = Ficha.builder().id(UUID.randomUUID()).documento("12345678")
                .nombres("Ana").apellidos("Torres").numeroHistoria("HC-00001").build();
        usuario = Usuario.builder().id(usuarioId).correo("paciente@demo.com").rol("PACIENTE")
                .activo(true).ficha(fichaPropia).build();
    }

    @AfterEach
    void limpiarContexto() {
        // Sin esto el contexto de seguridad se filtra entre pruebas y las de 403
        // pasarían por la razón equivocada.
        SecurityContextHolder.clearContext();
    }

    private void autenticar(String nombre, String autoridad) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nombre, "n/a",
                        List.of(new SimpleGrantedAuthority(autoridad))));
    }

    private static int estado(ResponseStatusException ex) {
        return ex.getStatusCode().value();
    }

    // ------------------------------------------------------------------
    // PACIENTE · HU-09, para sí mismo
    // ------------------------------------------------------------------

    @Test
    void resolver_comoPacienteSinPacienteId_devuelveSuPropiaFicha() {
        autenticar(usuarioId.toString(), "SCOPE_PACIENTE");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        AutorDeLaReserva.Reserva reserva = autor.resolver(null);

        assertEquals(fichaPropia.getId(), reserva.fichaId());
        assertEquals(usuarioId, reserva.usuarioId(), "quien reserva es él mismo");
    }

    @Test
    void resolver_comoPacienteConLaFichaDeOtro_lanzaForbidden() {
        // Criterio 4 de HU-14.
        autenticar(usuarioId.toString(), "SCOPE_PACIENTE");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> autor.resolver(UUID.randomUUID()));

        assertEquals(HttpStatus.FORBIDDEN.value(), estado(ex));
    }

    @Test
    void resolver_comoPacienteConSuPropiaFicha_tambienLanzaForbidden() {
        // Aceptarlo cuando coincide y negarlo cuando no convertiría la respuesta
        // en un oráculo: probando identificadores se averiguaría el de otro
        // paciente. Sin ese campo no hay nada que sondear.
        autenticar(usuarioId.toString(), "SCOPE_PACIENTE");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> autor.resolver(fichaPropia.getId()));

        assertEquals(HttpStatus.FORBIDDEN.value(), estado(ex));
        verify(usuarioRepository, never()).findById(usuarioId);
    }

    @Test
    void resolver_comoPacienteSinFicha_lanzaForbidden() {
        autenticar(usuarioId.toString(), "SCOPE_PACIENTE");
        usuario.setFicha(null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> autor.resolver(null));

        assertEquals(HttpStatus.FORBIDDEN.value(), estado(ex));
    }

    // ------------------------------------------------------------------
    // RECEPCIONISTA y ADMINISTRADOR · HU-14, en nombre de otro
    // ------------------------------------------------------------------

    @Test
    void resolver_comoRecepcionistaConPacienteId_devuelveEsaFichaYAlRecepcionistaComoAutor() {
        UUID fichaAjena = UUID.randomUUID();
        autenticar(usuarioId.toString(), "SCOPE_RECEPCIONISTA");
        when(fichaRepository.existsById(fichaAjena)).thenReturn(true);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        AutorDeLaReserva.Reserva reserva = autor.resolver(fichaAjena);

        assertEquals(fichaAjena, reserva.fichaId(), "la cita es del paciente");
        assertEquals(usuarioId, reserva.usuarioId(), "pero la encargó quien atiende el mostrador");
    }

    @Test
    void resolver_comoAdministradorConPacienteId_tambienReserva() {
        UUID fichaAjena = UUID.randomUUID();
        autenticar(usuarioId.toString(), "SCOPE_ADMINISTRADOR");
        when(fichaRepository.existsById(fichaAjena)).thenReturn(true);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        assertEquals(fichaAjena, autor.resolver(fichaAjena).fichaId());
    }

    @Test
    void resolver_comoRecepcionistaSobreFichaSinCuenta_reservaIgual() {
        // Criterio 2 de HU-14. Sale gratis porque la ficha se busca en
        // FichaRepository y no entre las cuentas: una ficha sin `Usuario` es
        // justo lo que crea el alta presencial de HU-12.
        UUID fichaSinCuenta = UUID.randomUUID();
        autenticar(usuarioId.toString(), "SCOPE_RECEPCIONISTA");
        when(fichaRepository.existsById(fichaSinCuenta)).thenReturn(true);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        assertEquals(fichaSinCuenta, autor.resolver(fichaSinCuenta).fichaId());
        verify(fichaRepository).existsById(fichaSinCuenta);
    }

    @Test
    void resolver_comoRecepcionistaSinPacienteId_lanzaIllegalArgument() {
        // 400 y no 403: el rol sí puede reservar, lo que falta es decir para
        // quién. Su cuenta no tiene ficha propia que suponer.
        autenticar(usuarioId.toString(), "SCOPE_RECEPCIONISTA");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> autor.resolver(null));

        org.junit.jupiter.api.Assertions.assertTrue(ex.getMessage().contains("pacienteId"));
    }

    @Test
    void resolver_comoRecepcionistaConPacienteInexistente_lanzaResourceNotFound() {
        UUID inventada = UUID.randomUUID();
        autenticar(usuarioId.toString(), "SCOPE_RECEPCIONISTA");
        when(fichaRepository.existsById(inventada)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> autor.resolver(inventada));
    }

    // ------------------------------------------------------------------
    // Resto de roles y credenciales · RNF-04
    // ------------------------------------------------------------------

    @Test
    void resolver_comoOdontologo_lanzaForbidden() {
        // La Tabla 10 no le concede reservar, ni para sí ni para otro.
        autenticar(usuarioId.toString(), "SCOPE_ODONTOLOGO");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> autor.resolver(null));

        assertEquals(HttpStatus.FORBIDDEN.value(), estado(ex));
    }

    @Test
    void resolver_sinAutenticacion_lanzaUnauthorized() {
        SecurityContextHolder.clearContext();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> autor.resolver(null));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), estado(ex));
    }

    @Test
    void resolver_conSubjectQueNoEsUuid_lanzaUnauthorized() {
        autenticar("no-es-un-uuid", "SCOPE_PACIENTE");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> autor.resolver(null));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), estado(ex));
    }

    @Test
    void resolver_conUsuarioQueYaNoExiste_lanzaUnauthorized() {
        autenticar(usuarioId.toString(), "SCOPE_PACIENTE");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> autor.resolver(null));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), estado(ex));
    }
}
