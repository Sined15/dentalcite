package pe.edu.dentalcite.paciente.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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

import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Criterios 2, 3 y 4 de HU-13 (RF-08): la ficha con sus alergias y sus citas, su
 * corrección, y quién puede ver la de quién.
 *
 * <p>El reparto que se comprueba es el que RF-08 declara: «Recepcionista y
 * Administrador (edición); Odontólogo y Paciente (lectura)».
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class FichaDePacienteIntegrationTest {

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

    private String ficha() {
        return "/api/v1/pacientes/" + escenario.pacienteConCuenta.getId();
    }

    /** Criterio 2: «veré sus datos, sus alergias y sus citas pasadas y futuras». */
    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void obtenerFicha_comoRecepcion_devuelveDatosAlergiasYCitas() throws Exception {
        mockMvc.perform(get(ficha()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Rosa"))
                .andExpect(jsonPath("$.numeroHistoria").value(escenario.pacienteConCuenta.getNumeroHistoria()))
                .andExpect(jsonPath("$.alergias").value("Penicilina"))
                // Una pasada (ATENDIDA), una futura (CONFIRMADA) y la CANCELADA:
                // la ficha las muestra todas, que es lo que RF-20 espera de ella.
                .andExpect(jsonPath("$.citas.length()").value(3))
                // En hora local de la clínica, no como el instante UTC de la base.
                .andExpect(jsonPath("$.citas[0].zonaHoraria").isNotEmpty())
                .andExpect(jsonPath("$.citas[?(@.estado == 'ATENDIDA')]").isNotEmpty())
                .andExpect(jsonPath("$.citas[?(@.estado == 'CONFIRMADA')]").isNotEmpty());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void obtenerFicha_deUnaFichaInexistente_devuelve404() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void obtenerFicha_comoElOdontologoQueLoAtendio_devuelve200() throws Exception {
        mockMvc.perform(get(ficha())
                        .with(user(escenario.usuarioOdontologoPropio.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alergias").value("Penicilina"));
    }

    /**
     * Criterio 4. Este odontólogo tiene una fila de cita con esa persona, pero
     * está CANCELADA: nunca llegó a atenderla, así que no ve su ficha.
     */
    @Test
    void obtenerFicha_comoOdontologoSinCitasConEsaPersona_devuelve403() throws Exception {
        mockMvc.perform(get(ficha())
                        .with(user(escenario.usuarioOdontologoAjeno.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void obtenerFicha_comoElPacienteTitular_devuelve200() throws Exception {
        mockMvc.perform(get(ficha())
                        .with(user(escenario.usuarioDelPaciente.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE"))))
                .andExpect(status().isOk());
    }

    @Test
    void obtenerFicha_comoUnPacienteAjeno_devuelve403() throws Exception {
        mockMvc.perform(get("/api/v1/pacientes/" + escenario.pacienteSinCuenta.getId())
                        .with(user(escenario.usuarioDelPaciente.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE"))))
                .andExpect(status().isForbidden());
    }

    /** Criterio 3: «los corrija y guarde, y la ficha quedará actualizada». */
    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void actualizarFicha_comoRecepcion_dejaLaFichaCorregida() throws Exception {
        String cuerpo = """
                {"nombres":"Rosa María","apellidos":"Huamán Ríos",
                 "telefono":"999111222","alergias":"Penicilina y latex"}
                """;

        mockMvc.perform(put(ficha()).contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefono").value("999111222"))
                .andExpect(jsonPath("$.alergias").value("Penicilina y latex"));

        // Y persiste: la siguiente lectura la ve corregida.
        mockMvc.perform(get(ficha()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombres").value("Rosa María"))
                // RN-10: el documento sigue siendo el mismo, no se edita.
                .andExpect(jsonPath("$.documento").value(escenario.pacienteConCuenta.getDocumento()));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void actualizarFicha_sinApellidos_devuelve400() throws Exception {
        mockMvc.perform(put(ficha()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"Rosa\",\"apellidos\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    /** RF-08: la edición es de recepción y administración, no del odontólogo. */
    @Test
    void actualizarFicha_comoOdontologo_devuelve403() throws Exception {
        mockMvc.perform(put(ficha())
                        .with(user(escenario.usuarioOdontologoPropio.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"Rosa\",\"apellidos\":\"Huaman\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void actualizarFicha_comoElPacienteTitular_devuelve403() throws Exception {
        mockMvc.perform(put(ficha())
                        .with(user(escenario.usuarioDelPaciente.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombres\":\"Rosa\",\"apellidos\":\"Huaman\"}"))
                .andExpect(status().isForbidden());
    }
}
