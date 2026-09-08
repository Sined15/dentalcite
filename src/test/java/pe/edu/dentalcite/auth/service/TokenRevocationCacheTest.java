package pe.edu.dentalcite.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-05 / RNF-12: Redis es una caché best-effort; PostgreSQL conserva el dato de
 * autoridad, de modo que ninguna caída de Redis puede propagarse como error.
 */
@ExtendWith(MockitoExtension.class)
class TokenRevocationCacheTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenRevocationCache cache;

    private final UUID usuarioId = UUID.randomUUID();
    private String clave;

    @BeforeEach
    void setUp() {
        clave = "token_validity:" + usuarioId;
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void leer_conMarcaCacheada_laDevuelveComoInstant() {
        Instant marca = Instant.parse("2026-09-06T10:15:30Z");
        when(valueOperations.get(clave)).thenReturn(marca.toString());

        assertEquals(marca, cache.leer(usuarioId));
    }

    @Test
    void leer_sinMarcaCacheada_devuelveNull() {
        when(valueOperations.get(clave)).thenReturn(null);

        assertNull(cache.leer(usuarioId));
    }

    @Test
    void leer_conRedisCaido_devuelveNullEnVezDePropagarElError() {
        when(valueOperations.get(clave)).thenThrow(new RuntimeException("Redis caído"));

        assertNull(cache.leer(usuarioId));
    }

    @Test
    void guardar_cacheaLaMarcaConTtlDeCincoMinutos() {
        Instant marca = Instant.parse("2026-09-06T10:15:30Z");

        cache.guardar(usuarioId, marca);

        verify(valueOperations).set(eq(clave), eq(marca.toString()), eq(Duration.ofMinutes(5)));
    }

    @Test
    void guardar_conRedisCaido_noPropagaElError() {
        Instant marca = Instant.now();
        org.mockito.Mockito.doThrow(new RuntimeException("Redis caído"))
                .when(valueOperations).set(eq(clave), org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any(Duration.class));

        assertDoesNotThrow(() -> cache.guardar(usuarioId, marca));
    }

    @Test
    void invalidar_borraLaClaveDelUsuario() {
        cache.invalidar(usuarioId);

        verify(redisTemplate).delete(clave);
    }

    @Test
    void invalidar_conRedisCaido_noPropagaElError() {
        when(redisTemplate.delete(clave)).thenThrow(new RuntimeException("Redis caído"));

        assertDoesNotThrow(() -> cache.invalidar(usuarioId));
    }

    @Test
    void invalidarTrasCommit_sinTransaccionActiva_borraDeInmediato() {
        // Fuera de una transacción no hay commit al que engancharse, así que el
        // borrado tiene que ocurrir igualmente.
        cache.invalidarTrasCommit(usuarioId);

        verify(redisTemplate).delete(clave);
    }
}
