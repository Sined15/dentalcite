package pe.edu.dentalcite.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenRevocationCache {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final String PREFIJO = "token_validity:";

    private final StringRedisTemplate redisTemplate;

    private String clave(UUID usuarioId) {
        return PREFIJO + usuarioId;
    }

    public Instant leer(UUID usuarioId) {
        try {
            String valor = redisTemplate.opsForValue().get(clave(usuarioId));
            return valor == null ? null : Instant.parse(valor);
        } catch (Exception e) {
            log.warn("Redis no disponible para lectura, usando PostgreSQL como fallback: {}", e.getMessage());
            return null;
        }
    }

    public void guardar(UUID usuarioId, Instant tokensValidosDesde) {
        try {
            redisTemplate.opsForValue().set(clave(usuarioId), tokensValidosDesde.toString(), TTL);
        } catch (Exception e) {
            log.warn("Redis no disponible para escritura, la vigencia no se cacheará: {}", e.getMessage());
        }
    }

    public void invalidar(UUID usuarioId) {
        try {
            redisTemplate.delete(clave(usuarioId));
        } catch (Exception e) {
            log.warn("Redis no disponible para invalidar la vigencia cacheada de {}: {}", usuarioId, e.getMessage());
        }
    }

    public void invalidarTrasCommit(UUID usuarioId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidar(usuarioId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidar(usuarioId);
            }
        });
    }
}
