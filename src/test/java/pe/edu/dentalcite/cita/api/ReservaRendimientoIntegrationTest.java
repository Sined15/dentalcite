package pe.edu.dentalcite.cita.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import pe.edu.dentalcite.cita.api.dto.CitaRequestDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * RNF-02: «la reserva de cita, <strong>la consulta de citas</strong> y la consulta
 * de ficha responderán con p95 ≤ 1 s bajo diez usuarios concurrentes». Aquí se
 * miden las dos primeras; la ficha llega con HU-13.
 *
 * <p>Diez pacientes distintos reservan a la vez, cada uno sobre <em>su propia</em>
 * franja. Mide caudal, no exclusión: que dos reservas simultáneas sobre la
 * <em>misma</em> franja produzcan una sola cita es HU-10, y lo comprueban
 * {@code ExclusionMutuaIntegrationTest} y {@code ExclusionSinRedisIntegrationTest}.
 *
 * <p>Como la de RNF-01, no corre en cada construcción: sembrar diez agendas y
 * medir alarga la build y una máquina cargada la volvería intermitente.
 * {@code ./mvnw verify "-Dperf=true"} la activa cuando se quiere la evidencia.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "perf", matches = "true")
class ReservaRendimientoIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(ReservaRendimientoIntegrationTest.class);

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final int USUARIOS = 10;
    private static final long UMBRAL_P95_MS = 1000;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Autowired private WebApplicationContext context;
    @Autowired private CitaRepository citaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private HorarioAtencionRepository horarioRepository;
    @Autowired private EspecialidadRepository especialidadRepository;

    private UUID tratamientoId;
    private UUID odontologoId;
    private UUID especialidadId;
    private LocalDate lunes;

    private final List<UUID> pacientes = new ArrayList<>();
    private final List<UUID> fichas = new ArrayList<>();
    private final List<UUID> horarios = new ArrayList<>();
    private final List<UUID> citas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        lunes = LocalDate.now(ZONA).plusDays(14);
        while (lunes.getDayOfWeek().getValue() != 1) {
            lunes = lunes.plusDays(1);
        }

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("PERF-RESERVA-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de carga")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        Ficha fichaOdo = nuevaFicha("Perf", "Odontologo");
        odontologoId = UUID.randomUUID();
        Odontologo odontologo = odontologoRepository.save(Odontologo.builder()
                .id(odontologoId)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Perf").apellidos("Odontologo")
                .ficha(fichaOdo).activo(true)
                .especialidades(Set.of(especialidad)).build());

        // Jornada larga: hacen falta al menos diez franjas de media hora distintas.
        horarios.add(horarioRepository.save(HorarioAtencion.builder()
                .odontologo(odontologo).diaSemana(1)
                .horaInicio(LocalTime.of(8, 0)).horaFin(LocalTime.of(18, 0)).build()).getId());

        for (int i = 0; i < USUARIOS; i++) {
            Ficha ficha = nuevaFicha("Perf", "Paciente " + i);
            pacientes.add(usuarioRepository.save(Usuario.builder()
                    .nombre("Perf Paciente " + i)
                    .correo("perf-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                    .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                    .rol("PACIENTE").activo(true).ficha(ficha).build()).getId());
        }
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        Ficha ficha = fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres(nombres).apellidos(apellidos).build());
        fichas.add(ficha.getId());
        return ficha;
    }

    @AfterEach
    void tearDown() {
        citaRepository.findAll().stream().map(Cita::getId).forEach(citas::add);
        citas.stream().distinct().forEach(id -> {
            try {
                citaRepository.deleteById(id);
            } catch (RuntimeException e) {
                // Otra prueba pudo dejarla; no es asunto de esta.
            }
        });
        horarios.forEach(horarioRepository::deleteById);
        pacientes.forEach(usuarioRepository::deleteById);
        odontologoRepository.deleteById(odontologoId);
        tratamientoRepository.deleteById(tratamientoId);
        fichas.forEach(id -> {
            try {
                fichaRepository.deleteById(id);
            } catch (RuntimeException e) {
                // La ficha del odontólogo cae con él.
            }
        });
        especialidadRepository.deleteById(especialidadId);
        citas.clear();
        horarios.clear();
        pacientes.clear();
        fichas.clear();
    }

    /**
     * Calienta el camino completo sin consumir franjas ni cuota: se pide una hora
     * fuera del horario declarado, que recorre autenticación, ficha, catálogo,
     * RN-05, RN-07 y el motor, y muere en el 409 justo antes del INSERT.
     *
     * <p>Sin esto se mediría el arranque en frío del JVM —cadena de filtros,
     * planes de Hibernate, serializadores— y no el tiempo de respuesta que RNF-02
     * fija como «límite de espera aceptable en un mostrador», que es de régimen
     * permanente.
     */
    private void calentar() throws Exception {
        for (int i = 0; i < 5; i++) {
            CitaRequestDTO fuera = CitaRequestDTO.builder()
                    .tratamientoId(tratamientoId).odontologoId(odontologoId)
                    .fecha(lunes).hora(LocalTime.of(22, 0)).build();
            mockMvc.perform(post("/api/v1/citas")
                    .with(user(pacientes.get(i).toString())
                            .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(fuera)));
        }
    }

    @Test
    void reservar_conDiezUsuariosConcurrentes_cumpleElP95() throws Exception {
        calentar();

        List<Long> muestras = Collections.synchronizedList(new ArrayList<>());
        List<Integer> estados = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(USUARIOS);
        ExecutorService pool = Executors.newFixedThreadPool(USUARIOS);

        for (int i = 0; i < USUARIOS; i++) {
            final UUID paciente = pacientes.get(i);
            // Cada uno a su franja: se mide el caudal de la reserva, no la disputa.
            final LocalTime hora = LocalTime.of(8, 0).plusMinutes(30L * i);
            pool.submit(() -> {
                try {
                    salida.await();
                    CitaRequestDTO cuerpo = CitaRequestDTO.builder()
                            .tratamientoId(tratamientoId).odontologoId(odontologoId)
                            .fecha(lunes).hora(hora).build();

                    long arranque = System.nanoTime();
                    int estado = mockMvc.perform(post("/api/v1/citas")
                                    .with(user(paciente.toString())
                                            .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(cuerpo)))
                            .andReturn().getResponse().getStatus();
                    muestras.add((System.nanoTime() - arranque) / 1_000_000);
                    estados.add(estado);
                } catch (Exception e) {
                    estados.add(-1);
                } finally {
                    terminados.countDown();
                }
            });
        }

        salida.countDown();
        assertTrue(terminados.await(60, TimeUnit.SECONDS), "las reservas no terminaron a tiempo");
        pool.shutdown();

        assertEquals(USUARIOS, estados.stream().filter(e -> e == 201).count(),
                "las diez reservas sobre franjas distintas debían crearse: " + estados);

        List<Long> ordenadas = new ArrayList<>(muestras);
        Collections.sort(ordenadas);
        long p95 = ordenadas.get((int) Math.ceil(USUARIOS * 0.95) - 1);
        long mediana = ordenadas.get(USUARIOS / 2);

        log.info("RNF-02 · reserva con {} usuarios concurrentes: mediana {} ms, p95 {} ms (umbral {} ms)",
                USUARIOS, mediana, p95, UMBRAL_P95_MS);

        assertTrue(p95 <= UMBRAL_P95_MS,
                "RNF-02: el p95 fue de " + p95 + " ms, por encima del umbral de " + UMBRAL_P95_MS + " ms");
    }

    /**
     * RNF-02 sobre la consulta de citas (HU-11 · RF-18): diez recepcionistas
     * mirando la agenda del día a la vez.
     *
     * <p>Es la mitad que más fácil se degrada: la reserva toca una fila y esta
     * recorre todas las del rango con cuatro asociaciones por fila. Sin el
     * {@code @EntityGraph} de {@code CitaRepository.buscar} serían cinco consultas
     * por cita en vez de una por página, y el umbral se perdería en cuanto la
     * clínica llevara unos meses operando.
     */
    @Test
    void consultarAgenda_conDiezUsuariosConcurrentes_cumpleElP95() throws Exception {
        calentar();
        sembrarCitasDelDia();

        List<Long> muestras = Collections.synchronizedList(new ArrayList<>());
        List<Integer> estados = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(USUARIOS);
        ExecutorService pool = Executors.newFixedThreadPool(USUARIOS);

        for (int i = 0; i < USUARIOS; i++) {
            final UUID recepcion = UUID.randomUUID();
            pool.submit(() -> {
                try {
                    salida.await();
                    long arranque = System.nanoTime();
                    int estado = mockMvc.perform(get("/api/v1/citas")
                                    .with(user(recepcion.toString())
                                            .authorities(new SimpleGrantedAuthority("SCOPE_RECEPCIONISTA")))
                                    .param("desde", lunes.toString())
                                    .param("hasta", lunes.toString()))
                            .andReturn().getResponse().getStatus();
                    muestras.add((System.nanoTime() - arranque) / 1_000_000);
                    estados.add(estado);
                } catch (Exception e) {
                    estados.add(-1);
                } finally {
                    terminados.countDown();
                }
            });
        }

        salida.countDown();
        assertTrue(terminados.await(60, TimeUnit.SECONDS), "las consultas no terminaron a tiempo");
        pool.shutdown();

        assertEquals(USUARIOS, estados.stream().filter(e -> e == 200).count(),
                "las diez consultas debian responder 200: " + estados);

        List<Long> ordenadas = new ArrayList<>(muestras);
        Collections.sort(ordenadas);
        long p95 = ordenadas.get((int) Math.ceil(USUARIOS * 0.95) - 1);
        long mediana = ordenadas.get(USUARIOS / 2);

        log.info("RNF-02 · consulta de agenda con {} usuarios concurrentes: mediana {} ms, p95 {} ms (umbral {} ms)",
                USUARIOS, mediana, p95, UMBRAL_P95_MS);

        assertTrue(p95 <= UMBRAL_P95_MS,
                "RNF-02: el p95 fue de " + p95 + " ms, por encima del umbral de " + UMBRAL_P95_MS + " ms");
    }

    /** Llena la agenda del dia para que la consulta tenga algo que recorrer. */
    private void sembrarCitasDelDia() throws Exception {
        for (int i = 0; i < USUARIOS; i++) {
            CitaRequestDTO cuerpo = CitaRequestDTO.builder()
                    .tratamientoId(tratamientoId).odontologoId(odontologoId)
                    .fecha(lunes).hora(LocalTime.of(8, 0).plusMinutes(30L * i)).build();
            mockMvc.perform(post("/api/v1/citas")
                    .with(user(pacientes.get(i).toString())
                            .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(cuerpo)));
        }
    }
}
