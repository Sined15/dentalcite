package pe.edu.dentalcite.horario.api;

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
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.horario.api.dto.HorarioRequest;
import pe.edu.dentalcite.horario.api.dto.HorarioResponseDTO;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sin {@code @Transactional} a propósito. La clase lo llevaba, y eso mantenía la
 * sesión de persistencia abierta durante la serialización de MockMvc: los
 * endpoints que devolvían la entidad {@code HorarioAtencion} pasaban aquí y
 * respondían 500 en producción, donde {@code open-in-view} está desactivado.
 * Cada prueba siembra su propio odontólogo con un sufijo aleatorio, de modo que
 * las filas que deja no colisionan con las de ninguna otra.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class HorarioControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FichaRepository fichaRepository;

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

    private static String sufijo() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Crea una ficha, su cuenta de rol ODONTOLOGO y el registro de odontólogo que
     * las une, tal como exige RN-11, y devuelve el par (idUsuario, idOdontologo).
     */
    private UUID[] sembrarOdontologo() {
        String sufijo = sufijo();

        Ficha ficha = fichaRepository.save(Ficha.builder()
                .documento("DOC" + sufijo)
                .numeroHistoria("HC-T" + sufijo)
                .build());

        Usuario usuario = usuarioRepository.save(Usuario.builder()
                .nombre("Odontologo " + sufijo)
                .correo("odontologo" + sufijo + "@test.com")
                .contrasenaHash("$2a$12$0000000000000000000000000000000000000000000000000000")
                .rol("ODONTOLOGO")
                .ficha(ficha)
                .build());

        Odontologo odontologo = odontologoRepository.save(Odontologo.builder()
                .id(UUID.randomUUID())
                .cop("COP" + sufijo)
                .nombres("Nombre" + sufijo)
                .apellidos("Apellido" + sufijo)
                .ficha(ficha)
                .activo(true)
                .build());

        return new UUID[] { usuario.getId(), odontologo.getId() };
    }

    private HorarioRequest lunesDe9a13() {
        HorarioRequest req = new HorarioRequest();
        req.setDiaSemana(1); // Lunes
        req.setHoraInicio(LocalTime.of(9, 0));
        req.setHoraFin(LocalTime.of(13, 0));
        return req;
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void crearHorario_conRolPaciente_retornaForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/odontologos/" + UUID.randomUUID() + "/horarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lunesDe9a13())))
                .andExpect(status().isForbidden());
    }

    @Test
    void crearHorario_comoOdontologoPropio_retorna201() throws Exception {
        UUID[] propio = sembrarOdontologo();

        mockMvc.perform(post("/api/v1/odontologos/" + propio[1] + "/horarios")
                .with(user(propio[0].toString())
                        .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lunesDe9a13())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.diaSemana").value(1));
    }

    @Test
    void crearHorario_comoOtroOdontologo_retorna403() throws Exception {
        UUID[] duenio = sembrarOdontologo();
        UUID[] intruso = sembrarOdontologo();

        // HU-07: «Dado que soy ODONTOLOGO, cuando modifique el horario de otro
        // odontólogo, entonces recibiré 403» (RNF-04).
        mockMvc.perform(post("/api/v1/odontologos/" + duenio[1] + "/horarios")
                .with(user(intruso[0].toString())
                        .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lunesDe9a13())))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearHorario_comoAdministrador_retorna201() throws Exception {
        UUID[] ajeno = sembrarOdontologo();

        mockMvc.perform(post("/api/v1/odontologos/" + ajeno[1] + "/horarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lunesDe9a13())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void declararConsultarYReemplazarUnTramo_recorreElCicloCompleto() throws Exception {
        // HU-07, primer criterio: «Dado que defino los lunes de 09:00 a 13:00,
        // cuando consulte el horario declarado de ese odontólogo, entonces el tramo
        // constará para el lunes». Los tres verbos serializan la respuesta, que es
        // justo donde reventaba al devolver la entidad.
        UUID odontologoId = sembrarOdontologo()[1];
        String base = "/api/v1/odontologos/" + odontologoId + "/horarios";

        String creado = mockMvc.perform(post(base)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lunesDe9a13())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        HorarioResponseDTO tramo = objectMapper.readValue(creado, HorarioResponseDTO.class);
        assertEquals(odontologoId, tramo.getOdontologoId());
        assertEquals(1, tramo.getDiaSemana());
        assertEquals(LocalTime.of(9, 0), tramo.getHoraInicio());
        assertEquals(LocalTime.of(13, 0), tramo.getHoraFin());

        String listado = mockMvc.perform(get(base))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<HorarioResponseDTO> declarados = objectMapper.readValue(listado,
                objectMapper.getTypeFactory().constructCollectionType(List.class, HorarioResponseDTO.class));
        assertEquals(1, declarados.size());
        assertEquals(tramo.getId(), declarados.get(0).getId());
        assertEquals(odontologoId, declarados.get(0).getOdontologoId());
        assertEquals(1, declarados.get(0).getDiaSemana());
        assertEquals(LocalTime.of(9, 0), declarados.get(0).getHoraInicio());

        HorarioRequest martes = new HorarioRequest();
        martes.setDiaSemana(2);
        martes.setHoraInicio(LocalTime.of(15, 0));
        martes.setHoraFin(LocalTime.of(19, 0));

        mockMvc.perform(put(base + "/" + tramo.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(martes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(tramo.getId().toString()))
                .andExpect(jsonPath("$.diaSemana").value(2));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearHorario_conTramoSolapado_retorna409() throws Exception {
        // HU-07: «todo tramo nuevo que se solape con él será rechazado con 409» (RN-03).
        UUID odontologoId = sembrarOdontologo()[1];
        String base = "/api/v1/odontologos/" + odontologoId + "/horarios";

        mockMvc.perform(post(base)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(lunesDe9a13())))
                .andExpect(status().isCreated());

        HorarioRequest solapado = new HorarioRequest();
        solapado.setDiaSemana(1);
        solapado.setHoraInicio(LocalTime.of(12, 0));
        solapado.setHoraFin(LocalTime.of(14, 0));

        // Se comprueba tambien el cuerpo: el 409 lo lanza el servicio como
        // ResponseStatusException, que sin manejador salia por el /error por
        // defecto de Spring con {timestamp, status, error, path} y sin el motivo.
        // El cliente solo entiende {message}, asi que quien solapaba un tramo veia
        // la ruta de la peticion en lugar de la causa del rechazo.
        mockMvc.perform(post(base)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(solapado)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("El horario se solapa con uno existente"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void listarHorarios_conRolRecepcionista_retorna403() throws Exception {
        // Tabla 10 (v2, I-02): la fila «Horario de atención» retira a la
        // recepcionista, que conserva únicamente los bloqueos.
        mockMvc.perform(get("/api/v1/odontologos/" + UUID.randomUUID() + "/horarios"))
                .andExpect(status().isForbidden());
    }
}
