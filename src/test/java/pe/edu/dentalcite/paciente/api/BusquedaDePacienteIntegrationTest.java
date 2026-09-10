package pe.edu.dentalcite.paciente.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Primer criterio de HU-13 (RF-07): «un término de búsqueda por documento,
 * apellidos o número de historia, y resultados paginados», más las pruebas
 * negativas de autorización que la Definición de Terminado exige.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BusquedaDePacienteIntegrationTest {

    private MockMvc mockMvc;
    private EscenarioDePacientes escenario;

    @Autowired private WebApplicationContext context;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private ConsultorioRepository consultorioRepository;
    @Autowired private CitaRepository citaRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        escenario = new EscenarioDePacientes(fichaRepository, usuarioRepository, odontologoRepository,
                especialidadRepository, tratamientoRepository, consultorioRepository, citaRepository);
        escenario.sembrar();
    }

    @AfterEach
    void limpiar() {
        escenario.limpiar();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void buscar_porApellido_devuelveResultadosPaginados() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes").param("q", "huaman" + escenario.sufijo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombres").value("Rosa"))
                // Es una página de Spring Data, no una lista suelta.
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.number").value(0));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void buscar_porDocumento_encuentraLaFicha() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes").param("q", escenario.pacienteSinCuenta.getDocumento()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].nombres").value("Mario"))
                // Alta presencial: existe la ficha, no la cuenta.
                .andExpect(jsonPath("$.content[0].tieneCuenta").value(false));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void buscar_porNumeroDeHistoria_encuentraLaFicha() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes").param("q", escenario.pacienteConCuenta.getNumeroHistoria()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].tieneCuenta").value(true));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void buscar_conUnTerminoQueNoCoincide_devuelvePaginaVacia() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes").param("q", "nadie-" + escenario.sufijo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.empty").value(true));
    }

    /**
     * RNF-06: el odontólogo solo busca entre las personas a las que ha atendido.
     * El paciente sin cuenta nunca ha tenido cita con nadie, así que no aparece
     * aunque el apellido coincida.
     */
    @Test
    void buscar_comoOdontologo_soloDevuelveSusPacientes() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes")
                        .with(user(escenario.usuarioOdontologoPropio.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.nombres == 'Rosa')]").isNotEmpty())
                .andExpect(jsonPath("$.content[?(@.nombres == 'Mario')]").isEmpty());
    }

    /** Su única cita con esa persona está CANCELADA: para él no es su paciente. */
    @Test
    void buscar_comoOdontologoConSoloUnaCitaCancelada_noLoEncuentra() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes")
                        .with(user(escenario.usuarioOdontologoAjeno.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .param("q", "huaman" + escenario.sufijo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }

    /** RF-07 da la búsqueda al «personal». El paciente no busca pacientes. */
    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void buscar_comoPaciente_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes"))
                .andExpect(status().isForbidden());
    }
}
