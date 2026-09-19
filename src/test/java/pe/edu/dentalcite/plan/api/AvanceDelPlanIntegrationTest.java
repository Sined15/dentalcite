package pe.edu.dentalcite.plan.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
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
import pe.edu.dentalcite.plan.domain.Plan;
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El avance de un plan y quién lo sigue, de extremo a extremo contra PostgreSQL.
 *
 * <p>La clínica de esta clase, sembrada entera por ella para no contar sobre la
 * semilla de demostración:
 *
 * <ul>
 *   <li>Julio tiene un plan de ortodoncia de seis sesiones que firmó Luis, con dos
 *       citas atendidas enlazadas —la segunda sin cerrar—, y uno de blanqueamiento
 *       de Luis ya suspendido. Además tiene una cita confirmada por venir con Ana.</li>
 *   <li>Pedro, sin cuenta, tiene un plan de endodoncia que firmó Ana, y con Luis
 *       solo una cita que se canceló.</li>
 *   <li>Marta tiene un plan de Ana y ninguna cita con Luis.</li>
 *   <li>Rosa es odontóloga y no ha visto a ninguno.</li>
 * </ul>
 *
 * <p>Los planes se guardan directamente por el repositorio y no por la API: aquí
 * se prueba cómo se leen, y crearlos por la API obligaría a sembrar el vínculo
 * que exige planificar.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AvanceDelPlanIntegrationTest {

    private static final String RUTA = "/api/v1/planes";

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private WebApplicationContext context;
    @Autowired private PlanRepository planRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private ConsultorioRepository consultorioRepository;

    private Especialidad especialidad;
    private Consultorio consultorio;
    private Tratamiento ortodoncia;
    private Tratamiento endodoncia;
    private Tratamiento blanqueamiento;

    private Odontologo luis;
    private Odontologo ana;
    private Odontologo rosa;
    private UUID luisUsuarioId;
    private UUID anaUsuarioId;
    private UUID rosaUsuarioId;

    private Ficha fichaJulio;
    private Ficha fichaPedro;
    private Ficha fichaMarta;
    private UUID julioUsuarioId;
    private UUID martaUsuarioId;
    private UUID recepcionistaId;
    private UUID administradorId;

    private UUID ortodonciaDeJulio;
    private UUID blanqueamientoDeJulio;
    private UUID endodonciaDePedro;
    private UUID planDeMarta;

    private final List<UUID> citas = new ArrayList<>();
    private final List<UUID> planes = new ArrayList<>();
    private final List<UUID> cuentas = new ArrayList<>();
    private final List<Ficha> fichas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("AVANCE-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        // Sala propia: la cita confirmada por venir no puede chocar con las que
        // la semilla de demostración pone en las salas de la clínica.
        consultorio = consultorioRepository.save(Consultorio.builder()
                .id(UUID.randomUUID())
                .nombre("Sala de avance " + UUID.randomUUID().toString().substring(0, 8))
                .inoperativo(false).build());
        ortodoncia = nuevoTratamiento("Ortodoncia");
        endodoncia = nuevoTratamiento("Endodoncia");
        blanqueamiento = nuevoTratamiento("Blanqueamiento");

        Ficha fichaLuis = nuevaFicha("Luis", "Perez");
        luis = nuevoOdontologo(fichaLuis, "Luis", "Perez");
        luisUsuarioId = nuevaCuenta("ODONTOLOGO", fichaLuis);
        Ficha fichaAna = nuevaFicha("Ana", "Quispe");
        ana = nuevoOdontologo(fichaAna, "Ana", "Quispe");
        anaUsuarioId = nuevaCuenta("ODONTOLOGO", fichaAna);
        Ficha fichaRosa = nuevaFicha("Rosa", "Diaz");
        rosa = nuevoOdontologo(fichaRosa, "Rosa", "Diaz");
        rosaUsuarioId = nuevaCuenta("ODONTOLOGO", fichaRosa);

        fichaJulio = nuevaFicha("Julio", "Ramos");
        julioUsuarioId = nuevaCuenta("PACIENTE", fichaJulio);
        fichaPedro = nuevaFicha("Pedro", "Soto");
        fichaMarta = nuevaFicha("Marta", "Vega");
        martaUsuarioId = nuevaCuenta("PACIENTE", fichaMarta);
        recepcionistaId = nuevaCuenta("RECEPCIONISTA", null);
        administradorId = nuevaCuenta("ADMINISTRADOR", null);

        Cita primera = nuevaCita(fichaJulio, luis, ortodoncia, -60, Cita.ESTADO_ATENDIDA);
        Cita segunda = nuevaCita(fichaJulio, luis, ortodoncia, -30, Cita.ESTADO_ATENDIDA);
        nuevaCita(fichaJulio, ana, ortodoncia, 40, "CONFIRMADA");
        nuevaCita(fichaPedro, luis, endodoncia, -20, "CANCELADA");

        ortodonciaDeJulio = nuevoPlan(fichaJulio, ortodoncia, luis, 6, true, primera, segunda);
        blanqueamientoDeJulio = nuevoPlan(fichaJulio, blanqueamiento, luis, 2, false);
        endodonciaDePedro = nuevoPlan(fichaPedro, endodoncia, ana, 3, true);
        planDeMarta = nuevoPlan(fichaMarta, ortodoncia, ana, 4, true);
    }

    private Tratamiento nuevoTratamiento(String nombre) {
        return tratamientoRepository.save(Tratamiento.builder()
                .id(UUID.randomUUID())
                .codigo("TA-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre(nombre).duracionMinutos(30)
                .especialidad(especialidad).activo(true).build());
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        Ficha ficha = fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres(nombres).apellidos(apellidos).build());
        fichas.add(ficha);
        return ficha;
    }

    private Odontologo nuevoOdontologo(Ficha ficha, String nombres, String apellidos) {
        return odontologoRepository.save(Odontologo.builder()
                .id(UUID.randomUUID())
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres(nombres).apellidos(apellidos)
                .ficha(ficha).activo(true).especialidades(new HashSet<>(Set.of(especialidad))).build());
    }

    private UUID nuevaCuenta(String rol, Ficha ficha) {
        UUID id = usuarioRepository.save(Usuario.builder()
                .nombre(rol)
                .correo("avance-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
        cuentas.add(id);
        return id;
    }

    /** Una cita a tantos días de hoy, en hora en punto para respetar los tramos de quince minutos. */
    private Cita nuevaCita(Ficha ficha, Odontologo odontologo, Tratamiento tratamiento, int dias,
            String estado) {
        OffsetDateTime inicio = OffsetDateTime.now().plusDays(dias)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        Cita cita = citaRepository.save(Cita.builder()
                .codigo(String.format("CIT-%06d", citaRepository.getNextCodigoCita()))
                .ficha(ficha).odontologo(odontologo).consultorio(consultorio)
                .tratamiento(tratamiento)
                .inicio(inicio).fin(inicio.plusMinutes(30))
                .estado(estado)
                .build());
        citas.add(cita.getId());
        return cita;
    }

    private UUID nuevoPlan(Ficha ficha, Tratamiento tratamiento, Odontologo odontologo, int sesiones,
            boolean activo, Cita... atendidas) {
        Plan plan = Plan.builder()
                .ficha(ficha).tratamiento(tratamiento).odontologo(odontologo)
                .sesionesPrevistas(sesiones).activo(true)
                .build();
        plan.generarSesiones();
        for (Cita cita : atendidas) {
            plan.primeraSesionPendiente().orElseThrow().enlazar(cita);
        }
        if (!activo) {
            plan.suspender("El paciente lo pospone");
        }
        UUID id = planRepository.save(plan).getId();
        planes.add(id);
        return id;
    }

    @AfterEach
    void tearDown() {
        // Los planes primero: sus sesiones apuntan a las citas y la clave ajena es
        // RESTRICT. Y todo lo demás antes que las fichas, por la misma razón.
        planes.forEach(planRepository::deleteById);
        citas.forEach(citaRepository::deleteById);
        cuentas.forEach(usuarioRepository::deleteById);
        List.of(luis, ana, rosa).forEach(o -> odontologoRepository.deleteById(o.getId()));
        List.of(ortodoncia, endodoncia, blanqueamiento)
                .forEach(t -> tratamientoRepository.deleteById(t.getId()));
        fichas.forEach(f -> fichaRepository.deleteById(f.getId()));
        consultorioRepository.deleteById(consultorio.getId());
        especialidadRepository.deleteById(especialidad.getId());
    }

    private ResultActions pedir(String ruta, UUID quien, String rol) throws Exception {
        return mockMvc.perform(get(ruta)
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority("SCOPE_" + rol))));
    }

    private List<String> tratamientosDelSeguimiento(UUID quien, boolean soloActivos) throws Exception {
        String cuerpo = pedir(RUTA + "/seguimiento?soloActivos=" + soloActivos + "&size=50",
                quien, "ODONTOLOGO")
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> vistos = new ArrayList<>();
        for (JsonNode plan : objectMapper.readTree(cuerpo).path("content")) {
            vistos.add(plan.path("paciente").path("nombre").asText() + " · "
                    + plan.path("tratamiento").path("nombre").asText());
        }
        return vistos;
    }

    // ------------------------------------------------------------------
    // El avance, contado sobre las citas atendidas
    // ------------------------------------------------------------------

    @Test
    void obtener_deSeisSesionesConDosAtendidas_daDosCompletadasYCuatroPendientes() throws Exception {
        pedir(RUTA + "/" + ortodonciaDeJulio, julioUsuarioId, "PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avance.completadas").value(2))
                .andExpect(jsonPath("$.avance.pendientes").value(4))
                .andExpect(jsonPath("$.sesiones.length()").value(6));
    }

    @Test
    void obtener_laLineaDeTiempoTraeLaFechaDeAtencionYSenalaLasPendientesDeCierre() throws Exception {
        pedir(RUTA + "/" + ortodonciaDeJulio, julioUsuarioId, "PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesiones[0].numero").value(1))
                .andExpect(jsonPath("$.sesiones[0].cita.inicio").exists())
                // Atendida y sin cerrar: es lo que la línea de tiempo señala.
                .andExpect(jsonPath("$.sesiones[1].estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.sesiones[2].estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.sesiones[2].cita").isEmpty())
                .andExpect(jsonPath("$.sesiones[2].recomendaciones").isEmpty());
    }

    @Test
    void deFicha_traeElAvanceDeCadaPlan() throws Exception {
        pedir(RUTA + "?pacienteId=" + fichaJulio.getId(), julioUsuarioId, "PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + ortodonciaDeJulio + "')].avance.completadas")
                        .value(2))
                .andExpect(jsonPath("$[?(@.id == '" + blanqueamientoDeJulio + "')].avance.pendientes")
                        .value(2));
    }

    // ------------------------------------------------------------------
    // Quién lee el detalle
    // ------------------------------------------------------------------

    @Test
    void obtener_elPlanDeOtroPaciente_retornaForbidden() throws Exception {
        pedir(RUTA + "/" + ortodonciaDeJulio, martaUsuarioId, "PACIENTE")
                .andExpect(status().isForbidden());
    }

    @Test
    void obtener_unPlanInexistenteComoPaciente_retornaForbiddenComoElAjeno() throws Exception {
        pedir(RUTA + "/" + UUID.randomUUID(), julioUsuarioId, "PACIENTE")
                .andExpect(status().isForbidden());
    }

    @Test
    void obtener_unPlanInexistenteComoAdministrador_retornaNotFound() throws Exception {
        pedir(RUTA + "/" + UUID.randomUUID(), administradorId, "ADMINISTRADOR")
                .andExpect(status().isNotFound());
    }

    @Test
    void obtener_comoAdministrador_retornaElPlan() throws Exception {
        pedir(RUTA + "/" + ortodonciaDeJulio, administradorId, "ADMINISTRADOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avance.completadas").value(2));
    }

    @Test
    void obtener_unOdontologoConCitaPorVenir_leeElPlanQueFirmoOtro() throws Exception {
        pedir(RUTA + "/" + ortodonciaDeJulio, anaUsuarioId, "ODONTOLOGO")
                .andExpect(status().isOk());
    }

    @Test
    void obtener_unOdontologoQueLoFirmo_loLeeAunqueNoHayaVistoAlPaciente() throws Exception {
        pedir(RUTA + "/" + endodonciaDePedro, anaUsuarioId, "ODONTOLOGO")
                .andExpect(status().isOk());
    }

    @Test
    void obtener_unOdontologoCuyaUnicaCitaSeCancelo_retornaForbidden() throws Exception {
        pedir(RUTA + "/" + endodonciaDePedro, luisUsuarioId, "ODONTOLOGO")
                .andExpect(status().isForbidden());
    }

    @Test
    void obtener_unOdontologoSinVinculo_retornaForbidden() throws Exception {
        pedir(RUTA + "/" + ortodonciaDeJulio, rosaUsuarioId, "ODONTOLOGO")
                .andExpect(status().isForbidden());
    }

    @Test
    void obtener_comoRecepcionista_retornaForbidden() throws Exception {
        pedir(RUTA + "/" + ortodonciaDeJulio, recepcionistaId, "RECEPCIONISTA")
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // El seguimiento del odontólogo
    // ------------------------------------------------------------------

    @Test
    void seguimiento_elOdontologoVeLosQueFirmoYNoLosDeQuienSoloLeCanceloUnaCita() throws Exception {
        assertEquals(List.of("Julio Ramos · Ortodoncia"), tratamientosDelSeguimiento(luisUsuarioId, true));
    }

    @Test
    void seguimiento_incluyeLosQueFirmoOtroSobreUnPacienteConCitaPorVenir() throws Exception {
        List<String> vistos = tratamientosDelSeguimiento(anaUsuarioId, true);

        assertEquals(Set.of("Julio Ramos · Ortodoncia", "Pedro Soto · Endodoncia",
                "Marta Vega · Ortodoncia"), Set.copyOf(vistos));
    }

    @Test
    void seguimiento_conSoloActivosFalso_incluyeLosSuspendidosDetrasDeLosActivos() throws Exception {
        assertEquals(List.of("Julio Ramos · Ortodoncia", "Julio Ramos · Blanqueamiento"),
                tratamientosDelSeguimiento(luisUsuarioId, false));
    }

    @Test
    void seguimiento_traeElAvanceDeCadaPlan() throws Exception {
        pedir(RUTA + "/seguimiento", luisUsuarioId, "ODONTOLOGO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ortodonciaDeJulio.toString()))
                .andExpect(jsonPath("$.content[0].avance.completadas").value(2))
                .andExpect(jsonPath("$.content[0].avance.pendientes").value(4));
    }

    @Test
    void seguimiento_unOdontologoSinPacientes_recibeUnaPaginaVacia() throws Exception {
        assertEquals(List.of(), tratamientosDelSeguimiento(rosaUsuarioId, false));
    }

    @Test
    void seguimiento_conUnOrdenInventado_noFalla() throws Exception {
        pedir(RUTA + "/seguimiento?sort=noExiste,desc", luisUsuarioId, "ODONTOLOGO")
                .andExpect(status().isOk());
    }

    @Test
    void seguimiento_comoPaciente_retornaForbidden() throws Exception {
        pedir(RUTA + "/seguimiento", julioUsuarioId, "PACIENTE")
                .andExpect(status().isForbidden());
    }

    @Test
    void seguimiento_comoRecepcionista_retornaForbidden() throws Exception {
        pedir(RUTA + "/seguimiento", recepcionistaId, "RECEPCIONISTA")
                .andExpect(status().isForbidden());
    }
}
