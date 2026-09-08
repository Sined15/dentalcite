package pe.edu.dentalcite.bloqueo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoRequest;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoResponseDTO;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin {@code @Transactional} a propósito: ver la nota de
 * {@code HorarioControllerIntegrationTest}. Devolver la entidad {@code Bloqueo}
 * respondía 500 en producción por sus asociaciones LAZY.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class BloqueoControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ConsultorioRepository consultorioRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private OdontologoRepository odontologoRepository;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    private Consultorio primerConsultorio() {
        return consultorioRepository.findAll().get(0);
    }

    private BloqueoRequest bloqueoDeConsultorio(UUID consultorioId, String motivo) {
        BloqueoRequest req = new BloqueoRequest();
        req.setMotivo(motivo);
        req.setConsultorioId(consultorioId);
        req.setFechaInicio(OffsetDateTime.now().plusDays(1));
        req.setFechaFin(OffsetDateTime.now().plusDays(2));
        return req;
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void crearBloqueo_conRolPaciente_retornaForbidden() throws Exception {
        BloqueoRequest req = new BloqueoRequest();
        req.setMotivo("Vacaciones");
        req.setFechaInicio(OffsetDateTime.now());
        req.setFechaFin(OffsetDateTime.now().plusDays(1));

        mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void listarBloqueos_conRolPaciente_retornaForbidden() throws Exception {
        // Tabla 10: la fila «Bloqueos de agenda y de consultorio» no concede nada al
        // paciente, ni siquiera lectura.
        mockMvc.perform(get("/api/v1/bloqueos"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void crearBloqueoDeConsultorio_conRolRecepcionista_retorna201() throws Exception {
        // HU-07: «Dado un consultorio fuera de servicio, cuando registre su bloqueo…»
        UUID consultorioId = primerConsultorio().getId();

        mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        bloqueoDeConsultorio(consultorioId, "Mantenimiento consultorio"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ODONTOLOGO")
    void crearBloqueoDeConsultorio_conRolOdontologo_retorna403() throws Exception {
        UUID consultorioId = primerConsultorio().getId();

        mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        bloqueoDeConsultorio(consultorioId, "Mantenimiento consultorio"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearBloqueo_conSuPropiaAgendaYAdemasUnConsultorio_comoOdontologo_retorna403() throws Exception {
        // El agujero que cerró A4. Adjuntando su propio identificador, la
        // verificación de propiedad daba el visto bueno y nadie miraba el
        // consultorio: un odontólogo podía dejarlo inoperativo para toda la clínica.
        UUID usuarioId = usuarioRepository.findByCorreo("dr.perez@dentalcite.com").orElseThrow().getId();
        UUID odontologoId = odontologoRepository.findByCop("COP-10001").orElseThrow().getId();

        BloqueoRequest req = bloqueoDeConsultorio(primerConsultorio().getId(), "Me llevo el consultorio");
        req.setOdontologoId(odontologoId);

        mockMvc.perform(post("/api/v1/bloqueos")
                .with(user(usuarioId.toString()).authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearBloqueo_soloDeSuPropiaAgenda_comoOdontologo_retorna201() throws Exception {
        // Control positivo: la Tabla 10 sí concede al odontólogo C sobre los
        // bloqueos de su propia agenda, y eso no puede haberse roto.
        UUID usuarioId = usuarioRepository.findByCorreo("dr.perez@dentalcite.com").orElseThrow().getId();
        UUID odontologoId = odontologoRepository.findByCop("COP-10001").orElseThrow().getId();

        BloqueoRequest req = new BloqueoRequest();
        req.setOdontologoId(odontologoId);
        req.setMotivo("Congreso");
        req.setFechaInicio(OffsetDateTime.now().plusDays(400));
        req.setFechaFin(OffsetDateTime.now().plusDays(402));

        mockMvc.perform(post("/api/v1/bloqueos")
                .with(user(usuarioId.toString()).authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.odontologoId").value(odontologoId.toString()))
                .andExpect(jsonPath("$.consultorioId").doesNotExist());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void registrarConsultarYReemplazarUnBloqueo_recorreElCicloCompleto() throws Exception {
        // HU-07: «cuando lo registre, entonces quedará aplicado y consultable sobre
        // el odontólogo o el consultorio indicado». Los tres verbos serializan la
        // respuesta, que es donde reventaba al devolver la entidad.
        Consultorio consultorio = primerConsultorio();
        String motivo = "Mantenimiento " + UUID.randomUUID().toString().substring(0, 8);

        String creado = mockMvc.perform(post("/api/v1/bloqueos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        bloqueoDeConsultorio(consultorio.getId(), motivo))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        BloqueoResponseDTO bloqueo = objectMapper.readValue(creado, BloqueoResponseDTO.class);
        assertEquals(consultorio.getId(), bloqueo.getConsultorioId());
        assertEquals(consultorio.getNombre(), bloqueo.getConsultorio());
        assertEquals(motivo, bloqueo.getMotivo());

        // RNF-06: la respuesta no arrastra la ficha del odontólogo, que es lo que
        // ocurría al serializar la entidad completa.
        assertFalse(creado.contains("documento"), "la respuesta no debe exponer el documento de la ficha");
        assertFalse(creado.contains("numeroHistoria"), "la respuesta no debe exponer el número de historia");

        String listado = mockMvc.perform(get("/api/v1/bloqueos"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<BloqueoResponseDTO> visibles = objectMapper.readValue(listado,
                objectMapper.getTypeFactory().constructCollectionType(List.class, BloqueoResponseDTO.class));
        assertTrue(visibles.stream().anyMatch(b -> bloqueo.getId().equals(b.getId())),
                "el bloqueo registrado debe ser consultable");

        BloqueoRequest reemplazo = bloqueoDeConsultorio(consultorio.getId(), "Fumigación");
        mockMvc.perform(put("/api/v1/bloqueos/" + bloqueo.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reemplazo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(bloqueo.getId().toString()))
                .andExpect(jsonPath("$.motivo").value("Fumigación"));
    }
}
