package pe.edu.dentalcite.tratamiento.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
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
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoRequestDTO;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class TratamientoControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private EspecialidadRepository especialidadRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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
    void crearTratamiento_conRolPaciente_retornaForbidden() throws Exception {
        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TRAT-99");
        req.setNombre("Test");
        req.setDuracionMinutos(30);
        req.setEspecialidadId(UUID.randomUUID());

        mockMvc.perform(post("/api/v1/tratamientos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearTratamiento_conRolAdministrador_retornaCreated() throws Exception {
        Especialidad esp = especialidadRepository.save(Especialidad.builder().id(UUID.randomUUID()).nombre("ESP-TEST").activo(true).build());

        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TRAT-TEST");
        req.setNombre("Limpieza");
        req.setDuracionMinutos(30);
        req.setEspecialidadId(esp.getId());

        mockMvc.perform(post("/api/v1/tratamientos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void darDeBajaTratamiento_conRolPaciente_retornaForbidden() throws Exception {
        mockMvc.perform(patch("/api/v1/tratamientos/" + UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void listarTratamientos_conCualquierRolAutenticado_retornaOk() throws Exception {
        // Tabla 10: el catálogo clínico es de lectura para los cuatro roles.
        mockMvc.perform(get("/api/v1/tratamientos")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void actualizarTratamiento_conRolAdministrador_retornaOk() throws Exception {
        Especialidad esp = especialidadRepository.save(
                Especialidad.builder().id(UUID.randomUUID()).nombre("ESP-PUT-TRAT").activo(true).build());

        TratamientoRequestDTO crear = new TratamientoRequestDTO();
        crear.setCodigo("TRAT-PUT");
        crear.setNombre("Limpieza");
        crear.setDuracionMinutos(30);
        crear.setEspecialidadId(esp.getId());

        String creado = mockMvc.perform(post("/api/v1/tratamientos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(crear)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(creado).get("id").asText();

        TratamientoRequestDTO actualizar = new TratamientoRequestDTO();
        actualizar.setCodigo("TRAT-PUT");
        actualizar.setNombre("Limpieza profunda");
        actualizar.setDuracionMinutos(45);
        actualizar.setEspecialidadId(esp.getId());

        mockMvc.perform(put("/api/v1/tratamientos/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(actualizar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Limpieza profunda"))
                .andExpect(jsonPath("$.duracionMinutos").value(45));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void actualizarTratamiento_conDuracionNoMultiploDe15_retorna400() throws Exception {
        // RN-04 se aplica igual al actualizar que al crear.
        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TRAT-RN04");
        req.setNombre("Limpieza");
        req.setDuracionMinutos(20);
        req.setEspecialidadId(UUID.randomUUID());

        mockMvc.perform(put("/api/v1/tratamientos/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void actualizarTratamiento_conRolPaciente_retornaForbidden() throws Exception {
        // RNF-04: antes de restringir todos los métodos, PUT quedaba abierto a
        // cualquier rol autenticado.
        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TRAT-99");
        req.setNombre("Test");
        req.setDuracionMinutos(30);
        req.setEspecialidadId(UUID.randomUUID());

        mockMvc.perform(put("/api/v1/tratamientos/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ODONTOLOGO")
    void actualizarTratamiento_conRolOdontologo_retornaForbidden() throws Exception {
        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TRAT-98");
        req.setNombre("Test");
        req.setDuracionMinutos(30);
        req.setEspecialidadId(UUID.randomUUID());

        mockMvc.perform(put("/api/v1/tratamientos/" + UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    /**
     * Inserta un tratamiento saltandose TratamientoService, para comprobar que la
     * regla vive tambien en la base y no solo en la capa de servicio.
     */
    private void insertarDirectamente(int duracionMinutos) {
        Especialidad esp = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID()).nombre("ESP-CHK-" + UUID.randomUUID()).activo(true).build());

        jdbcTemplate.update(
                "INSERT INTO tratamientos (id, codigo, nombre, duracion_minutos, especialidad_id, activo) "
                        + "VALUES (?, ?, ?, ?, ?, TRUE)",
                UUID.randomUUID(), "CHK-" + UUID.randomUUID().toString().substring(0, 8),
                "Tratamiento de prueba", duracionMinutos, esp.getId());
    }

    @Test
    void restriccionDeIntegridad_rechazaUnaDuracionQueNoEsMultiploDeQuince() {
        // RN-04: la regla se implementa «en el servicio de disponibilidad y
        // validacion en la base de datos». Hasta V10 la base solo comprobaba el
        // rango, de modo que una escritura fuera del servicio podia dejar 20.
        assertThrows(DataIntegrityViolationException.class, () -> insertarDirectamente(20));
    }

    @Test
    void restriccionDeIntegridad_rechazaUnaDuracionFueraDelRango() {
        assertThrows(DataIntegrityViolationException.class, () -> insertarDirectamente(255));
        assertThrows(DataIntegrityViolationException.class, () -> insertarDirectamente(0));
    }

    @Test
    void restriccionDeIntegridad_aceptaLosMultiplosDeQuinceDentroDelRango() {
        assertDoesNotThrow(() -> insertarDirectamente(15));
        assertDoesNotThrow(() -> insertarDirectamente(90));
        assertDoesNotThrow(() -> insertarDirectamente(240));
    }
}
