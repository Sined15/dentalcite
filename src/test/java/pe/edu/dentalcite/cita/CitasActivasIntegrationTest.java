package pe.edu.dentalcite.cita;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoRequest;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RN-12 y RN-03 ejercidas contra filas reales de {@code citas}.
 *
 * <p>Estas reglas estaban implementadas y probadas solo con dobles de Mockito, de
 * modo que las dos consultas <strong>nativas</strong> de {@code hasCitasActivas}
 * no llegaban a ejecutarse nunca. Precisamente por ahi entro el defecto original:
 * una consulta nativa contra una tabla que ninguna migracion creaba, invisible al
 * arrancar y que devolvia 500 en lugar de 409.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class CitasActivasIntegrationTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private FichaRepository fichaRepository;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private OdontologoRepository odontologoRepository;
    @Autowired
    private EspecialidadRepository especialidadRepository;
    @Autowired
    private TratamientoRepository tratamientoRepository;
    @Autowired
    private ConsultorioRepository consultorioRepository;
    @Autowired
    private CitaRepository citaRepository;

    private Odontologo odontologo;
    private Tratamiento tratamiento;
    private Consultorio consultorio;
    private Ficha fichaPaciente;

    private static String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        String s = corto();

        fichaPaciente = fichaRepository.save(Ficha.builder()
                .documento("PAC" + s).numeroHistoria("HC-P" + s)
                .nombres("Ana").apellidos("Quispe").build());

        Ficha fichaOdontologo = fichaRepository.save(Ficha.builder()
                .documento("ODO" + s).numeroHistoria("HC-O" + s)
                .nombres("Luis").apellidos("Perez").build());

        usuarioRepository.save(Usuario.builder()
                .nombre("Luis Perez").correo("odo-" + s + "@dentalcite.com")
                .contrasenaHash("$2a$12$0000000000000000000000000000000000000000000000000000")
                .rol("ODONTOLOGO").ficha(fichaOdontologo).build());

        odontologo = odontologoRepository.save(Odontologo.builder()
                .id(UUID.randomUUID()).cop("COP" + s)
                .nombres("Luis").apellidos("Perez")
                .ficha(fichaOdontologo).activo(true).build());

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID()).nombre("ESP-" + s).activo(true).build());

        tratamiento = tratamientoRepository.save(Tratamiento.builder()
                .id(UUID.randomUUID()).codigo("TR-" + s).nombre("Limpieza")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        consultorio = consultorioRepository.findAll().get(0);
    }

    /** Cita de una hora a partir del instante indicado, en el estado dado. */
    private Cita sembrarCita(String estado, OffsetDateTime inicio) {
        return citaRepository.saveAndFlush(Cita.builder()
                .codigo("CT-" + corto())
                .ficha(fichaPaciente)
                .odontologo(odontologo)
                .tratamiento(tratamiento)
                .consultorio(consultorio)
                .inicio(inicio)
                .fin(inicio.plusHours(1))
                .estado(estado)
                .build());
    }

    private BloqueoRequest bloqueoDe(OffsetDateTime inicio, OffsetDateTime fin,
            UUID odontologoId, UUID consultorioId) {
        BloqueoRequest req = new BloqueoRequest();
        req.setMotivo("Congreso");
        req.setFechaInicio(inicio);
        req.setFechaFin(fin);
        req.setOdontologoId(odontologoId);
        req.setConsultorioId(consultorioId);
        return req;
    }

    // ------------------------------------------------------------ RN-12 · baja

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void darDeBajaTratamiento_conCitaActiva_retorna409() throws Exception {
        sembrarCita(Cita.ESTADO_CONFIRMADA, OffsetDateTime.now().plusDays(2));

        mockMvc.perform(patch("/api/v1/tratamientos/" + tratamiento.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("RN-12")));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void darDeBajaTratamiento_sinCitas_retorna204() throws Exception {
        mockMvc.perform(patch("/api/v1/tratamientos/" + tratamiento.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void darDeBajaTratamiento_conCitaCancelada_retorna204() throws Exception {
        // RN-09: la cancelada no ocupa odontologo ni consultorio, asi que no impide la baja.
        sembrarCita("CANCELADA", OffsetDateTime.now().plusDays(2));

        mockMvc.perform(patch("/api/v1/tratamientos/" + tratamiento.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void darDeBajaTratamiento_conCitaPasada_retorna204() throws Exception {
        // RN-09: activa es la confirmada cuya hora de fin no ha pasado.
        sembrarCita(Cita.ESTADO_CONFIRMADA, OffsetDateTime.now().minusDays(3));

        mockMvc.perform(patch("/api/v1/tratamientos/" + tratamiento.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void darDeBajaOdontologo_conCitaActiva_retorna409() throws Exception {
        sembrarCita(Cita.ESTADO_CONFIRMADA, OffsetDateTime.now().plusDays(2));

        mockMvc.perform(patch("/api/v1/odontologos/" + odontologo.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("RN-12")));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void darDeBajaOdontologo_sinCitas_retorna204() throws Exception {
        mockMvc.perform(patch("/api/v1/odontologos/" + odontologo.getId()))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------ RN-03 · bloqueo

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearBloqueo_queAlcanzaUnaCitaActiva_retorna409ConLaListaDeCitas() throws Exception {
        // HU-07: el sistema lista esas citas y no aplica el bloqueo hasta que se
        // cancelen con motivo.
        Cita cita = sembrarCita(Cita.ESTADO_CONFIRMADA, OffsetDateTime.now().plusDays(2));

        BloqueoRequest req = bloqueoDe(OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(5), odontologo.getId(), null);

        mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.citasActivas.length()").value(1))
                .andExpect(jsonPath("$.citasActivas[0].codigo").value(cita.getCodigo()))
                .andExpect(jsonPath("$.citasActivas[0].paciente").value("Ana Quispe"))
                .andExpect(jsonPath("$.citasActivas[0].numeroHistoria")
                        .value(fichaPaciente.getNumeroHistoria()));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearBloqueo_conLaCitaFueraDelRango_retorna201() throws Exception {
        // La cita cae despues del bloqueo: no hay solapamiento.
        sembrarCita(Cita.ESTADO_CONFIRMADA, OffsetDateTime.now().plusDays(20));

        BloqueoRequest req = bloqueoDe(OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(5), odontologo.getId(), null);

        mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearBloqueo_conLaCitaCancelada_retorna201() throws Exception {
        sembrarCita("CANCELADA", OffsetDateTime.now().plusDays(2));

        BloqueoRequest req = bloqueoDe(OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(5), odontologo.getId(), null);

        mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearBloqueoDeConsultorio_queAlcanzaUnaCitaActiva_retorna409() throws Exception {
        // RN-02: el consultorio tambien se comprueba, no solo el odontologo.
        sembrarCita(Cita.ESTADO_CONFIRMADA, OffsetDateTime.now().plusDays(2));

        BloqueoRequest req = bloqueoDe(OffsetDateTime.now().plusDays(1),
                OffsetDateTime.now().plusDays(5), null, consultorio.getId());

        mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.citasActivas.length()").value(1));
    }
}
