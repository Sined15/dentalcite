package pe.edu.dentalcite;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;
import pe.edu.dentalcite.bloqueo.repository.BloqueoRepository;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-01 · Entorno reproducible (RNF-11, RNF-13).
 *
 * <p>Cubre los criterios que sí son verificables desde el backend: los datos de
 * demostración, la idempotencia de Flyway y el almacenamiento en UTC. El criterio
 * de «una sola orden de composición» se verifica levantando el compose, no desde
 * aquí.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class EntornoReproducibleIntegrationTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    /** Las ocho especialidades del caso simulado del informe (§3.5 Supuestos). */
    private static final List<String> ESPECIALIDADES_DEL_CASO = List.of(
            "Odontología General", "Ortodoncia", "Endodoncia", "Periodoncia",
            "Odontopediatría", "Cirugía Bucal y Maxilofacial", "Rehabilitación Oral",
            "Odontología Estética");

    /** Los siete odontólogos: COP-10001 en V8, COP-10002 en V15, el resto en V20. */
    private static final List<String> COPS_DEL_CASO = List.of(
            "COP-10001", "COP-10002", "COP-10003", "COP-10004", "COP-10005",
            "COP-10006", "COP-10007");

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Flyway flyway;

    @Autowired
    private BloqueoRepository bloqueoRepository;

    @Autowired
    private pe.edu.dentalcite.consultorio.repository.ConsultorioRepository consultorioRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private int contar(String tabla) {
        Integer n = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tabla, Integer.class);
        return n == null ? 0 : n;
    }

    // ---------------------------------------------------------------- datos semilla

    @Test
    void alArrancar_existenLosDatosSemillaDelCatalogoYLaAgenda() {
        // «existirán las especialidades, los consultorios, los feriados [y] el
        // catálogo de recomendaciones». Los consultorios del caso se cuentan por
        // nombre: la base del contenedor la comparten todas las pruebas de
        // integración y varias crean los suyos, así que un `COUNT(*)` a secas
        // pasaría en verde con la semilla incompleta. Las ocho especialidades y
        // los siete odontólogos se comprueban abajo, uno a uno y por eso mismo.
        Integer consultorios = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM consultorios WHERE nombre LIKE 'Consultorio %'", Integer.class);
        assertTrue(consultorios != null && consultorios >= 5,
                "el caso simulado tiene cinco consultorios");
        assertTrue(contar("feriados") >= 1, "faltan feriados sembrados");
        assertTrue(contar("recomendaciones") >= 1, "falta el catálogo cerrado de recomendaciones");
        // V15 añade lo que faltaba para que la agenda exista de arranque: sin
        // tratamientos el motor de disponibilidad no puede ni invocarse, y sin
        // horario declarado no propone ninguna franja. Demostrar el Sprint 2
        // exigía entonces darlos de alta a mano, contra RNF-11.
        assertTrue(contar("tratamientos") >= 1, "faltan tratamientos sembrados");
        assertTrue(contar("horarios_atencion") >= 1, "ningún odontólogo tiene horario declarado");
    }

    /**
     * El caso simulado del informe: ocho especialidades, cinco consultorios y
     * siete odontólogos (§3.5 Supuestos). Contarlos no bastaría, y no solo porque
     * otras pruebas dejen filas en la base compartida: un odontólogo sin horario
     * declarado no existe para el motor de disponibilidad, y una especialidad que
     * nadie ejerce no puede proponer ninguna franja. Sembrarlos así sería no
     * sembrarlos, y RNF-11 pide que la demostración arranque sin ningún alta
     * manual.
     */
    @Test
    void alArrancar_laSemillaReproduceElCasoSimuladoDelInforme() {
        for (String especialidad : ESPECIALIDADES_DEL_CASO) {
            Integer sembrada = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM especialidades WHERE nombre = ? AND activo",
                    Integer.class, especialidad);
            assertEquals(1, sembrada == null ? -1 : sembrada,
                    "falta la especialidad del caso simulado: " + especialidad);

            Integer quienLaEjerce = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM odontologo_especialidad oe
                    JOIN odontologos o ON o.id = oe.odontologo_id AND o.activo
                    JOIN especialidades e ON e.id = oe.especialidad_id
                    WHERE e.nombre = ?
                    """, Integer.class, especialidad);
            assertTrue(quienLaEjerce != null && quienLaEjerce >= 1,
                    "ningún odontólogo ejerce " + especialidad + ": no puede proponer franjas");
        }

        for (String cop : COPS_DEL_CASO) {
            Integer conHorario = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*) FROM horarios_atencion h
                    JOIN odontologos o ON o.id = h.odontologo_id AND o.activo
                    WHERE o.cop = ?
                    """, Integer.class, cop);
            assertTrue(conHorario != null && conHorario >= 1,
                    "el odontólogo " + cop + " no está sembrado o no tiene horario declarado");
        }
    }

    @Test
    void alArrancar_existeUnaCuentaPorCadaUnoDeLosCuatroRoles() {
        for (String rol : Arrays.asList("ADMINISTRADOR", "RECEPCIONISTA", "ODONTOLOGO", "PACIENTE")) {
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usuarios WHERE rol = ? AND activo", Integer.class, rol);
            assertTrue(n != null && n >= 1, "no hay cuenta de demostración con rol " + rol);
        }
    }

    @Test
    void lasCuatroCredencialesDeDemostracion_puedenIniciarSesion() throws Exception {
        // Este es el criterio literal: «una credencial de demostración por cada uno
        // de los cuatro roles». El hash sembrado originalmente para las tres cuentas
        // de personal no correspondía a la contraseña documentada, de modo que tres
        // de las cuatro no podían entrar y nada lo detectaba.
        for (String correo : Arrays.asList(
                "admin@dentalcite.com",
                "recepcion@dentalcite.com",
                "dr.perez@dentalcite.com",
                "paciente@demo.com")) {

            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"correo\": \"" + correo + "\", \"password\": \"Password123\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").isNotEmpty());
        }
    }

    @Test
    void laCuentaOdontologoDeDemostracion_resuelveASuRegistroDeOdontologo() {
        // RN-11: sin ficha ni fila en `odontologos`, esa cuenta no podía declarar su
        // horario ni sus bloqueos (HU-07).
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM usuarios u
                JOIN odontologos o ON o.ficha_id = u.ficha_id
                WHERE u.correo = 'dr.perez@dentalcite.com' AND u.rol = 'ODONTOLOGO'
                """, Integer.class);
        assertEquals(1, n);

        Integer especialidades = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM odontologo_especialidad oe
                JOIN odontologos o ON o.id = oe.odontologo_id
                WHERE o.cop = 'COP-10001'
                """, Integer.class);
        assertTrue(especialidades != null && especialidades >= 1, "el odontólogo demo no tiene especialidades");
    }

    // ---------------------------------------------------------------- Flyway

    @Test
    void flyway_alVolverAMigrar_noReaplicaNadaYDejaLaMismaVersion() {
        // «cuando se levante dos veces seguidas, entonces Flyway aplicará las
        // migraciones una sola vez y dejará el esquema en la misma versión».
        String versionAntes = flyway.info().current().getVersion().getVersion();

        MigrateResult segundaPasada = flyway.migrate();

        assertEquals(0, segundaPasada.migrationsExecuted,
                "una segunda migración no debe ejecutar ninguna migración");
        assertEquals(versionAntes, flyway.info().current().getVersion().getVersion());
    }

    @Test
    void flyway_todasLasMigracionesEstanAplicadasYNingunaConChecksumRoto() {
        for (MigrationInfo info : flyway.info().all()) {
            assertEquals(MigrationState.SUCCESS, info.getState(),
                    "la migración " + info.getVersion() + " no está aplicada correctamente");
        }
    }

    // ---------------------------------------------------------------- RNF-13 · UTC

    @Test
    void laJvmCorreEnUtc() {
        assertEquals(ZoneOffset.UTC, TimeZone.getDefault().toZoneId().normalized());
    }

    @Test
    void limaNoAplicaHorarioDeVerano_elDesplazamientoEsFijoTodoElAno() {
        // RNF-13: «Perú no aplica horario de verano, de modo que la conversión entre
        // la hora local y el instante persistido en UTC es una traslación fija».
        ZoneOffset enero = LIMA.getRules().getOffset(LocalDateTime.of(2026, 1, 15, 12, 0));
        ZoneOffset julio = LIMA.getRules().getOffset(LocalDateTime.of(2026, 7, 15, 12, 0));

        assertEquals(ZoneOffset.ofHours(-5), enero);
        assertEquals(ZoneOffset.ofHours(-5), julio);
    }

    @Test
    void unaFechaLocalDeLima_sePersisteComoElMismoInstanteEnUtc() {
        // 2026-07-15 09:00 en Lima es 2026-07-15 14:00 UTC.
        OffsetDateTime inicioLima = LocalDateTime.of(2026, 7, 15, 9, 0).atZone(LIMA).toOffsetDateTime();
        OffsetDateTime finLima = inicioLima.plusHours(2);

        // La tabla exige odontólogo o consultorio (CHECK bloqueo_al_menos_uno).
        Bloqueo guardado = bloqueoRepository.save(Bloqueo.builder()
                .motivo("Prueba de frontera horaria")
                .consultorio(consultorioRepository.findAll().get(0))
                .fechaInicio(inicioLima)
                .fechaFin(finLima)
                .build());

        Instant esperado = LocalDateTime.of(2026, 7, 15, 14, 0).toInstant(ZoneOffset.UTC);

        // El instante persistido es el mismo, se lea desde JPA...
        Bloqueo releido = bloqueoRepository.findById(guardado.getId()).orElseThrow();
        assertEquals(esperado, releido.getFechaInicio().toInstant());

        // ...o directamente desde la columna TIMESTAMPTZ.
        Instant enBaseDeDatos = jdbcTemplate.queryForObject(
                "SELECT fecha_inicio FROM bloqueos WHERE id = ?",
                (rs, n) -> rs.getObject("fecha_inicio", OffsetDateTime.class).toInstant(),
                guardado.getId());
        assertEquals(esperado, enBaseDeDatos);

        bloqueoRepository.deleteById(guardado.getId());
    }

    @Test
    void unaFechaDeEneroYUnaDeJulio_seTrasladanConElMismoDesplazamiento() {
        // La misma hora local en invierno y en verano austral debe separarse
        // exactamente 5 horas del instante UTC en ambos casos: sin salto estacional.
        OffsetDateTime enero = LocalDateTime.of(2026, 1, 20, 8, 30).atZone(LIMA).toOffsetDateTime();
        OffsetDateTime julio = LocalDateTime.of(2026, 7, 20, 8, 30).atZone(LIMA).toOffsetDateTime();

        assertEquals(LocalDateTime.of(2026, 1, 20, 13, 30).toInstant(ZoneOffset.UTC), enero.toInstant());
        assertEquals(LocalDateTime.of(2026, 7, 20, 13, 30).toInstant(ZoneOffset.UTC), julio.toInstant());
    }
}
