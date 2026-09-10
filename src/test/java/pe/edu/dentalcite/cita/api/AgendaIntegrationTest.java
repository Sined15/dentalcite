package pe.edu.dentalcite.cita.api;

import com.fasterxml.jackson.databind.JsonNode;
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
import pe.edu.dentalcite.cita.api.dto.CancelacionRequestDTO;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-11 de extremo a extremo contra PostgreSQL y Redis reales: la agenda que ve
 * recepción (RF-18), la cancelación sin ventana (RF-20) y la bitácora (RF-21).
 *
 * <p>La prueba que más aporta es
 * {@link #cancelar_devuelveLaFranjaALaOfertaDeInmediato()}: es la única que
 * ejercita de verdad la invalidación de la caché de franjas, que es lo que hace
 * cierto el «de inmediato» de RN-06.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class AgendaIntegrationTest {

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

    private UUID pacienteUsuarioId;
    private UUID recepcionUsuarioId;
    private UUID tratamientoId;
    private UUID odontologoId;
    private UUID otroOdontologoId;
    private UUID especialidadId;
    private Ficha fichaPaciente;
    private Ficha fichaOdontologo;
    private Ficha fichaOtroOdontologo;
    private LocalDate lunes;

    private final List<UUID> horariosSembrados = new ArrayList<>();

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
                .nombre("AGENDA-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TA-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de agenda")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        fichaOdontologo = nuevaFicha("Odontologo", "De Agenda");
        odontologoId = sembrarOdontologo(fichaOdontologo, "Luis", "Perez", especialidad);
        fichaOtroOdontologo = nuevaFicha("Otro", "Odontologo");
        otroOdontologoId = sembrarOdontologo(fichaOtroOdontologo, "Marta", "Diaz", especialidad);

        fichaPaciente = nuevaFicha("Ana", "Torres");
        pacienteUsuarioId = sembrarUsuario("Ana Torres", "PACIENTE", fichaPaciente);
        recepcionUsuarioId = sembrarUsuario("Recepcion de Agenda", "RECEPCIONISTA", null);
    }

    private UUID sembrarOdontologo(Ficha ficha, String nombres, String apellidos,
            Especialidad especialidad) {
        UUID id = UUID.randomUUID();
        Odontologo odontologo = odontologoRepository.save(Odontologo.builder()
                .id(id)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres(nombres).apellidos(apellidos)
                .ficha(ficha).activo(true)
                .especialidades(Set.of(especialidad)).build());
        horariosSembrados.add(horarioRepository.save(HorarioAtencion.builder()
                .odontologo(odontologo).diaSemana(1)
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(13, 0)).build()).getId());
        return id;
    }

    private UUID sembrarUsuario(String nombre, String rol, Ficha ficha) {
        return usuarioRepository.save(Usuario.builder()
                .nombre(nombre)
                .correo("agenda-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        return fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres(nombres).apellidos(apellidos).build());
    }

    @AfterEach
    void tearDown() {
        // El historial cae con la cita: su clave ajena es CASCADE precisamente
        // porque una transicion de una cita inexistente no significa nada.
        citaRepository.findAll().stream()
                .filter(c -> c.getFicha().getId().equals(fichaPaciente.getId()))
                .map(Cita::getId)
                .toList()
                .forEach(citaRepository::deleteById);
        horariosSembrados.forEach(horarioRepository::deleteById);
        usuarioRepository.deleteById(pacienteUsuarioId);
        usuarioRepository.deleteById(recepcionUsuarioId);
        odontologoRepository.deleteById(odontologoId);
        odontologoRepository.deleteById(otroOdontologoId);
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(fichaPaciente.getId());
        fichaRepository.deleteById(fichaOdontologo.getId());
        fichaRepository.deleteById(fichaOtroOdontologo.getId());
        especialidadRepository.deleteById(especialidadId);
        horariosSembrados.clear();
    }

    // ------------------------------------------------------------------
    // Utilidades de petición
    // ------------------------------------------------------------------

    private SimpleGrantedAuthority rol(String autoridad) {
        return new SimpleGrantedAuthority(autoridad);
    }

    /** Reserva como paciente y devuelve el identificador de la cita creada. */
    private UUID reservar(UUID odontologo, LocalTime hora) throws Exception {
        CitaRequestDTO cuerpo = CitaRequestDTO.builder()
                .tratamientoId(tratamientoId).odontologoId(odontologo)
                .fecha(lunes).hora(hora).build();
        String respuesta = mockMvc.perform(post(RUTA)
                        .with(user(pacienteUsuarioId.toString()).authorities(rol("SCOPE_PACIENTE")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cuerpo)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(respuesta).path("id").asText());
    }

    private ResultActions consultarAgenda(String autoridad, UUID usuarioId,
            String... parametrosExtra) throws Exception {
        var peticion = get(RUTA)
                .with(user(usuarioId.toString()).authorities(rol(autoridad)))
                .param("desde", lunes.toString())
                .param("hasta", lunes.toString());
        for (int i = 0; i < parametrosExtra.length; i += 2) {
            peticion = peticion.param(parametrosExtra[i], parametrosExtra[i + 1]);
        }
        return mockMvc.perform(peticion);
    }

    private ResultActions consultarAgenda(String... parametrosExtra) throws Exception {
        return consultarAgenda("SCOPE_RECEPCIONISTA", recepcionUsuarioId, parametrosExtra);
    }

    private ResultActions cancelar(UUID citaId, String motivo, String autoridad,
            UUID usuarioId) throws Exception {
        CancelacionRequestDTO cuerpo = CancelacionRequestDTO.builder().motivo(motivo).build();
        return mockMvc.perform(patch(RUTA + "/" + citaId + "/cancelar")
                .with(user(usuarioId.toString()).authorities(rol(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private ResultActions cancelar(UUID citaId, String motivo) throws Exception {
        return cancelar(citaId, motivo, "SCOPE_RECEPCIONISTA", recepcionUsuarioId);
    }

    /** Las horas que la disponibilidad ofrece ese lunes para el odontólogo dado. */
    private List<String> iniciosOfrecidos(UUID odontologo) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/disponibilidad")
                        .with(user(pacienteUsuarioId.toString()).authorities(rol("SCOPE_PACIENTE")))
                        .param("tratamientoId", tratamientoId.toString())
                        .param("odontologoId", odontologo.toString())
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> horas = new ArrayList<>();
        JsonNode dias = objectMapper.readTree(cuerpo).path("odontologos").path(0).path("dias");
        for (JsonNode dia : dias) {
            if (lunes.toString().equals(dia.path("fecha").asText())) {
                dia.path("inicios").forEach(h -> horas.add(h.asText()));
            }
        }
        return horas;
    }

    // ------------------------------------------------------------------
    // RF-18 · la agenda del día
    // ------------------------------------------------------------------

    @Test
    void consultarAgenda_devuelveCadaCitaConSuPacienteHoraYConsultorio() throws Exception {
        reservar(odontologoId, LocalTime.of(9, 0));

        consultarAgenda()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].paciente.nombre").value("Ana Torres"))
                .andExpect(jsonPath("$.content[0].paciente.numeroHistoria").exists())
                .andExpect(jsonPath("$.content[0].hora").value("09:00:00"))
                .andExpect(jsonPath("$.content[0].fecha").value(lunes.toString()))
                .andExpect(jsonPath("$.content[0].consultorio.nombre").exists())
                .andExpect(jsonPath("$.content[0].odontologo.nombre").value("Luis Perez"))
                .andExpect(jsonPath("$.content[0].estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.content[0].zonaHoraria").value("America/Lima"));
    }

    @Test
    void consultarAgenda_ordenaLasCitasPorHora() throws Exception {
        // Se reservan al reves para que el orden no salga por casualidad.
        reservar(odontologoId, LocalTime.of(11, 0));
        reservar(odontologoId, LocalTime.of(9, 30));
        reservar(odontologoId, LocalTime.of(10, 0));

        consultarAgenda()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].hora").value("09:30:00"))
                .andExpect(jsonPath("$.content[1].hora").value("10:00:00"))
                .andExpect(jsonPath("$.content[2].hora").value("11:00:00"));
    }

    @Test
    void consultarAgenda_filtraPorOdontologo() throws Exception {
        reservar(odontologoId, LocalTime.of(9, 0));
        reservar(otroOdontologoId, LocalTime.of(10, 0));

        consultarAgenda("odontologoId", odontologoId.toString())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].odontologo.nombre").value("Luis Perez"));
    }

    @Test
    void consultarAgenda_filtraPorEstado() throws Exception {
        UUID cancelada = reservar(odontologoId, LocalTime.of(9, 0));
        reservar(odontologoId, LocalTime.of(10, 0));
        cancelar(cancelada, "El paciente reprograma").andExpect(status().isOk());

        consultarAgenda("estado", "CONFIRMADA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].hora").value("10:00:00"));

        consultarAgenda("estado", "CANCELADA")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].motivoCancelacion").value("El paciente reprograma"));
    }

    @Test
    void consultarAgenda_incluyeLaCitaDeUltimaHoraDelDia() throws Exception {
        // El extremo superior del rango se abre al dia siguiente: si se comparase
        // contra el inicio del ultimo dia, esta cita quedaria fuera.
        reservar(odontologoId, LocalTime.of(12, 30));

        consultarAgenda()
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].hora").value("12:30:00"));
    }

    @Test
    void consultarAgenda_rangoInvertido_devuelve400() throws Exception {
        mockMvc.perform(get(RUTA)
                        .with(user(recepcionUsuarioId.toString()).authorities(rol("SCOPE_RECEPCIONISTA")))
                        .param("desde", lunes.plusDays(1).toString())
                        .param("hasta", lunes.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultarAgenda_estadoDesconocido_devuelve400() throws Exception {
        consultarAgenda("estado", "PENDIENTE").andExpect(status().isBadRequest());
    }

    @Test
    void consultarAgenda_sinFechas_devuelve400() throws Exception {
        mockMvc.perform(get(RUTA)
                        .with(user(recepcionUsuarioId.toString()).authorities(rol("SCOPE_RECEPCIONISTA"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void consultarAgenda_comoAdministrador_devuelve200() throws Exception {
        consultarAgenda("SCOPE_ADMINISTRADOR", recepcionUsuarioId).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // RF-20 · la cancelación
    // ------------------------------------------------------------------

    @Test
    void cancelar_dejaLaCitaEnCanceladaConservandoSuFila() throws Exception {
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));
        String codigo = citaRepository.findById(citaId).orElseThrow().getCodigo();

        cancelar(citaId, "El paciente reprograma")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"))
                .andExpect(jsonPath("$.codigo").value(codigo));

        // RN-12: la fila sigue ahi, entera.
        Cita cita = citaRepository.findById(citaId).orElseThrow();
        assertEquals("CANCELADA", cita.getEstado());
        assertEquals("El paciente reprograma", cita.getMotivoCancelacion());
        assertEquals(codigo, cita.getCodigo());
        assertNotNull(cita.getInicio());
    }

    @Test
    void cancelar_dentroDeLasVeinticuatroHoras_seCancelaIgual() throws Exception {
        // Recepcion no tiene ventana. Se fabrica una cita que empieza en una hora
        // moviendola en la base: reservarla seria imposible, porque RN-05 exige
        // dos horas de antelacion al *reservar*, no al cancelar.
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));
        Cita cita = citaRepository.findById(citaId).orElseThrow();
        cita.setInicio(java.time.OffsetDateTime.now().plusHours(1));
        cita.setFin(java.time.OffsetDateTime.now().plusHours(1).plusMinutes(30));
        citaRepository.save(cita);

        cancelar(citaId, "El paciente avisa que no llega")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("CANCELADA"));
    }

    @Test
    void cancelar_sinMotivo_devuelve400() throws Exception {
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));

        cancelar(citaId, null).andExpect(status().isBadRequest());
        cancelar(citaId, "   ").andExpect(status().isBadRequest());

        // Y no cambio nada.
        assertEquals("CONFIRMADA", citaRepository.findById(citaId).orElseThrow().getEstado());
    }

    @Test
    void cancelar_citaYaCancelada_devuelve409() throws Exception {
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));
        cancelar(citaId, "El paciente reprograma").andExpect(status().isOk());

        cancelar(citaId, "Otra vez").andExpect(status().isConflict());
    }

    @Test
    void cancelar_citaInexistente_devuelve404() throws Exception {
        cancelar(UUID.randomUUID(), "Motivo").andExpect(status().isNotFound());
    }

    @Test
    void cancelar_devuelveLaFranjaALaOfertaDeInmediato() throws Exception {
        // RN-06 y el criterio de HU-11. Es la prueba que ejercita de verdad la
        // invalidacion de la cache de franjas: sin ella, la consulta seguiria
        // sirviendo la respuesta cacheada hasta que caducase su TTL.
        assertTrue(iniciosOfrecidos(odontologoId).contains("09:00:00"));

        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));
        assertFalse(iniciosOfrecidos(odontologoId).contains("09:00:00"),
                "la franja reservada no puede seguir ofreciendose");

        cancelar(citaId, "El paciente reprograma").andExpect(status().isOk());

        assertTrue(iniciosOfrecidos(odontologoId).contains("09:00:00"),
                "la franja cancelada debe volver a ofrecerse de inmediato (RN-06)");
    }

    @Test
    void cancelar_liberaLaFranjaParaOtraReserva() throws Exception {
        // La restriccion de exclusion de V13 es parcial sobre CONFIRMADA: en
        // cuanto la cita se cancela deja de estorbar, sin ningun trabajo extra.
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));
        cancelar(citaId, "El paciente reprograma").andExpect(status().isOk());

        UUID nueva = reservar(odontologoId, LocalTime.of(9, 0));
        assertEquals("CONFIRMADA", citaRepository.findById(nueva).orElseThrow().getEstado());
    }

    @Test
    void cancelar_noConsumeLaCuotaDeRn07() throws Exception {
        // RN-07 cuenta activas, y una cancelada no lo es: tras cancelar se puede
        // volver a reservar aunque se hubiera llegado al tope.
        UUID primera = reservar(odontologoId, LocalTime.of(9, 0));
        reservar(odontologoId, LocalTime.of(9, 30));
        reservar(odontologoId, LocalTime.of(10, 0));

        cancelar(primera, "El paciente reprograma").andExpect(status().isOk());

        UUID cuarta = reservar(odontologoId, LocalTime.of(10, 30));
        assertNotNull(cuarta);
    }

    // ------------------------------------------------------------------
    // RF-21 · la bitácora
    // ------------------------------------------------------------------

    @Test
    void historial_registraLaTransicionConFechaResponsableYMotivo() throws Exception {
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));
        cancelar(citaId, "El paciente reprograma").andExpect(status().isOk());

        mockMvc.perform(get(RUTA + "/" + citaId + "/historial")
                        .with(user(recepcionUsuarioId.toString()).authorities(rol("SCOPE_RECEPCIONISTA"))))
                .andExpect(status().isOk())
                // Solo la cancelacion: el alta no se registra, porque su fila hija
                // bloqueaba la cita recien insertada y provocaba interbloqueos en
                // el camino disputado de HU-10. Cuando nacio la cita lo dice
                // `citas.creado_en`; quien la cancelo, solo esto.
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estadoAnterior").value("CONFIRMADA"))
                .andExpect(jsonPath("$[0].estadoNuevo").value("CANCELADA"))
                .andExpect(jsonPath("$[0].motivo").value("El paciente reprograma"))
                .andExpect(jsonPath("$[0].ocurridoEn").exists())
                .andExpect(jsonPath("$[0].responsable.id").value(recepcionUsuarioId.toString()))
                .andExpect(jsonPath("$[0].responsable.rol").value("RECEPCIONISTA"));
    }

    @Test
    void historial_citaInexistente_devuelve404() throws Exception {
        mockMvc.perform(get(RUTA + "/" + UUID.randomUUID() + "/historial")
                        .with(user(recepcionUsuarioId.toString()).authorities(rol("SCOPE_RECEPCIONISTA"))))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // RNF-04 · quién no puede
    // ------------------------------------------------------------------

    @Test
    void consultarAgenda_comoPaciente_devuelve403() throws Exception {
        // Que el paciente vea *sus* citas es HU-15, y por otra ruta: esta expone
        // la agenda de toda la clinica.
        consultarAgenda("SCOPE_PACIENTE", pacienteUsuarioId).andExpect(status().isForbidden());
    }

    @Test
    void consultarAgenda_comoOdontologo_devuelve403() throws Exception {
        consultarAgenda("SCOPE_ODONTOLOGO", recepcionUsuarioId).andExpect(status().isForbidden());
    }

    @Test
    void consultarAgenda_sinToken_devuelve401() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.toString()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cancelar_comoPacienteQueNoEsElTitular_devuelve403() throws Exception {
        // Hasta HU-15 el 403 alcanzaba a todo PACIENTE, incluido el titular. Ahora
        // el titular puede cancelar la suya dentro de la ventana de RN-06 —eso lo
        // cubre CitasDelPacienteIntegrationTest—, y lo que sigue siendo 403 es la
        // cita de otro. Esta cuenta no tiene ficha, así que ninguna es suya.
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));

        cancelar(citaId, "Ya no puedo ir", "SCOPE_PACIENTE", recepcionUsuarioId)
                .andExpect(status().isForbidden());

        assertEquals("CONFIRMADA", citaRepository.findById(citaId).orElseThrow().getEstado());
    }

    @Test
    void cancelar_comoOdontologo_devuelve403() throws Exception {
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));

        cancelar(citaId, "Motivo", "SCOPE_ODONTOLOGO", recepcionUsuarioId)
                .andExpect(status().isForbidden());
    }

    @Test
    void historial_comoPaciente_devuelve403() throws Exception {
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));

        mockMvc.perform(get(RUTA + "/" + citaId + "/historial")
                        .with(user(pacienteUsuarioId.toString()).authorities(rol("SCOPE_PACIENTE"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void historial_comoOdontologo_devuelve403() throws Exception {
        // La bitacora nombra al responsable de cada transicion sobre citas de
        // toda la clinica: es de recepcion y administracion, como la agenda.
        UUID citaId = reservar(odontologoId, LocalTime.of(9, 0));

        mockMvc.perform(get(RUTA + "/" + citaId + "/historial")
                        .with(user(recepcionUsuarioId.toString()).authorities(rol("SCOPE_ODONTOLOGO"))))
                .andExpect(status().isForbidden());
    }
}
