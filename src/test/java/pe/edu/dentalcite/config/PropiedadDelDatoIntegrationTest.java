package pe.edu.dentalcite.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.auth.service.JwtService;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;
import pe.edu.dentalcite.bloqueo.repository.BloqueoRepository;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.plan.domain.Plan;
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Las celdas de la matriz que dependen de de quién es el dato, probadas con dos
 * usuarios del mismo rol: ninguno alcanza lo del otro.
 *
 * <p>La clínica de esta clase, sembrada entera por ella:
 * <ul>
 *   <li>Alicia y Bruno son pacientes con cuenta.</li>
 *   <li>Luis es odontólogo y atendió a Alicia, a la que tiene además citada.</li>
 *   <li>Ana es odontóloga y tiene dos citas con Bruno: una ya vencida y por
 *       cerrar, y otra por venir. Firmó el plan de Bruno.</li>
 *   <li>Luis firmó el plan de Alicia. Cada odontólogo tiene su horario y un
 *       bloqueo de agenda.</li>
 * </ul>
 *
 * <p>Cada rechazo se comprueba dos veces: que es un 403 y que su cuerpo no lleva
 * nada de lo que protege. Donde la API responde 403 también a lo que no existe,
 * se exige además que el cuerpo sea el mismo en los dos casos, porque un texto
 * distinto delataría cuál de los dos era.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class PropiedadDelDatoIntegrationTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private WebApplicationContext context;
    @Autowired private JwtService jwtService;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private ConsultorioRepository consultorioRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private PlanRepository planRepository;
    @Autowired private HorarioAtencionRepository horarioRepository;
    @Autowired private BloqueoRepository bloqueoRepository;

    private Especialidad especialidad;
    private Tratamiento tratamiento;
    private Consultorio consultorio;

    private Ficha fichaAlicia;
    private Ficha fichaBruno;
    private Usuario cuentaAlicia;
    private UUID aliciaId;
    private UUID luisUsuarioId;

    private Odontologo luis;
    private Odontologo ana;

    private Cita citaDeBrunoPorCerrar;
    private Cita citaDeBrunoPorVenir;
    private UUID planDeAlicia;
    private UUID planDeBruno;
    private UUID horarioDeAna;
    private UUID bloqueoDeAna;

    private final List<UUID> citas = new ArrayList<>();
    private final List<UUID> cuentas = new ArrayList<>();
    private final List<Ficha> fichas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        String sufijo = UUID.randomUUID().toString().substring(0, 8);
        especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID()).nombre("PROPIEDAD-" + sufijo).activo(true).build());
        tratamiento = tratamientoRepository.save(Tratamiento.builder()
                .id(UUID.randomUUID()).codigo("TP-" + sufijo).nombre("Ortodoncia " + sufijo)
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());
        // Sala propia: las citas de esta clase no pueden chocar con las que la
        // semilla de demostración pone en las salas de la clínica.
        consultorio = consultorioRepository.save(Consultorio.builder()
                .id(UUID.randomUUID()).nombre("Sala de propiedad " + sufijo).inoperativo(false).build());

        Ficha fichaLuis = nuevaFicha("Luis", "Paredes");
        luis = nuevoOdontologo(fichaLuis);
        luisUsuarioId = nuevaCuenta("ODONTOLOGO", fichaLuis).getId();
        Ficha fichaAna = nuevaFicha("Ana", "Quiroz");
        ana = nuevoOdontologo(fichaAna);
        nuevaCuenta("ODONTOLOGO", fichaAna);

        fichaAlicia = nuevaFicha("Alicia", "Mendoza" + sufijo);
        cuentaAlicia = nuevaCuenta("PACIENTE", fichaAlicia);
        aliciaId = cuentaAlicia.getId();
        fichaBruno = nuevaFicha("Bruno", "Salazar" + sufijo);
        nuevaCuenta("PACIENTE", fichaBruno);

        nuevaCita(fichaAlicia, luis, -30, Cita.ESTADO_ATENDIDA);
        nuevaCita(fichaAlicia, luis, 20, Cita.ESTADO_CONFIRMADA);
        citaDeBrunoPorCerrar = nuevaCita(fichaBruno, ana, -2, Cita.ESTADO_CONFIRMADA);
        citaDeBrunoPorVenir = nuevaCita(fichaBruno, ana, 21, Cita.ESTADO_CONFIRMADA);

        planDeAlicia = nuevoPlan(fichaAlicia, luis);
        planDeBruno = nuevoPlan(fichaBruno, ana);

        nuevoHorario(luis);
        horarioDeAna = nuevoHorario(ana);
        nuevoBloqueo(luis);
        bloqueoDeAna = nuevoBloqueo(ana);
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        Ficha ficha = fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000)))
                .nombres(nombres).apellidos(apellidos).build());
        fichas.add(ficha);
        return ficha;
    }

    private Odontologo nuevoOdontologo(Ficha ficha) {
        return odontologoRepository.save(Odontologo.builder()
                .id(UUID.randomUUID())
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres(ficha.getNombres()).apellidos(ficha.getApellidos())
                .ficha(ficha).activo(true).especialidades(new HashSet<>(Set.of(especialidad))).build());
    }

    private Usuario nuevaCuenta(String rol, Ficha ficha) {
        Usuario cuenta = usuarioRepository.save(Usuario.builder()
                .nombre(ficha.getNombres())
                .correo("propiedad-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build());
        cuentas.add(cuenta.getId());
        return cuenta;
    }

    private Cita nuevaCita(Ficha ficha, Odontologo odontologo, int dias, String estado) {
        OffsetDateTime inicio = OffsetDateTime.now().plusDays(dias)
                .withHour(10).withMinute(0).withSecond(0).withNano(0);
        Cita cita = citaRepository.save(Cita.builder()
                .codigo(String.format("CIT-%06d", citaRepository.getNextCodigoCita()))
                .ficha(ficha).odontologo(odontologo).consultorio(consultorio).tratamiento(tratamiento)
                .inicio(inicio).fin(inicio.plusMinutes(30)).estado(estado)
                .build());
        citas.add(cita.getId());
        return cita;
    }

    private UUID nuevoPlan(Ficha ficha, Odontologo odontologo) {
        Plan plan = Plan.builder()
                .ficha(ficha).tratamiento(tratamiento).odontologo(odontologo)
                .sesionesPrevistas(3).activo(true)
                .build();
        plan.generarSesiones();
        return planRepository.save(plan).getId();
    }

    private UUID nuevoHorario(Odontologo odontologo) {
        return horarioRepository.save(HorarioAtencion.builder()
                .odontologo(odontologo).diaSemana(3)
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(12, 0)).build()).getId();
    }

    private UUID nuevoBloqueo(Odontologo odontologo) {
        OffsetDateTime inicio = OffsetDateTime.now().plusDays(60).withHour(8).withMinute(0).withSecond(0).withNano(0);
        return bloqueoRepository.save(Bloqueo.builder()
                .odontologo(odontologo).motivo("Congreso")
                .fechaInicio(inicio).fechaFin(inicio.plusHours(4)).build()).getId();
    }

    @AfterEach
    void tearDown() {
        // Los planes primero, que sus sesiones pueden apuntar a las citas; y todo
        // lo que cuelga de un odontólogo antes que él, y él antes que su ficha.
        List.of(planDeAlicia, planDeBruno).forEach(planRepository::deleteById);
        citas.forEach(citaRepository::deleteById);
        bloqueoRepository.findAll().stream()
                .filter(b -> b.getOdontologo() != null
                        && Set.of(luis.getId(), ana.getId()).contains(b.getOdontologo().getId()))
                .forEach(bloqueoRepository::delete);
        horarioRepository.findAll().stream()
                .filter(h -> Set.of(luis.getId(), ana.getId()).contains(h.getOdontologo().getId()))
                .forEach(horarioRepository::delete);
        cuentas.forEach(usuarioRepository::deleteById);
        List.of(luis, ana).forEach(o -> odontologoRepository.deleteById(o.getId()));
        tratamientoRepository.deleteById(tratamiento.getId());
        fichas.forEach(f -> fichaRepository.deleteById(f.getId()));
        consultorioRepository.deleteById(consultorio.getId());
        especialidadRepository.deleteById(especialidad.getId());
    }

    // ------------------------------------------------------------------
    // Peticiones y comprobaciones
    // ------------------------------------------------------------------

    private MvcResult como(UUID quien, String rol, MockHttpServletRequestBuilder peticion) throws Exception {
        return mockMvc.perform(peticion
                        .with(user(quien.toString()).authorities(new SimpleGrantedAuthority("SCOPE_" + rol))))
                .andReturn();
    }

    private static MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String json) {
        return peticion.contentType(MediaType.APPLICATION_JSON).content(json);
    }

    /** Lo que ningún 403 de esta clase puede nombrar: quién es Bruno y qué tiene. */
    private String[] datosDeBruno() {
        return new String[] {
                fichaBruno.getNombres(), fichaBruno.getApellidos(), fichaBruno.getDocumento(),
                "Ana", "Quiroz", tratamiento.getNombre(),
                citaDeBrunoPorCerrar.getCodigo(), citaDeBrunoPorVenir.getCodigo()
        };
    }

    private String rechazado(MvcResult resultado) throws Exception {
        assertEquals(403, resultado.getResponse().getStatus(), resultado.getResponse().getContentAsString());
        String cuerpo = resultado.getResponse().getContentAsString();
        CuerpoDeRechazo.comprobar(cuerpo, datosDeBruno());
        return cuerpo;
    }

    private List<String> ids(MvcResult resultado, String coleccion) throws Exception {
        assertEquals(200, resultado.getResponse().getStatus(), resultado.getResponse().getContentAsString());
        JsonNode arbol = objectMapper.readTree(resultado.getResponse().getContentAsString());
        JsonNode elementos = coleccion == null ? arbol : arbol.path(coleccion);
        List<String> ids = new ArrayList<>();
        elementos.forEach(e -> ids.add(e.path("id").asText()));
        return ids;
    }

    // ------------------------------------------------------------------
    // Ficha
    // ------------------------------------------------------------------

    @Test
    void ficha_unPacienteAbreLaDeOtro_retornaForbidden() throws Exception {
        rechazado(como(aliciaId, "PACIENTE", get("/api/v1/pacientes/" + fichaBruno.getId())));
    }

    @Test
    void ficha_unOdontologoAbreLaDeUnPacienteQueNoHaAtendido_retornaForbidden() throws Exception {
        rechazado(como(luisUsuarioId, "ODONTOLOGO", get("/api/v1/pacientes/" + fichaBruno.getId())));
    }

    @Test
    void ficha_laBusquedaDelOdontologoNoTraeAPacientesDeOtro() throws Exception {
        MvcResult resultado = como(luisUsuarioId, "ODONTOLOGO",
                get("/api/v1/pacientes").param("q", fichaBruno.getApellidos()));

        assertTrue(ids(resultado, "content").isEmpty());
    }

    // ------------------------------------------------------------------
    // Cita
    // ------------------------------------------------------------------

    @Test
    void cita_unPacienteCancelaLaDeOtro_retornaForbiddenYLaCitaSigueConfirmada() throws Exception {
        rechazado(como(aliciaId, "PACIENTE", conCuerpo(
                patch("/api/v1/citas/" + citaDeBrunoPorVenir.getId() + "/cancelar"), "{\"motivo\":\"Viaje\"}")));

        assertEquals(Cita.ESTADO_CONFIRMADA,
                citaRepository.findById(citaDeBrunoPorVenir.getId()).orElseThrow().getEstado());
    }

    @Test
    void cita_lasDelPacienteNoIncluyenLasDeOtro() throws Exception {
        List<String> vistas = ids(como(aliciaId, "PACIENTE", get("/api/v1/citas/mias").param("size", "100")),
                "content");

        assertEquals(2, vistas.size());
        assertFalse(vistas.contains(citaDeBrunoPorVenir.getId().toString()));
        assertFalse(vistas.contains(citaDeBrunoPorCerrar.getId().toString()));
    }

    @Test
    void cita_unOdontologoCierraLaDeOtro_retornaForbiddenYLaCitaSigueConfirmada() throws Exception {
        rechazado(como(luisUsuarioId, "ODONTOLOGO", conCuerpo(
                patch("/api/v1/citas/" + citaDeBrunoPorCerrar.getId() + "/resultado"),
                "{\"resultado\":\"ATENDIDA\"}")));

        assertEquals(Cita.ESTADO_CONFIRMADA,
                citaRepository.findById(citaDeBrunoPorCerrar.getId()).orElseThrow().getEstado());
    }

    @Test
    void cita_laAgendaDelOdontologoIgnoraElFiltroPorOtroOdontologo() throws Exception {
        MvcResult resultado = como(luisUsuarioId, "ODONTOLOGO", get("/api/v1/citas")
                .param("desde", LocalDate.now().minusDays(40).toString())
                .param("hasta", LocalDate.now().plusDays(40).toString())
                .param("odontologoId", ana.getId().toString())
                .param("size", "100"));

        List<String> vistas = ids(resultado, "content");
        assertEquals(2, vistas.size(), "Debe ver las suyas, no una página vacía ni las de Ana");
        assertFalse(vistas.contains(citaDeBrunoPorVenir.getId().toString()));
    }

    @Test
    void cita_laColaDeCierreDelOdontologoNoTraeLasDeOtro() throws Exception {
        List<String> vistas = ids(como(luisUsuarioId, "ODONTOLOGO",
                get("/api/v1/citas/pendientes-cierre").param("size", "100")), "content");

        assertFalse(vistas.contains(citaDeBrunoPorCerrar.getId().toString()));
    }

    // ------------------------------------------------------------------
    // Plan
    // ------------------------------------------------------------------

    @Test
    void plan_unPacienteAbreElDeOtro_retornaForbidden() throws Exception {
        rechazado(como(aliciaId, "PACIENTE", get("/api/v1/planes/" + planDeBruno)));
    }

    @Test
    void plan_unPacienteListaLosDeOtraFicha_retornaForbidden() throws Exception {
        rechazado(como(aliciaId, "PACIENTE", get("/api/v1/planes").param("pacienteId", fichaBruno.getId().toString())));
    }

    @Test
    void plan_unOdontologoSinVinculoAbreElDeOtro_retornaForbidden() throws Exception {
        rechazado(como(luisUsuarioId, "ODONTOLOGO", get("/api/v1/planes/" + planDeBruno)));
    }

    @Test
    void plan_unOdontologoSuspendeElQueFirmoOtro_retornaForbiddenYSigueActivo() throws Exception {
        rechazado(como(luisUsuarioId, "ODONTOLOGO", conCuerpo(
                patch("/api/v1/planes/" + planDeBruno + "/suspender"), "{\"motivo\":\"Abandono\"}")));

        assertTrue(planRepository.findById(planDeBruno).orElseThrow().getActivo());
    }

    @Test
    void plan_unOdontologoCierraUnaSesionDelQueFirmoOtro_retornaForbidden() throws Exception {
        String cuerpo = "{\"recomendacionIds\":[\"" + UUID.randomUUID() + "\"],"
                + "\"proximoControl\":\"" + LocalDate.now().plusDays(30) + "\"}";

        rechazado(como(luisUsuarioId, "ODONTOLOGO", conCuerpo(
                post("/api/v1/planes/" + planDeBruno + "/sesiones/1/cierre"), cuerpo)));
    }

    @Test
    void plan_unOdontologoPlanificaParaUnPacienteQueNoHaAtendido_retornaForbidden() throws Exception {
        String cuerpo = "{\"pacienteId\":\"" + fichaBruno.getId() + "\",\"tratamientoId\":\""
                + tratamiento.getId() + "\",\"sesionesPrevistas\":2}";

        rechazado(como(luisUsuarioId, "ODONTOLOGO", conCuerpo(post("/api/v1/planes"), cuerpo)));
    }

    // ------------------------------------------------------------------
    // Horario y bloqueos
    // ------------------------------------------------------------------

    @Test
    void horario_unOdontologoLeeOCambiaElDeOtro_retornaForbidden() throws Exception {
        String ruta = "/api/v1/odontologos/" + ana.getId() + "/horarios";
        String cuerpo = "{\"diaSemana\":2,\"horaInicio\":\"08:00\",\"horaFin\":\"09:00\"}";

        rechazado(como(luisUsuarioId, "ODONTOLOGO", get(ruta)));
        rechazado(como(luisUsuarioId, "ODONTOLOGO", conCuerpo(post(ruta), cuerpo)));
        rechazado(como(luisUsuarioId, "ODONTOLOGO", conCuerpo(put(ruta + "/" + horarioDeAna), cuerpo)));
        rechazado(como(luisUsuarioId, "ODONTOLOGO", delete(ruta + "/" + horarioDeAna)));

        assertTrue(horarioRepository.findById(horarioDeAna).isPresent());
    }

    @Test
    void bloqueo_unOdontologoCreaCambiaOLevantaElDeOtro_retornaForbidden() throws Exception {
        OffsetDateTime inicio = OffsetDateTime.now().plusDays(70).withNano(0);
        String cuerpo = "{\"odontologoId\":\"%s\",\"motivo\":\"Curso\",\"fechaInicio\":\"" + inicio
                + "\",\"fechaFin\":\"" + inicio.plusHours(2) + "\"}";

        rechazado(como(luisUsuarioId, "ODONTOLOGO",
                conCuerpo(post("/api/v1/bloqueos"), cuerpo.formatted(ana.getId()))));
        // Reescribirlo como propio tampoco vale: primero se mira de quién es ahora.
        rechazado(como(luisUsuarioId, "ODONTOLOGO",
                conCuerpo(put("/api/v1/bloqueos/" + bloqueoDeAna), cuerpo.formatted(luis.getId()))));
        rechazado(como(luisUsuarioId, "ODONTOLOGO", delete("/api/v1/bloqueos/" + bloqueoDeAna)));

        assertTrue(bloqueoRepository.findById(bloqueoDeAna).isPresent());
    }

    @Test
    void bloqueo_elListadoDelOdontologoNoTraeLosDeOtro() throws Exception {
        List<String> vistos = ids(como(luisUsuarioId, "ODONTOLOGO", get("/api/v1/bloqueos")), null);

        assertFalse(vistos.contains(bloqueoDeAna.toString()));
    }

    // ------------------------------------------------------------------
    // El rechazo no distingue lo ajeno de lo inexistente
    // ------------------------------------------------------------------

    @Test
    void plan_ajenoEInexistente_danElMismoCuerpoAlPacienteYAlOdontologo() throws Exception {
        UUID inventado = UUID.randomUUID();

        assertEquals(rechazado(como(aliciaId, "PACIENTE", get("/api/v1/planes/" + planDeBruno))),
                rechazado(como(aliciaId, "PACIENTE", get("/api/v1/planes/" + inventado))));
        assertEquals(rechazado(como(luisUsuarioId, "ODONTOLOGO", get("/api/v1/planes/" + planDeBruno))),
                rechazado(como(luisUsuarioId, "ODONTOLOGO", get("/api/v1/planes/" + inventado))));
    }

    @Test
    void cancelacion_ajenaEInexistente_danElMismoCuerpoAlPaciente() throws Exception {
        String motivo = "{\"motivo\":\"Viaje\"}";

        assertEquals(
                rechazado(como(aliciaId, "PACIENTE", conCuerpo(
                        patch("/api/v1/citas/" + citaDeBrunoPorVenir.getId() + "/cancelar"), motivo))),
                rechazado(como(aliciaId, "PACIENTE", conCuerpo(
                        patch("/api/v1/citas/" + UUID.randomUUID() + "/cancelar"), motivo))));
    }

    // ------------------------------------------------------------------
    // Con un token de verdad
    // ------------------------------------------------------------------

    /**
     * Las demás pruebas se identifican sin token, y el 403 que corta una regla de
     * ruta sale entonces por otro manejador que el de una petición con token.
     * Aquí se recorren los dos rechazos —el de la ruta y el del servicio— con el
     * token que emite el inicio de sesión.
     */
    @Test
    void conTokenReal_elRechazoDeRutaYElDelServicioNoLlevanDatos() throws Exception {
        String bearer = "Bearer " + jwtService.generateToken(cuentaAlicia);

        rechazado(mockMvc.perform(get("/api/v1/usuarios").header("Authorization", bearer)).andReturn());
        rechazado(mockMvc.perform(get("/api/v1/pacientes/" + fichaBruno.getId())
                .header("Authorization", bearer)).andReturn());
    }
}
