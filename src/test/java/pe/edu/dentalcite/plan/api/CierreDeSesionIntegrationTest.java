package pe.edu.dentalcite.plan.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
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
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;
import pe.edu.dentalcite.recomendacion.repository.RecomendacionRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El cierre de la sesión de extremo a extremo contra PostgreSQL real.
 *
 * <p>Lo que <strong>solo</strong> se puede comprobar aquí es que las recomendaciones
 * acaban en su tabla puente y que la sesión llega a CERRADA sin que la base la
 * rechace: con repositorios simulados, el CHECK que ata el estado con la fecha del
 * próximo control no diría nada.
 *
 * <p>También vive aquí la lectura que el paciente estrena: sus propios planes, con lo
 * que se le indicó en cada sesión.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CierreDeSesionIntegrationTest {

    private static final String PLANES = "/api/v1/planes";
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
    @Autowired private RecomendacionRepository recomendacionRepository;

    private UUID especialidadId;
    private UUID ortodonciaId;

    private Ficha fichaLuis;
    private UUID luisId;
    private UUID luisUsuarioId;

    private Ficha fichaAna;
    private UUID anaId;
    private UUID anaUsuarioId;

    private Ficha fichaPaciente;
    private UUID pacienteUsuarioId;
    private Ficha fichaOtroPaciente;
    private UUID recepcionistaId;
    private UUID administradorId;
    private Consultorio consultorio;

    private OffsetDateTime enPunto;
    private List<Recomendacion> catalogo;

    private final List<UUID> citas = new ArrayList<>();
    private final List<UUID> planes = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        enPunto = OffsetDateTime.now(ZONA).truncatedTo(ChronoUnit.HOURS);

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID()).nombre("CIERRE-" + corto()).activo(true).build());
        especialidadId = especialidad.getId();
        ortodonciaId = nuevoTratamiento("Ortodoncia", especialidad);

        fichaLuis = nuevaFicha("Luis", "Perez");
        luisId = nuevoOdontologo(fichaLuis, "Luis", "Perez", especialidad);
        luisUsuarioId = nuevaCuenta("Luis Perez", "ODONTOLOGO", fichaLuis);

        fichaAna = nuevaFicha("Ana", "Quispe");
        anaId = nuevoOdontologo(fichaAna, "Ana", "Quispe", especialidad);
        anaUsuarioId = nuevaCuenta("Ana Quispe", "ODONTOLOGO", fichaAna);

        fichaPaciente = nuevaFicha("Julio", "Ramos");
        pacienteUsuarioId = nuevaCuenta("Julio Ramos", "PACIENTE", fichaPaciente);
        fichaOtroPaciente = nuevaFicha("Marta", "Solis");
        recepcionistaId = nuevaCuenta("Recepcion", "RECEPCIONISTA", null);
        administradorId = nuevaCuenta("Admin", "ADMINISTRADOR", null);

        consultorio = consultorioRepository.save(Consultorio.builder()
                .id(UUID.randomUUID()).nombre("Consultorio de cierre " + corto())
                .inoperativo(false).build());

        // El catálogo cerrado lo siembra la primera migración; esta prueba elige de
        // él, que es justo lo que hace quien cierra una sesión.
        catalogo = recomendacionRepository.findByActivaTrueOrderByDescripcionAsc();

        // Luis ya ha visto a Julio, que es lo que le permite planificarle. Sembrada
        // como paciente que no vino: basta para el vínculo y no ocupa ninguna sesión.
        sembrar(Cita.ESTADO_NO_ASISTIO, enPunto.minusDays(40));
    }

    @AfterEach
    void tearDown() {
        planes.stream().distinct().forEach(planRepository::deleteById);
        planes.clear();
        // La bitácora antes que las cuentas: su clave ajena al usuario no deja borrar
        // a quien registró una transición.
        citas.forEach(id -> historialRepository.findByCitaIdOrderByOcurridoEnAsc(id)
                .forEach(historialRepository::delete));
        citas.forEach(citaRepository::deleteById);
        citas.clear();
        usuarioRepository.deleteById(administradorId);
        usuarioRepository.deleteById(recepcionistaId);
        usuarioRepository.deleteById(pacienteUsuarioId);
        usuarioRepository.deleteById(luisUsuarioId);
        usuarioRepository.deleteById(anaUsuarioId);
        odontologoRepository.deleteById(luisId);
        odontologoRepository.deleteById(anaId);
        tratamientoRepository.deleteById(ortodonciaId);
        fichaRepository.deleteById(fichaPaciente.getId());
        fichaRepository.deleteById(fichaOtroPaciente.getId());
        fichaRepository.deleteById(fichaLuis.getId());
        fichaRepository.deleteById(fichaAna.getId());
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
                .id(id).codigo("TC-" + corto()).nombre(nombre).duracionMinutos(30)
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
                .id(id).cop("COP-" + corto()).nombres(nombres).apellidos(apellidos)
                .ficha(ficha).activo(true).especialidades(Set.of(esp)).build());
        return id;
    }

    private UUID nuevaCuenta(String nombre, String rol, Ficha ficha) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .correo("hu19-" + corto() + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    private Cita sembrar(String estado, OffsetDateTime inicio) {
        Cita cita = citaRepository.save(Cita.builder()
                .codigo("CI-" + corto())
                .ficha(fichaPaciente)
                .odontologo(odontologoRepository.findById(luisId).orElseThrow())
                .tratamiento(tratamientoRepository.findById(ortodonciaId).orElseThrow())
                .consultorio(consultorio)
                .inicio(inicio).fin(inicio.plusMinutes(30))
                .estado(estado)
                .build());
        citas.add(cita.getId());
        return cita;
    }

    /** Crea el plan de Luis para Julio y devuelve su identificador. */
    private UUID crearPlan(int sesiones) throws Exception {
        String cuerpo = mockMvc.perform(post(PLANES)
                        .with(odontologo(luisUsuarioId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":\"" + fichaPaciente.getId() + "\","
                                + "\"tratamientoId\":\"" + ortodonciaId + "\","
                                + "\"sesionesPrevistas\":" + sesiones + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(objectMapper.readTree(cuerpo).path("id").asText());
        planes.add(id);
        return id;
    }

    /**
     * Deja la primera sesión del plan ocupada por una cita atendida, que es como se
     * llega de verdad a una sesión cerrable: cerrando la cita.
     */
    private void atenderUnaCita(int empezoHaceHoras) throws Exception {
        Cita cita = sembrar(Cita.ESTADO_CONFIRMADA, enPunto.minusHours(empezoHaceHoras));
        mockMvc.perform(patch("/api/v1/citas/" + cita.getId() + "/resultado")
                        .with(user(recepcionistaId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_RECEPCIONISTA")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultado\":\"ATENDIDA\"}"))
                .andExpect(status().isOk());
    }

    /** Quien más peticiones hace en esta clase: el odontólogo que planificó. */
    private static RequestPostProcessor odontologo(UUID quien) {
        return user(quien.toString()).authorities(new SimpleGrantedAuthority("SCOPE_ODONTOLOGO"));
    }

    private String cuerpoDeCierre(List<UUID> recomendaciones, String proximoControl,
            String observacion) {
        String lista = recomendaciones.stream()
                .map(id -> "\"" + id + "\"")
                .collect(Collectors.joining(","));
        return "{\"recomendacionIds\":[" + lista + "],"
                + "\"proximoControl\":" + (proximoControl == null ? "null" : "\"" + proximoControl + "\"")
                + (observacion == null ? "" : ",\"observacion\":\"" + observacion + "\"")
                + "}";
    }

    private ResultActions cerrar(UUID planId, int numero, String cuerpo, UUID quien,
            String autoridad) throws Exception {
        return mockMvc.perform(post(PLANES + "/" + planId + "/sesiones/" + numero + "/cierre")
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    /** El cierre corriente: dos recomendaciones del catálogo y un control en un mes. */
    private ResultActions cerrarComoLuis(UUID planId, int numero) throws Exception {
        return cerrar(planId, numero,
                cuerpoDeCierre(List.of(catalogo.get(0).getId(), catalogo.get(1).getId()),
                        LocalDate.now(ZONA).plusDays(30).toString(),
                        "Volver antes si aparece dolor"),
                luisUsuarioId, "SCOPE_ODONTOLOGO");
    }

    private int recomendacionesGuardadas(UUID planId) {
        Integer total = jdbc.queryForObject("""
                SELECT COUNT(*) FROM plan_sesion_recomendaciones psr
                JOIN plan_sesiones ps ON ps.id = psr.sesion_id
                WHERE ps.plan_id = ?
                """, Integer.class, planId);
        return total == null ? 0 : total;
    }

    // ------------------------------------------------------------------
    // El cierre
    // ------------------------------------------------------------------

    @Test
    void cerrarSesion_conRecomendacionesYFecha_dejaLaSesionCerrada() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);
        String control = LocalDate.now(ZONA).plusDays(30).toString();

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId(), catalogo.get(1).getId()),
                        control, "Volver antes si aparece dolor"),
                luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesiones[0].estado").value("CERRADA"))
                .andExpect(jsonPath("$.sesiones[0].recomendaciones.length()").value(2))
                .andExpect(jsonPath("$.sesiones[0].proximoControl").value(control))
                .andExpect(jsonPath("$.sesiones[0].observacion")
                        .value("Volver antes si aparece dolor"))
                // La cita que la ocupaba sigue siendo la suya.
                .andExpect(jsonPath("$.sesiones[0].cita.codigo").exists())
                // Y las demás siguen como estaban.
                .andExpect(jsonPath("$.sesiones[1].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.sesiones[1].recomendaciones.length()").value(0));

        assertEquals(2, recomendacionesGuardadas(planId), "las recomendaciones van a su tabla");
    }

    @Test
    void cerrarSesion_sinNingunaRecomendacion_retornaBadRequest() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(), LocalDate.now(ZONA).plusDays(30).toString(), null),
                luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isBadRequest());

        assertEquals(0, recomendacionesGuardadas(planId));
    }

    @Test
    void cerrarSesion_sinFechaDeProximoControl_retornaBadRequest() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1, cuerpoDeCierre(List.of(catalogo.get(0).getId()), null, null),
                luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isBadRequest());
    }

    @Test
    void cerrarSesion_conUnaFechaAnteriorAHoy_retornaBadRequest() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId()),
                        LocalDate.now(ZONA).minusDays(1).toString(), null),
                luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isBadRequest());
    }

    @Test
    void cerrarSesion_conUnaObservacionDemasiadoLarga_retornaBadRequest() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId()),
                        LocalDate.now(ZONA).plusDays(30).toString(), "x".repeat(301)),
                luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isBadRequest());
    }

    @Test
    void cerrarSesion_conUnaRecomendacionQueNoEstaEnElCatalogo_retornaBadRequest() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId(), UUID.randomUUID()),
                        LocalDate.now(ZONA).plusDays(30).toString(), null),
                luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isBadRequest());

        // Ni la que sí existía: el cierre es todo o nada.
        assertEquals(0, recomendacionesGuardadas(planId));
    }

    @Test
    void cerrarSesion_deUnaSesionSinCitaAtendida_retornaConflict() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        // La segunda sesión sigue pendiente.
        cerrarComoLuis(planId, 2).andExpect(status().isConflict());
    }

    @Test
    void cerrarSesion_dosVeces_laSegundaRetornaConflict() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrarComoLuis(planId, 1).andExpect(status().isOk());
        cerrarComoLuis(planId, 1).andExpect(status().isConflict());

        // Y lo indicado la primera vez no se ha duplicado ni sustituido.
        assertEquals(2, recomendacionesGuardadas(planId));
    }

    @Test
    void cerrarSesion_deUnaSesionQueElPlanNoTiene_retornaNotFound() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrarComoLuis(planId, 99).andExpect(status().isNotFound());
    }

    @Test
    void cerrarSesion_deUnPlanQueNoExiste_retornaNotFound() throws Exception {
        cerrarComoLuis(UUID.randomUUID(), 1).andExpect(status().isNotFound());
    }

    @Test
    void cerrarSesion_conElPlanSuspendido_igualmenteSeCierra() throws Exception {
        // Las recomendaciones son los cuidados de una consulta que ya ocurrió, y
        // suspender el plan no deshace lo que se hizo en ella.
        UUID planId = crearPlan(3);
        atenderUnaCita(4);
        mockMvc.perform(patch(PLANES + "/" + planId + "/suspender")
                        .with(odontologo(luisUsuarioId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"Se replantea el tratamiento\"}"))
                .andExpect(status().isOk());

        cerrarComoLuis(planId, 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activo").value(false))
                .andExpect(jsonPath("$.sesiones[0].estado").value("CERRADA"));
    }

    // ------------------------------------------------------------------
    // Quién puede cerrar
    // ------------------------------------------------------------------

    @Test
    void cerrarSesion_comoOtroOdontologo_retornaForbidden() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId()),
                        LocalDate.now(ZONA).plusDays(30).toString(), null),
                anaUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isForbidden());

        assertEquals(0, recomendacionesGuardadas(planId));
    }

    @Test
    void cerrarSesion_comoAdministrador_puedeConElPlanDeCualquiera() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId()),
                        LocalDate.now(ZONA).plusDays(30).toString(), null),
                administradorId, "SCOPE_ADMINISTRADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesiones[0].estado").value("CERRADA"));
    }

    @Test
    void cerrarSesion_comoPaciente_retornaForbidden() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId()),
                        LocalDate.now(ZONA).plusDays(30).toString(), null),
                pacienteUsuarioId, "SCOPE_PACIENTE")
                .andExpect(status().isForbidden());
    }

    @Test
    void cerrarSesion_comoRecepcionista_retornaForbidden() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);

        cerrar(planId, 1,
                cuerpoDeCierre(List.of(catalogo.get(0).getId()),
                        LocalDate.now(ZONA).plusDays(30).toString(), null),
                recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // El catálogo cerrado
    // ------------------------------------------------------------------

    @Test
    void catalogo_loLeeQuienCierraSesiones() throws Exception {
        mockMvc.perform(get("/api/v1/recomendaciones").with(odontologo(luisUsuarioId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].descripcion").exists())
                // La marca de actividad no sale: el listado ya son las vigentes.
                .andExpect(jsonPath("$[0].activa").doesNotExist());
    }

    @Test
    void catalogo_comoPaciente_retornaForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/recomendaciones")
                        .with(user(pacienteUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE"))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Lo que el paciente ve de lo suyo
    // ------------------------------------------------------------------

    @Test
    void listar_comoPacienteSobreSuFicha_devuelveSusPlanesConLoIndicado() throws Exception {
        UUID planId = crearPlan(3);
        atenderUnaCita(4);
        cerrarComoLuis(planId, 1).andExpect(status().isOk());

        String cuerpo = mockMvc.perform(get(PLANES)
                        .with(user(pacienteUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                        .param("pacienteId", fichaPaciente.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sesiones[0].estado").value("CERRADA"))
                .andExpect(jsonPath("$[0].sesiones[0].recomendaciones.length()").value(2))
                .andExpect(jsonPath("$[0].sesiones[0].proximoControl").exists())
                .andReturn().getResponse().getContentAsString();

        JsonNode indicadas = objectMapper.readTree(cuerpo)
                .path(0).path("sesiones").path(0).path("recomendaciones");
        assertFalse(indicadas.path(0).path("descripcion").asText().isBlank(),
                "el paciente tiene que poder leer qué se le indicó");
    }

    @Test
    void listar_comoPacienteSobreLaFichaDeOtro_retornaForbidden() throws Exception {
        mockMvc.perform(get(PLANES)
                        .with(user(pacienteUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                        .param("pacienteId", fichaOtroPaciente.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void crear_comoPaciente_siguePrivadoDelPersonal() throws Exception {
        // Abrir la lectura no abre la escritura: el comodín de la ruta sigue detrás.
        mockMvc.perform(post(PLANES)
                        .with(user(pacienteUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":\"" + fichaPaciente.getId() + "\","
                                + "\"tratamientoId\":\"" + ortodonciaId + "\","
                                + "\"sesionesPrevistas\":3}"))
                .andExpect(status().isForbidden());
    }
}
