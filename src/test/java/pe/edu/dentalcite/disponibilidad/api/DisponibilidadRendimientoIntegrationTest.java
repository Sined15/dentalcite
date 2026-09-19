package pe.edu.dentalcite.disponibilidad.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RNF-01: «la consulta de disponibilidad para un rango de catorce días responderá
 * con p95 ≤ 1,5 s sobre la agenda de cinco odontólogos con ocupación del 70 % y
 * caché fría».
 *
 * <p>No corre en cada construcción: sembrar cinco agendas de catorce días y medir
 * añade cerca de un minuto, y una máquina cargada la volvería intermitente.
 * {@code ./mvnw verify "-Dperf=true"} la activa cuando se quiere la evidencia,
 * que es como la pide la Definición de Terminado y como se comparará contra el
 * margen del 20 % en los Sprints 3 y 4.
 *
 * <p>La medición es «caché fría» de verdad: cada iteración invalida la caché de
 * franjas antes de cronometrar, de modo que se mide el cálculo completo y no un
 * acierto de Redis.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@EnabledIfSystemProperty(named = "perf", matches = "true")
class DisponibilidadRendimientoIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(DisponibilidadRendimientoIntegrationTest.class);

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final int ODONTOLOGOS = 5;
    private static final int DIAS = 14;
    private static final int MEDICIONES = 20;
    /** Once de las dieciseis medias horas de la jornada: 33 de 48 plazas, ~70 %. */
    private static final int FRANJAS_OCUPADAS = 11;
    private static final long UMBRAL_P95_MS = 1500;

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private HorarioAtencionRepository horarioRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private ConsultorioRepository consultorioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private UUID tratamientoId;
    private UUID especialidadId;
    private LocalDate desde;
    private final List<UUID> odontologos = new ArrayList<>();
    private final List<UUID> fichas = new ArrayList<>();
    private final List<UUID> citas = new ArrayList<>();
    private final List<UUID> horarios = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        desde = LocalDate.now(ZONA).plusDays(7);

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("PERF-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true)
                .build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        Tratamiento tratamiento = tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de carga")
                .duracionMinutos(30)
                .especialidad(especialidad)
                .activo(true)
                .build());

        List<Consultorio> consultorios = consultorioRepository.findByInoperativoFalse();
        List<Odontologo> entidades = new ArrayList<>(ODONTOLOGOS);

        for (int i = 0; i < ODONTOLOGOS; i++) {
            Ficha ficha = fichaRepository.save(Ficha.builder()
                    .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                    .tipoDocumento("DNI")
                    .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                    .nombres("Perf").apellidos("Odontologo " + i)
                    .build());
            fichas.add(ficha.getId());

            UUID odontologoId = UUID.randomUUID();
            Odontologo odontologo = odontologoRepository.save(Odontologo.builder()
                    .id(odontologoId)
                    .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                    .nombres("Perf").apellidos("Odontologo " + i)
                    .ficha(ficha)
                    .activo(true)
                    .especialidades(Set.of(especialidad))
                    .build());
            odontologos.add(odontologoId);

            // Jornada de ocho horas los siete días de la semana: el rango completo
            // de catorce días queda cubierto para los cinco.
            for (int dia = 1; dia <= 7; dia++) {
                horarios.add(horarioRepository.save(HorarioAtencion.builder()
                        .odontologo(odontologo)
                        .diaSemana(dia)
                        .horaInicio(LocalTime.of(9, 0))
                        .horaFin(LocalTime.of(17, 0))
                        .build()).getId());
            }

            entidades.add(odontologo);
        }

        sembrarOcupacion(entidades, consultorios, tratamiento);
    }

    /**
     * Llena la agenda al 70 % de la <strong>capacidad de la clínica</strong>, que
     * es lo que RNF-01 mide: no el 70 % de la jornada de cada odontólogo.
     *
     * <p>La diferencia no es cosmética: ocupar a cada odontólogo el 70 % de sus
     * dieciséis medias horas puede pedir más citas simultáneas que consultorios
     * tiene la clínica, que es un escenario imposible y viola RN-02. Antes pasaba
     * inadvertido porque nada lo comprobaba; desde HU-10 lo rechaza la restricción
     * de exclusión de la base, que es exactamente su trabajo.
     *
     * <p>Se llenan once de las dieciséis franjas con todos los consultorios
     * ocupados, un 69 % de la capacidad: con los cinco consultorios que siembra
     * V20 son 55 citas diarias de 80 plazas (antes de V20, 33 de 48). En cada
     * franja atienden tantos odontólogos como consultorios haya, rotando para que
     * ninguno se solape consigo mismo.
     */
    private void sembrarOcupacion(List<Odontologo> entidades, List<Consultorio> consultorios,
            Tratamiento tratamiento) {
        int simultaneos = Math.min(consultorios.size(), entidades.size());

        for (int d = 0; d < DIAS; d++) {
            LocalDate fecha = desde.plusDays(d);
            for (int bloque = 0; bloque < FRANJAS_OCUPADAS; bloque++) {
                LocalTime inicio = LocalTime.of(9, 0).plusMinutes(30L * bloque);
                for (int c = 0; c < simultaneos; c++) {
                    Odontologo odontologo = entidades.get((bloque + c) % entidades.size());
                    citas.add(citaRepository.save(Cita.builder()
                            .codigo("CP-" + UUID.randomUUID().toString().substring(0, 12))
                            .ficha(odontologo.getFicha())
                            .odontologo(odontologo)
                            .tratamiento(tratamiento)
                            .consultorio(consultorios.get(c))
                            .inicio(fecha.atTime(inicio).atZone(ZONA).toOffsetDateTime())
                            .fin(fecha.atTime(inicio.plusMinutes(30)).atZone(ZONA).toOffsetDateTime())
                            .estado(Cita.ESTADO_CONFIRMADA)
                            .build()).getId());
                }
            }
        }
    }

    @AfterEach
    void tearDown() {
        citas.forEach(citaRepository::deleteById);
        horarios.forEach(horarioRepository::deleteById);
        odontologos.forEach(odontologoRepository::deleteById);
        fichas.forEach(fichaRepository::deleteById);
        tratamientoRepository.deleteById(tratamientoId);
        especialidadRepository.deleteById(especialidadId);
        citas.clear();
        horarios.clear();
        odontologos.clear();
        fichas.clear();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conCincoAgendasAlSetentaPorCientoYCacheFria_cumpleElP95() throws Exception {
        medirP95("con sesion");
    }

    /**
     * La misma medición sin sesión. Abrir la consulta al visitante no puede
     * costarle el umbral: la petición anónima se salta la validación del token,
     * pero el cálculo es el mismo, y es el cálculo lo que se mide.
     */
    @Test
    void consultarDisponibilidad_sinSesionConCincoAgendasAlSetentaPorCientoYCacheFria_cumpleElP95() throws Exception {
        medirP95("sin sesion");
    }

    private void medirP95(String quien) throws Exception {
        List<Long> muestras = new ArrayList<>();

        for (int i = 0; i < MEDICIONES; i++) {
            // Caché fría en cada medición: se cronometra el cálculo, no Redis.
            redisTemplate.opsForValue().increment("disponibilidad:version");

            long arranque = System.nanoTime();
            mockMvc.perform(get("/api/v1/disponibilidad")
                            .param("tratamientoId", tratamientoId.toString())
                            .param("desde", desde.toString())
                            .param("hasta", desde.plusDays(DIAS - 1L).toString()))
                    .andExpect(status().isOk());
            muestras.add((System.nanoTime() - arranque) / 1_000_000);
        }

        muestras.sort(Long::compareTo);
        long p95 = muestras.get((int) Math.ceil(MEDICIONES * 0.95) - 1);
        long mediana = muestras.get(MEDICIONES / 2);

        // Queda en el log para la comparación del margen del 20 % en los Sprints 3 y 4.
        log.info("RNF-01 · disponibilidad {} de {} dias, {} odontologos, cache fria: mediana {} ms, p95 {} ms (umbral {} ms)",
                quien, DIAS, ODONTOLOGOS, mediana, p95, UMBRAL_P95_MS);

        assertTrue(p95 <= UMBRAL_P95_MS,
                "RNF-01 (" + quien + "): el p95 fue de " + p95 + " ms, por encima del umbral de "
                        + UMBRAL_P95_MS + " ms");
    }
}
