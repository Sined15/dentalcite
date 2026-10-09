package pe.edu.dentalcite.common.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Un orden que el listado no conoce se ignora: la página sale igual, ordenada
 * como el listado ordena por defecto.
 *
 * <p>El {@code sort} viaja en la barra de direcciones, y basta con escribir mal
 * una propiedad para que llegue a la consulta. Si nadie lo criba, la consulta
 * falla y el usuario recibe un 500 por una errata suya, no un fallo del sistema.
 *
 * <p>Las cuentas que necesitan ficha —el paciente y el odontólogo— son las de la
 * semilla de demostración, que cualquier base recién migrada trae.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OrdenInventadoIntegrationTest {

    private static final String ORDEN_INVENTADO = "noExiste,desc";

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private UsuarioRepository usuarioRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static Stream<Arguments> listados() {
        return Stream.of(
                Arguments.of("/api/v1/citas?desde=2026-01-01&hasta=2026-12-31", "RECEPCIONISTA", null),
                Arguments.of("/api/v1/citas/mias", "PACIENTE", "paciente@demo.com"),
                Arguments.of("/api/v1/citas/pendientes-cierre", "RECEPCIONISTA", null),
                Arguments.of("/api/v1/especialidades", "ADMINISTRADOR", null),
                Arguments.of("/api/v1/tratamientos", "ADMINISTRADOR", null),
                Arguments.of("/api/v1/odontologos", "ADMINISTRADOR", null),
                // Los tres que ya lo cribaban: siguen igual.
                Arguments.of("/api/v1/pacientes", "RECEPCIONISTA", null),
                Arguments.of("/api/v1/usuarios", "ADMINISTRADOR", null),
                Arguments.of("/api/v1/planes/seguimiento", "ODONTOLOGO", "dr.perez@dentalcite.com"));
    }

    @ParameterizedTest(name = "{0} como {1}")
    @MethodSource("listados")
    void listado_conUnOrdenInventado_loIgnoraYRespondeLaPagina(String ruta, String rol, String cuenta)
            throws Exception {
        String sujeto = cuenta == null
                ? "00000000-0000-0000-0000-000000000000"
                : usuarioRepository.findByCorreo(cuenta).orElseThrow().getId().toString();
        String separador = ruta.contains("?") ? "&" : "?";

        MvcResult resultado = mockMvc.perform(get(ruta + separador + "sort=" + ORDEN_INVENTADO)
                        .with(user(sujeto).authorities(new SimpleGrantedAuthority("SCOPE_" + rol))))
                .andReturn();

        assertEquals(200, resultado.getResponse().getStatus(),
                ruta + " respondió " + resultado.getResponse().getStatus() + ": "
                        + resultado.getResponse().getContentAsString());
    }
}
