package pe.edu.dentalcite;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RNF-14 y Definición de Terminado: «el endpoint está documentado en el contrato
 * OpenAPI navegable». No basta con que springdoc genere el documento: si una
 * operación se añade sin describir, el contrato sigue siendo válido pero deja de
 * ser útil. Esta prueba exige que toda operación publicada tenga resumen,
 * descripción y códigos de respuesta.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ContratoOpenApiIntegrationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private JsonNode contrato() throws Exception {
        String json = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    @Test
    void elContratoEsAccesibleSinAutenticacionYSeIdentifica() throws Exception {
        JsonNode raiz = contrato();

        assertEquals("DentalCite API", raiz.path("info").path("title").asText());
        assertEquals("v1", raiz.path("info").path("version").asText());
        assertTrue(raiz.path("components").path("securitySchemes").has("bearerAuth"),
                "el contrato debe declarar el esquema bearerAuth");
    }

    @Test
    void todosLosEndpointsDelSprint1EstanPublicados() throws Exception {
        JsonNode paths = contrato().path("paths");

        for (String ruta : Arrays.asList(
                "/api/v1/auth/registro", "/api/v1/auth/login", "/api/v1/auth/logout",
                "/api/v1/usuarios", "/api/v1/usuarios/{id}", "/api/v1/usuarios/me",
                "/api/v1/usuarios/me/password", "/api/v1/usuarios/{id}/password-provisional",
                "/api/v1/especialidades", "/api/v1/especialidades/{id}",
                "/api/v1/tratamientos", "/api/v1/tratamientos/{id}",
                "/api/v1/odontologos", "/api/v1/odontologos/{id}",
                "/api/v1/odontologos/{odontologoId}/horarios",
                "/api/v1/odontologos/{odontologoId}/horarios/{horarioId}",
                "/api/v1/bloqueos", "/api/v1/bloqueos/{id}",
                "/api/v1/consultorios")) {
            assertTrue(paths.has(ruta), "falta en el contrato la ruta " + ruta);
        }
    }

    @Test
    void todaOperacionPublicadaTieneResumenDescripcionYCodigosDeRespuesta() throws Exception {
        JsonNode paths = contrato().path("paths");
        List<String> sinDocumentar = new ArrayList<>();

        paths.fieldNames().forEachRemaining(ruta -> {
            JsonNode operaciones = paths.path(ruta);
            operaciones.fieldNames().forEachRemaining(metodo -> {
                JsonNode op = operaciones.path(metodo);
                String id = metodo.toUpperCase() + " " + ruta;

                if (op.path("summary").asText("").isBlank()) {
                    sinDocumentar.add(id + " · sin resumen");
                }
                if (op.path("description").asText("").isBlank()) {
                    sinDocumentar.add(id + " · sin descripción");
                }
                if (op.path("responses").size() < 2) {
                    sinDocumentar.add(id + " · declara menos de dos códigos de respuesta");
                }
            });
        });

        assertTrue(sinDocumentar.isEmpty(),
                "operaciones sin documentar en el contrato OpenAPI:\n  " + String.join("\n  ", sinDocumentar));
    }

    @Test
    void cadaOperacionQuedaAgrupadaBajoSuModulo() throws Exception {
        JsonNode paths = contrato().path("paths");
        List<String> sinEtiqueta = new ArrayList<>();

        paths.fieldNames().forEachRemaining(ruta -> {
            JsonNode operaciones = paths.path(ruta);
            operaciones.fieldNames().forEachRemaining(metodo -> {
                if (operaciones.path(metodo).path("tags").isEmpty()) {
                    sinEtiqueta.add(metodo.toUpperCase() + " " + ruta);
                }
            });
        });

        assertTrue(sinEtiqueta.isEmpty(), "operaciones sin agrupar bajo un módulo: " + sinEtiqueta);
    }

    @Test
    void elRegistroYElLoginSeDeclaranPublicos() throws Exception {
        // Son los dos únicos endpoints que SecurityConfig deja en permitAll: el
        // contrato debe reflejarlo en vez de sugerir que exigen token.
        JsonNode paths = contrato().path("paths");

        for (String ruta : Arrays.asList("/api/v1/auth/registro", "/api/v1/auth/login")) {
            JsonNode seguridad = paths.path(ruta).path("post").path("security");
            assertTrue(seguridad.isArray() && seguridad.isEmpty(),
                    ruta + " debe declararse sin requisito de seguridad");
        }

        // El resto sí hereda el requisito global.
        assertFalse(contrato().path("security").isEmpty(),
                "el contrato debe declarar bearerAuth como requisito global");
    }
}
