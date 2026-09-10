package pe.edu.dentalcite.cita.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.test.web.servlet.ResultActions;
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
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-15 de extremo a extremo: «ver mis citas y cancelar la que ya no podré
 * atender».
 *
 * <p>Las citas se siembran directamente en la base en vez de reservarlas por la
 * API, porque los casos que interesan son justo los que la reserva no permite
 * crear: una cita ya pasada, una que empieza en doce horas y se reservó la
 * semana pasada, una de otro paciente. Reservarlas exigiría viajar en el tiempo.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CitasDelPacienteIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final String MIAS = "/api/v1/citas/mias";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Autowired private WebApplicationContext context;
    @Autowired private CitaRepository citaRepository;
    @Autowired private CitaHistorialRepository historialRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private ConsultorioRepository consultorioRepository;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private UUID especialidadId;
    private UUID tratamientoId;
    private UUID odontologoId;
    private Ficha fichaOdontologo;

    private UUID anaId;
    private Ficha fichaAna;
    private UUID brunoId;
    private Ficha fichaBruno;
    private UUID recepcionistaId;

    private Consultorio consultorio;

    private final List<UUID> citas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("MIAS-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TM-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de mis citas")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        fichaOdontologo = nuevaFicha("Odontologo", "De Mis Citas");
        odontologoId = UUID.randomUUID();
        odontologoRepository.save(Odontologo.builder()
                .id(odontologoId)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Luis").apellidos("Perez")
                .ficha(fichaOdontologo).activo(true)
                .especialidades(Set.of(especialidad)).build());

        fichaAna = nuevaFicha("Ana", "Torres");
        anaId = nuevaCuenta("Ana Torres", "PACIENTE", fichaAna);
        fichaBruno = nuevaFicha("Bruno", "Diaz");
        brunoId = nuevaCuenta("Bruno Diaz", "PACIENTE", fichaBruno);
        recepcionistaId = nuevaCuenta("Recepcion", "RECEPCIONISTA", null);

        consultorio = consultorioRepository.findByInoperativoFalse().get(0);
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        return fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres(nombres).apellidos(apellidos).build());
    }

    private UUID nuevaCuenta(String nombre, String rol, Ficha ficha) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .correo("hu15-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    /**
     * Siembra una cita saltándose la reserva, que es lo único que permite
     * construir los escenarios de RN-06.
     *
     * @param empiezaEnHoras dentro de cuánto empieza; negativo para el pasado
     * @param reservadaHaceHoras hace cuánto se pidió
     * @param autor quién la pidió, o {@code null} para una anterior a HU-14
     */
    private Cita sembrar(Ficha deQuien, long empiezaEnHoras, long reservadaHaceHoras,
            UUID autor, String estado) {
        OffsetDateTime inicio = OffsetDateTime.now(ZONA).plusHours(empiezaEnHoras);
        Cita cita = citaRepository.save(Cita.builder()
                .codigo("CM-" + UUID.randomUUID().toString().substring(0, 8))
                .ficha(deQuien)
                .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                .consultorio(consultorio)
                .inicio(inicio).fin(inicio.plusMinutes(30))
                .estado(estado)
                .creadoPor(autor == null ? null : usuarioRepository.findById(autor).orElseThrow())
                .build());
        citas.add(cita.getId());

        // `creado_en` lo pone @CreationTimestamp y la columna es `updatable =
        // false`, de modo que Hibernate no la incluye en ningún UPDATE: para
        // simular una reserva antigua hay que escribirla por debajo del mapeo.
        // Es deliberado que en producción no se pueda tocar —de ahí depende la
        // excepción de RN-06—, así que la trampa se queda aquí.
        jdbcTemplate.update("UPDATE citas SET creado_en = ? WHERE id = ?",
                java.sql.Timestamp.from(OffsetDateTime.now(ZONA)
                        .minusHours(reservadaHaceHoras).toInstant()),
                cita.getId());
        return citaRepository.findById(cita.getId()).orElseThrow();
    }

    @AfterEach
    void tearDown() {
        citas.forEach(id -> historialRepository.findByCitaIdOrderByOcurridoEnAsc(id)
                .forEach(historialRepository::delete));
        citas.forEach(citaRepository::deleteById);
        usuarioRepository.deleteById(recepcionistaId);
        usuarioRepository.deleteById(anaId);
        usuarioRepository.deleteById(brunoId);
        odontologoRepository.deleteById(odontologoId);
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(fichaAna.getId());
        fichaRepository.deleteById(fichaBruno.getId());
        fichaRepository.deleteById(fichaOdontologo.getId());
        especialidadRepository.deleteById(especialidadId);
        citas.clear();
    }

    private ResultActions misCitas(UUID quien, String autoridad) throws Exception {
        return mockMvc.perform(get(MIAS)
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .param("size", "50"));
    }

    private ResultActions cancelar(UUID cita, UUID quien, String autoridad) throws Exception {
        return mockMvc.perform(patch("/api/v1/citas/" + cita + "/cancelar")
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"motivo\":\"Ya no podre asistir\"}"));
    }

    // ------------------------------------------------------------------
    // Criterio 1 · «las futuras y las pasadas, y ninguna de otro paciente»
    // ------------------------------------------------------------------

    @Test
    void misCitas_devuelveLasFuturasYLasPasadasConSuEstado() throws Exception {
        sembrar(fichaAna, 48, 72, anaId, Cita.ESTADO_CONFIRMADA);
        sembrar(fichaAna, -240, 300, anaId, "ATENDIDA");
        sembrar(fichaAna, -100, 200, anaId, Cita.ESTADO_CANCELADA);

        misCitas(anaId, "SCOPE_PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // Orden por defecto: de la más reciente a la más antigua.
                .andExpect(jsonPath("$.content[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.content[0].tratamiento.nombre").exists())
                .andExpect(jsonPath("$.content[0].odontologo.nombre").exists());
    }

    @Test
    void misCitas_noDevuelveLasDeOtroPaciente() throws Exception {
        sembrar(fichaAna, 48, 72, anaId, Cita.ESTADO_CONFIRMADA);
        // A otra hora: las dos son CONFIRMADA del mismo odontólogo, y la
        // restricción de exclusión de V13 no las dejaría solaparse.
        sembrar(fichaBruno, 50, 72, brunoId, Cita.ESTADO_CONFIRMADA);

        misCitas(anaId, "SCOPE_PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].paciente.numeroHistoria")
                        .value(fichaAna.getNumeroHistoria()));
    }

    @Test
    void misCitas_admiteFiltrarPorEstado() throws Exception {
        sembrar(fichaAna, 48, 72, anaId, Cita.ESTADO_CONFIRMADA);
        sembrar(fichaAna, -240, 300, anaId, "ATENDIDA");

        mockMvc.perform(get(MIAS)
                        .with(user(anaId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                        .param("estado", "ATENDIDA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].estado").value("ATENDIDA"));
    }

    @Test
    void misCitas_comoRecepcionista_retornaForbidden() throws Exception {
        // La agenda de la clínica es GET /api/v1/citas; esta ruta es del paciente.
        misCitas(recepcionistaId, "SCOPE_RECEPCIONISTA").andExpect(status().isForbidden());
    }

    @Test
    void misCitas_marcaCadaFilaConSiElPacientePuedeCancelarla() throws Exception {
        // RN-06 calculada por el servidor: es lo que el portal obedece para pintar
        // el botón, en vez de reimplementar la regla con otro reloj.
        sembrar(fichaAna, 48, 72, anaId, Cita.ESTADO_CONFIRMADA);

        misCitas(anaId, "SCOPE_PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].cancelablePorPaciente").value(true));
    }

    @Test
    void misCitas_marcaComoNoCancelableLaQueEstaFueraDeVentana() throws Exception {
        sembrar(fichaAna, 12, 24 * 7, anaId, Cita.ESTADO_CONFIRMADA);

        misCitas(anaId, "SCOPE_PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].cancelablePorPaciente").value(false));
    }

    // ------------------------------------------------------------------
    // Criterio 2 · cancelar con más de veinticuatro horas
    // ------------------------------------------------------------------

    @Test
    void cancelar_miCitaAMasDeVeinticuatroHoras_laCancelaYRegistraLaTransicion() throws Exception {
        Cita cita = sembrar(fichaAna, 48, 72, anaId, Cita.ESTADO_CONFIRMADA);

        cancelar(cita.getId(), anaId, "SCOPE_PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));

        // RF-21: la transición queda con fecha, usuario y motivo.
        var bitacora = historialRepository.findByCitaIdOrderByOcurridoEnAsc(cita.getId());
        org.junit.jupiter.api.Assertions.assertEquals(1, bitacora.size());
        org.junit.jupiter.api.Assertions.assertEquals("CANCELADA", bitacora.get(0).getEstadoNuevo());
        org.junit.jupiter.api.Assertions.assertEquals(anaId, bitacora.get(0).getUsuario().getId());
        org.junit.jupiter.api.Assertions.assertEquals("Ya no podre asistir", bitacora.get(0).getMotivo());
    }

    // ------------------------------------------------------------------
    // Criterio 3 · el 422 de RN-06
    // ------------------------------------------------------------------

    @Test
    void cancelar_miCitaAdoceHorasReservadaLaSemanaPasada_retorna422() throws Exception {
        Cita cita = sembrar(fichaAna, 12, 24 * 7, anaId, Cita.ESTADO_CONFIRMADA);

        cancelar(cita.getId(), anaId, "SCOPE_PACIENTE")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("recepción")));
    }

    @Test
    void cancelar_esaMismaCita_siLaCancelaRecepcion_seCancelaSinVentana() throws Exception {
        // HU-11 no cambia: el mostrador atiende precisamente la cancelación de
        // última hora que el paciente ya no puede hacer.
        Cita cita = sembrar(fichaAna, 12, 24 * 7, anaId, Cita.ESTADO_CONFIRMADA);

        cancelar(cita.getId(), recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    // ------------------------------------------------------------------
    // Criterio 4 · la excepción: lo que uno reserva, uno lo deshace
    // ------------------------------------------------------------------

    @Test
    void cancelar_laQueReserveHaceUnaHoraYEmpiezaEnTres_seCancela() throws Exception {
        Cita cita = sembrar(fichaAna, 3, 1, anaId, Cita.ESTADO_CONFIRMADA);

        cancelar(cita.getId(), anaId, "SCOPE_PACIENTE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelar_laQueReservoRecepcionHaceUnaHora_retorna422() throws Exception {
        // La excepción alcanza solo a «la cita que él mismo reservó».
        Cita cita = sembrar(fichaAna, 3, 1, recepcionistaId, Cita.ESTADO_CONFIRMADA);

        cancelar(cita.getId(), anaId, "SCOPE_PACIENTE")
                .andExpect(status().isUnprocessableEntity());
    }

    // ------------------------------------------------------------------
    // Criterio 5 · RNF-04, sin revelar que la cita existe
    // ------------------------------------------------------------------

    @Test
    void cancelar_laCitaDeOtroPaciente_retornaForbidden() throws Exception {
        Cita deBruno = sembrar(fichaBruno, 48, 72, brunoId, Cita.ESTADO_CONFIRMADA);

        cancelar(deBruno.getId(), anaId, "SCOPE_PACIENTE")
                .andExpect(status().isForbidden());

        // Y sigue confirmada: el 403 no es cosmético.
        org.junit.jupiter.api.Assertions.assertEquals(Cita.ESTADO_CONFIRMADA,
                citaRepository.findById(deBruno.getId()).orElseThrow().getEstado());
    }

    @Test
    void cancelar_unaCitaInexistente_retornaElMismoForbidden() throws Exception {
        // Si esto fuera 404, comparar respuestas diría qué identificadores
        // corresponden a citas reales.
        cancelar(UUID.randomUUID(), anaId, "SCOPE_PACIENTE")
                .andExpect(status().isForbidden());
    }

    @Test
    void cancelar_unaCitaInexistente_paraRecepcion_sigueSiendoNotFound() throws Exception {
        // Recepción puede verlas todas, así que ocultarle la diferencia no
        // protegería nada y solo estorbaría al diagnóstico.
        cancelar(UUID.randomUUID(), recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // RN-09 sigue mandando
    // ------------------------------------------------------------------

    @Test
    void cancelar_miCitaYaCancelada_retornaConflict() throws Exception {
        // 409 y no 422: lo que pasa es que ya está cancelada, no que la ventana
        // se haya cerrado.
        Cita cita = sembrar(fichaAna, 48, 72, anaId, Cita.ESTADO_CANCELADA);

        cancelar(cita.getId(), anaId, "SCOPE_PACIENTE")
                .andExpect(status().isConflict());
    }
}
