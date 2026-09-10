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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-09 de extremo a extremo contra PostgreSQL y Redis reales.
 *
 * <p>Siembra su propio paciente, odontólogo y tratamiento y los retira al
 * terminar: sin {@code @Transactional}, como el resto de pruebas de integración
 * del proyecto, las filas son reales y compartir las de demostración con otras
 * clases haría que se estorbasen.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReservaIntegrationTest {

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
    private UUID tratamientoId;
    private UUID odontologoId;
    private UUID especialidadId;
    private Ficha fichaPaciente;
    private Ficha fichaOdontologo;
    private LocalDate lunes;

    private final List<UUID> citasSembradas = new ArrayList<>();
    private final List<UUID> horariosSembrados = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Un lunes futuro: lejos de las dos horas de RN-05 y dentro de los noventa días.
        lunes = LocalDate.now(ZONA).plusDays(14);
        while (lunes.getDayOfWeek().getValue() != 1) {
            lunes = lunes.plusDays(1);
        }

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("RESERVA-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TR-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de reserva")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        fichaOdontologo = nuevaFicha("Odontologo", "De Prueba");
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

        fichaPaciente = nuevaFicha("Ana", "Torres");
        // `Usuario` genera su identificador con @GeneratedValue: asignarlo a mano
        // convertiria el save en un merge sobre una fila que no existe.
        pacienteUsuarioId = usuarioRepository.save(Usuario.builder()
                .nombre("Ana Torres")
                .correo("reserva-" + UUID.randomUUID().toString().substring(0, 8) + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol("PACIENTE").activo(true).ficha(fichaPaciente).build()).getId();
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
        citaRepository.findAll().stream()
                .filter(c -> c.getFicha().getId().equals(fichaPaciente.getId()))
                .map(Cita::getId)
                .forEach(citasSembradas::add);
        citasSembradas.stream().distinct().forEach(citaRepository::deleteById);
        horariosSembrados.forEach(horarioRepository::deleteById);
        usuarioRepository.deleteById(pacienteUsuarioId);
        odontologoRepository.deleteById(odontologoId);
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(fichaPaciente.getId());
        fichaRepository.deleteById(fichaOdontologo.getId());
        especialidadRepository.deleteById(especialidadId);
        citasSembradas.clear();
        horariosSembrados.clear();
    }

    // ------------------------------------------------------------------
    // Utilidades de petición
    // ------------------------------------------------------------------

    private CitaRequestDTO peticion(LocalDate fecha, LocalTime hora) {
        return CitaRequestDTO.builder()
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(fecha).hora(hora).build();
    }

    private org.springframework.test.web.servlet.ResultActions reservar(CitaRequestDTO cuerpo,
            String autoridad) throws Exception {
        return mockMvc.perform(post(RUTA)
                .with(user(pacienteUsuarioId.toString())
                        .authorities(new SimpleGrantedAuthority(autoridad)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(cuerpo)));
    }

    private org.springframework.test.web.servlet.ResultActions reservar(CitaRequestDTO cuerpo) throws Exception {
        return reservar(cuerpo, "SCOPE_PACIENTE");
    }

    /** Las horas que la disponibilidad ofrece ese lunes para nuestro odontólogo. */
    private List<String> iniciosOfrecidos() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/disponibilidad")
                        .with(user(pacienteUsuarioId.toString())
                                .authorities(new SimpleGrantedAuthority("SCOPE_PACIENTE")))
                        .param("tratamientoId", tratamientoId.toString())
                        .param("odontologoId", odontologoId.toString())
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
    // Camino feliz · RF-15, RN-09, RF-14
    // ------------------------------------------------------------------

    @Test
    void reservar_conFranjaDisponible_creaLaCitaYDevuelveSuCodigo() throws Exception {
        reservar(peticion(lunes, LocalTime.of(9, 0)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").exists())
                .andExpect(jsonPath("$.estado").value("CONFIRMADA"))
                .andExpect(jsonPath("$.fecha").value(lunes.toString()))
                .andExpect(jsonPath("$.hora").value("09:00:00"))
                .andExpect(jsonPath("$.duracionMinutos").value(30))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
                .andExpect(jsonPath("$.consultorio.nombre").exists())
                .andExpect(jsonPath("$.odontologo.nombre").value("Luis Perez"));
    }

    @Test
    void reservar_generaUnCodigoConElPrefijoYSuCorrelativo() throws Exception {
        String cuerpo = reservar(peticion(lunes, LocalTime.of(9, 0)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String codigo = objectMapper.readTree(cuerpo).path("codigo").asText();
        assertTrue(codigo.matches("CIT-\\d{6}"), "codigo inesperado: " + codigo);
    }

    @Test
    void reservar_asignaUnConsultorioDeLosOperativos() throws Exception {
        // RF-14: la asignación es del sistema, no la elige el paciente.
        String cuerpo = reservar(peticion(lunes, LocalTime.of(9, 0)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID asignado = UUID.fromString(objectMapper.readTree(cuerpo).path("consultorio").path("id").asText());
        assertTrue(consultorioRepository.findByInoperativoFalse().stream()
                .anyMatch(c -> c.getId().equals(asignado)));
    }

    /** El círculo que une HU-08 y HU-09. */
    @Test
    void reservar_dejaLaFranjaFueraDeLaDisponibilidad() throws Exception {
        assertTrue(iniciosOfrecidos().contains("09:00:00"), "la franja debía ofrecerse antes de reservar");

        reservar(peticion(lunes, LocalTime.of(9, 0))).andExpect(status().isCreated());

        List<String> despues = iniciosOfrecidos();
        assertFalse(despues.contains("09:00:00"), "la franja tomada no puede seguir ofreciéndose");
        // El tratamiento dura 30 min: las 08:45 tampoco caben, pero las 09:30 sí.
        assertTrue(despues.contains("09:30:00"));
    }

    // ------------------------------------------------------------------
    // RN-05 · ventana de reserva → 422
    // ------------------------------------------------------------------

    @Test
    void reservar_conFranjaAMenosDeDosHoras_retornaUnprocessableEntity() throws Exception {
        LocalTime dentroDeUnaHora = LocalTime.now(ZONA).plusHours(1).withSecond(0).withNano(0);

        reservar(peticion(LocalDate.now(ZONA), dentroDeUnaHora))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("antelación")));
    }

    @Test
    void reservar_conFranjaAMasDeNoventaDias_retornaUnprocessableEntity() throws Exception {
        LocalDate lejano = lunes.plusDays(120);

        reservar(peticion(lejano, LocalTime.of(9, 0)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("90 días")));
    }

    // ------------------------------------------------------------------
    // RN-07 · cuota → 409
    // ------------------------------------------------------------------

    @Test
    void reservar_conLaCuotaAgotada_retornaConflict() throws Exception {
        reservar(peticion(lunes, LocalTime.of(9, 0))).andExpect(status().isCreated());
        reservar(peticion(lunes, LocalTime.of(10, 0))).andExpect(status().isCreated());
        reservar(peticion(lunes, LocalTime.of(11, 0))).andExpect(status().isCreated());

        reservar(peticion(lunes, LocalTime.of(12, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("máximo permitido")));
    }

    @Test
    void reservar_conUnaCitaVencida_esaYaNoConsumeCuota() throws Exception {
        // RN-09: la confirmada cuya hora de fin ya pasó queda pendiente de cierre y
        // no cuenta. Se siembran tres citas vencidas: la cuarta debe entrar igual.
        for (int i = 0; i < 3; i++) {
            OffsetDateTime inicio = OffsetDateTime.now(ZONA).minusDays(10 + i).withHour(9).withMinute(0);
            citasSembradas.add(citaRepository.save(Cita.builder()
                    .codigo("CV-" + UUID.randomUUID().toString().substring(0, 8))
                    .ficha(fichaPaciente)
                    .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                    .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                    .consultorio(consultorioRepository.findByInoperativoFalse().get(0))
                    .inicio(inicio).fin(inicio.plusMinutes(30))
                    .estado(Cita.ESTADO_CONFIRMADA).build()).getId());
        }

        reservar(peticion(lunes, LocalTime.of(9, 0))).andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Franja no ofrecida → 409
    // ------------------------------------------------------------------

    @Test
    void reservar_fueraDelHorarioDeclarado_retornaConflict() throws Exception {
        // El odontólogo declaró de 09:00 a 13:00; las 15:00 no existen para él.
        reservar(peticion(lunes, LocalTime.of(15, 0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ya no está disponible")));
    }

    @Test
    void reservar_sobreUnaFranjaYaTomada_retornaConflict() throws Exception {
        reservar(peticion(lunes, LocalTime.of(9, 0))).andExpect(status().isCreated());

        reservar(peticion(lunes, LocalTime.of(9, 0)))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------
    // Catálogo y cuerpo
    // ------------------------------------------------------------------

    @Test
    void reservar_conTratamientoInexistente_retornaNotFound() throws Exception {
        CitaRequestDTO cuerpo = CitaRequestDTO.builder()
                .tratamientoId(UUID.randomUUID()).odontologoId(odontologoId)
                .fecha(lunes).hora(LocalTime.of(9, 0)).build();

        reservar(cuerpo).andExpect(status().isNotFound());
    }

    @Test
    void reservar_sinHora_retornaBadRequest() throws Exception {
        CitaRequestDTO cuerpo = CitaRequestDTO.builder()
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(lunes).build();

        reservar(cuerpo).andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // RNF-04 · autorización negativa
    // ------------------------------------------------------------------

    @Test
    void reservar_comoOdontologo_retornaForbidden() throws Exception {
        reservar(peticion(lunes, LocalTime.of(9, 0)), "SCOPE_ODONTOLOGO")
                .andExpect(status().isForbidden());
    }

    @Test
    void reservar_comoRecepcionistaSinIndicarPaciente_retornaBadRequest() throws Exception {
        // Hasta HU-14 esto era un 403: recepción no podía reservar en absoluto.
        // Ahora sí puede, pero su cuenta no tiene ficha propia, así que el cuerpo
        // sin `pacienteId` está incompleto, no prohibido. Reservar en nombre de un
        // paciente se prueba en ReservaDesdeRecepcionIntegrationTest.
        reservar(peticion(lunes, LocalTime.of(9, 0)), "SCOPE_RECEPCIONISTA")
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservar_comoAdministradorSinIndicarPaciente_retornaBadRequest() throws Exception {
        reservar(peticion(lunes, LocalTime.of(9, 0)), "SCOPE_ADMINISTRADOR")
                .andExpect(status().isBadRequest());
    }

    @Test
    void reservar_sinAutenticar_retornaUnauthorized() throws Exception {
        mockMvc.perform(post(RUTA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(peticion(lunes, LocalTime.of(9, 0)))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reservar_soloCreaCitasParaLaFichaDelToken() throws Exception {
        // Aunque desde HU-14 el cuerpo admite `pacienteId`, al PACIENTE se le
        // rechaza siempre: cuando no lo manda, la ficha sigue saliendo del token.
        String cuerpo = reservar(peticion(lunes, LocalTime.of(9, 0)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        UUID citaId = UUID.fromString(objectMapper.readTree(cuerpo).path("id").asText());
        Cita creada = citaRepository.findById(citaId).orElseThrow();
        assertEquals(fichaPaciente.getId(), creada.getFicha().getId());
    }
}
