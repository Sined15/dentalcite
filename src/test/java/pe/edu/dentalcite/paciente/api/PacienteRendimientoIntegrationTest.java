package pe.edu.dentalcite.paciente.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * RNF-02, la parte que faltaba: «la reserva de cita, la consulta de citas y
 * <strong>la consulta de ficha</strong> responderán con p95 ≤ 1 s bajo diez
 * usuarios concurrentes». Las dos primeras las mide
 * {@code cita/api/ReservaRendimientoIntegrationTest}; la ficha y la búsqueda que
 * la precede, esta.
 *
 * <p>Lo que de verdad vigila el umbral son dos decisiones que no se ven desde
 * fuera: que el apellido se busque por prefijo con {@code ix_fichas_apellidos}
 * —un {@code LIKE '%…%'} recorrería la tabla— y que {@code tieneCuenta} se
 * resuelva con una consulta por página y no con una por fila.
 *
 * <p>Como las de HU-08 y HU-10, no corre en cada construcción: sembrar
 * doscientas fichas y medir alarga la build y una máquina cargada la volvería
 * intermitente. {@code ./mvnw verify "-Dperf=true"} la activa.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "perf", matches = "true")
class PacienteRendimientoIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PacienteRendimientoIntegrationTest.class);

    private static final int USUARIOS = 10;
    private static final long UMBRAL_P95_MS = 1000;
    /** Bastantes más de las que caben en una página, para que paginar signifique algo. */
    private static final int FICHAS = 200;

    private MockMvc mockMvc;
    private EscenarioDePacientes escenario;
    private final List<UUID> relleno = new ArrayList<>();
    private String sufijo;

    @Autowired private WebApplicationContext context;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private ConsultorioRepository consultorioRepository;
    @Autowired private CitaRepository citaRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        escenario = new EscenarioDePacientes(fichaRepository, usuarioRepository, odontologoRepository,
                especialidadRepository, tratamientoRepository, consultorioRepository, citaRepository);
        escenario.sembrar();

        sufijo = escenario.sufijo;
        for (int i = 0; i < FICHAS; i++) {
            relleno.add(fichaRepository.save(Ficha.builder()
                    .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                    .tipoDocumento("DNI")
                    .documento(String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L)))
                    .nombres("Relleno " + i).apellidos("Carga" + sufijo)
                    .build()).getId());
        }
    }

    @AfterEach
    void limpiar() {
        relleno.forEach(fichaRepository::deleteById);
        relleno.clear();
        escenario.limpiar();
    }

    /**
     * Sin esto se mediría el arranque en frío —cadena de filtros, planes de
     * Hibernate, serializadores— y no el tiempo de respuesta de régimen
     * permanente, que es lo que RNF-02 fija como espera aceptable en un
     * mostrador.
     */
    private void calentar(Supplier<Integer> peticion) {
        for (int i = 0; i < 5; i++) {
            peticion.get();
        }
    }

    private int buscar() {
        try {
            return mockMvc.perform(get("/api/v1/pacientes")
                            .param("q", "carga" + sufijo)
                            .with(user(UUID.randomUUID().toString())
                                    .authorities(new SimpleGrantedAuthority("SCOPE_RECEPCIONISTA"))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return -1;
        }
    }

    private int abrirFicha() {
        try {
            return mockMvc.perform(get("/api/v1/pacientes/" + escenario.pacienteConCuenta.getId())
                            .with(user(UUID.randomUUID().toString())
                                    .authorities(new SimpleGrantedAuthority("SCOPE_RECEPCIONISTA"))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception e) {
            return -1;
        }
    }

    private void medir(String que, Supplier<Integer> peticion) throws Exception {
        calentar(peticion);

        List<Long> muestras = Collections.synchronizedList(new ArrayList<>());
        List<Integer> estados = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch terminados = new CountDownLatch(USUARIOS);
        ExecutorService pool = Executors.newFixedThreadPool(USUARIOS);

        for (int i = 0; i < USUARIOS; i++) {
            pool.submit(() -> {
                try {
                    salida.await();
                    long arranque = System.nanoTime();
                    int estado = peticion.get();
                    muestras.add((System.nanoTime() - arranque) / 1_000_000);
                    estados.add(estado);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    estados.add(-1);
                } finally {
                    terminados.countDown();
                }
            });
        }

        salida.countDown();
        assertTrue(terminados.await(60, TimeUnit.SECONDS), "las peticiones no terminaron a tiempo");
        pool.shutdown();

        assertEquals(USUARIOS, estados.stream().filter(e -> e == 200).count(),
                "las diez peticiones debían responder 200: " + estados);

        List<Long> ordenadas = new ArrayList<>(muestras);
        Collections.sort(ordenadas);
        long mediana = ordenadas.get(ordenadas.size() / 2);
        long p95 = ordenadas.get((int) Math.ceil(USUARIOS * 0.95) - 1);

        log.info("RNF-02 · {} con {} usuarios concurrentes: mediana {} ms, p95 {} ms (umbral {} ms)",
                que, USUARIOS, mediana, p95, UMBRAL_P95_MS);

        assertTrue(p95 <= UMBRAL_P95_MS,
                "RNF-02: el p95 fue de " + p95 + " ms, por encima del umbral de " + UMBRAL_P95_MS + " ms");
    }

    @Test
    void buscarPacientes_conDiezUsuariosConcurrentes_cumpleElP95() throws Exception {
        medir("búsqueda de pacientes", this::buscar);
    }

    @Test
    void abrirFicha_conDiezUsuariosConcurrentes_cumpleElP95() throws Exception {
        medir("consulta de ficha", this::abrirFicha);
    }
}
