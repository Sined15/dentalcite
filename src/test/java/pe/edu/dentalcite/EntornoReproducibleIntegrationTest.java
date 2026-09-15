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

    /**
     * Los pacientes de demostración de `V22`, por documento. Antes había ocho
     * fichas y siete eran de odontólogos —cada uno necesita una para vincular su
     * cuenta con su registro—, así que el padrón no servía para demostrar la
     * búsqueda, la reserva desde recepción ni la ficha clínica sin dar altas a
     * mano, que es lo que la semilla existe para evitar.
     */
    private static final List<String> PACIENTES_DEL_CASO = List.of(
            "45110001", "45110002", "45110003", "45110004", "45110005", "45110006");

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

    /**
     * El entorno tiene que arrancar con pacientes de verdad, no solo con el
     * catálogo. «De verdad» es lo que se comprueba —ficha sin registro de
     * odontólogo y sin cuenta—, porque el odontólogo también tiene ficha y
     * contarlas a secas daría en verde con siete odontólogos y ningún paciente,
     * que es exactamente la situación que `V22` vino a corregir. Sin credenciales
     * es además la forma que crea el alta presencial en el mostrador.
     */
    @Test
    void alArrancar_laSemillaTraePacientesYNoSoloFichasDeOdontologo() {
        for (String documento : PACIENTES_DEL_CASO) {
            Integer n = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM fichas f
                    WHERE f.documento = ?
                      AND f.nombres IS NOT NULL AND f.apellidos IS NOT NULL
                      AND NOT EXISTS (SELECT 1 FROM odontologos o WHERE o.ficha_id = f.id)
                      AND NOT EXISTS (SELECT 1 FROM usuarios u WHERE u.ficha_id = f.id)
                    """, Integer.class, documento);
            assertEquals(1, n == null ? -1 : n,
                    "falta el paciente de demostración con documento " + documento
                            + ", o dejó de ser una ficha de paciente sin cuenta");
        }

        // Dos comparten apellido a propósito: la búsqueda compara el apellido por
        // prefijo, y con un solo portador no se distingue «encontró a esta
        // persona» de «encontró a todas las que empiezan así».
        Integer camacho = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fichas WHERE LOWER(apellidos) LIKE 'camacho%'", Integer.class);
        assertTrue(camacho != null && camacho >= 2,
                "la búsqueda por prefijo de apellido necesita más de un portador");
    }

    /**
     * `V1` sembró las cuentas sin nombre y `V9` las bautizó con el trozo del correo
     * para poder declarar la columna NOT NULL. El efecto era que la cuenta
     * «paciente» y la ficha «Rosa Delgado» eran la misma persona sin que nada lo
     * dijera, justo lo que el administrador necesita cruzar entre «Cuentas de
     * acceso» y «Pacientes». Quien tiene ficha lleva además su mismo nombre.
     */
    @Test
    void lasCuentasDeDemostracion_llevanNombreDePersonaYNoElTrozoDelCorreo() {
        for (String correo : Arrays.asList(
                "admin@dentalcite.com",
                "recepcion@dentalcite.com",
                "dr.perez@dentalcite.com",
                "paciente@demo.com")) {

            Integer derivado = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM usuarios WHERE correo = ? AND nombre = split_part(correo, '@', 1)",
                    Integer.class, correo);
            assertEquals(0, derivado == null ? -1 : derivado,
                    "la cuenta " + correo + " sigue llamándose como el trozo de su correo");
        }

        Integer cuadran = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM usuarios u
                JOIN fichas f ON f.id = u.ficha_id
                WHERE u.correo IN ('dr.perez@dentalcite.com', 'paciente@demo.com')
                  AND u.nombre = TRIM(f.nombres || ' ' || f.apellidos)
                """, Integer.class);
        assertEquals(2, cuadran == null ? -1 : cuadran,
                "la cuenta con ficha tiene que llamarse como su ficha: es el cruce que se demuestra");
    }

    /**
     * El odontólogo declara su horario, registra sus bloqueos, consulta su agenda
     * y cierra sus citas, y ninguna de esas cosas la hace quien no puede iniciar
     * sesión. Hasta `V23` solo Pérez tenía cuenta: para calcular la disponibilidad
     * bastaba el registro con su horario, así que seis de los siete dependían de
     * que recepción se lo llevara todo.
     *
     * <p>La comprobación es por **ficha**, que es el vínculo que sigue
     * `OdontologoOwnershipGuard`: una cuenta que no resuelva a su registro tiene
     * credenciales y ningún permiso sobre lo suyo.
     */
    @Test
    void alArrancar_cadaOdontologoDelCasoTieneCuentaQueResuelveASuRegistro() {
        for (String cop : COPS_DEL_CASO) {
            Integer n = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM odontologos o
                    JOIN usuarios u ON u.ficha_id = o.ficha_id
                    WHERE o.cop = ? AND u.rol = 'ODONTOLOGO' AND u.activo
                    """, Integer.class, cop);
            assertEquals(1, n == null ? -1 : n,
                    "el odontólogo " + cop + " no tiene cuenta activa que resuelva a su registro");
        }
    }

    /**
     * La agenda del odontólogo de demostración, que es lo último que le faltaba a
     * la semilla: sin citas suyas, «Mi agenda» sale vacía, la cola de cierre sale
     * vacía y su lista de pacientes también, porque quién es paciente suyo se
     * deduce de las citas.
     *
     * <p>Se comprueban las tres formas que sostienen esas pantallas por separado
     * —pasada con desenlace, confirmada ya vencida y confirmada por venir—, y no
     * un total: un {@code COUNT(*)} daría en verde con diez citas futuras y
     * ninguna atendida, que deja la ficha clínica sin historial.
     */
    @Test
    void alArrancar_elOdontologoDeDemostracionTieneAgendaEnLosTresTiempos() {
        assertTrue(citasDePerez("c.estado IN ('ATENDIDA', 'NO_ASISTIO')") > 0,
                "sin citas con desenlace, la ficha del paciente no tiene historial que enseñar");
        assertTrue(citasDePerez("c.estado = 'CONFIRMADA' AND c.fin < CURRENT_TIMESTAMP") > 0,
                "sin una confirmada ya vencida, la cola de «por cerrar» sale vacía");
        assertTrue(citasDePerez("c.estado = 'CONFIRMADA' AND c.inicio > CURRENT_TIMESTAMP") > 0,
                "sin citas por venir, «mi agenda» sale vacía");
    }

    /**
     * Y cada transición sembrada dejó su rastro, que es lo que la bitácora enseña.
     * El alta no lo deja —ninguna fila tiene estado anterior nulo—, igual que en la
     * aplicación.
     */
    @Test
    void alArrancar_lasCitasConDesenlaceTraenSuBitacora() {
        Integer sinRastro = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM citas c
                JOIN odontologos o ON o.id = c.odontologo_id AND o.cop = 'COP-10001'
                WHERE c.estado <> 'CONFIRMADA'
                  AND NOT EXISTS (SELECT 1 FROM citas_historial h WHERE h.cita_id = c.id)
                """, Integer.class);
        assertEquals(0, sinRastro == null ? -1 : sinRastro,
                "hay citas con desenlace sin ninguna transición registrada");
    }

    /**
     * Y de esas citas sale su lista de pacientes, que es el motivo de sembrarlas:
     * el odontólogo alcanza la ficha de quien ha tratado, y «haber tratado» se
     * acredita con una cita en cualquier estado menos cancelada.
     *
     * <p>Por eso se comprueba también el reverso: el paciente cuya única cita con
     * él está cancelada no cuenta. Sembrar uno así es lo que permite enseñar la
     * diferencia sin montarla a mano.
     */
    @Test
    void alArrancar_laAgendaDejaPacientesAlAlcanceDelOdontologo() {
        Integer suyos = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT c.ficha_id)
                FROM citas c
                JOIN odontologos o ON o.id = c.odontologo_id AND o.cop = 'COP-10001'
                WHERE c.estado <> 'CANCELADA'
                """, Integer.class);
        assertTrue(suyos != null && suyos >= 4,
                "la semilla no deja pacientes suficientes en la lista del odontólogo");

        Integer soloCancelada = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM fichas f
                WHERE f.documento = '45110004'
                  AND NOT EXISTS (
                      SELECT 1 FROM citas c
                      JOIN odontologos o ON o.id = c.odontologo_id AND o.cop = 'COP-10001'
                      WHERE c.ficha_id = f.id AND c.estado <> 'CANCELADA')
                """, Integer.class);
        assertEquals(1, soloCancelada == null ? -1 : soloCancelada,
                "el paciente con la cita cancelada dejó de servir para enseñar que no cuenta");
    }

    private int citasDePerez(String condicion) {
        Integer n = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM citas c
                JOIN odontologos o ON o.id = c.odontologo_id AND o.cop = 'COP-10001'
                WHERE
                """ + condicion, Integer.class);
        return n == null ? 0 : n;
    }

    /**
     * Y esas cuentas entran con la contraseña documentada, como las cuatro de
     * `V8`: una credencial sembrada que no puede iniciar sesión es justo el fallo
     * que `V8` vino a corregir, y nada lo detectaba.
     */
    @Test
    void lasCuentasDeLosOdontologos_puedenIniciarSesion() throws Exception {
        String correo = jdbcTemplate.queryForObject("""
                SELECT u.correo
                FROM odontologos o
                JOIN usuarios u ON u.ficha_id = o.ficha_id
                WHERE o.cop = 'COP-10007'
                """, String.class);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"correo\": \"" + correo + "\", \"password\": \"Password123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
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
