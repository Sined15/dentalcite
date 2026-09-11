package pe.edu.dentalcite.publico.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-06 (v4) · El catálogo clínico sin sesión.
 *
 * <p>Ninguna prueba lleva {@code @WithMockUser}, y esa ausencia es el objeto de la
 * clase: lo que se comprueba es justamente que la API responde a quien no se ha
 * identificado, y solo en estas rutas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CatalogoPublicoIntegrationTest {

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private FichaRepository fichaRepository;

    private String sufijo;
    private UUID especialidadId;
    private UUID especialidadDeBajaId;
    private UUID tratamientoId;
    private UUID odontologoId;
    private UUID fichaId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        sufijo = UUID.randomUUID().toString().substring(0, 8);

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("PUBLICA-" + sufijo)
                .descripcion("Descripción que el visitante no debe ver")
                .imagenUrl("/img/catalogo/prueba.svg")
                .activo(true)
                .build());
        especialidadId = especialidad.getId();

        especialidadDeBajaId = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("DE-BAJA-" + sufijo)
                .activo(false)
                .build()).getId();

        tratamientoId = tratamientoRepository.save(Tratamiento.builder()
                .id(UUID.randomUUID())
                .codigo("PUB-" + sufijo)
                .nombre("Tratamiento público " + sufijo)
                .duracionMinutos(30)
                .especialidad(especialidad)
                .imagenUrl("/img/catalogo/tratamiento-prueba.svg")
                .activo(true)
                .build()).getId();

        Ficha ficha = fichaRepository.save(Ficha.builder()
                .tipoDocumento("DNI")
                .documento("PUB" + sufijo)
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .build());
        fichaId = ficha.getId();

        odontologoId = odontologoRepository.save(Odontologo.builder()
                .id(UUID.randomUUID())
                .cop("COP-PUB-" + sufijo)
                .nombres("Publica")
                .apellidos("Prueba")
                .ficha(ficha)
                .especialidades(Set.of(especialidad))
                .imagenUrl("/img/catalogo/odontologo-prueba.svg")
                .activo(true)
                .build()).getId();
    }

    @AfterEach
    void limpiar() {
        odontologoRepository.deleteById(odontologoId);
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(fichaId);
        especialidadRepository.deleteById(especialidadId);
        especialidadRepository.deleteById(especialidadDeBajaId);
    }

    @Test
    void listarEspecialidades_sinToken_respondeSoloConNombreEImagen() throws Exception {
        mockMvc.perform(get("/api/v1/publico/especialidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='PUBLICA-" + sufijo + "')].imagenUrl")
                        .value("/img/catalogo/prueba.svg"))
                // El criterio (v4) dice que el visitante no ve la descripción ni el
                // estado. No basta con que el cliente los oculte: la API no los manda.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Descripción que el visitante no debe ver"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"activo\""))));
    }

    @Test
    void listarEspecialidades_sinToken_noOfreceLasDadasDeBaja() throws Exception {
        mockMvc.perform(get("/api/v1/publico/especialidades"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='DE-BAJA-" + sufijo + "')]")
                        .isEmpty());
    }

    @Test
    void listarOdontologos_sinToken_noExponeLaFichaDelPersonal() throws Exception {
        mockMvc.perform(get("/api/v1/publico/odontologos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.cop=='COP-PUB-" + sufijo + "')].apellidos")
                        .value("Prueba"))
                // La ficha es la razón de que el catálogo público tenga rutas
                // propias: publicarla sería regalar un identificador que nadie pidió.
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("fichaId"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("\"activo\""))));
    }

    @Test
    void listarTratamientos_sinToken_traeLosActivosConSuEspecialidad() throws Exception {
        mockMvc.perform(get("/api/v1/publico/tratamientos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.nombre=='Tratamiento público " + sufijo + "')].duracionMinutos")
                        .value(30))
                .andExpect(jsonPath("$[?(@.nombre=='Tratamiento público " + sufijo + "')].especialidadNombre")
                        .value("PUBLICA-" + sufijo));
    }

    @Test
    void detalleDeEspecialidad_sinToken_traeSusTratamientosYSusOdontologos() throws Exception {
        mockMvc.perform(get("/api/v1/publico/especialidades/" + especialidadId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("PUBLICA-" + sufijo))
                .andExpect(jsonPath("$.tratamientos[0].nombre").value("Tratamiento público " + sufijo))
                .andExpect(jsonPath("$.odontologos[0].cop").value("COP-PUB-" + sufijo));
    }

    @Test
    void detalleDeEspecialidad_dadaDeBaja_respondeNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/publico/especialidades/" + especialidadDeBajaId))
                .andExpect(status().isNotFound());
    }

    @Test
    void catalogoAutenticado_sinToken_sigueRespondiendoUnauthorized() throws Exception {
        // La contrapartida del criterio: abrir el catálogo público no abre el resto.
        mockMvc.perform(get("/api/v1/especialidades")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/odontologos")).andExpect(status().isUnauthorized());
    }

    /**
     * Una ruta pública no es una ruta sin autenticación: es una ruta que no la
     * exige. Si llega una cabecera {@code Authorization} con un token que no vale
     * —caducado, revocado, manipulado—, el filtro del servidor de recursos la
     * rechaza antes de llegar al controlador, y responde 401 aunque la regla sea
     * {@code permitAll}.
     *
     * <p>Importa porque tiene consecuencia en el cliente: el interceptor cierra la
     * sesión ante un 401, así que un usuario con el token caducado que estuviera
     * mirando la portada acabaría en el login. Es el comportamiento correcto, pero
     * conviene que esté escrito y no descubierto.
     */
    @Test
    void catalogoPublico_conTokenInvalido_respondeUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/publico/especialidades")
                        .header("Authorization", "Bearer no-es-un-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void escrituraBajoElPrefijoPublico_sinToken_noPasa() throws Exception {
        // El prefijo es de solo lectura: sin la regla que lo niega, una ruta nueva
        // que no fuera GET caería en el anyRequest() y quedaría abierta.
        mockMvc.perform(post("/api/v1/publico/especialidades")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is4xxClientError());
    }
}
