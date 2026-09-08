package pe.edu.dentalcite.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El cliente Angular vive en otro origen, de modo que el navegador exige
 * cabeceras CORS antes de dejarle consumir la API.
 *
 * <p>La configuracion anterior invocaba {@code cors.configure(http)} sin registrar
 * ninguna {@code CorsConfigurationSource}: la API no emitia cabecera alguna y toda
 * llamada desde el portal moria en el preflight, sin error visible en el servidor.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CorsIntegrationTest {

    private static final String ORIGEN_CLIENTE = "http://localhost:4200";

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void preflight_desdeElOrigenDelCliente_autorizaLaPeticion() throws Exception {
        // El preflight no lleva token: si la cadena de seguridad lo exigiera, el
        // navegador nunca llegaria a enviar la peticion real.
        mockMvc.perform(options("/api/v1/tratamientos")
                .header("Origin", ORIGEN_CLIENTE)
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGEN_CLIENTE))
                .andExpect(header().string("Access-Control-Allow-Methods", containsString("GET")));
    }

    @Test
    void preflight_admiteLaCabeceraDeAutorizacion() throws Exception {
        // Sin esto el cliente no podria adjuntar el JWT en las llamadas reales.
        mockMvc.perform(options("/api/v1/usuarios/me")
                .header("Origin", ORIGEN_CLIENTE)
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Headers", containsString("Authorization")));
    }

    @Test
    void preflight_desdeUnOrigenNoAutorizado_seRechaza() throws Exception {
        mockMvc.perform(options("/api/v1/tratamientos")
                .header("Origin", "http://sitio-no-autorizado.example")
                .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
