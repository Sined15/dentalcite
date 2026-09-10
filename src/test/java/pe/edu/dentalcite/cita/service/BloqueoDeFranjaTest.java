package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El bloqueo que impide que dos pacientes elijan al mismo odontólogo, a la misma
 * hora, en el mismo instante (HU-10).
 *
 * <p>Lo que más importa aquí es el tercer desenlace: <strong>Redis caído no puede
 * confundirse con franja ocupada</strong>. RNF-12 exige que Redis no sea la
 * barrera única, así que sin Redis la reserva debe seguir su curso y dejar que la
 * restricción de exclusión de la base decida.
 */
@ExtendWith(MockitoExtension.class)
class BloqueoDeFranjaTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private BloqueoDeFranja bloqueo;
    private UUID odontologoId;
    private OffsetDateTime inicio;

    @BeforeEach
    void setUp() {
        bloqueo = new BloqueoDeFranja(redisTemplate, 10);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        odontologoId = UUID.randomUUID();
        inicio = OffsetDateTime.of(2026, 10, 12, 9, 0, 0, 0, ZoneOffset.UTC);
    }

    @Test
    void tomar_conLaFranjaLibre_concedeElBloqueoConSuTtl() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), eq(Duration.ofSeconds(10))))
                .thenReturn(true);

        BloqueoDeFranja.Adquisicion adquisicion = bloqueo.tomar(odontologoId, inicio);

        assertEquals(BloqueoDeFranja.Estado.TOMADO, adquisicion.estado());
        assertNotNull(adquisicion.token());
        assertTrue(adquisicion.permiteSeguir());
    }

    @Test
    void tomar_conLaFranjaYaBloqueada_devuelveOcupado() {
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        BloqueoDeFranja.Adquisicion adquisicion = bloqueo.tomar(odontologoId, inicio);

        assertEquals(BloqueoDeFranja.Estado.OCUPADO, adquisicion.estado());
        assertNull(adquisicion.token());
        // Es el único desenlace que corta la reserva.
        assertTrue(!adquisicion.permiteSeguir());
    }

    @Test
    void tomar_conRedisCaido_dejaSeguirPorqueLaGarantiaEsLaBase() {
        // RNF-12. Si esto devolviera OCUPADO, Redis sería la barrera única y una
        // caída dejaría la clínica sin poder reservar.
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenThrow(new RedisConnectionFailureException("caído"));

        BloqueoDeFranja.Adquisicion adquisicion = bloqueo.tomar(odontologoId, inicio);

        assertEquals(BloqueoDeFranja.Estado.SIN_REDIS, adquisicion.estado());
        assertTrue(adquisicion.permiteSeguir());
    }

    @Test
    void laClave_distingueOdontologoYFranja() {
        // Dos odontólogos a la misma hora, o el mismo a horas distintas, no se
        // estorban: es lo que hace que la clínica siga trabajando en paralelo.
        UUID otro = UUID.randomUUID();
        String base = BloqueoDeFranja.clave(odontologoId, inicio);

        assertTrue(!base.equals(BloqueoDeFranja.clave(otro, inicio)));
        assertTrue(!base.equals(BloqueoDeFranja.clave(odontologoId, inicio.plusMinutes(15))));
        assertEquals(base, BloqueoDeFranja.clave(odontologoId, inicio));
    }

    @Test
    void liberar_conNuestroToken_borraLaClave() {
        String clave = "reserva:x";
        when(valueOperations.get(clave)).thenReturn("token-nuestro");

        bloqueo.liberar(new BloqueoDeFranja.Adquisicion(
                BloqueoDeFranja.Estado.TOMADO, clave, "token-nuestro"));

        verify(redisTemplate).delete(clave);
    }

    @Test
    void liberar_conElBloqueoYaDeOtro_noLoSuelta() {
        // Si el nuestro caducó y otro lo tomó, borrarlo dejaría la franja abierta
        // a dos reservas a la vez.
        String clave = "reserva:x";
        when(valueOperations.get(clave)).thenReturn("token-de-otro");

        bloqueo.liberar(new BloqueoDeFranja.Adquisicion(
                BloqueoDeFranja.Estado.TOMADO, clave, "token-nuestro"));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void liberar_sinTokenONulo_noHaceNada() {
        bloqueo.liberar(null);
        bloqueo.liberar(new BloqueoDeFranja.Adquisicion(BloqueoDeFranja.Estado.SIN_REDIS, "c", null));

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    void liberar_conRedisCaido_noPropaga() {
        // El TTL lo soltará igualmente.
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("caído"));

        bloqueo.liberar(new BloqueoDeFranja.Adquisicion(
                BloqueoDeFranja.Estado.TOMADO, "reserva:x", "token"));
    }
}
