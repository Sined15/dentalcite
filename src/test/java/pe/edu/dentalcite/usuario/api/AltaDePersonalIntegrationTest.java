package pe.edu.dentalcite.usuario.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-04, RF-04: el alta de personal por el administrador, hasta el primer inicio
 * de sesión de la cuenta creada. El criterio no termina en el 201 —«quedará
 * activa y <em>podrá iniciar sesión</em> con los permisos de ese rol»—, y ese
 * segundo tramo no se estaba comprobando: la contraseña generada se hasheaba y no
 * se devolvía en ninguna parte, de modo que una cuenta creada sin indicar clave
 * nacía inaccesible para siempre.
 *
 * <p>Sin {@code @Transactional}: cada paso es una petición HTTP distinta y la
 * siguiente tiene que ver lo que la anterior confirmó.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AltaDePersonalIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static String correoNuevo() {
        return "alta-" + UUID.randomUUID() + "@dentalcite.com";
    }

    private String crearCuenta(String correo, String rol, String passwordProvisional) throws Exception {
        String cuerpo = passwordProvisional == null
                ? "{\"nombre\":\"Persona de Alta\",\"correo\":\"" + correo + "\",\"rol\":\"" + rol + "\"}"
                : "{\"nombre\":\"Persona de Alta\",\"correo\":\"" + correo + "\",\"rol\":\"" + rol
                        + "\",\"passwordProvisional\":\"" + passwordProvisional + "\"}";

        return mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
    }

    private org.springframework.test.web.servlet.ResultActions login(String correo, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\":\"" + correo + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void altaConCorreoNombreYRol_devuelveLaProvisionalYLaCuentaPuedeIniciarSesion() throws Exception {
        String correo = correoNuevo();

        JsonNode creada = objectMapper.readTree(crearCuenta(correo, "RECEPCIONISTA", null));

        assertTrue(creada.hasNonNull("passwordProvisional"),
                "sin la contraseña generada nadie puede entrar nunca a la cuenta");
        String provisional = creada.get("passwordProvisional").asText();
        assertFalse(provisional.isBlank());

        // El tramo que faltaba del criterio: la cuenta entra de verdad.
        login(correo, provisional)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void altaConContrasenaIndicada_noLaRepiteYLaCuentaPuedeIniciarSesion() throws Exception {
        String correo = correoNuevo();

        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Persona de Alta\",\"correo\":\"" + correo
                        + "\",\"rol\":\"RECEPCIONISTA\",\"passwordProvisional\":\"temporal123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passwordProvisional").doesNotExist())
                .andExpect(jsonPath("$.requiereCambioPassword").value(true));

        login(correo, "temporal123").andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void altaDeOdontologo_encadenaConSuRegistroYConLaDeclaracionDeSuHorario() throws Exception {
        // HU-06 / RN-11: «cuando lo vincule a una cuenta de rol ODONTOLOGO, esa
        // cuenta resolverá a su identificador y podrá ejercer las operaciones
        // marcadas “propio”». La cadena entera era inalcanzable por API: el alta
        // creaba cuentas sin ficha, no hay endpoint de fichas, y sin ficha
        // OdontologoOwnershipGuard rechaza siempre. El único camino era registrarse
        // por el portal público como PACIENTE y cambiar el rol a mano.
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        String correo = correoNuevo();

        // 1. Alta del profesional, con su documento.
        JsonNode cuenta = objectMapper.readTree(mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Luis Ramos\",\"correo\":\"" + correo
                        + "\",\"rol\":\"ODONTOLOGO\",\"documento\":\"DOC" + sufijo + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fichaId").exists())
                .andReturn().getResponse().getContentAsString());

        String fichaId = cuenta.get("fichaId").asText();
        String provisional = cuenta.get("passwordProvisional").asText();

        // 2. Registro de odontólogo contra esa ficha (RF-10).
        String especialidadId = objectMapper.readTree(mockMvc.perform(get("/api/v1/especialidades"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .get("content").get(0).get("id").asText();

        String odontologoId = objectMapper.readTree(mockMvc.perform(post("/api/v1/odontologos")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cop\":\"COP-" + sufijo + "\",\"nombres\":\"Luis\",\"apellidos\":\"Ramos\","
                        + "\"fichaId\":\"" + fichaId + "\",\"especialidadesIds\":[\"" + especialidadId + "\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString())
                .get("id").asText();

        // 3. La cuenta entra y estrena su contraseña (F-06).
        String tokenProvisional = objectMapper.readTree(
                login(correo, provisional).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(post("/api/v1/usuarios/me/password")
                .header("Authorization", "Bearer " + tokenProvisional)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"passwordActual\":\"" + provisional + "\",\"nuevoPassword\":\"DefinitivaX9\"}"))
                .andExpect(status().isNoContent());

        String token = objectMapper.readTree(
                login(correo, "DefinitivaX9").andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("token").asText();

        // 4. Y ejerce la operación marcada «propio»: declara su propio horario.
        mockMvc.perform(post("/api/v1/odontologos/" + odontologoId + "/horarios")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"diaSemana\":1,\"horaInicio\":\"09:00:00\",\"horaFin\":\"13:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.odontologoId").value(odontologoId));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void altaDeOdontologoSinDocumento_retorna400() throws Exception {
        // Sin documento no hay ficha, y sin ficha la cuenta nunca podría registrarse
        // como odontólogo ni declarar su horario: se rechaza en el alta.
        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Sin Ficha\",\"correo\":\"" + correoNuevo() + "\",\"rol\":\"ODONTOLOGO\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void contrasenaProvisional_levantaElBloqueoPorIntentosFallidos() throws Exception {
        // HU-04: «Dado un usuario que perdió su contraseña, cuando le asigne una
        // provisional, entonces podrá entrar con ella». Es justo el usuario que la
        // ha fallado tres veces, así que llega con el bloqueo de RNF-05 puesto.
        String correo = correoNuevo();
        JsonNode creada = objectMapper.readTree(crearCuenta(correo, "RECEPCIONISTA", "olvidada123"));
        String id = creada.get("id").asText();

        for (int i = 0; i < 3; i++) {
            login(correo, "loQueSea");
        }
        login(correo, "olvidada123").andExpect(status().is(423));

        mockMvc.perform(post("/api/v1/usuarios/" + id + "/password-provisional")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"NuevaClave123\"}"))
                .andExpect(status().isNoContent());

        login(correo, "NuevaClave123")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    /**
     * F-06 / HU-04: mientras el token exija cambiar la contraseña provisional,
     * {@code PasswordFilter} cierra la API salvo el propio cambio de contraseña.
     * El cuerpo de ese 403 no se estaba comprobando, y era el único error de la
     * API con forma propia —un {@code {"error": ...}} escrito a mano— y sin
     * charset declarado, de modo que la eñe del mensaje llegaba corrompida al
     * cliente, que además solo entiende {@code {message}}.
     */
    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void operarConPasswordProvisional_devuelveForbiddenConMensajeLegible() throws Exception {
        String correo = correoNuevo();
        String provisional = objectMapper.readTree(crearCuenta(correo, "RECEPCIONISTA", null))
                .get("passwordProvisional").asText();

        String token = objectMapper.readTree(
                login(correo, provisional).andExpect(status().isOk())
                        .andReturn().getResponse().getContentAsString())
                .get("token").asText();

        mockMvc.perform(get("/api/v1/tratamientos")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        "Debe cambiar su contraseña provisional antes de operar el sistema."))
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
