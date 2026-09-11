package pe.edu.dentalcite.plan.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
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
import pe.edu.dentalcite.plan.api.dto.PlanRequestDTO;
import pe.edu.dentalcite.plan.domain.Plan;
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-17 de extremo a extremo contra PostgreSQL real.
 *
 * <p>Lo que <strong>solo</strong> se puede comprobar aquí es el criterio 2: que
 * el 409 lo dé el índice único parcial de {@code V19} y no una comprobación del
 * servicio. Con la base simulada, esa prueba no probaría nada.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PlanDeTratamientoIntegrationTest {

    private static final String RUTA = "/api/v1/planes";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Autowired private WebApplicationContext context;
    @Autowired private PlanRepository planRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private ConsultorioRepository consultorioRepository;

    private UUID especialidadId;
    private UUID ortodonciaId;
    private UUID endodonciaId;

    private UUID luisId;
    private Ficha fichaLuis;
    private UUID luisUsuarioId;
    private UUID anaId;
    private Ficha fichaAna;
    private UUID anaUsuarioId;

    private Ficha fichaPaciente;
    private UUID citaDeLuisConJulio;
    private UUID pacienteUsuarioId;
    private UUID recepcionistaId;
    private UUID administradorId;

    private final List<UUID> planes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("PLAN-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        ortodonciaId = nuevoTratamiento("Ortodoncia", especialidad);
        endodonciaId = nuevoTratamiento("Endodoncia", especialidad);

        fichaLuis = nuevaFicha("Luis", "Perez");
        luisId = nuevoOdontologo(fichaLuis, "Luis", "Perez", especialidad);
        luisUsuarioId = nuevaCuenta("Luis Perez", "ODONTOLOGO", fichaLuis);

        fichaAna = nuevaFicha("Ana", "Quispe");
        anaId = nuevoOdontologo(fichaAna, "Ana", "Quispe", especialidad);
        anaUsuarioId = nuevaCuenta("Ana Quispe", "ODONTOLOGO", fichaAna);

        fichaPaciente = nuevaFicha("Julio", "Ramos");
        pacienteUsuarioId = nuevaCuenta("Julio Ramos", "PACIENTE", fichaPaciente);
        recepcionistaId = nuevaCuenta("Recepcion", "RECEPCIONISTA", null);
        administradorId = nuevaCuenta("Admin", "ADMINISTRADOR", null);

        // RNF-06: Luis ha atendido a Julio y por eso puede planificarle. Ana no,
        // y es lo que hace comprobables las dos pruebas del final.
        //
        // La cita se siembra ATENDIDA y en el pasado a proposito. Las
        // restricciones de exclusion de V13 son *parciales sobre CONFIRMADA*, asi
        // que una cita atendida no puede chocar con el fixture de ninguna otra
        // clase de prueba; y `atendioAPorFichaDelOdontologo` la acepta igual,
        // porque solo descarta las CANCELADAS.
        citaDeLuisConJulio = nuevaCitaAtendida();
    }

    /** La cita que acredita el vinculo de RNF-06 entre Luis y Julio. */
    private UUID nuevaCitaAtendida() {
        Consultorio consultorio = consultorioRepository.findByInoperativoFalse().get(0);
        // Un solo `now()`: derivar el fin de otra llamada dejaria una duracion que
        // no es multiplo de quince si el reloj avanza entre las dos (V10).
        OffsetDateTime inicio = OffsetDateTime.now().minusDays(30)
                .withMinute(0).withSecond(0).withNano(0);
        return citaRepository.save(Cita.builder()
                // De la secuencia, nunca de un count(): la misma regla que en produccion.
                .codigo(String.format("CIT-%06d", citaRepository.getNextCodigoCita()))
                .ficha(fichaPaciente)
                .odontologo(odontologoRepository.findById(luisId).orElseThrow())
                .consultorio(consultorio)
                .tratamiento(tratamientoRepository.findById(ortodonciaId).orElseThrow())
                .inicio(inicio)
                .fin(inicio.plusMinutes(30))
                .estado("ATENDIDA")
                .build()).getId();
    }

    private UUID nuevoTratamiento(String nombre, Especialidad especialidad) {
        UUID id = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(id)
                .codigo("TP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre(nombre).duracionMinutos(30)
                .especialidad(especialidad).activo(true).build());
        return id;
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        return fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres(nombres).apellidos(apellidos).build());
    }

    private UUID nuevoOdontologo(Ficha ficha, String nombres, String apellidos, Especialidad esp) {
        UUID id = UUID.randomUUID();
        odontologoRepository.save(Odontologo.builder()
                .id(id)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres(nombres).apellidos(apellidos)
                .ficha(ficha).activo(true).especialidades(Set.of(esp)).build());
        return id;
    }

    private UUID nuevaCuenta(String nombre, String rol, Ficha ficha) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .correo("hu17-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    @AfterEach
    void tearDown() {
        planRepository.findByFichaIdOrderByCreadoEnDesc(fichaPaciente.getId())
                .forEach(p -> planes.add(p.getId()));
        planes.stream().distinct().forEach(planRepository::deleteById);
        // Antes que la ficha, el odontologo y el tratamiento: sus claves ajenas
        // son RESTRICT y no dejarian borrarlos con la cita todavia apuntandoles.
        citaRepository.deleteById(citaDeLuisConJulio);
        usuarioRepository.deleteById(administradorId);
        usuarioRepository.deleteById(recepcionistaId);
        usuarioRepository.deleteById(pacienteUsuarioId);
        usuarioRepository.deleteById(luisUsuarioId);
        usuarioRepository.deleteById(anaUsuarioId);
        odontologoRepository.deleteById(luisId);
        odontologoRepository.deleteById(anaId);
        tratamientoRepository.deleteById(ortodonciaId);
        tratamientoRepository.deleteById(endodonciaId);
        fichaRepository.deleteById(fichaPaciente.getId());
        fichaRepository.deleteById(fichaLuis.getId());
        fichaRepository.deleteById(fichaAna.getId());
        especialidadRepository.deleteById(especialidadId);
        planes.clear();
    }

    // ------------------------------------------------------------------
    // Utilidades de petición
    // ------------------------------------------------------------------

    private PlanRequestDTO peticion(UUID tratamientoId, int sesiones) {
        return PlanRequestDTO.builder()
                .pacienteId(fichaPaciente.getId())
                .tratamientoId(tratamientoId)
                .sesionesPrevistas(sesiones)
                .build();
    }

    private ResultActions crear(PlanRequestDTO cuerpo, UUID quien, String autoridad) throws Exception {
        return mockMvc.perform(post(RUTA)
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions suspender(UUID planId, String motivo, UUID quien, String autoridad)
            throws Exception {
        return mockMvc.perform(patch(RUTA + "/" + planId + "/suspender")
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"" + motivo + "\"}"));
    }

    private UUID crearPlanDeLuis(UUID tratamientoId, int sesiones) throws Exception {
        String cuerpo = crear(peticion(tratamientoId, sesiones), luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(cuerpo).path("id").asText());
        planes.add(id);
        return id;
    }

    // ------------------------------------------------------------------
    // Criterio 1 · ACTIVO, con sus sesiones numeradas y todas pendientes
    // ------------------------------------------------------------------

    @Test
    void crear_dejaElPlanActivoConSusSesionesNumeradasYPendientes() throws Exception {
        String cuerpo = crear(peticion(ortodonciaId, 6), luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true))
                .andExpect(jsonPath("$.sesionesPrevistas").value(6))
                .andExpect(jsonPath("$.motivoSuspension").doesNotExist())
                .andExpect(jsonPath("$.paciente.nombre").value("Julio Ramos"))
                .andExpect(jsonPath("$.tratamiento.nombre").value("Ortodoncia"))
                .andExpect(jsonPath("$.odontologo.nombre").value("Luis Perez"))
                .andReturn().getResponse().getContentAsString();

        JsonNode plan = objectMapper.readTree(cuerpo);
        planes.add(UUID.fromString(plan.path("id").asText()));

        JsonNode sesiones = plan.path("sesiones");
        assertEquals(6, sesiones.size());
        for (int i = 0; i < sesiones.size(); i++) {
            assertEquals(i + 1, sesiones.get(i).path("numero").asInt(), "numeradas desde 1 y en orden");
            assertEquals("PENDIENTE", sesiones.get(i).path("estado").asText());
        }
    }

    @Test
    void crear_persisteLasSesionesEnLaBase() throws Exception {
        UUID planId = crearPlanDeLuis(ortodonciaId, 4);

        Plan guardado = planRepository.findConDetalleById(planId).orElseThrow();
        assertEquals(4, guardado.getSesiones().size());
        assertTrue(guardado.getSesiones().stream()
                .allMatch(s -> "PENDIENTE".equals(s.getEstado())));
    }

    @Test
    void crear_conCeroSesiones_retornaBadRequest() throws Exception {
        crear(peticion(ortodonciaId, 0), luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_conUnPacienteQueNoExiste_retornaNotFound() throws Exception {
        PlanRequestDTO inventado = peticion(ortodonciaId, 3);
        inventado.setPacienteId(UUID.randomUUID());

        crear(inventado, luisUsuarioId, "SCOPE_ODONTOLOGO").andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Criterio 2 · RN-13, y que la garantía sea el índice
    // ------------------------------------------------------------------

    @Test
    void crear_unSegundoPlanActivoDelMismoTratamiento_retornaConflict() throws Exception {
        crearPlanDeLuis(ortodonciaId, 6);

        crear(peticion(ortodonciaId, 3), luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("RN-13")));
    }

    @Test
    void crear_loRechazaElIndiceYNoUnaComprobacionPrevia() throws Exception {
        // Se salta el servicio y se inserta el segundo plan directamente: si la
        // garantía fuera una consulta previa, esto pasaría sin más.
        crearPlanDeLuis(ortodonciaId, 6);

        Plan duplicado = Plan.builder()
                .ficha(fichaRepository.findById(fichaPaciente.getId()).orElseThrow())
                .tratamiento(tratamientoRepository.findById(ortodonciaId).orElseThrow())
                .odontologo(odontologoRepository.findById(luisId).orElseThrow())
                .sesionesPrevistas(2).activo(true)
                .build();
        duplicado.generarSesiones();

        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class,
                () -> planRepository.saveAndFlush(duplicado));
    }

    @Test
    void crear_unPlanDeOtroTratamiento_seCreaSinProblema() throws Exception {
        // RN-13 acota «del mismo tratamiento»: dos tratamientos distintos pueden
        // estar en curso a la vez.
        crearPlanDeLuis(ortodonciaId, 6);

        crear(peticion(endodonciaId, 2), luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Criterio 3 · suspender conserva el registro y libera el tratamiento
    // ------------------------------------------------------------------

    @Test
    void suspender_dejaElPlanInactivoConservandoSuRegistro() throws Exception {
        UUID planId = crearPlanDeLuis(ortodonciaId, 6);

        suspender(planId, "El paciente se muda de ciudad", luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.motivoSuspension").value("El paciente se muda de ciudad"))
                // «Su registro se conservara»: las sesiones previstas siguen ahi.
                .andExpect(jsonPath("$.sesionesPrevistas").value(6))
                .andExpect(jsonPath("$.sesiones.length()").value(6));

        // RN-12: la fila sigue en la base.
        Plan guardado = planRepository.findConDetalleById(planId).orElseThrow();
        assertFalse(guardado.getActivo());
        assertEquals(6, guardado.getSesiones().size());
    }

    @Test
    void suspender_devuelveEseTratamientoAlConjuntoDeLosPlanificables() throws Exception {
        // Es el segundo medio criterio, y sale gratis: el indice unico es parcial
        // sobre `activo`, asi que la fila suspendida deja de ocupar el hueco.
        UUID planId = crearPlanDeLuis(ortodonciaId, 6);

        crear(peticion(ortodonciaId, 3), luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isConflict());

        suspender(planId, "Se replantea el tratamiento", luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isOk());

        crear(peticion(ortodonciaId, 3), luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activo").value(true));
    }

    @Test
    void suspender_sinMotivo_retornaBadRequest() throws Exception {
        UUID planId = crearPlanDeLuis(ortodonciaId, 6);

        mockMvc.perform(patch(RUTA + "/" + planId + "/suspender")
                        .with(user(luisUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void suspender_dosVeces_laSegundaRetornaConflict() throws Exception {
        UUID planId = crearPlanDeLuis(ortodonciaId, 6);

        suspender(planId, "Primero", luisUsuarioId, "SCOPE_ODONTOLOGO").andExpect(status().isOk());
        suspender(planId, "Segundo", luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isConflict());
    }

    @Test
    void suspender_unPlanQueNoExiste_retornaNotFound() throws Exception {
        suspender(UUID.randomUUID(), "Motivo", luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // El listado, que es lo que hace alcanzable la suspension
    // ------------------------------------------------------------------

    @Test
    void listar_devuelveLosPlanesDelPacienteActivosYSuspendidos() throws Exception {
        UUID enCurso = crearPlanDeLuis(ortodonciaId, 6);
        UUID suspendido = crearPlanDeLuis(endodonciaId, 2);
        suspender(suspendido, "Se replantea", luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isOk());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(RUTA)
                        .with(user(luisUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .param("pacienteId", fichaPaciente.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.id=='" + enCurso + "')].activo").value(true))
                .andExpect(jsonPath("$[?(@.id=='" + suspendido + "')].activo").value(false));
    }

    @Test
    void listar_noTraeAvance() throws Exception {
        // RN-14 lo deriva de las citas atendidas enlazadas, y eso es HU-18: un
        // campo aqui prometeria un dato que todavia no significa nada.
        crearPlanDeLuis(ortodonciaId, 6);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(RUTA)
                        .with(user(luisUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .param("pacienteId", fichaPaciente.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].avance").doesNotExist())
                .andExpect(jsonPath("$[0].sesiones.length()").value(6));
    }

    @Test
    void listar_comoPaciente_retornaForbidden() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(RUTA)
                        .with(user(pacienteUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                        .param("pacienteId", fichaPaciente.getId().toString()))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Criterio 4 · RNF-04
    // ------------------------------------------------------------------

    @Test
    void crear_comoPaciente_retornaForbidden() throws Exception {
        crear(peticion(ortodonciaId, 3), pacienteUsuarioId, "SCOPE_PACIENTE")
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_comoRecepcionista_retornaForbidden() throws Exception {
        crear(peticion(ortodonciaId, 3), recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_comoAdministradorIndicandoElOdontologo_seCrea() throws Exception {
        PlanRequestDTO conOdontologo = peticion(ortodonciaId, 3);
        conOdontologo.setOdontologoId(anaId);

        String cuerpo = crear(conOdontologo, administradorId, "SCOPE_ADMINISTRADOR")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.odontologo.nombre").value("Ana Quispe"))
                .andReturn().getResponse().getContentAsString();
        planes.add(UUID.fromString(objectMapper.readTree(cuerpo).path("id").asText()));
    }

    @Test
    void crear_comoAdministradorSinIndicarOdontologo_retornaBadRequest() throws Exception {
        crear(peticion(ortodonciaId, 3), administradorId, "SCOPE_ADMINISTRADOR")
                .andExpect(status().isBadRequest());
    }

    @Test
    void crear_comoOdontologoIndicandoOtroOdontologo_retornaForbidden() throws Exception {
        PlanRequestDTO ajeno = peticion(ortodonciaId, 3);
        ajeno.setOdontologoId(anaId);

        crear(ajeno, luisUsuarioId, "SCOPE_ODONTOLOGO").andExpect(status().isForbidden());
    }

    @Test
    void suspender_elPlanDeOtroOdontologo_retornaForbidden() throws Exception {
        UUID planId = crearPlanDeLuis(ortodonciaId, 6);

        suspender(planId, "Motivo", anaUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isForbidden());

        assertTrue(planRepository.findConDetalleById(planId).orElseThrow().getActivo());
    }

    @Test
    void suspender_comoAdministrador_puedeConElPlanDeCualquiera() throws Exception {
        UUID planId = crearPlanDeLuis(ortodonciaId, 6);

        suspender(planId, "Correccion administrativa", administradorId, "SCOPE_ADMINISTRADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false));
    }

    // ------------------------------------------------------------------
    // RNF-06 · el vinculo con el paciente
    //
    // Ana es odontologa y su rol le permite planificar, pero nunca ha atendido a
    // Julio: la cita del fixture es de Luis. Es la misma situacion en la que
    // HU-13 devuelve 403 al abrir la ficha, y aqui tiene que responder igual.
    // ------------------------------------------------------------------

    @Test
    void crear_comoOdontologoQueNoHaAtendidoAlPaciente_retornaForbidden() throws Exception {
        crear(peticion(ortodonciaId, 6), anaUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isForbidden());

        // Y no queda plan a medio crear.
        assertTrue(planRepository.findByFichaIdOrderByCreadoEnDesc(fichaPaciente.getId()).isEmpty());
    }

    @Test
    void listar_comoOdontologoQueNoHaAtendidoAlPaciente_retornaForbidden() throws Exception {
        crearPlanDeLuis(ortodonciaId, 6);

        String cuerpo = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(RUTA)
                        .with(user(anaUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .param("pacienteId", fichaPaciente.getId().toString()))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();

        // Lo que se estaba filtrando era la identidad del paciente, asi que el 403
        // no vale si el cuerpo la sigue nombrando.
        assertFalse(cuerpo.contains("Julio"), "el 403 no debe revelar el nombre del paciente");
        assertFalse(cuerpo.contains("Ramos"), "el 403 no debe revelar el apellido del paciente");
        assertFalse(cuerpo.contains(fichaPaciente.getNumeroHistoria()),
                "el 403 no debe revelar el numero de historia");
    }

    @Test
    void listar_comoAdministrador_noLeAplicaLaRestriccionDeRNF06() throws Exception {
        // Administracion ve todas las fichas (RF-07), asi que el guard la deja
        // pasar sin preguntar por citas. Sin esta prueba, apretar RNF-06 podria
        // haber cerrado tambien esa puerta sin que nadie se diera cuenta.
        crearPlanDeLuis(ortodonciaId, 6);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get(RUTA)
                        .with(user(administradorId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ADMINISTRADOR")))
                        .param("pacienteId", fichaPaciente.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }
}
