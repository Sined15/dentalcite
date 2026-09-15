package pe.edu.dentalcite.publico.api;

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

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Los días en que la clínica abre, sin sesión.
 *
 * <p>Ninguna prueba lleva {@code @WithMockUser}, y esa ausencia es el objeto de la
 * clase: quien pide cita todavía no tiene cuenta, y su calendario necesita saber
 * qué días puede marcar antes de identificarse.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CalendarioPublicoIntegrationTest {

    private static final String RUTA = "/api/v1/publico/calendario";

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    /**
     * La semilla declara horario de lunes a viernes para los siete odontólogos.
     * El sábado y el domingo no aparecen porque no los declara nadie, que es
     * exactamente lo que el calendario del cliente pinta cerrado.
     */
    @Test
    void calendario_sinSesion_devuelveLosDiasEnQueSeAtiende() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diasDeAtencion").value(org.hamcrest.Matchers.hasItems(1, 2, 3, 4, 5)))
                .andExpect(jsonPath("$.diasDeAtencion").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(7))))
                .andExpect(jsonPath("$.feriados").isNotEmpty())
                .andExpect(jsonPath("$.porOdontologo").isNotEmpty());
    }

    /** Cada odontólogo lleva sus días, que es lo que estrecha el calendario al elegirlo. */
    @Test
    void calendario_desglosaLosDiasDeCadaOdontologo() throws Exception {
        mockMvc.perform(get(RUTA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.porOdontologo[0].odontologoId").isNotEmpty())
                .andExpect(jsonPath("$.porOdontologo[0].dias").isNotEmpty());
    }

    /**
     * Público no quiere decir que no autentique: una cabecera con un token que no
     * vale la rechaza el filtro antes de llegar al controlador. Importa porque el
     * interceptor del cliente cierra la sesión ante un 401.
     */
    @Test
    void calendario_conTokenInvalido_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA).header("Authorization", "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized());
    }
}
