package pe.edu.dentalcite.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.config.MatrizDeControlDeAcceso.Operacion;
import pe.edu.dentalcite.config.MatrizDeControlDeAcceso.Rol;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

/**
 * La matriz de control de acceso entera, contra la API tal como está expuesta.
 *
 * <p>Tres preguntas:
 * <ul>
 *   <li>¿responde 403 cada operación a cada rol que la matriz no le concede?</li>
 *   <li>¿responde 401 cada operación que exige sesión cuando no se envía token,
 *       y deja pasar sin él las que el visitante sí puede hacer?</li>
 *   <li>¿está en la matriz cada operación que la aplicación expone? Sin esta
 *       tercera, las dos primeras solo probarían lo que alguien se acordó de
 *       apuntar.</li>
 * </ul>
 *
 * <p>La propiedad del dato —dos usuarios del mismo rol— no se decide por ruta
 * sino con el recurso en la mano, y la cubre {@code PropiedadDelDatoIntegrationTest}.
 *
 * <p>Las rutas llevan identificadores que no existen, y es a propósito: la
 * denegación por rol ocurre en la cadena de filtros, antes de mirar si el
 * recurso existe, así que un 404 aquí delataría una regla de ruta que falta.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ControlDeAccesoIntegrationTest {

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeos;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    static Stream<Arguments> combinacionesNoAutorizadas() {
        return MatrizDeControlDeAcceso.OPERACIONES.stream()
                .flatMap(operacion -> Stream.of(Rol.values())
                        .filter(rol -> !operacion.autorizados().contains(rol))
                        .map(rol -> Arguments.of(operacion, rol)));
    }

    static Stream<Operacion> operaciones() {
        return MatrizDeControlDeAcceso.OPERACIONES.stream();
    }

    private static MockHttpServletRequestBuilder peticion(Operacion operacion) {
        MockHttpServletRequestBuilder peticion =
                MockMvcRequestBuilders.request(operacion.metodo(), operacion.rutaConcreta());
        // Un cuerpo vacío basta: la regla de ruta decide antes de validar nada.
        return operacion.llevaCuerpo()
                ? peticion.contentType(MediaType.APPLICATION_JSON).content("{}")
                : peticion;
    }

    @ParameterizedTest(name = "{0} como {1}")
    @MethodSource("combinacionesNoAutorizadas")
    void operacionExpuesta_rolNoAutorizado_responde403(Operacion operacion, Rol rol) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion(operacion)
                        .with(user(UUID.randomUUID().toString())
                                .authorities(new SimpleGrantedAuthority(rol.autoridad()))))
                .andReturn();

        assertEquals(403, resultado.getResponse().getStatus(), operacion + " como " + rol);
        CuerpoDeRechazo.comprobar(resultado.getResponse().getContentAsString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("operaciones")
    void operacionExpuesta_sinToken_respondeSegunSiEsPublica(Operacion operacion) throws Exception {
        int estado = mockMvc.perform(peticion(operacion)).andReturn().getResponse().getStatus();

        if (operacion.publica()) {
            // Lo que responda depende de lo que se pida —un 400 por faltar
            // parámetros, un 404 por un id inventado—, pero no puede ser un
            // rechazo por no haberse identificado.
            assertNotEquals(401, estado, operacion + " debería responder sin sesión");
            assertNotEquals(403, estado, operacion + " debería responder sin sesión");
        } else {
            assertEquals(401, estado, operacion + " sin token");
        }
    }

    @Test
    void matriz_cubreTodaOperacionExpuesta() {
        TreeSet<String> expuestas = new TreeSet<>();
        mapeos.getHandlerMethods().keySet().forEach(info -> {
            if (info.getPathPatternsCondition() == null) {
                return;
            }
            for (String ruta : info.getPathPatternsCondition().getPatternValues()) {
                if (!ruta.startsWith("/api/v1/")) {
                    continue;
                }
                info.getMethodsCondition().getMethods().forEach(metodo ->
                        expuestas.add(metodo.name() + " " + Operacion.patron(ruta)));
            }
        });

        TreeSet<String> enLaMatriz = new TreeSet<>();
        MatrizDeControlDeAcceso.OPERACIONES.forEach(operacion ->
                enLaMatriz.add(operacion.metodo().name() + " " + operacion.patron()));

        TreeSet<String> sinFila = new TreeSet<>(expuestas);
        sinFila.removeAll(enLaMatriz);
        TreeSet<String> sinEndpoint = new TreeSet<>(enLaMatriz);
        sinEndpoint.removeAll(expuestas);

        assertEquals(Set.of(), sinFila, "Endpoints expuestos sin fila en la matriz");
        assertEquals(Set.of(), sinEndpoint, "Filas de la matriz que ya no son un endpoint");
    }
}
