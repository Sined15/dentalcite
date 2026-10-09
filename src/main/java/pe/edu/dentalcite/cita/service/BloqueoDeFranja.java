package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Component
public class BloqueoDeFranja {

    private static final String PREFIJO = "reserva:";

    public enum Estado {
        TOMADO,
        OCUPADO,
        SIN_REDIS
    }

    public record Adquisicion(Estado estado, String clave, String token) {

        public boolean permiteSeguir() {
            return estado != Estado.OCUPADO;
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public BloqueoDeFranja(StringRedisTemplate redisTemplate,
            @Value("${app.citas.bloqueo-ttl-segundos:10}") long ttlSegundos) {
        this.redisTemplate = redisTemplate;
        this.ttl = Duration.ofSeconds(ttlSegundos);
    }

    static String clave(UUID odontologoId, OffsetDateTime inicio) {
        return PREFIJO + odontologoId + ":" + inicio.toInstant().getEpochSecond();
    }

    public Adquisicion tomar(UUID odontologoId, OffsetDateTime inicio) {
        String clave = clave(odontologoId, inicio);
        String token = UUID.randomUUID().toString();
        try {
            Boolean concedido = redisTemplate.opsForValue().setIfAbsent(clave, token, ttl);
            return Boolean.TRUE.equals(concedido)
                    ? new Adquisicion(Estado.TOMADO, clave, token)
                    : new Adquisicion(Estado.OCUPADO, clave, null);
        } catch (Exception e) {
            log.warn("Redis no disponible: la reserva sigue sin bloqueo distribuido, "
                    + "la restriccion de exclusion es la garantia: {}", e.getMessage());
            return new Adquisicion(Estado.SIN_REDIS, clave, null);
        }
    }

    public void liberar(Adquisicion adquisicion) {
        if (adquisicion == null || adquisicion.token() == null) {
            return;
        }
        try {
            String actual = redisTemplate.opsForValue().get(adquisicion.clave());
            if (adquisicion.token().equals(actual)) {
                redisTemplate.delete(adquisicion.clave());
            }
        } catch (Exception e) {
            log.warn("No se pudo liberar el bloqueo de franja, caducara solo: {}", e.getMessage());
        }
    }
}
