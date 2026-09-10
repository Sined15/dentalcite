package pe.edu.dentalcite.cita.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.cita.api.dto.CitaRequestDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * HU-10 · exclusión mutua bajo concurrencia, contra PostgreSQL y Redis reales.
 *
 * <p>Estas pruebas van en la suite normal y no detrás del flag de rendimiento:
 * son criterios de aceptación, no mediciones. Si dejan de pasar, el sistema
 * permite dobles reservas.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ExclusionMutuaIntegrationTest {

    private static final int PETICIONES = 100;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;
    private SembradorDeAgenda sembrador;

    @Autowired private WebApplicationContext context;
    @Autowired private CitaRepository citaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private HorarioAtencionRepository horarioRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private ConsultorioRepository consultorioRepository;
    @Autowired private EntityManager entityManager;

    private LocalDate lunes;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sembrador = new SembradorDeAgenda(usuarioRepository, fichaRepository, odontologoRepository,
                tratamientoRepository, horarioRepository, especialidadRepository, consultorioRepository);
        lunes = SembradorDeAgenda.proximoLunes();
        sembrador.sembrarCatalogo("EXCLUSION");
    }

    @AfterEach
    void tearDown() {
        sembrador.limpiar(this::borrarCitasDeFicha);
    }

    private void borrarCitasDeFicha(UUID fichaId) {
        citaRepository.findAll().stream()
                .filter(c -> c.getFicha().getId().equals(fichaId))
                .map(Cita::getId)
                .toList()
                .forEach(citaRepository::deleteById);
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private final List<String> motivos = Collections.synchronizedList(new ArrayList<>());

    private int reservar(UUID paciente, UUID odontologoId, LocalTime hora) {
        try {
            CitaRequestDTO cuerpo = CitaRequestDTO.builder()
                    .tratamientoId(sembrador.tratamientoId())
                    .odontologoId(odontologoId)
                    .fecha(lunes).hora(hora).build();

            var respuesta = mockMvc.perform(post("/api/v1/citas")
                            .with(user(paciente.toString())
                                    .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(cuerpo)))
                    .andReturn().getResponse();
            if (respuesta.getStatus() != 201) {
                motivos.add(respuesta.getContentAsString());
            }
            return respuesta.getStatus();
        } catch (Exception e) {
            Throwable raiz = e;
            while (raiz.getCause() != null && raiz.getCause() != raiz) {
                raiz = raiz.getCause();
            }
            motivos.add("EXCEPCION " + raiz.getClass().getSimpleName() + ": " + raiz.getMessage());
            return -1;
        }
    }

    /** Lanza todas las peticiones de verdad a la vez y espera a que terminen. */
    private void enParalelo(List<Runnable> tareas) throws InterruptedException {
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(tareas.size());
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(tareas.size(), 32));

        for (Runnable tarea : tareas) {
            pool.submit(() -> {
                try {
                    salida.await();
                    tarea.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    fin.countDown();
                }
            });
        }
        salida.countDown();
        assertTrue(fin.await(120, TimeUnit.SECONDS), "las reservas no terminaron a tiempo");
        pool.shutdown();
    }

    private long citasConfirmadasDe(UUID odontologoId, LocalTime hora) {
        return citaRepository.findAll().stream()
                .filter(c -> c.getOdontologo().getId().equals(odontologoId))
                .filter(c -> Cita.ESTADO_CONFIRMADA.equals(c.getEstado()))
                .filter(c -> c.getInicio().atZoneSameInstant(java.time.ZoneId.of("America/Lima"))
                        .toLocalTime().equals(hora))
                .count();
    }

    // ------------------------------------------------------------------
    // CA-1 · RN-01 · cien peticiones sobre la misma franja
    // ------------------------------------------------------------------

    @Test
    void cienPeticionesSobreLaMismaFranja_creanExactamenteUnaCita() throws Exception {
        UUID odontologoId = sembrador.sembrarOdontologo();
        // Cien pacientes distintos: RN-07 no dejaría a uno solo pedir cien veces.
        List<UUID> pacientes = sembrador.sembrarPacientes(PETICIONES);
        LocalTime hora = LocalTime.of(9, 0);

        List<Integer> estados = Collections.synchronizedList(new ArrayList<>());
        List<Runnable> tareas = pacientes.stream()
                .map(p -> (Runnable) () -> estados.add(reservar(p, odontologoId, hora)))
                .toList();
        enParalelo(tareas);

        long creadas = estados.stream().filter(e -> e == 201).count();
        long rechazadas = estados.stream().filter(e -> e == 409).count();

        assertEquals(1, creadas, "solo una reserva puede prosperar; estados: " + estados
                + " motivos: " + motivos);
        assertEquals(PETICIONES - 1L, rechazadas,
                "las demás deben recibir 409, no otro error; estados: " + estados
                        + " motivos: " + motivos);
        assertEquals(1, citasConfirmadasDe(odontologoId, hora),
                "la base no puede contener dos citas del mismo odontólogo a la misma hora");
    }

    // ------------------------------------------------------------------
    // CA-2 · RN-02 · el pool de consultorios
    // ------------------------------------------------------------------

    @Test
    void cuatroOdontologosConTresConsultoriosLibres_creanSoloTresCitas() throws Exception {
        sembrador.dejarSoloConsultoriosLibres(3);

        List<UUID> odontologos = List.of(sembrador.sembrarOdontologo(), sembrador.sembrarOdontologo(),
                sembrador.sembrarOdontologo(), sembrador.sembrarOdontologo());
        List<UUID> pacientes = sembrador.sembrarPacientes(4);
        LocalTime hora = LocalTime.of(10, 0);

        List<Integer> estados = Collections.synchronizedList(new ArrayList<>());
        List<Runnable> tareas = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            final UUID paciente = pacientes.get(i);
            final UUID odontologo = odontologos.get(i);
            tareas.add(() -> estados.add(reservar(paciente, odontologo, hora)));
        }
        enParalelo(tareas);

        long creadas = estados.stream().filter(e -> e == 201).count();
        assertEquals(3, creadas,
                "solo caben tres citas simultáneas con tres consultorios; estados: " + estados
                        + " motivos: " + motivos);
        assertEquals(1, estados.stream().filter(e -> e == 409).count(),
                "la cuarta debe recibir 409, no otro error; estados: " + estados
                        + " motivos: " + motivos);
    }

    // ------------------------------------------------------------------
    // RNF-12 · inspección de las restricciones
    // ------------------------------------------------------------------

    @Test
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    void laBaseDeclaraLasDosRestriccionesDeExclusion() {
        // El instrumento de verificación que nombra RNF-12 es literalmente este:
        // «inspección de las restricciones». Que el sistema se comporte bien no
        // basta si la garantía no está donde el requisito la exige.
        List<String> nombres = entityManager.createNativeQuery("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'citas'::regclass AND contype = 'x'
                ORDER BY conname
                """).getResultList();

        assertEquals(List.of("citas_sin_solape_consultorio", "citas_sin_solape_odontologo"), nombres,
                "las restricciones de exclusión son la garantía; sin ellas Redis sería la barrera única");
    }
}
