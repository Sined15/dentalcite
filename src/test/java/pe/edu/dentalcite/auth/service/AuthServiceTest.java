package pe.edu.dentalcite.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.dentalcite.auth.api.dto.RegistroRequest;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.consentimiento.repository.ConsentimientoRepository;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import org.springframework.security.authentication.LockedException;
import pe.edu.dentalcite.auth.api.dto.LoginRequest;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private FichaRepository fichaRepository;

    @Mock
    private ConsentimientoRepository consentimientoRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private TokenRevocationCache tokenRevocationCache;

    @Mock
    private JwtService jwtService;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AuthService authService;

    private RegistroRequest request;

    @BeforeEach
    void setUp() {
        request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setTipoDocumento("DNI");
        request.setDocumento("12345678");
        request.setCorreo("test@test.com");
        request.setTelefono("987654321");
        request.setPassword("secreta123");
        request.setConsentimientoAceptado(true);
        request.setVersionConsentimiento("v1.0");
    }

    @Test
    void registrarPaciente_exitoNuevaFicha() {
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(fichaRepository.findByTipoDocumentoAndDocumento(anyString(), anyString())).thenReturn(Optional.empty());
        when(fichaRepository.getNextHistoriaClinica()).thenReturn(1L);
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArguments()[0]);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArguments()[0]);

        assertDoesNotThrow(() -> authService.registrarPaciente(request));

        verify(fichaRepository, times(1)).save(any(Ficha.class));
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
        verify(consentimientoRepository, times(1)).save(any(Consentimiento.class));
    }

    @Test
    void registrarPaciente_errorCorreoExistente() {
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> authService.registrarPaciente(request));

        verify(fichaRepository, never()).save(any(Ficha.class));
        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrarPaciente_exitoVincularFichaExistente() {
        Ficha fichaExistente = Ficha.builder().id(UUID.randomUUID()).documento("12345678").build();
        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(fichaRepository.findByTipoDocumentoAndDocumento(anyString(), anyString())).thenReturn(Optional.of(fichaExistente));
        // Simular que ningun usuario tiene esta ficha vinculada todavia
        when(usuarioRepository.existsByFichaId(any(UUID.class))).thenReturn(false);
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArguments()[0]);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArguments()[0]);

        assertDoesNotThrow(() -> authService.registrarPaciente(request));

        // La ficha existente se completa con los datos del registro (telefono, nombres),
        // pero no se crea otra: sigue siendo la misma fila.
        assertEquals("987654321", fichaExistente.getTelefono());
        assertEquals("Ana", fichaExistente.getNombres());
        verify(usuarioRepository, times(1)).save(any(Usuario.class));
    }

    @Test
    void registrarPaciente_errorFichaYaVinculada() {
        Ficha fichaExistente = Ficha.builder().id(UUID.randomUUID()).documento("12345678").build();
        // Usuario usuarioConFicha =
        // Usuario.builder().id(UUID.randomUUID()).ficha(fichaExistente).build();

        when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
        when(fichaRepository.findByTipoDocumentoAndDocumento(anyString(), anyString())).thenReturn(Optional.of(fichaExistente));
        when(usuarioRepository.existsByFichaId(any(UUID.class))).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> authService.registrarPaciente(request));
        assertEquals("La ficha ya tiene una cuenta asociada.", ex.getMessage());

        verify(usuarioRepository, never()).save(any(Usuario.class));
    }

    @Test
    void registrarPaciente_conCorreoEnMayusculas_normalizaAntesDeGuardar() {
        request.setCorreo("Nuevo.Paciente@Test.COM");

        when(usuarioRepository.existsByCorreo("nuevo.paciente@test.com")).thenReturn(false);
        when(fichaRepository.findByTipoDocumentoAndDocumento(anyString(), anyString())).thenReturn(Optional.empty());
        when(fichaRepository.getNextHistoriaClinica()).thenReturn(1L);
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArguments()[0]);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-pwd");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArguments()[0]);

        assertDoesNotThrow(() -> authService.registrarPaciente(request));

        verify(usuarioRepository).existsByCorreo("nuevo.paciente@test.com");
        org.mockito.ArgumentCaptor<Usuario> captor = org.mockito.ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        assertEquals("nuevo.paciente@test.com", captor.getValue().getCorreo());
    }

    @Test
    void login_exito_devuelveTokenYBorraLock() {
        LoginRequest loginReq = new LoginRequest("admin@dentalcite.com", "Password123");
        Usuario usuario = Usuario.builder().correo("admin@dentalcite.com").contrasenaHash("hash").activo(true).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:admin@dentalcite.com")).thenReturn(null);
        when(usuarioRepository.findByCorreo("admin@dentalcite.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Password123", "hash")).thenReturn(true);
        when(jwtService.generateToken(usuario)).thenReturn("mocked.jwt.token");

        String token = authService.login(loginReq);

        assertEquals("mocked.jwt.token", token);
        verify(redisTemplate, times(1)).delete("login_attempts:admin@dentalcite.com");
    }

    @Test
    void login_fuerzaBruta_bloqueaAlTercerIntento() {
        LoginRequest loginReq = new LoginRequest("hacker@dentalcite.com", "incorrecta");
        // RNF-05: PostgreSQL es la fuente de verdad del conteo; ya lleva 2 fallos.
        Usuario usuario = Usuario.builder().correo("hacker@dentalcite.com").contrasenaHash("hash").activo(true)
                .intentosFallidos(2).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:hacker@dentalcite.com")).thenReturn(null);
        when(valueOperations.increment("login_attempts:hacker@dentalcite.com")).thenReturn(3L);
        when(usuarioRepository.findByCorreo("hacker@dentalcite.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        LockedException ex = assertThrows(LockedException.class, () -> authService.login(loginReq));
        assertEquals("Cuenta bloqueada temporalmente por múltiples intentos fallidos.", ex.getMessage());

        // El bloqueo queda persistido en PostgreSQL...
        assertEquals(3, usuario.getIntentosFallidos());
        assertNotNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository, times(1)).save(usuario);
        // ...y también cacheado en Redis como camino rápido.
        verify(valueOperations, times(1)).set(eq("login_attempts:hacker@dentalcite.com"), eq("LOCKED"),
                any(java.time.Duration.class));
    }

    @Test
    void login_conBloqueoPersistidoYRedisCaido_siguebloqueando() {
        // RNF-05: si Redis falla en la lectura, el bloqueo persistido en
        // PostgreSQL debe seguir aplicándose (antes fallaba abierto).
        LoginRequest loginReq = new LoginRequest("bloqueado@dentalcite.com", "cualquiera");
        Usuario usuario = Usuario.builder().correo("bloqueado@dentalcite.com").contrasenaHash("hash").activo(true)
                .bloqueadoHasta(java.time.OffsetDateTime.now().plusMinutes(10)).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:bloqueado@dentalcite.com"))
                .thenThrow(new RuntimeException("Redis no disponible"));
        when(usuarioRepository.findByCorreo("bloqueado@dentalcite.com")).thenReturn(Optional.of(usuario));

        LockedException ex = assertThrows(LockedException.class, () -> authService.login(loginReq));
        assertEquals("Cuenta bloqueada temporalmente por múltiples intentos fallidos.", ex.getMessage());

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_conBloqueoCaducado_reiniciaElContadorYDevuelveLosTresIntentos() {
        // RNF-05 concede tres intentos por ventana, no uno. La cuenta salió de un
        // bloqueo (contador al máximo, marca ya vencida): el primer fallo posterior
        // debe ser el primero de una ventana nueva —401— y no un re-bloqueo —423—.
        LoginRequest loginReq = new LoginRequest("caducado@dentalcite.com", "incorrecta");
        Usuario usuario = Usuario.builder().correo("caducado@dentalcite.com").contrasenaHash("hash").activo(true)
                .intentosFallidos(3)
                .bloqueadoHasta(java.time.OffsetDateTime.now().minusMinutes(1))
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:caducado@dentalcite.com")).thenReturn(null);
        when(valueOperations.increment("login_attempts:caducado@dentalcite.com")).thenReturn(1L);
        when(usuarioRepository.findByCorreo("caducado@dentalcite.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authService.login(loginReq));

        assertEquals(1, usuario.getIntentosFallidos(), "la ventana caducada debe reiniciar el conteo");
        assertNull(usuario.getBloqueadoHasta(), "la marca vencida debe limpiarse, no arrastrarse");
        verify(redisTemplate, times(1)).delete("login_attempts:caducado@dentalcite.com");
    }

    @Test
    void login_intentosFallidosConcurrentes_usaIncrementoAtomico() {
        // RNF-05: el contador debe usar INCR atómico de Redis (no read-then-write),
        // para que solicitudes concurrentes no pisen el valor leído entre sí.
        LoginRequest loginReq = new LoginRequest("concurrente@dentalcite.com", "incorrecta");
        Usuario usuario = Usuario.builder().correo("concurrente@dentalcite.com").contrasenaHash("hash").activo(true)
                .build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:concurrente@dentalcite.com")).thenReturn(null);
        when(valueOperations.increment("login_attempts:concurrente@dentalcite.com")).thenReturn(1L);
        when(usuarioRepository.findByCorreo("concurrente@dentalcite.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class,
                () -> authService.login(loginReq));

        // Nunca se debe leer el contador para calcular el siguiente valor: solo INCR.
        verify(valueOperations, never()).set(eq("login_attempts:concurrente@dentalcite.com"), anyString(),
                any(java.time.Duration.class));
        verify(redisTemplate, times(1)).expire(eq("login_attempts:concurrente@dentalcite.com"),
                eq(java.time.Duration.ofMinutes(5))); // RNF-05: cinco minutos
    }

    @Test
    void logout_invalidaTokensAnteriores() {
        UUID usuarioId = UUID.randomUUID();
        Usuario usuario = Usuario.builder().id(usuarioId).correo("admin@dentalcite.com").build();

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArguments()[0]);

        authService.logout(usuarioId);

        // Verificamos que se haya establecido un nuevo tokensValidosDesde
        assertNotNull(usuario.getTokensValidosDesde());
        verify(usuarioRepository, times(1)).save(usuario);
    }

    @Test
    void login_conCuentaYaBloqueadaEnRedis_lanzaLockedException() {
        LoginRequest loginReq = new LoginRequest("admin@dentalcite.com", "Password123");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:admin@dentalcite.com")).thenReturn("LOCKED");

        LockedException ex = assertThrows(LockedException.class, () -> authService.login(loginReq));
        assertEquals("Cuenta bloqueada temporalmente por múltiples intentos fallidos.", ex.getMessage());
    }

    @Test
    void login_conUsuarioNoEncontrado_lanzaBadCredentials() {
        LoginRequest loginReq = new LoginRequest("noexiste@dentalcite.com", "Password123");
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:noexiste@dentalcite.com")).thenReturn(null);
        when(usuarioRepository.findByCorreo("noexiste@dentalcite.com")).thenReturn(Optional.empty());

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> authService.login(loginReq));
    }

    @Test
    void login_conCuentaInactiva_lanzaDisabledException() {
        LoginRequest loginReq = new LoginRequest("inactivo@dentalcite.com", "Password123");
        Usuario usuario = Usuario.builder().correo("inactivo@dentalcite.com").activo(false).build();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:inactivo@dentalcite.com")).thenReturn(null);
        when(usuarioRepository.findByCorreo("inactivo@dentalcite.com")).thenReturn(Optional.of(usuario));

        assertThrows(org.springframework.security.authentication.DisabledException.class, () -> authService.login(loginReq));
    }

    @Test
    void login_conContrasenaIncorrecta_incrementaIntentosAtomicamente() {
        LoginRequest loginReq = new LoginRequest("user@dentalcite.com", "incorrecta");
        // Ya lleva 1 fallo previo; este será el 2do.
        Usuario usuario = Usuario.builder().correo("user@dentalcite.com").contrasenaHash("hash").activo(true)
                .intentosFallidos(1).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:user@dentalcite.com")).thenReturn(null);
        when(valueOperations.increment("login_attempts:user@dentalcite.com")).thenReturn(2L);
        when(usuarioRepository.findByCorreo("user@dentalcite.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("incorrecta", "hash")).thenReturn(false);

        assertThrows(org.springframework.security.authentication.BadCredentialsException.class, () -> authService.login(loginReq));

        // Con 2 intentos aún no se bloquea, pero sí queda persistido el conteo
        assertEquals(2, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
        verify(usuarioRepository, times(1)).save(usuario);
        verify(valueOperations, never()).set(eq("login_attempts:user@dentalcite.com"), anyString(), any(java.time.Duration.class));
        verify(valueOperations, times(1)).increment("login_attempts:user@dentalcite.com");
    }

    @Test
    void login_conCorreoEnMayusculas_seNormalizaParaBuscarYCachear() {
        // RN: "User@Dentalcite.com" y "user@dentalcite.com" deben tratarse como la
        // misma cuenta.
        LoginRequest loginReq = new LoginRequest("User@Dentalcite.com", "Password123");
        Usuario usuario = Usuario.builder().correo("user@dentalcite.com").contrasenaHash("hash").activo(true).build();

        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("login_attempts:user@dentalcite.com")).thenReturn(null);
        when(usuarioRepository.findByCorreo("user@dentalcite.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Password123", "hash")).thenReturn(true);
        when(jwtService.generateToken(usuario)).thenReturn("mocked.jwt.token");

        String token = authService.login(loginReq);

        assertEquals("mocked.jwt.token", token);
        verify(usuarioRepository).findByCorreo("user@dentalcite.com");
    }
}
