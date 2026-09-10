package pe.edu.dentalcite.cita.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
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
import java.time.ZoneId;
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
 * RNF-12: «prueba de concurrencia, <strong>repetida con Redis detenido</strong>».
 *
 * <p>Es la prueba que demuestra que Redis no es la barrera única. Sin él no hay
 * bloqueo distribuido ni caché de franjas: las cien peticiones llegan enteras a la
 * base y es la restricción de exclusión la que deja pasar exactamente una.
 *
 * <p>«Redis detenido» se monta apuntando a un puerto donde no escucha nadie, en
 * lugar de parar el contenedor: es determinista, no depende del orden de las
 * pruebas y —al cambiar las propiedades— Spring le da a esta clase su propio
 * contexto, de modo que no deja sin Redis al resto de la suite.
 */
@SpringBootTest(properties = {
        "spring.data.redis.host=localhost",
        "spring.data.redis.port=6399"
})
@ActiveProfiles("test")
@Import(ExclusionSinRedisIntegrationTest.SoloPostgres.class)
class ExclusionSinRedisIntegrationTest {

    /** Sin el contenedor de Redis: la aplicación arranca y funciona igual. */
    @TestConfiguration(proxyBeanMethods = false)
    static class SoloPostgres {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
        }
    }

    private static final int PETICIONES = 100;
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

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

    private LocalDate lunes;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        sembrador = new SembradorDeAgenda(usuarioRepository, fichaRepository, odontologoRepository,
                tratamientoRepository, horarioRepository, especialidadRepository, consultorioRepository);
        lunes = SembradorDeAgenda.proximoLunes();
        sembrador.sembrarCatalogo("SINREDIS");
    }

    @AfterEach
    void tearDown() {
        sembrador.limpiar(fichaId -> citaRepository.findAll().stream()
                .filter(c -> c.getFicha().getId().equals(fichaId))
                .map(Cita::getId)
                .toList()
                .forEach(citaRepository::deleteById));
    }

    @Test
    void conRedisDetenido_cienPeticionesSiguenCreandoExactamenteUnaCita() throws Exception {
        UUID odontologoId = sembrador.sembrarOdontologo();
        List<UUID> pacientes = sembrador.sembrarPacientes(PETICIONES);
        LocalTime hora = LocalTime.of(9, 0);

        List<Integer> estados = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch salida = new CountDownLatch(1);
        CountDownLatch fin = new CountDownLatch(PETICIONES);
        ExecutorService pool = Executors.newFixedThreadPool(32);

        for (UUID paciente : pacientes) {
            pool.submit(() -> {
                try {
                    salida.await();
                    CitaRequestDTO cuerpo = CitaRequestDTO.builder()
                            .tratamientoId(sembrador.tratamientoId())
                            .odontologoId(odontologoId)
                            .fecha(lunes).hora(hora).build();
                    estados.add(mockMvc.perform(post("/api/v1/citas")
                                    .with(user(paciente.toString())
                                            .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(cuerpo)))
                            .andReturn().getResponse().getStatus());
                } catch (Exception e) {
                    estados.add(-1);
                } finally {
                    fin.countDown();
                }
            });
        }
        salida.countDown();
        assertTrue(fin.await(180, TimeUnit.SECONDS), "las reservas no terminaron a tiempo");
        pool.shutdown();

        long creadas = citaRepository.findAll().stream()
                .filter(c -> c.getOdontologo().getId().equals(odontologoId))
                .filter(c -> Cita.ESTADO_CONFIRMADA.equals(c.getEstado()))
                .filter(c -> c.getInicio().atZoneSameInstant(ZONA).toLocalTime().equals(hora))
                .count();

        // Lo que RNF-12 exige demostrar: la garantía está en la base, no en Redis.
        assertEquals(1, creadas,
                "sin Redis la restricción de exclusión sigue siendo la barrera; estados: " + estados);
        assertEquals(1, estados.stream().filter(e -> e == 201).count(),
                "solo una petición puede recibir 201; estados: " + estados);
    }
}
