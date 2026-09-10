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
import pe.edu.dentalcite.cita.api.dto.CitaRequestDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
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
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-14 · RF-17 de extremo a extremo: reservar en nombre de un paciente.
 *
 * <p>Aparte de {@link ReservaIntegrationTest}, que es HU-09 —el paciente para sí
 * mismo— y sigue siendo el contrato que esta historia no puede romper. Lo que se
 * comprueba aquí es lo que cambia: de quién es la cita, quién la encargó y quién
 * tiene permitido decirlo.
 *
 * <p>La concurrencia no se reprueba: el camino de escritura es el mismo que ya
 * cubre {@code ExclusionMutuaIntegrationTest}, y el tercer criterio —«dos
 * recepcionistas a la vez, una sola cita»— lo garantizan las restricciones de
 * exclusión de {@code V13}, que no distinguen quién reserva.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReservaDesdeRecepcionIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final String RUTA = "/api/v1/citas";

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
    @Autowired private ConsultorioRepository consultorioRepository;

    private UUID especialidadId;
    private UUID tratamientoId;
    private UUID odontologoId;
    private Ficha fichaOdontologo;

    /** Quien atiende el mostrador: su cuenta no tiene ficha propia. */
    private UUID recepcionistaId;
    /** Paciente con cuenta, el del portal. */
    private UUID pacienteConCuentaId;
    private Ficha fichaConCuenta;
    /** Ficha dada de alta presencialmente (HU-12): existe sin credenciales. */
    private Ficha fichaSinCuenta;

    private LocalDate lunes;

    private final List<UUID> citasSembradas = new ArrayList<>();
    private final List<UUID> horariosSembrados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        lunes = SembradorDeAgenda.proximoLunes();

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("RECEPCION-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TR-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de recepcion")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        fichaOdontologo = nuevaFicha("Odontologo", "De Recepcion");
        odontologoId = UUID.randomUUID();
        Odontologo odontologo = odontologoRepository.save(Odontologo.builder()
                .id(odontologoId)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Luis").apellidos("Perez")
                .ficha(fichaOdontologo).activo(true)
                .especialidades(Set.of(especialidad)).build());

        horariosSembrados.add(horarioRepository.save(HorarioAtencion.builder()
                .odontologo(odontologo).diaSemana(1)
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(13, 0)).build()).getId());

        fichaConCuenta = nuevaFicha("Ana", "Torres");
        pacienteConCuentaId = nuevaCuenta("Ana Torres", "PACIENTE", fichaConCuenta);

        // RF-06: el alta presencial crea la ficha sin credenciales de acceso.
        fichaSinCuenta = nuevaFicha("Julio", "Ramos");

        recepcionistaId = nuevaCuenta("Recepcion Central", "RECEPCIONISTA", null);
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
                .correo("hu14-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    @AfterEach
    void tearDown() {
        List<UUID> fichas = List.of(fichaConCuenta.getId(), fichaSinCuenta.getId());
        citaRepository.findAll().stream()
                .filter(c -> fichas.contains(c.getFicha().getId()))
                .map(Cita::getId)
                .forEach(citasSembradas::add);
        citasSembradas.stream().distinct().forEach(citaRepository::deleteById);
        horariosSembrados.forEach(horarioRepository::deleteById);
        usuarioRepository.deleteById(recepcionistaId);
        usuarioRepository.deleteById(pacienteConCuentaId);
        odontologoRepository.deleteById(odontologoId);
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(fichaConCuenta.getId());
        fichaRepository.deleteById(fichaSinCuenta.getId());
        fichaRepository.deleteById(fichaOdontologo.getId());
        especialidadRepository.deleteById(especialidadId);
        citasSembradas.clear();
        horariosSembrados.clear();
    }

    // ------------------------------------------------------------------
    // Utilidades de petición
    // ------------------------------------------------------------------

    private CitaRequestDTO peticion(UUID pacienteId, LocalTime hora) {
        return CitaRequestDTO.builder()
                .pacienteId(pacienteId)
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(lunes).hora(hora).build();
    }

    private ResultActions reservar(UUID quien, String autoridad, CitaRequestDTO cuerpo) throws Exception {
        return mockMvc.perform(post(RUTA)
                .with(user(quien.toString()).authorities(new SimpleGrantedAuthority(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private Cita citaDe(Ficha ficha) {
        return citaRepository.findAll().stream()
                .filter(c -> c.getFicha().getId().equals(ficha.getId()))
                .findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------
    // Criterio 1 · nace CONFIRMADA y queda registrado quién la creó (RN-09)
    // ------------------------------------------------------------------

    @Test
    void reservar_comoRecepcionistaEnNombreDeUnPaciente_creaLaCitaConfirmada() throws Exception {
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaConCuenta.getId(), LocalTime.of(9, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").exists())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.fecha").value(lunes.toString()))
                .andExpect(jsonPath("$.hora").value("09:00:00"))
                .andExpect(jsonPath("$.consultorio.nombre").exists());
    }

    @Test
    void reservar_comoRecepcionista_dejaLaCitaAnombreDelPacienteYnoDelRecepcionista() throws Exception {
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaConCuenta.getId(), LocalTime.of(9, 0)))
                .andExpect(status().isCreated());

        Cita cita = citaDe(fichaConCuenta);
        assertEquals(fichaConCuenta.getId(), cita.getFicha().getId(), "la cita es del paciente");
        assertNotNull(cita.getCreadoPor(), "RF-17: debe constar quién la creó");
        assertEquals(recepcionistaId, cita.getCreadoPor().getId(), "la encargó el mostrador");
    }

    @Test
    void reservar_comoAdministrador_tambienReservaEnNombreDeOtro() throws Exception {
        UUID administradorId = nuevaCuenta("Admin", "ADMINISTRADOR", null);
        try {
            reservar(administradorId, "SCOPE_ADMINISTRADOR", peticion(fichaConCuenta.getId(), LocalTime.of(10, 0)))
                    .andExpect(status().isCreated());
            assertEquals(administradorId, citaDe(fichaConCuenta).getCreadoPor().getId());
        } finally {
            citaRepository.findAll().stream()
                    .filter(c -> c.getFicha().getId().equals(fichaConCuenta.getId()))
                    .map(Cita::getId).forEach(citasSembradas::add);
            citasSembradas.stream().distinct().forEach(citaRepository::deleteById);
            citasSembradas.clear();
            usuarioRepository.deleteById(administradorId);
        }
    }

    // ------------------------------------------------------------------
    // Criterio 2 · el paciente sin cuenta de acceso
    // ------------------------------------------------------------------

    @Test
    void reservar_paraUnPacienteSinCuentaDeAcceso_seCompletaIgual() throws Exception {
        // Es el caso que motiva la historia: HU-12 dio de alta la ficha sin
        // credenciales, y esa persona tiene que poder tener cita.
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaSinCuenta.getId(), LocalTime.of(9, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"));

        assertEquals(fichaSinCuenta.getId(), citaDe(fichaSinCuenta).getFicha().getId());
    }

    // ------------------------------------------------------------------
    // Criterio 4 · RNF-04
    // ------------------------------------------------------------------

    @Test
    void reservar_comoPacienteIndicandoLaFichaDeOtro_retornaForbidden() throws Exception {
        reservar(pacienteConCuentaId, "SCOPE_PACIENTE", peticion(fichaSinCuenta.getId(), LocalTime.of(9, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservar_comoPacienteIndicandoSuPropiaFicha_tambienRetornaForbidden() throws Exception {
        // Si coincidir bastara, probar identificadores diría cuál es el de otro.
        reservar(pacienteConCuentaId, "SCOPE_PACIENTE", peticion(fichaConCuenta.getId(), LocalTime.of(9, 0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void reservar_comoPacienteSinIndicarFicha_sigueReservandoParaSiMismo() throws Exception {
        // HU-09 no puede romperse: el contrato del portal es el mismo de antes.
        reservar(pacienteConCuentaId, "SCOPE_PACIENTE", peticion(null, LocalTime.of(9, 0)))
                .andExpect(status().isCreated());

        Cita cita = citaDe(fichaConCuenta);
        assertEquals(pacienteConCuentaId, cita.getCreadoPor().getId(), "la reservó él mismo");
    }

    @Test
    void reservar_comoOdontologo_retornaForbidden() throws Exception {
        UUID odontologoUsuarioId = nuevaCuenta("Dr Perez", "ODONTOLOGO", fichaOdontologo);
        try {
            reservar(odontologoUsuarioId, "SCOPE_ODONTOLOGO", peticion(fichaConCuenta.getId(), LocalTime.of(9, 0)))
                    .andExpect(status().isForbidden());
        } finally {
            usuarioRepository.deleteById(odontologoUsuarioId);
        }
    }

    // ------------------------------------------------------------------
    // Casos de cuerpo
    // ------------------------------------------------------------------

    @Test
    void reservar_comoRecepcionistaSinIndicarPaciente_retornaBadRequest() throws Exception {
        // No es 403: el rol puede reservar, lo que falta es para quién.
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(null, LocalTime.of(9, 0)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservar_conUnPacienteQueNoExiste_retornaNotFound() throws Exception {
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(UUID.randomUUID(), LocalTime.of(9, 0)))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Las reglas de HU-09 siguen valiendo cuando reserva recepción
    // ------------------------------------------------------------------

    @Test
    void reservar_desdeRecepcion_respetaLaCuotaDelPacienteYNoLaDeQuienReserva() throws Exception {
        // RN-07 cuenta las citas activas del paciente: es suya la cuota, aunque la
        // pida el mostrador.
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaConCuenta.getId(), LocalTime.of(9, 0)))
                .andExpect(status().isCreated());
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaConCuenta.getId(), LocalTime.of(10, 0)))
                .andExpect(status().isCreated());
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaConCuenta.getId(), LocalTime.of(11, 0)))
                .andExpect(status().isCreated());

        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaConCuenta.getId(), LocalTime.of(12, 0)))
                .andExpect(status().isConflict());

        // Y la de otro paciente entra sin problema: la cuota no era del mostrador.
        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", peticion(fichaSinCuenta.getId(), LocalTime.of(12, 0)))
                .andExpect(status().isCreated());
    }

    @Test
    void reservar_desdeRecepcionFueraDeLaVentana_sigueDando422() throws Exception {
        // RN-05 no depende de quién reserve.
        CitaRequestDTO lejana = CitaRequestDTO.builder()
                .pacienteId(fichaConCuenta.getId())
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(lunes.plusDays(120)).hora(LocalTime.of(9, 0)).build();

        reservar(recepcionistaId, "SCOPE_RECEPCIONISTA", lejana)
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reservar_desdeElPortal_dejaSinAutorLasCitasAnterioresAEstaHistoria() throws Exception {
        // La columna admite nulo: una cita sembrada antes de HU-14 no tiene autor,
        // y eso no es un dato perdido sino su fecha de nacimiento.
        Cita anterior = citaRepository.save(Cita.builder()
                .codigo("CIT-HU14-" + UUID.randomUUID().toString().substring(0, 4))
                .ficha(fichaSinCuenta)
                .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                .consultorio(consultorioRepository.findByInoperativoFalse().get(0))
                .inicio(lunes.minusDays(30).atTime(9, 0).atZone(ZONA).toOffsetDateTime())
                .fin(lunes.minusDays(30).atTime(9, 30).atZone(ZONA).toOffsetDateTime())
                .estado(Cita.ESTADO_CONFIRMADA).build());
        citasSembradas.add(anterior.getId());

        assertNull(citaRepository.findById(anterior.getId()).orElseThrow().getCreadoPor());
    }
}
