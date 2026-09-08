package pe.edu.dentalcite.auth.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.auth.service.JwtService;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ciclo de vida de la sesión con tokens JWT reales, a través de toda la cadena de
 * filtros. Cubre HU-03 (RF-03, «cerrar la sesión invalidando el token en curso») y
 * el filtro de contraseña provisional de HU-04.
 *
 * <p>Deliberadamente <strong>sin</strong> {@code @Transactional}: la invalidación
 * de la caché de vigencia se engancha al commit, de modo que envolver el test en
 * una transacción que hace rollback nunca la dispararía y la prueba no ejercería
 * el comportamiento real.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SesionIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private Usuario crearCuenta(String rol, boolean requiereCambioPassword) {
        return usuarioRepository.save(Usuario.builder()
                .nombre("Cuenta de Prueba")
                .correo("sesion-" + UUID.randomUUID() + "@dentalcite.com")
                .contrasenaHash(passwordEncoder.encode("Password123"))
                .rol(rol)
                .activo(true)
                .requiereCambioPassword(requiereCambioPassword)
                .build());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void tokenValido_permiteOperarSobreLaCuentaPropia() {
        Usuario usuario = crearCuenta("PACIENTE", false);
        String token = jwtService.generateToken(usuario);

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.correo").value(usuario.getCorreo())));
    }

    @Test
    void logout_invalidaElTokenEnLaPeticionSiguiente() throws Exception {
        // HU-03: «Dado que cierro sesión, cuando reutilice el token anterior,
        // entonces recibiré 401 aunque no hayan pasado las ocho horas.» (RF-03)
        Usuario usuario = crearCuenta("PACIENTE", false);
        String token = jwtService.generateToken(usuario);

        // 1. El token funciona.
        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());

        // 2. Cierre de sesión.
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        // 3. El mismo token ya no vale, sin esperar a que caduque ni al TTL de la caché.
        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logout_yNuevoLoginInmediato_devuelveUnTokenValido() throws Exception {
        // La marca de vigencia se redondea al segundo hacia arriba, de modo que
        // tras el logout queda por delante del reloj. El token emitido a
        // continuacion no puede nacer revocado: seria un 401 sobre una credencial
        // recien creada.
        Usuario usuario = crearCuenta("PACIENTE", false);

        mockMvc.perform(post("/api/v1/auth/logout")
                .header("Authorization", bearer(jwtService.generateToken(usuario))))
                .andExpect(status().isNoContent());

        String tokenNuevo = jwtService.generateToken(
                usuarioRepository.findById(usuario.getId()).orElseThrow());

        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer(tokenNuevo)))
                .andExpect(status().isOk());
    }

    @Test
    void desactivarLaCuenta_invalidaSuTokenEnLaPeticionSiguiente() throws Exception {
        // HU-04: «cuando la desactive […] su token dejará de ser aceptado en la
        // petición siguiente, sin esperar a que caduque.»
        Usuario admin = crearCuenta("ADMINISTRADOR", false);
        Usuario victima = crearCuenta("RECEPCIONISTA", false);

        String tokenAdmin = jwtService.generateToken(admin);
        String tokenVictima = jwtService.generateToken(victima);

        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer(tokenVictima)))
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .patch("/api/v1/usuarios/" + victima.getId())
                .header("Authorization", bearer(tokenAdmin))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\": false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer(tokenVictima)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinToken_lasRutasProtegidasResponden401() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/tratamientos")).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordProvisional_bloqueaCualquierOperacionSalvoElCambioDeContrasena() throws Exception {
        // HU-04: «podrá entrar con ella y el sistema le exigirá cambiarla antes de operar.»
        Usuario usuario = crearCuenta("RECEPCIONISTA", true);
        String token = jwtService.generateToken(usuario);

        // El cuerpo usa {message}, la misma forma que el resto de errores de la
        // API: antes era un {"error": ...} propio de este filtro, que el cliente
        // no contempla.
        mockMvc.perform(get("/api/v1/tratamientos").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void passwordProvisional_dejaPasarElCambioDeContrasenaPropio() throws Exception {
        Usuario usuario = crearCuenta("RECEPCIONISTA", true);
        String token = jwtService.generateToken(usuario);

        // La ruta está exenta del filtro: es justo la que el usuario tiene que poder usar.
        mockMvc.perform(post("/api/v1/usuarios/me/password")
                .header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\": \"Password123\", \"nuevoPassword\": \"NuevaClave456\"}"))
                .andExpect(status().isNoContent());

        // Y el cambio expulsa las sesiones previas (RF-04).
        mockMvc.perform(get("/api/v1/usuarios/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinPasswordProvisional_elFiltroNoEstorba() throws Exception {
        Usuario usuario = crearCuenta("ADMINISTRADOR", false);
        String token = jwtService.generateToken(usuario);

        mockMvc.perform(get("/api/v1/tratamientos").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }
}
