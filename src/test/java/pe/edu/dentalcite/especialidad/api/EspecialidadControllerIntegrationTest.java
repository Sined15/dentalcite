package pe.edu.dentalcite.especialidad.api;

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
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadRequestDTO;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EspecialidadControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private Especialidad sembrar(String nombre) {
        return especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre(nombre)
                .activo(true)
                .build());
    }

    private EspecialidadRequestDTO peticion(String nombre) {
        EspecialidadRequestDTO req = new EspecialidadRequestDTO();
        req.setNombre(nombre);
        req.setDescripcion("Descripción de prueba");
        return req;
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE") // No es administrador
    void crearEspecialidad_conRolPaciente_retornaForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/especialidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(peticion("CIRUGIA"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearEspecialidad_conRolAdministrador_retornaCreated() throws Exception {
        mockMvc.perform(post("/api/v1/especialidades")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(peticion("CIRUGIA MAXILOFACIAL"))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void listarEspecialidades_conCualquierRolAutenticado_retornaOk() throws Exception {
        // Tabla 10: el catálogo clínico es de lectura para los cuatro roles.
        mockMvc.perform(get("/api/v1/especialidades")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void actualizarEspecialidad_conRolAdministrador_retornaOk() throws Exception {
        Especialidad esp = sembrar("ESP-PUT-ADMIN");

        mockMvc.perform(put("/api/v1/especialidades/" + esp.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(peticion("ESP-PUT-RENOMBRADA"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("ESP-PUT-RENOMBRADA"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void actualizarEspecialidad_conRolPaciente_retornaForbidden() throws Exception {
        // RNF-04: la escritura sobre el catálogo es solo del ADMINISTRADOR. Antes de
        // restringir todos los métodos, PUT caía en `anyRequest().authenticated()`.
        mockMvc.perform(put("/api/v1/especialidades/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(peticion("NO DEBERIA PASAR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void actualizarEspecialidad_conRolRecepcionista_retornaForbidden() throws Exception {
        mockMvc.perform(put("/api/v1/especialidades/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(peticion("NO DEBERIA PASAR"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void darDeBajaEspecialidad_sinTratamientosActivos_retorna204() throws Exception {
        Especialidad esp = sembrar("ESP-BAJA-OK");

        mockMvc.perform(patch("/api/v1/especialidades/" + esp.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ODONTOLOGO")
    void darDeBajaEspecialidad_conRolOdontologo_retornaForbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/especialidades/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void actualizarEspecialidad_conIdInexistente_retorna404() throws Exception {
        mockMvc.perform(put("/api/v1/especialidades/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(peticion("INEXISTENTE"))))
                .andExpect(status().isNotFound());
    }
}
