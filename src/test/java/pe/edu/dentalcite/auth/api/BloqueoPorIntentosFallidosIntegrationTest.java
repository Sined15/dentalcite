package pe.edu.dentalcite.auth.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RNF-05 contra la base de datos real: «el sistema bloqueará la cuenta durante
 * cinco minutos tras tres intentos fallidos consecutivos», verificado —dice el
 * propio requisito— con una prueba de integración.
 *
 * <p>Deliberadamente <strong>sin</strong> {@code @Transactional}. Las pruebas de
 * este bloqueo eran unitarias y con repositorios simulados, de modo que
 * comprobaban la llamada a {@code save(...)} pero no el commit: {@code login} es
 * transaccional y termina lanzando una excepción no verificada, así que el
 * {@code UPDATE} del contador se revertía y PostgreSQL nunca guardaba nada. El
 * bloqueo sobrevivía solo en Redis, justo al revés de lo que RNF-05 y RNF-12
 * exigen. Aquí se lee la fila real después de que la petición ha terminado.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BloqueoPorIntentosFallidosIntegrationTest {

    private static final String PASSWORD = "Password123";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    /** Cuenta propia y desechable: bloquear una de demostración rompería otras pruebas. */
    private Usuario crearCuenta() {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Cuenta de Fuerza Bruta")
                .correo("fuerzabruta-" + UUID.randomUUID() + "@dentalcite.com")
                .contrasenaHash(passwordEncoder.encode(PASSWORD))
                .rol("PACIENTE")
                .activo(true)
                .build());
    }

    private org.springframework.test.web.servlet.ResultActions login(String correo, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"password\":\"" + password + "\"}"));
    }

    private int intentosFallidosEnBd(UUID id) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT intentos_fallidos FROM usuarios WHERE id = ?", Integer.class, id);
        return n == null ? -1 : n;
    }

    /** Se compara en SQL para no depender del mapeo JDBC de {@code timestamptz}. */
    private boolean bloqueoVigenteEnBd(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT bloqueado_hasta IS NOT NULL AND bloqueado_hasta > now() FROM usuarios WHERE id = ?",
                Boolean.class, id));
    }

    private boolean sinBloqueoEnBd(UUID id) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT bloqueado_hasta IS NULL FROM usuarios WHERE id = ?", Boolean.class, id));
    }

    @Test
    void tresIntentosFallidos_bloqueanLaCuentaYElBloqueoQuedaEnPostgreSQL() throws Exception {
        Usuario usuario = crearCuenta();
        String correo = usuario.getCorreo();

        // HU-03: «401, 401, y al tercero 423».
        login(correo, "incorrecta").andExpect(status().isUnauthorized());
        assertEquals(1, intentosFallidosEnBd(usuario.getId()), "el primer fallo debe quedar contado en la BD");

        login(correo, "incorrecta").andExpect(status().isUnauthorized());
        assertEquals(2, intentosFallidosEnBd(usuario.getId()));

        login(correo, "incorrecta").andExpect(status().is(423));

        assertEquals(3, intentosFallidosEnBd(usuario.getId()));
        assertTrue(bloqueoVigenteEnBd(usuario.getId()),
                "RNF-05: el bloqueo debe persistir en PostgreSQL, no solo en Redis");
    }

    @Test
    void conElBloqueoSoloEnPostgreSQL_laContrasenaCorrectaSigueRecibiendo423() throws Exception {
        // HU-03: «Dados tres intentos fallidos consecutivos, cuando lo intente de
        // nuevo con la contraseña correcta, entonces recibiré 423». Y RNF-12: la
        // caché no puede ser la única barrera, así que se borra la marca de Redis
        // antes de reintentar y el rechazo debe seguir viniendo de PostgreSQL.
        Usuario usuario = crearCuenta();
        String correo = usuario.getCorreo();

        for (int i = 0; i < 3; i++) {
            login(correo, "incorrecta");
        }

        redisTemplate.delete("login_attempts:" + correo);

        login(correo, PASSWORD).andExpect(status().is(423));
    }

    @Test
    void alCaducarElBloqueo_laCuentaRecuperaSusTresIntentos() throws Exception {
        // RNF-05 concede tres intentos por ventana. Con el contador arrastrado del
        // bloqueo anterior, el primer fallo posterior volvía a bloquear la cuenta.
        Usuario usuario = crearCuenta();
        String correo = usuario.getCorreo();

        jdbcTemplate.update(
                "UPDATE usuarios SET intentos_fallidos = 3, bloqueado_hasta = now() - interval '1 minute' WHERE id = ?",
                usuario.getId());
        redisTemplate.delete("login_attempts:" + correo);

        login(correo, "incorrecta").andExpect(status().isUnauthorized());

        assertEquals(1, intentosFallidosEnBd(usuario.getId()), "la ventana caducada debe reiniciar el conteo");
        assertTrue(sinBloqueoEnBd(usuario.getId()), "la marca vencida debe limpiarse, no arrastrarse");
    }
}
