package pe.edu.dentalcite.disponibilidad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pe.edu.dentalcite.disponibilidad.api.dto.AgendaOdontologoDTO;
import pe.edu.dentalcite.disponibilidad.api.dto.DiaDisponibleDTO;
import pe.edu.dentalcite.disponibilidad.api.dto.DisponibilidadResponseDTO;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RNF-12: la caché de franjas es una comodidad, no una dependencia. Redis caído
 * debe costar un recálculo y una advertencia en el log, nunca un error al cliente.
 */
@ExtendWith(MockitoExtension.class)
class FranjasCacheTest {

    private static final String CLAVE_VERSION = "disponibilidad:version";

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private ObjectMapper objectMapper;
    private FranjasCache franjasCache;

    private UUID tratamientoId;
    private LocalDate desde;
    private LocalDate hasta;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().build();
        franjasCache = new FranjasCache(redisTemplate, objectMapper, 60);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tratamientoId = UUID.randomUUID();
        desde = LocalDate.of(2026, 9, 14);
        hasta = LocalDate.of(2026, 9, 27);
    }

    private DisponibilidadResponseDTO respuesta() {
        return DisponibilidadResponseDTO.builder()
                .tratamientoId(tratamientoId)
                .duracionMinutos(30)
                .desde(desde)
                .hasta(hasta)
                .zonaHoraria("America/Lima")
                .odontologos(List.of(AgendaOdontologoDTO.builder()
                        .id(UUID.randomUUID())
                        .nombres("Juan")
                        .apellidos("Pérez")
                        .dias(List.of(DiaDisponibleDTO.builder()
                                .fecha(desde)
                                .inicios(List.of(LocalTime.parse("09:00"), LocalTime.parse("09:15")))
                                .build()))
                        .build()))
                .build();
    }

    @Test
    void clave_conLaVersionVigente_incluyeVersionTratamientoYRango() {
        when(valueOperations.get(CLAVE_VERSION)).thenReturn("7");

        String clave = franjasCache.clave(tratamientoId, null, desde, hasta);

        assertEquals("disponibilidad:v7:" + tratamientoId + ":todos:" + desde + ":" + hasta, clave);
    }

    @Test
    void clave_conOdontologoConcreto_loDistingueDeLaConsultaGeneral() {
        when(valueOperations.get(CLAVE_VERSION)).thenReturn("0");
        UUID odontologoId = UUID.randomUUID();

        assertTrue(franjasCache.clave(tratamientoId, odontologoId, desde, hasta).contains(odontologoId.toString()));
    }

    @Test
    void clave_sinVersionPrevia_arrancaEnCero() {
        when(valueOperations.get(CLAVE_VERSION)).thenReturn(null);

        assertTrue(franjasCache.clave(tratamientoId, null, desde, hasta).startsWith("disponibilidad:v0:"));
    }

    @Test
    void guardarYLeer_conLaMismaClave_devuelveLaRespuestaIntacta() {
        String clave = "disponibilidad:v0:x";
        DisponibilidadResponseDTO original = respuesta();
        franjasCache.guardar(clave, original);

        // Se le devuelve exactamente lo que la cache escribio: asi el ida y vuelta
        // pasa por el mapper de la aplicacion y no por una suposicion de la prueba.
        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(eq(clave), json.capture(), eq(Duration.ofSeconds(60)));
        when(valueOperations.get(clave)).thenReturn(json.getValue());

        DisponibilidadResponseDTO leida = franjasCache.leer(clave);

        assertNotNull(leida);
        assertEquals(original.getTratamientoId(), leida.getTratamientoId());
        assertEquals(original.getDuracionMinutos(), leida.getDuracionMinutos());
        assertEquals(LocalTime.parse("09:00"),
                leida.getOdontologos().get(0).getDias().get(0).getInicios().get(0));
        assertEquals(desde, leida.getDesde());
    }

    @Test
    void leer_sinNadaCacheado_devuelveNull() {
        when(valueOperations.get("disponibilidad:v0:x")).thenReturn(null);

        assertNull(franjasCache.leer("disponibilidad:v0:x"));
    }

    @Test
    void invalidarTodo_incrementaLaVersion() {
        // Una sola operación deja obsoletas todas las claves anteriores; no hace
        // falta enumerarlas ni borrarlas.
        franjasCache.invalidarTodo();

        verify(valueOperations).increment(CLAVE_VERSION);
    }

    // ------------------------------------------------------------------
    // RNF-12: Redis detenido
    // ------------------------------------------------------------------

    @Test
    void clave_conRedisCaido_devuelveNullParaQueSeCalculeSinCache() {
        when(valueOperations.get(CLAVE_VERSION)).thenThrow(new RedisConnectionFailureException("caído"));

        assertNull(franjasCache.clave(tratamientoId, null, desde, hasta));
    }

    @Test
    void leer_conClaveNula_devuelveNullSinTocarRedis() {
        assertNull(franjasCache.leer(null));
    }

    @Test
    void leer_conRedisCaido_devuelveNullYNoPropaga() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("caído"));

        assertNull(franjasCache.leer("disponibilidad:v0:x"));
    }

    @Test
    void leer_conJsonCorrupto_devuelveNullYNoPropaga() {
        when(valueOperations.get(anyString())).thenReturn("{no es json");

        assertNull(franjasCache.leer("disponibilidad:v0:x"));
    }

    @Test
    void guardar_conRedisCaido_noPropaga() {
        doThrow(new RedisConnectionFailureException("caído"))
                .when(valueOperations).set(anyString(), anyString(), any(Duration.class));

        franjasCache.guardar("disponibilidad:v0:x", respuesta());
    }

    @Test
    void guardar_conClaveNula_noTocaRedis() {
        franjasCache.guardar(null, respuesta());

        verify(valueOperations, org.mockito.Mockito.never())
                .set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void invalidarTodo_conRedisCaido_noPropaga() {
        when(valueOperations.increment(CLAVE_VERSION)).thenThrow(new RedisConnectionFailureException("caído"));

        franjasCache.invalidarTodo();
    }

    @Test
    void invalidarTrasCommit_sinTransaccionActiva_invalidaEnElActo() {
        franjasCache.invalidarTrasCommit();

        verify(valueOperations).increment(CLAVE_VERSION);
    }
}
