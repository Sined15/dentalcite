package pe.edu.dentalcite.cita.api;

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

import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-16 de extremo a extremo: «dejar constancia de si el paciente vino o no».
 *
 * <p>Las citas se siembran directamente en la base, como en
 * {@link CitasDelPacienteIntegrationTest} y por la misma razón: lo que hay que
 * cerrar es una cita <em>ya vencida</em>, y RN-05 impide reservarla.
 *
 * <p>Hay dos odontólogos porque la mitad de los criterios de esta historia
 * hablan de «la propia»: con uno solo no se puede distinguir el 403 del acierto.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CierreDeCitaIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final String PENDIENTES = "/api/v1/citas/pendientes-cierre";

    private MockMvc mockMvc;

    @Autowired private WebApplicationContext context;
    @Autowired private CitaRepository citaRepository;
    @Autowired private CitaHistorialRepository historialRepository;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private EspecialidadRepository especialidadRepository;
    @Autowired private ConsultorioRepository consultorioRepository;

    private UUID especialidadId;
    private UUID tratamientoId;

    private UUID luisId;
    private Ficha fichaLuis;
    private UUID luisUsuarioId;
    private UUID anaId;
    private Ficha fichaAna;
    private UUID anaUsuarioId;

    private Ficha fichaPaciente;
    private UUID pacienteUsuarioId;
    private UUID recepcionistaId;

    private Consultorio consultorio;

    private final List<UUID> citas = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("CIERRE-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TC-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de cierre")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        fichaLuis = nuevaFicha("Luis", "Perez");
        luisId = nuevoOdontologo(fichaLuis, "Luis", "Perez", especialidad);
        luisUsuarioId = nuevaCuenta("Luis Perez", "ODONTOLOGO", fichaLuis);

        fichaAna = nuevaFicha("Ana", "Quispe");
        anaId = nuevoOdontologo(fichaAna, "Ana", "Quispe", especialidad);
        anaUsuarioId = nuevaCuenta("Ana Quispe", "ODONTOLOGO", fichaAna);

        fichaPaciente = nuevaFicha("Julio", "Ramos");
        pacienteUsuarioId = nuevaCuenta("Julio Ramos", "PACIENTE", fichaPaciente);
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

    private UUID nuevoOdontologo(Ficha ficha, String nombres, String apellidos, Especialidad esp) {
        UUID id = UUID.randomUUID();
        odontologoRepository.save(Odontologo.builder()
                .id(id)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres(nombres).apellidos(apellidos)
                .ficha(ficha).activo(true).especialidades(Set.of(esp)).build());
        return id;
    }

    private UUID nuevaCuenta(String nombre, String rol, Ficha ficha) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .correo("hu16-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    /** @param terminoHace hace cuántas horas terminó; negativo para el futuro. */
    private Cita sembrar(UUID odontologoId, long terminoHace, String estado) {
        OffsetDateTime fin = OffsetDateTime.now(ZONA).minusHours(terminoHace);
        Cita cita = citaRepository.save(Cita.builder()
                .codigo("CC-" + UUID.randomUUID().toString().substring(0, 8))
                .ficha(fichaPaciente)
                .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                .consultorio(consultorio)
                .inicio(fin.minusMinutes(30)).fin(fin)
                .estado(estado)
                .build());
        citas.add(cita.getId());
        return cita;
    }

    @AfterEach
    void tearDown() {
        citas.forEach(id -> historialRepository.findByCitaIdOrderByOcurridoEnAsc(id)
                .forEach(historialRepository::delete));
        citas.forEach(citaRepository::deleteById);
        usuarioRepository.deleteById(recepcionistaId);
        usuarioRepository.deleteById(pacienteUsuarioId);
        usuarioRepository.deleteById(luisUsuarioId);
        usuarioRepository.deleteById(anaUsuarioId);
        odontologoRepository.deleteById(luisId);
        odontologoRepository.deleteById(anaId);
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(fichaPaciente.getId());
        fichaRepository.deleteById(fichaLuis.getId());
        fichaRepository.deleteById(fichaAna.getId());
        especialidadRepository.deleteById(especialidadId);
        citas.clear();
    }

    private ResultActions registrar(UUID cita, String resultado, UUID quien, String autoridad)
            throws Exception {
        return mockMvc.perform(patch("/api/v1/citas/" + cita + "/resultado")
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"resultado\":\"" + resultado + "\"}"));
    }

    private ResultActions pendientes(UUID quien, String autoridad) throws Exception {
        return mockMvc.perform(get(PENDIENTES)
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .param("size", "50"));
    }

    // ------------------------------------------------------------------
    // Criterios 1 y 2 · las dos transiciones, con su trazabilidad (RN-09)
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_atendida_dejaLaCitaAtendidaConSuTransicion() throws Exception {
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "ATENDIDA", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"))
                .andExpect(jsonPath("$.codigo").value(cita.getCodigo()));

        var bitacora = historialRepository.findByCitaIdOrderByOcurridoEnAsc(cita.getId());
        assertEquals(1, bitacora.size());
        assertEquals("CONFIRMADA", bitacora.get(0).getEstadoAnterior());
        assertEquals("ATENDIDA", bitacora.get(0).getEstadoNuevo());
        assertEquals(recepcionistaId, bitacora.get(0).getUsuario().getId());
        // «Y la marca temporal»: la pone la base al insertar.
        org.junit.jupiter.api.Assertions.assertNotNull(bitacora.get(0).getOcurridoEn());
    }

    @Test
    void registrarResultado_noAsistio_dejaLaCitaEnNoAsistioConLaMismaTrazabilidad() throws Exception {
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "NO_ASISTIO", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("NO_ASISTIO"));

        var bitacora = historialRepository.findByCitaIdOrderByOcurridoEnAsc(cita.getId());
        assertEquals("NO_ASISTIO", bitacora.get(0).getEstadoNuevo());
        assertEquals(recepcionistaId, bitacora.get(0).getUsuario().getId());
    }

    // ------------------------------------------------------------------
    // Criterio 3 · un estado final no se reescribe
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_sobreUnaCitaEnEstadoFinal_retornaConflict() throws Exception {
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CANCELADA);

        registrar(cita.getId(), "ATENDIDA", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isConflict());

        assertEquals(Cita.ESTADO_CANCELADA,
                citaRepository.findById(cita.getId()).orElseThrow().getEstado());
    }

    @Test
    void registrarResultado_dosVeces_laSegundaRetornaConflict() throws Exception {
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "ATENDIDA", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk());
        registrar(cita.getId(), "NO_ASISTIO", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // La cita tiene que haber terminado
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_sobreUnaCitaQueNoHaTerminado_retornaConflict() throws Exception {
        // Sacarla de CONFIRMADA la retiraria de la exclusion parcial de V13 y
        // dejaria libre una franja que todavia se va a ocupar.
        Cita cita = sembrar(luisId, -3, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "ATENDIDA", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isConflict());

        assertEquals(Cita.ESTADO_CONFIRMADA,
                citaRepository.findById(cita.getId()).orElseThrow().getEstado());
    }

    @Test
    void registrarResultado_conUnResultadoDesconocido_retornaBadRequest() throws Exception {
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "ATENDIDO", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isBadRequest());
    }

    @Test
    void registrarResultado_sobreUnaCitaInexistente_retornaNotFound() throws Exception {
        registrar(UUID.randomUUID(), "ATENDIDA", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Criterio 5 · RNF-04
    // ------------------------------------------------------------------

    @Test
    void registrarResultado_comoPacienteSobreLaSuya_retornaForbidden() throws Exception {
        // «Incluida la mia»: la cita es de su ficha y aun asi no puede cerrarla.
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "ATENDIDA", pacienteUsuarioId, "SCOPE_PACIENTE")
                .andExpect(status().isForbidden());

        assertEquals(Cita.ESTADO_CONFIRMADA,
                citaRepository.findById(cita.getId()).orElseThrow().getEstado());
    }

    @Test
    void registrarResultado_comoElOdontologoDeLaCita_laCierra() throws Exception {
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "ATENDIDA", luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ATENDIDA"));
    }

    @Test
    void registrarResultado_comoOtroOdontologo_retornaForbidden() throws Exception {
        // «(la propia)»: Ana no cierra las citas de Luis.
        Cita cita = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        registrar(cita.getId(), "ATENDIDA", anaUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isForbidden());

        assertEquals(Cita.ESTADO_CONFIRMADA,
                citaRepository.findById(cita.getId()).orElseThrow().getEstado());
    }

    // ------------------------------------------------------------------
    // Criterio 4 · el listado de pendientes de cierre (RF-22)
    // ------------------------------------------------------------------

    /**
     * Recepción ve la cola de toda la clínica, así que estas comprobaciones se
     * hacen sobre los códigos que la prueba siembra y no sobre el total: la
     * semilla de demostración deja una cita pendiente de cerrar, y contar filas
     * ataría el criterio de esta historia a cuántas traiga la semilla.
     */
    @Test
    void pendientesDeCierre_traeLasConfirmadasQueYaTerminaron() throws Exception {
        Cita vencida = sembrar(luisId, 3, Cita.ESTADO_CONFIRMADA);
        Cita porVenir = sembrar(luisId, -5, Cita.ESTADO_CONFIRMADA);   // todavía no ha pasado
        Cita cancelada = sembrar(luisId, 6, Cita.ESTADO_CANCELADA);    // ya tiene desenlace
        Cita atendida = sembrar(luisId, 9, Cita.ESTADO_ATENDIDA);      // ya tiene resultado

        pendientes(recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].codigo", hasItem(vencida.getCodigo())))
                .andExpect(jsonPath("$.content[*].codigo", not(hasItem(porVenir.getCodigo()))))
                .andExpect(jsonPath("$.content[*].codigo", not(hasItem(cancelada.getCodigo()))))
                .andExpect(jsonPath("$.content[*].codigo", not(hasItem(atendida.getCodigo()))))
                .andExpect(jsonPath("$.content[?(@.codigo == '" + vencida.getCodigo() + "')].estado")
                        .value(hasItem("CONFIRMADA")))
                .andExpect(jsonPath("$.content[?(@.codigo == '" + vencida.getCodigo()
                        + "')].paciente.numeroHistoria")
                        .value(hasItem(fichaPaciente.getNumeroHistoria())));
    }

    @Test
    void pendientesDeCierre_saleDeLaColaAlRegistrarSuResultado() throws Exception {
        // No hace falta comprobar «sin resultado» aparte: registrarlo la saca de
        // CONFIRMADA, y con eso de la cola.
        Cita cita = sembrar(luisId, 3, Cita.ESTADO_CONFIRMADA);

        pendientes(recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(jsonPath("$.content[*].codigo", hasItem(cita.getCodigo())));

        registrar(cita.getId(), "ATENDIDA", recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk());

        pendientes(recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(jsonPath("$.content[*].codigo", not(hasItem(cita.getCodigo()))));
    }

    @Test
    void pendientesDeCierre_elOdontologoVeSoloLasSuyas() throws Exception {
        Cita deLuis = sembrar(luisId, 3, Cita.ESTADO_CONFIRMADA);
        Cita deAna = sembrar(anaId, 4, Cita.ESTADO_CONFIRMADA);

        pendientes(luisUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].codigo").value(deLuis.getCodigo()));

        pendientes(anaUsuarioId, "SCOPE_ODONTOLOGO")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].codigo").value(deAna.getCodigo()));
    }

    @Test
    void pendientesDeCierre_recepcionVeLasDeTodos() throws Exception {
        // Lo que distingue a recepción del odontólogo es que ve las de los dos,
        // no cuántas hay: el odontólogo solo alcanzaría una de estas.
        Cita deLuis = sembrar(luisId, 3, Cita.ESTADO_CONFIRMADA);
        Cita deAna = sembrar(anaId, 4, Cita.ESTADO_CONFIRMADA);

        pendientes(recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].codigo", hasItem(deLuis.getCodigo())))
                .andExpect(jsonPath("$.content[*].codigo", hasItem(deAna.getCodigo())));
    }

    @Test
    void pendientesDeCierre_ordenaDeLaMasAntiguaALaMasReciente() throws Exception {
        // Es una cola de trabajo: lo que lleva mas tiempo sin cerrar va primero.
        Cita antigua = sembrar(luisId, 30, Cita.ESTADO_CONFIRMADA);
        Cita reciente = sembrar(luisId, 2, Cita.ESTADO_CONFIRMADA);

        // Una detrás de la otra, no en las posiciones 0 y 1: entre ambas puede
        // colarse la pendiente que trae la semilla, y el orden es lo que se
        // comprueba, no el sitio exacto que ocupan.
        pendientes(recepcionistaId, "SCOPE_RECEPCIONISTA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].codigo",
                        containsInRelativeOrder(antigua.getCodigo(), reciente.getCodigo())));
    }

    @Test
    void pendientesDeCierre_comoPaciente_retornaForbidden() throws Exception {
        pendientes(pacienteUsuarioId, "SCOPE_PACIENTE").andExpect(status().isForbidden());
    }
}
