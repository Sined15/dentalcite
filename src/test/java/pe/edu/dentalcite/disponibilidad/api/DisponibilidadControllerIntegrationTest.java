package pe.edu.dentalcite.disponibilidad.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;
import pe.edu.dentalcite.bloqueo.repository.BloqueoRepository;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
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
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HU-08 de extremo a extremo, contra PostgreSQL y Redis reales.
 *
 * <p>Como el resto de pruebas de integración del proyecto, no lleva
 * {@code @Transactional}: la petición atraviesa la cadena de filtros y el motor
 * lee lo que hay realmente en la base. Por eso siembra su propio odontólogo y su
 * propio tratamiento y los retira al terminar, en vez de apoyarse en los datos de
 * demostración de Flyway, que otras pruebas también usan.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DisponibilidadControllerIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final String RUTA = "/api/v1/disponibilidad";

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Autowired private WebApplicationContext context;
    @Autowired private OdontologoRepository odontologoRepository;
    @Autowired private TratamientoRepository tratamientoRepository;
    @Autowired private HorarioAtencionRepository horarioRepository;
    @Autowired private BloqueoRepository bloqueoRepository;
    @Autowired private CitaRepository citaRepository;
    @Autowired private ConsultorioRepository consultorioRepository;
    @Autowired private FichaRepository fichaRepository;
    @Autowired private pe.edu.dentalcite.especialidad.repository.EspecialidadRepository especialidadRepository;

    private UUID tratamientoId;
    private UUID odontologoId;
    private Ficha ficha;
    private LocalDate lunes;
    private final List<UUID> citasSembradas = new ArrayList<>();
    private final List<UUID> bloqueosSembrados = new ArrayList<>();
    private final List<UUID> horariosSembrados = new ArrayList<>();
    private final List<UUID> odontologosAuxiliares = new ArrayList<>();
    private final List<UUID> fichasAuxiliares = new ArrayList<>();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Un lunes futuro, lejos de la ventana de dos horas de RN-05 y dentro de
        // los noventa días del horizonte.
        lunes = LocalDate.now(ZONA).plusDays(14);
        while (lunes.getDayOfWeek().getValue() != 1) {
            lunes = lunes.plusDays(1);
        }

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre("DISPONIBILIDAD-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true)
                .build());

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TD-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de prueba")
                .duracionMinutos(30)
                .especialidad(especialidad)
                .activo(true)
                .build());

        ficha = fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres("Odontologo")
                .apellidos("De Prueba")
                .build());

        odontologoId = UUID.randomUUID();
        odontologoRepository.save(Odontologo.builder()
                .id(odontologoId)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Juan")
                .apellidos("Perez")
                .ficha(ficha)
                .activo(true)
                .especialidades(java.util.Set.of(especialidad))
                .build());

        horariosSembrados.add(horarioRepository.save(HorarioAtencion.builder()
                .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                .diaSemana(1)
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(13, 0))
                .build()).getId());
    }

    @AfterEach
    void tearDown() {
        citasSembradas.forEach(citaRepository::deleteById);
        bloqueosSembrados.forEach(bloqueoRepository::deleteById);
        horariosSembrados.forEach(horarioRepository::deleteById);
        citasSembradas.clear();
        bloqueosSembrados.clear();
        horariosSembrados.clear();
        odontologoRepository.deleteById(odontologoId);
        odontologosAuxiliares.forEach(odontologoRepository::deleteById);
        fichasAuxiliares.forEach(fichaRepository::deleteById);
        odontologosAuxiliares.clear();
        fichasAuxiliares.clear();
        tratamientoRepository.deleteById(tratamientoId);
        fichaRepository.deleteById(ficha.getId());
    }

    private OffsetDateTime enLima(LocalDate fecha, int hora, int minuto) {
        return fecha.atTime(hora, minuto).atZone(ZONA).toOffsetDateTime();
    }

    private void sembrarCita(Consultorio consultorio, int horaInicio, int horaFin) {
        citasSembradas.add(citaRepository.save(Cita.builder()
                .codigo("CT-" + UUID.randomUUID().toString().substring(0, 8))
                .ficha(ficha)
                .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                .consultorio(consultorio)
                .inicio(enLima(lunes, horaInicio, 0))
                .fin(enLima(lunes, horaFin, 0))
                .estado(Cita.ESTADO_CONFIRMADA)
                .build()).getId());
    }

    /** Las horas de inicio que la respuesta ofrece para el lunes sembrado. */
    private List<String> iniciosDelLunes(String cuerpo) throws Exception {
        JsonNode dias = objectMapper.readTree(cuerpo).path("odontologos").path(0).path("dias");
        for (JsonNode dia : dias) {
            if (lunes.toString().equals(dia.path("fecha").asText())) {
                List<String> horas = new ArrayList<>();
                dia.path("inicios").forEach(h -> horas.add(h.asText()));
                return horas;
            }
        }
        return List.of();
    }

    private String consultar(String desde, String hasta) throws Exception {
        return mockMvc.perform(get(RUTA)
                        .param("tratamientoId", tratamientoId.toString())
                        .param("odontologoId", odontologoId.toString())
                        .param("desde", desde)
                        .param("hasta", hasta))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------
    // Camino feliz
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conHorarioDeclarado_devuelveLasFranjasDeEseTramo() throws Exception {
        String cuerpo = consultar(lunes.toString(), lunes.toString());

        List<String> inicios = iniciosDelLunes(cuerpo);
        assertEquals("09:00:00", inicios.get(0));
        assertEquals("12:30:00", inicios.get(inicios.size() - 1), "el ultimo bloque de 30 que cabe antes de las 13:00");
        assertEquals(15, inicios.size(), "de 09:00 a 12:30 de quince en quince (RN-04)");
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_siempre_declaraDuracionYZonaHoraria() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("tratamientoId", tratamientoId.toString())
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duracionMinutos").value(30))
                .andExpect(jsonPath("$.zonaHoraria").value("America/Lima"))
                .andExpect(jsonPath("$.tratamientoId").value(tratamientoId.toString()));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conCitaActiva_omiteLosBloquesQueLaSolapan() throws Exception {
        // CA-1: cita de 10:00 a 10:30 sobre un tratamiento de 30 minutos.
        sembrarCita(consultorioRepository.findByInoperativoFalse().get(0), 10, 11);

        List<String> inicios = iniciosDelLunes(consultar(lunes.toString(), lunes.toString()));

        assertFalse(inicios.contains("10:00:00"));
        assertFalse(inicios.contains("10:30:00"));
        assertTrue(inicios.contains("09:30:00"), "termina justo cuando empieza la cita");
        assertTrue(inicios.contains("11:00:00"), "empieza justo cuando la cita termina");
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conBloqueoDelOdontologo_noOfreceEseRango() throws Exception {
        // CA-4 / RN-03: el bloqueo declarado en HU-07 lo consume este motor.
        bloqueosSembrados.add(bloqueoRepository.save(Bloqueo.builder()
                .odontologo(odontologoRepository.findById(odontologoId).orElseThrow())
                .motivo("Congreso")
                .fechaInicio(enLima(lunes, 10, 0))
                .fechaFin(enLima(lunes, 12, 0))
                .build()).getId());

        List<String> inicios = iniciosDelLunes(consultar(lunes.toString(), lunes.toString()));

        assertFalse(inicios.contains("10:00:00"));
        assertFalse(inicios.contains("11:30:00"));
        assertTrue(inicios.contains("09:30:00"));
        assertTrue(inicios.contains("12:00:00"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conTodosLosConsultoriosOcupados_noOfreceEsaFranja() throws Exception {
        // CA-6 / RF-14: la franja sin consultorio libre no se ofrece. Las citas que
        // los ocupan son de otros odontologos, asi que no bloquean al nuestro.
        //
        // Cada consultorio lo ocupa un odontologo DISTINTO. Antes los ocupaba el
        // mismo para todos, que es un solapamiento que RN-01 prohibe: la base lo
        // aceptaba solo porque aun no existia la restriccion de exclusion de
        // HU-10, y con ella la siembra fallaba al segundo INSERT.
        List<Consultorio> consultorios = consultorioRepository.findByInoperativoFalse();
        for (Consultorio consultorio : consultorios) {
            Odontologo ocupante = nuevoOdontologoAuxiliar();
            citasSembradas.add(citaRepository.save(Cita.builder()
                    .codigo("CT-" + UUID.randomUUID().toString().substring(0, 8))
                    .ficha(ficha)
                    .odontologo(ocupante)
                    .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                    .consultorio(consultorio)
                    .inicio(enLima(lunes, 11, 0))
                    .fin(enLima(lunes, 12, 0))
                    .estado(Cita.ESTADO_CONFIRMADA)
                    .build()).getId());
        }

        List<String> inicios = iniciosDelLunes(consultar(lunes.toString(), lunes.toString()));

        assertFalse(inicios.contains("11:00:00"), "todos los consultorios estan ocupados");
        assertFalse(inicios.contains("11:30:00"));
        assertTrue(inicios.contains("10:30:00"), "fuera de la franja copada si hay sitio");
        assertTrue(inicios.contains("12:00:00"));
    }

    /** Un odontologo de usar y tirar, para ocupar un consultorio sin chocar con RN-01. */
    private Odontologo nuevoOdontologoAuxiliar() {
        Ficha suya = fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres("Ocupante").apellidos("Auxiliar")
                .build());
        fichasAuxiliares.add(suya.getId());

        UUID id = UUID.randomUUID();
        odontologosAuxiliares.add(id);
        return odontologoRepository.save(Odontologo.builder()
                .id(id)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Ocupante").apellidos("Auxiliar")
                .ficha(suya).activo(true)
                .build());
    }

    // ------------------------------------------------------------------
    // Errores
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conRangoInvertido_retornaBadRequest() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("tratamientoId", tratamientoId.toString())
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conRangoMayorQueCatorceDias_retornaBadRequest() throws Exception {
        // RNF-01 acota el rango consultable; quince dias ya no se sirven.
        mockMvc.perform(get(RUTA)
                        .param("tratamientoId", tratamientoId.toString())
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.plusDays(14).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_sinRango_retornaBadRequest() throws Exception {
        mockMvc.perform(get(RUTA).param("tratamientoId", tratamientoId.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void consultarDisponibilidad_conTratamientoInexistente_retornaNotFound() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("tratamientoId", UUID.randomUUID().toString())
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void consultarDisponibilidad_sinAutenticar_retornaUnauthorized() throws Exception {
        mockMvc.perform(get(RUTA)
                        .param("tratamientoId", tratamientoId.toString())
                        .param("desde", lunes.toString())
                        .param("hasta", lunes.toString()))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // RNF-04 · autorizacion
    // ------------------------------------------------------------------

    @Test
    @WithMockUser(authorities = "SCOPE_ODONTOLOGO")
    void consultarDisponibilidad_comoOdontologo_retornaOk() throws Exception {
        consultar(lunes.toString(), lunes.toString());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void consultarDisponibilidad_comoRecepcionista_retornaOk() throws Exception {
        consultar(lunes.toString(), lunes.toString());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void consultarDisponibilidad_comoAdministrador_retornaOk() throws Exception {
        consultar(lunes.toString(), lunes.toString());
    }
}
