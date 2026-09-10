package pe.edu.dentalcite.paciente.api;

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
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-12, RF-06: el alta presencial de extremo a extremo, con sus tres primeros
 * criterios y las pruebas negativas de autorización que la Definición de
 * Terminado exige de toda operación nueva (RNF-04).
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class RegistroPresencialIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /** Cada prueba estrena documento: la base sobrevive de un método al siguiente. */
    private static String documentoNuevo() {
        return String.valueOf(ThreadLocalRandom.current().nextLong(40_000_000L, 79_999_999L));
    }

    private static String cuerpo(String documento) {
        return """
                {"nombres":"Rosa","apellidos":"Huaman","tipoDocumento":"DNI",
                 "documento":"%s","telefono":"987654321",
                 "consentimientoAceptado":true,"versionConsentimiento":"1.0"}
                """.formatted(documento);
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void registrarPaciente_conDatosValidos_creaLaFichaConHistoriaYSinCuenta() throws Exception {
        String respuesta = mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(documentoNuevo())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numeroHistoria").exists())
                .andExpect(jsonPath("$.tieneCuenta").value(false))
                .andExpect(jsonPath("$.nombres").value("Rosa"))
                .andReturn().getResponse().getContentAsString();

        JsonNode paciente = objectMapper.readTree(respuesta);
        assertTrue(paciente.path("numeroHistoria").asText().startsWith("HC-"),
                "el número de historia sale de la secuencia con el formato HC-%05d");
        // «Sin credenciales de acceso»: la ficha nace sin ninguna cuenta asociada.
        assertFalse(usuarioRepository.existsByFichaId(
                java.util.UUID.fromString(paciente.path("id").asText())));
    }

    /** Criterio 2, RN-10: la identidad es el par (tipo, número) de documento. */
    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void registrarPaciente_conDocumentoYaRegistrado_devuelve409() throws Exception {
        String documento = documentoNuevo();

        mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(documento)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(documento)))
                .andExpect(status().isConflict());
    }

    /** Criterio 3, RNF-06: sin consentimiento del titular no hay alta. */
    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void registrarPaciente_sinConsentimiento_devuelve400() throws Exception {
        String sinConsentimiento = """
                {"nombres":"Rosa","apellidos":"Huaman","tipoDocumento":"DNI",
                 "documento":"%s","versionConsentimiento":"1.0",
                 "consentimientoAceptado":false}
                """.formatted(documentoNuevo());

        mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sinConsentimiento))
                .andExpect(status().isBadRequest());

        String omitido = """
                {"nombres":"Rosa","apellidos":"Huaman","tipoDocumento":"DNI",
                 "documento":"%s","versionConsentimiento":"1.0"}
                """.formatted(documentoNuevo());

        mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(omitido))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void registrarPaciente_comoAdministrador_tambienEstaAutorizado() throws Exception {
        mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(documentoNuevo())))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void registrarPaciente_comoPaciente_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(documentoNuevo())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ODONTOLOGO")
    void registrarPaciente_comoOdontologo_devuelve403() throws Exception {
        mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(documentoNuevo())))
                .andExpect(status().isForbidden());
    }
}
