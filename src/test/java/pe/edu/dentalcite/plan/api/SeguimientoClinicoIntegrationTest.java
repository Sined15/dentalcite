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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El seguimiento clínico de punta a punta, tal como lo enuncia el criterio de
 * éxito del plan de proyecto: un plan de cuatro sesiones, dos atendidas y
 * cerradas, y el avance en dos de cuatro después del cierre y después de una
 * cancelación.
 *
 * <p>Las piezas ya estaban probadas por separado —el enlace de la cita con su
 * sesión, el cierre de la sesión, el avance que se cuenta—; lo que faltaba era el
 * recorrido entero en el orden en que ocurre en la clínica, y leído por quien lo
 * tiene que ver, que es el paciente.
 *
 * <p>Como en las demás pruebas de planes, las citas se siembran en la base en un
 * consultorio propio, y la clase no es transaccional: cada paso tiene que ver lo
 * que confirmó el anterior.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class SeguimientoClinicoIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired private WebApplicationContext context;
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
    private UUID tratamientoId;
    private Ficha fichaOdontologo;
    private UUID odontologoId;
    private UUID odontologoUsuarioId;
    private Ficha fichaPaciente;
    private UUID pacienteUsuarioId;
    private UUID recepcionistaId;
    private Consultorio consultorio;
    private OffsetDateTime enPunto;

    private final List<UUID> citas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        enPunto = OffsetDateTime.now(ZONA).truncatedTo(ChronoUnit.HOURS);

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("SEGUIMIENTO-" + corto())
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TS-" + corto())
                .nombre("Ortodoncia").duracionMinutos(30)
                .especialidad(especialidad).activo(true).build());

        fichaOdontologo = nuevaFicha("Luis", "Perez");
        odontologoId = UUID.randomUUID();
        odontologoRepository.save(Odontologo.builder()
                .id(odontologoId)
                .cop("COP-" + corto())
                .nombres("Luis").apellidos("Perez")
                .ficha(fichaOdontologo).activo(true).especialidades(Set.of(especialidad)).build());
        odontologoUsuarioId = nuevaCuenta("Luis Perez", "ODONTOLOGO", fichaOdontologo);

        fichaPaciente = nuevaFicha("Julio", "Ramos");
        pacienteUsuarioId = nuevaCuenta("Julio Ramos", "PACIENTE", fichaPaciente);
        recepcionistaId = nuevaCuenta("Recepcion", "RECEPCIONISTA", null);

        consultorio = consultorioRepository.save(Consultorio.builder()
                .id(UUID.randomUUID()).nombre("Consultorio de seguimiento " + corto())
                .inoperativo(false).build());

        // El odontólogo planifica sobre pacientes que ha visto. La primera consulta
        // fue una a la que el paciente no vino: acredita el vínculo y no ocupa
        // ninguna sesión.
        sembrar(Cita.ESTADO_NO_ASISTIO, enPunto.minusDays(40));
    }

    @AfterEach
    void tearDown() {
        // Los planes antes que las citas, y la bitácora antes que las cuentas: son
        // las dos claves ajenas que no dejan borrar en otro orden.
        planRepository.findByFichaIdOrderByCreadoEnDesc(fichaPaciente.getId())
                .forEach(p -> planRepository.deleteById(p.getId()));
        citas.forEach(id -> historialRepository.findByCitaIdOrderByOcurridoEnAsc(id)
                .forEach(historialRepository::delete));
        citas.forEach(citaRepository::deleteById);
        citas.clear();
        usuarioRepository.deleteById(recepcionistaId);
        usuarioRepository.deleteById(pacienteUsuarioId);
        usuarioRepository.deleteById(odontologoUsuarioId);
        odontologoRepository.deleteById(odontologoId);
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(fichaPaciente.getId());
        fichaRepository.deleteById(fichaOdontologo.getId());
        especialidadRepository.deleteById(especialidadId);
        consultorioRepository.deleteById(consultorio.getId());
    }

    @Test
    void planDeCuatroSesiones_conDosAtendidasYCerradas_avanzaDosDeCuatroTrasElCierreYTrasUnaCancelacion()
            throws Exception {
        // 1. El odontólogo planifica cuatro sesiones: nacen todas pendientes.
        UUID planId = crearPlan(4);
        assertAvance(planId, 0, 4);

        // 2. Dos consultas terminadas del mismo tratamiento, cerradas como atendidas
        //    desde recepción: ocupan las dos primeras sesiones.
        for (Cita cita : List.of(sembrar(Cita.ESTADO_CONFIRMADA, enPunto.minusHours(5)),
                sembrar(Cita.ESTADO_CONFIRMADA, enPunto.minusHours(3)))) {
            mockMvc.perform(patch("/api/v1/citas/" + cita.getId() + "/resultado")
                            .with(como(recepcionistaId, "RECEPCIONISTA"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"resultado\":\"ATENDIDA\"}"))
                    .andExpect(status().isOk());
        }

        // 3. El odontólogo cierra esas dos sesiones con lo que indicó.
        UUID recomendacion = recomendacionRepository.findByActivaTrueOrderByDescripcionAsc().get(0).getId();
        String proximoControl = LocalDate.now(ZONA).plusDays(30).toString();
        for (int numero = 1; numero <= 2; numero++) {
            mockMvc.perform(post("/api/v1/planes/" + planId + "/sesiones/" + numero + "/cierre")
                            .with(como(odontologoUsuarioId, "ODONTOLOGO"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recomendacionIds\":[\"" + recomendacion + "\"],"
                                    + "\"proximoControl\":\"" + proximoControl + "\"}"))
                    .andExpect(status().isOk());
        }

        // Tras el cierre: dos de cuatro, y así lo lee el paciente.
        JsonNode tras = assertAvance(planId, 2, 2);
        assertEquals("CERRADA", tras.path("sesiones").get(0).path("estado").asText());
        assertEquals("CERRADA", tras.path("sesiones").get(1).path("estado").asText());
        assertEquals("PENDIENTE", tras.path("sesiones").get(2).path("estado").asText());

        // 4. La siguiente cita del tratamiento se cancela: no ocurrió, y el avance
        //    no se mueve.
        Cita siguiente = sembrar(Cita.ESTADO_CONFIRMADA, enPunto.plusDays(3));
        mockMvc.perform(patch("/api/v1/citas/" + siguiente.getId() + "/cancelar")
                        .with(como(recepcionistaId, "RECEPCIONISTA"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"motivo\":\"El paciente viaja\"}"))
                .andExpect(status().isOk());

        assertAvance(planId, 2, 2);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Lee el plan como su paciente y comprueba el avance que le llega. */
    private JsonNode assertAvance(UUID planId, int completadas, int pendientes) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/planes/" + planId)
                        .with(como(pacienteUsuarioId, "PACIENTE")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode plan = objectMapper.readTree(cuerpo);
        assertEquals(completadas, plan.path("avance").path("completadas").asInt(), cuerpo);
        assertEquals(pendientes, plan.path("avance").path("pendientes").asInt(), cuerpo);
        return plan;
    }

    private UUID crearPlan(int sesiones) throws Exception {
        String cuerpo = mockMvc.perform(post("/api/v1/planes")
                        .with(como(odontologoUsuarioId, "ODONTOLOGO"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pacienteId\":\"" + fichaPaciente.getId() + "\","
                                + "\"tratamientoId\":\"" + tratamientoId + "\","
                                + "\"sesionesPrevistas\":" + sesiones + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(cuerpo).path("id").asText());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor como(UUID quien, String rol) {
        return user(quien.toString()).authorities(new SimpleGrantedAuthority("SCOPE_" + rol));
    }

    private Cita sembrar(String estado, OffsetDateTime inicio) {
        Cita cita = citaRepository.save(Cita.builder()
                .codigo("SG-" + corto())
                .ficha(fichaPaciente)
                .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                .consultorio(consultorio)
                .inicio(inicio).fin(inicio.plusMinutes(30))
                .estado(estado)
                .build());
        citas.add(cita.getId());
        return cita;
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        return fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                // Al azar y no con el reloj: dos fichas creadas en el mismo instante
                // compartirían los dígitos altos de nanoTime y chocarían en la base.
                .documento(String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L)))
                .nombres(nombres).apellidos(apellidos).build());
    }

    private UUID nuevaCuenta(String nombre, String rol, Ficha ficha) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .correo("seguimiento-" + corto() + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    private static String corto() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
