package pe.edu.dentalcite.odontologo.api;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.api.dto.OdontologoRequestDTO;

import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class OdontologoControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    @Autowired
    private FichaRepository fichaRepository;

    @Autowired
    private pe.edu.dentalcite.usuario.repository.UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void registrarOdontologo_conRolPaciente_retornaForbidden() throws Exception {
        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("99999");
        req.setNombres("Test");
        req.setApellidos("Test");
        req.setFichaId(UUID.randomUUID());
        req.setEspecialidadesIds(Set.of(UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/odontologos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void registrarOdontologo_conRolAdministrador_retornaCreated() throws Exception {
        Especialidad esp = especialidadRepository.save(Especialidad.builder().id(UUID.randomUUID()).nombre("ESP-ODO-TEST").activo(true).build());
        Ficha ficha = fichaRepository.save(Ficha.builder().numeroHistoria("123456").documento("12345678").build());

        // RN-11: el odontólogo se vincula a una cuenta *de rol ODONTOLOGO*; sin ella
        // el registro se rechaza con 409.
        usuarioRepository.save(pe.edu.dentalcite.usuario.domain.Usuario.builder()
                .nombre("Juan Perez")
                .correo("juan.perez.test@dentalcite.com")
                .contrasenaHash("$2a$12$0000000000000000000000000000000000000000000000000000")
                .rol("ODONTOLOGO")
                .ficha(ficha)
                .build());

        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("COP-123");
        req.setNombres("Juan");
        req.setApellidos("Perez");
        req.setFichaId(ficha.getId());
        req.setEspecialidadesIds(Set.of(esp.getId()));

        mockMvc.perform(post("/api/v1/odontologos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void registrarOdontologo_conFichaSinCuentaOdontologo_retornaConflicto409() throws Exception {
        // RN-11 / HU-06: «cuando lo vincule a una cuenta de rol ODONTOLOGO».
        Especialidad esp = especialidadRepository.save(
                Especialidad.builder().id(UUID.randomUUID()).nombre("ESP-ODO-TEST-2").activo(true).build());
        Ficha ficha = fichaRepository.save(
                Ficha.builder().numeroHistoria("123457").documento("12345679").build());

        usuarioRepository.save(pe.edu.dentalcite.usuario.domain.Usuario.builder()
                .nombre("Ana Quispe")
                .correo("ana.paciente.test@dentalcite.com")
                .contrasenaHash("$2a$12$0000000000000000000000000000000000000000000000000000")
                .rol("PACIENTE")
                .ficha(ficha)
                .build());

        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("COP-124");
        req.setNombres("Juan");
        req.setApellidos("Perez");
        req.setFichaId(ficha.getId());
        req.setEspecialidadesIds(Set.of(esp.getId()));

        mockMvc.perform(post("/api/v1/odontologos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void actualizarOdontologo_conRolAdministrador_reemplazaColegiaturaYEspecialidades() throws Exception {
        // Tabla 10, U sobre el catálogo clínico. Las especialidades son las que
        // RN-08 exigirá al asignarlo a un tratamiento, así que poder cambiarlas es
        // el fondo de la operación.
        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        Especialidad inicial = especialidadRepository.save(
                Especialidad.builder().id(UUID.randomUUID()).nombre("ESP-PUT-A-" + sufijo).activo(true).build());
        Especialidad posterior = especialidadRepository.save(
                Especialidad.builder().id(UUID.randomUUID()).nombre("ESP-PUT-B-" + sufijo).activo(true).build());
        Ficha ficha = fichaRepository.save(
                Ficha.builder().numeroHistoria("HC-PUT" + sufijo).documento("PUT" + sufijo).build());

        usuarioRepository.save(pe.edu.dentalcite.usuario.domain.Usuario.builder()
                .nombre("Odontologo Put")
                .correo("odonto.put." + sufijo + "@dentalcite.com")
                .contrasenaHash("$2a$12$0000000000000000000000000000000000000000000000000000")
                .rol("ODONTOLOGO")
                .ficha(ficha)
                .build());

        OdontologoRequestDTO alta = new OdontologoRequestDTO();
        alta.setCop("COP-PUT-" + sufijo);
        alta.setNombres("Juan");
        alta.setApellidos("Perez");
        alta.setFichaId(ficha.getId());
        alta.setEspecialidadesIds(Set.of(inicial.getId()));

        String creado = mockMvc.perform(post("/api/v1/odontologos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(alta)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(creado).get("id").asText();

        OdontologoRequestDTO reemplazo = new OdontologoRequestDTO();
        reemplazo.setCop("COP-PUT2-" + sufijo);
        reemplazo.setNombres("Juan Carlos");
        reemplazo.setApellidos("Perez Ramos");
        reemplazo.setFichaId(ficha.getId());
        reemplazo.setEspecialidadesIds(Set.of(posterior.getId()));

        mockMvc.perform(put("/api/v1/odontologos/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(reemplazo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cop").value("COP-PUT2-" + sufijo))
                .andExpect(jsonPath("$.nombres").value("Juan Carlos"))
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.especialidades.length()").value(1))
                .andExpect(jsonPath("$.especialidades[0].nombre").value("ESP-PUT-B-" + sufijo));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ODONTOLOGO")
    void actualizarOdontologo_conRolOdontologo_retornaForbidden() throws Exception {
        // RNF-04: la escritura sobre el catálogo clínico es exclusiva del administrador.
        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("99999");
        req.setNombres("Test");
        req.setApellidos("Test");
        req.setFichaId(UUID.randomUUID());
        req.setEspecialidadesIds(Set.of(UUID.randomUUID()));

        mockMvc.perform(put("/api/v1/odontologos/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void darDeBajaOdontologo_conRolPaciente_retornaForbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/odontologos/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }
}
