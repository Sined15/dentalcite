package pe.edu.dentalcite.plan.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.plan.domain.PlanSesion;
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El enlace de una cita atendida con la sesión de su plan, de extremo a extremo
 * contra PostgreSQL real.
 *
 * <p>Lo que solo se puede comprobar aquí: que dos cierres simultáneos no ocupen
 * la misma sesión, que la base rechace una cita repetida o una sesión atendida
 * sin cita, y que el esquema no tenga dónde guardar el avance.
 *
 * <p>Las citas se siembran directamente en la base, porque lo que se cierra es
 * una cita ya terminada y la reserva no admite horas pasadas. Van en un
 * consultorio propio para no chocar con la agenda sembrada ni con la de otras
 * clases de prueba, y la clase no es transaccional porque los cierres
 * simultáneos necesitan ver lo que confirma el otro.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EnlaceDeCitaConSesionIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private WebApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlanRepository planRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private CitaHistorialRepository historialRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private ConsultorioRepository consultorioRepository;

    private UUID especialidadId;
    private UUID ortodonciaId;
    private UUID endodonciaId;

    private Ficha fichaLuis;
    private UUID luisId;
    private UUID luisUsuarioId;

    private Ficha fichaPaciente;
    private UUID recepcionistaId;
    private Consultorio consultorio;

    /** La hora en punto de referencia: las citas se colocan a horas o días enteros de ella. */
    private OffsetDateTime enPunto;

    private final List<UUID> citas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        enPunto = OffsetDateTime.now(ZONA).truncatedTo(ChronoUnit.HOURS);

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("ENLACE-" + corto())
                .activo(true).build());
        especialidadId = especialidad.getId();

        ortodonciaId = nuevoTratamiento("Ortodoncia", especialidad);
        endodonciaId = nuevoTratamiento("Endodoncia", especialidad);

        fichaLuis = nuevaFicha("Luis", "Perez");
        luisId = UUID.randomUUID();
        odontologoRepository.save(Odontologo.builder()
                .id(luisId)
                .cop("COP-" + corto())
                .nombres("Luis").apellidos("Perez")
                .ficha(fichaLuis).activo(true).especialidades(Set.of(especialidad)).build());
        luisUsuarioId = nuevaCuenta("Luis Perez", "ODONTOLOGO", fichaLuis);

        fichaPaciente = nuevaFicha("Julio", "Ramos");
        recepcionistaId = nuevaCuenta("Recepcion", "RECEPCIONISTA", null);

        consultorio = consultorioRepository.save(Consultorio.builder()
                .id(UUID.randomUUID()).nombre("Consultorio de enlace " + corto())
                .inoperativo(false).build());

        // Luis puede planificarle a Julio porque ya lo ha visto una vez. La cita es
        // de un paciente que no vino, a propósito: basta para el vínculo y no ocupa
        // ninguna sesión, así que no enturbia lo que cuenta cada prueba.
        sembrar(ortodonciaId, Cita.ESTADO_NO_ASISTIO, enPunto.minusDays(40));
    }

    @AfterEach
    void tearDown() {
        // Los planes antes que las citas: sus sesiones apuntan a ellas y esa clave
        // ajena no deja borrar una cita que ocupa una sesión.
        planRepository.findByFichaIdOrderByCreadoEnDesc(fichaPaciente.getId())
                .forEach(p -> planRepository.deleteById(p.getId()));
        // La bitácora antes que las cuentas: su clave ajena al usuario no deja
        // borrar a quien registró una transición.
        citas.forEach(id -> historialRepository.findByCitaIdOrderByOcurridoEnAsc(id)
                .forEach(historialRepository::delete));
        citas.forEach(citaRepository::deleteById);
        citas.clear();
        usuarioRepository.deleteById(recepcionistaId);
        usuarioRepository.deleteById(luisUsuarioId);
        odontologoRepository.deleteById(luisId);
        tratamientoRepository.deleteById(ortodonciaId);
        tratamientoRepository.deleteById(endodonciaId);
        fichaRepository.deleteById(fichaPaciente.getId());
        fichaRepository.deleteById(fichaLuis.getId());
        especialidadRepository.deleteById(especialidadId);
        consultorioRepository.deleteById(consultorio.getId());
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private static String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID nuevoTratamiento(String nombre, Especialidad especialidad) {
        UUID id = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(id)
                .codigo("TE-" + corto())
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

    private UUID nuevaCuenta(String nombre, String rol, Ficha ficha) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .correo("enlace-" + corto() + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    private Cita sembrar(UUID tratamientoId, String estado, OffsetDateTime inicio) {
        Cita cita = citaRepository.save(Cita.builder()
                .codigo("EN-" + corto())
                .ficha(fichaPaciente)
                .odontologo(odontologoRepository.findById(luisId).orElseThrow())
                .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                .consultorio(consultorio)
                .inicio(inicio).fin(inicio.plusMinutes(30))
                .estado(estado)
                .build());
        citas.add(cita.getId());
        return cita;
    }

    /** Una cita confirmada que ya terminó: la que se puede cerrar. */
    private Cita porCerrar(UUID tratamientoId, int empezoHaceHoras) {
        return sembrar(tratamientoId, Cita.ESTADO_CONFIRMADA, enPunto.minusHours(empezoHaceHoras));
    }

    private ResultActions cerrar(UUID citaId, String resultado) throws Exception {
        return mockMvc.perform(patch("/api/v1/citas/" + citaId + "/resultado")
                .with(user(recepcionistaId.toString())
                        .authorities(new SimpleGrantedAuthority("SCOPE_RECEPCIONISTA")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultado\":\"" + resultado + "\"}"));
    }

    private JsonNode crearPlan(UUID tratamientoId, int sesiones) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/planes")
                        .with(user(luisUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":\"" + fichaPaciente.getId() + "\","
                                + "\"tratamientoId\":\"" + tratamientoId + "\","
                                + "\"sesionesPrevistas\":" + sesiones + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(cuerpo);
    }

    private void suspender(UUID planId) throws Exception {
        mockMvc.perform(patch("/api/v1/planes/" + planId + "/suspender")
                        .with(user(luisUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Cambio de indicacion\"}"))
                .andExpect(status().isOk());
    }

    private static UUID id(JsonNode plan) {
        return UUID.fromString(plan.path("id").asText());
    }

    /** Para cada sesión, en orden, la cita que la ocupa o {@code null} si sigue pendiente. */
    private List<UUID> citasPorSesion(UUID planId) {
        return planRepository.findConDetalleById(planId).orElseThrow().getSesiones().stream()
                .sorted(Comparator.comparing(PlanSesion::getNumero))
                .map(s -> s.getCita() == null ? null : s.getCita().getId())
                .toList();
    }

    private List<String> estadosPorSesion(UUID planId) {
        return planRepository.findConDetalleById(planId).orElseThrow().getSesiones().stream()
                .sorted(Comparator.comparing(PlanSesion::getNumero))
                .map(PlanSesion::getEstado)
                .toList();
    }

    // ------------------------------------------------------------------
    // Al registrar el resultado de la cita
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_atendidaConPlanActivo_ocupaLaPrimeraSesionPendiente() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 3));
        Cita primera = porCerrar(ortodonciaId, 4);
        Cita segunda = porCerrar(ortodonciaId, 2);

        cerrar(primera.getId(), "ATENDIDA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesionEnlazada.numero").value(1))
                .andExpect(jsonPath("$.sesionEnlazada.planId").value(planId.toString()))
                .andExpect(jsonPath("$.sesionEnlazada.tratamiento").value("Ortodoncia"));
        // Con la primera ya ocupada, la siguiente va a la segunda y no a otra cualquiera.
        cerrar(segunda.getId(), "ATENDIDA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesionEnlazada.numero").value(2));

        assertEquals(Arrays.asList(primera.getId(), segunda.getId(), null), citasPorSesion(planId));
        assertEquals(List.of("ATENDIDA", "ATENDIDA", "PENDIENTE"), estadosPorSesion(planId));
    }

    @Test
    void registrarResultado_deOtroTratamiento_noOcupaNingunaSesion() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 3));
        Cita deEndodoncia = porCerrar(endodonciaId, 2);

        cerrar(deEndodoncia.getId(), "ATENDIDA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.sesionEnlazada").doesNotExist());

        assertEquals(Arrays.asList(null, null, null), citasPorSesion(planId));
    }

    @Test
    void registrarResultado_noAsistio_noOcupaNingunaSesion() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 3));
        Cita cita = porCerrar(ortodonciaId, 2);

        cerrar(cita.getId(), "NO_ASISTIO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesionEnlazada").doesNotExist());

        assertEquals(Arrays.asList(null, null, null), citasPorSesion(planId));
    }

    @Test
    void registrarResultado_conElPlanSuspendido_noOcupaNingunaSesion() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 3));
        suspender(planId);
        Cita cita = porCerrar(ortodonciaId, 2);

        cerrar(cita.getId(), "ATENDIDA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesionEnlazada").doesNotExist());

        assertEquals(Arrays.asList(null, null, null), citasPorSesion(planId));
    }

    @Test
    void registrarResultado_conElPlanCompleto_noOcupaNadaNiExcedeLasSesionesPrevistas() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 1));
        Cita unica = porCerrar(ortodonciaId, 4);
        Cita sobrante = porCerrar(ortodonciaId, 2);

        cerrar(unica.getId(), "ATENDIDA")
                .andExpect(jsonPath("$.sesionEnlazada.numero").value(1));
        cerrar(sobrante.getId(), "ATENDIDA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.sesionEnlazada").doesNotExist());

        assertEquals(1, planRepository.findConDetalleById(planId).orElseThrow().getSesionesPrevistas());
        assertEquals(List.of(unica.getId()), citasPorSesion(planId));
    }

    @Test
    void registrarResultado_dosCierresSimultaneosDelMismoPlan_ocupanSesionesDistintas() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 3));
        Cita una = porCerrar(ortodonciaId, 4);
        Cita otra = porCerrar(ortodonciaId, 2);

        CountDownLatch salida = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> respuestas = new ArrayList<>();
            for (Cita cita : List.of(una, otra)) {
                respuestas.add(hilos.submit(() -> {
                    salida.await();
                    return cerrar(cita.getId(), "ATENDIDA").andReturn().getResponse().getStatus();
                }));
            }
            salida.countDown();
            for (Future<Integer> respuesta : respuestas) {
                assertEquals(200, respuesta.get(30, TimeUnit.SECONDS));
            }
        } finally {
            hilos.shutdownNow();
        }

        List<UUID> ocupadas = citasPorSesion(planId);
        assertEquals(Set.of(una.getId(), otra.getId()), new HashSet<>(ocupadas.subList(0, 2)));
        assertNull(ocupadas.get(2));
    }

    // ------------------------------------------------------------------
    // Al crear el plan
    // ------------------------------------------------------------------

    @Test
    void crear_conCitasAtendidasRecientes_lasEnlazaEnOrdenDejandoUnaPendiente() throws Exception {
        // Se siembran desordenadas para que el orden lo ponga la consulta y no la inserción.
        Cita reciente = sembrar(ortodonciaId, Cita.ESTADO_ATENDIDA, enPunto.minusDays(5));
        Cita antigua = sembrar(ortodonciaId, Cita.ESTADO_ATENDIDA, enPunto.minusDays(20));
        Cita intermedia = sembrar(ortodonciaId, Cita.ESTADO_ATENDIDA, enPunto.minusDays(10));

        JsonNode plan = crearPlan(ortodonciaId, 3);

        JsonNode sesiones = plan.path("sesiones");
        assertEquals(antigua.getCodigo(), sesiones.get(0).path("cita").path("codigo").asText());
        assertEquals("ATENDIDA", sesiones.get(0).path("estado").asText());
        assertEquals(intermedia.getCodigo(), sesiones.get(1).path("cita").path("codigo").asText());
        // Sobra una cita y aun así queda una sesión por delante: el plan no nace terminado.
        assertEquals("PENDIENTE", sesiones.get(2).path("estado").asText());
        assertTrue(sesiones.get(2).path("cita").isNull());

        List<UUID> ocupadas = citasPorSesion(id(plan));
        assertEquals(Arrays.asList(antigua.getId(), intermedia.getId(), null), ocupadas);
        assertFalse(ocupadas.contains(reciente.getId()));
    }

    @Test
    void crear_noEnlazaCitasFueraDeLaVentanaNiLasQueYaOcupanOtraSesion() throws Exception {
        Cita fueraDeVentana = sembrar(ortodonciaId, Cita.ESTADO_ATENDIDA, enPunto.minusDays(120));
        Cita dentro = sembrar(ortodonciaId, Cita.ESTADO_ATENDIDA, enPunto.minusDays(10));

        UUID primerPlan = id(crearPlan(ortodonciaId, 3));
        assertEquals(Arrays.asList(dentro.getId(), null, null), citasPorSesion(primerPlan));

        suspender(primerPlan);
        UUID segundoPlan = id(crearPlan(ortodonciaId, 3));

        // La de hace ciento veinte días no entra en ninguno, y la de hace diez
        // sigue siendo del plan suspendido.
        assertEquals(Arrays.asList(null, null, null), citasPorSesion(segundoPlan));
        assertEquals(Arrays.asList(dentro.getId(), null, null), citasPorSesion(primerPlan));
        assertFalse(citasPorSesion(primerPlan).contains(fueraDeVentana.getId()));
    }

    // ------------------------------------------------------------------
    // Lo que garantiza la base
    // ------------------------------------------------------------------

    @Test
    void esquema_noTieneNingunaColumnaQueGuardeElAvance() {
        List<String> columnas = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name IN ('planes', 'plan_sesiones')
                """, String.class);

        // Primero que la consulta ve de verdad las dos tablas: una lista vacía
        // también pasaría el filtro de abajo.
        assertTrue(columnas.contains("plan_sesiones.cita_id"), columnas.toString());
        assertTrue(columnas.contains("planes.sesiones_previstas"), columnas.toString());
        assertTrue(columnas.stream().noneMatch(c ->
                c.matches("(?i).*(avance|progreso|porcentaje|atendidas|completad|realizadas).*")),
                columnas.toString());
    }

    @Test
    void sesion_laMismaCitaEnDosSesiones_laRechazaLaBase() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 3));
        Cita cita = sembrar(ortodonciaId, Cita.ESTADO_ATENDIDA, enPunto.minusDays(2));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                UPDATE plan_sesiones SET cita_id = ?, estado = 'ATENDIDA'
                WHERE plan_id = ? AND numero IN (1, 2)
                """, cita.getId(), planId));
    }

    @Test
    void sesion_atendidaSinCita_laRechazaLaBase() throws Exception {
        UUID planId = id(crearPlan(ortodonciaId, 3));

        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "UPDATE plan_sesiones SET estado = 'ATENDIDA' WHERE plan_id = ? AND numero = 1", planId));
    }
}
