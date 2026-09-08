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

/**
 * Caché de la marca {@code tokens_validos_desde} de un usuario, sobre la que se
 * apoya la validación de vigencia de cada JWT (HU-05).
 *
 * <p>La clave depende <strong>solo</strong> del usuario, no del instante de
 * emisión del token: así, un único borrado invalida de golpe la caché de todos
 * sus tokens. La versión anterior indexaba por {@code userId:issuedAt} y nunca
 * borraba nada, de modo que un token cerrado con logout —o el de una cuenta
 * recién desactivada— seguía aceptándose hasta cinco minutos, incumpliendo
 * HU-03 y HU-04.
 *
 * <p>PostgreSQL sigue siendo la fuente de autoridad (RNF-12): toda operación
 * sobre Redis es best-effort y su fallo solo cuesta una consulta a la base.
 */
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

    /**
     * @return la marca cacheada, o {@code null} si no está en caché o Redis no
     *         responde; en ambos casos hay que consultar PostgreSQL.
     */
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

    /**
     * Debe llamarse en todo punto que suba {@code tokensValidosDesde}: logout,
     * cambio de rol, desactivación y cambio de contraseña.
     */
    public void invalidar(UUID usuarioId) {
        try {
            redisTemplate.delete(clave(usuarioId));
        } catch (Exception e) {
            log.warn("Redis no disponible para invalidar la vigencia cacheada de {}: {}", usuarioId, e.getMessage());
        }
    }

    /**
     * Variante para usar dentro de un método {@code @Transactional}: espera al
     * commit antes de borrar. Borrar antes dejaría una ventana en la que otra
     * petición releería de PostgreSQL la marca <em>anterior</em> y la volvería a
     * cachear durante cinco minutos, deshaciendo la revocación.
     */
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
