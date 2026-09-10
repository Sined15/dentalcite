package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bloqueo distribuido de una franja concreta de un odontólogo (HU-10).
 *
 * <p>Es lo que impide que dos pacientes elijan al mismo odontólogo, a la misma
 * hora, en el mismo instante. La llave es la que describe el apartado 1.5.3 del
 * informe —la franja horaria y el identificador del odontólogo—, y no la agenda
 * entera: dos reservas con odontólogos distintos, o con el mismo odontólogo a
 * horas distintas, no se estorban.
 *
 * <p><strong>Redis va por delante de la garantía, no en su lugar.</strong> RNF-12
 * exige que «la aplicación y Redis no sean la barrera única» y que la prueba de
 * concurrencia se repita con Redis detenido. Por eso esta clase distingue tres
 * desenlaces y no dos:
 *
 * <ul>
 *   <li>{@link Estado#TOMADO} — se tiene el bloqueo; seguir.</li>
 *   <li>{@link Estado#OCUPADO} — <em>otro</em> lo tiene; rechazar con 409 sin
 *       tocar la base, que es el 409 que reciben las noventa y nueve peticiones
 *       perdedoras del criterio de aceptación.</li>
 *   <li>{@link Estado#SIN_REDIS} — Redis no responde; <strong>seguir igual</strong>.
 *       La restricción de exclusión de PostgreSQL sigue impidiendo la doble
 *       reserva.</li>
 * </ul>
 *
 * <p>Confundir {@code OCUPADO} con {@code SIN_REDIS} convertiría a Redis en
 * barrera única —caído, nadie reservaría; o peor, caído, todos reservarían sin
 * red— que es justo lo que el requisito prohíbe.
 */
@Slf4j
@Component
public class BloqueoDeFranja {

    private static final String PREFIJO = "reserva:";

    public enum Estado {
        TOMADO,
        OCUPADO,
        SIN_REDIS
    }

    /**
     * @param estado desenlace del intento
     * @param clave  la llave usada, para poder liberarla
     * @param token  quién la tiene; solo se libera si coincide
     */
    public record Adquisicion(Estado estado, String clave, String token) {

        /** Se puede reservar: o se tiene el bloqueo, o Redis no está para darlo. */
        public boolean permiteSeguir() {
            return estado != Estado.OCUPADO;
        }
    }

    private final StringRedisTemplate redisTemplate;
    private final Duration ttl;

    public BloqueoDeFranja(StringRedisTemplate redisTemplate,
            @Value("${app.citas.bloqueo-ttl-segundos:10}") long ttlSegundos) {
        this.redisTemplate = redisTemplate;
        // Corto a propósito: si un proceso muere con el bloqueo tomado, la franja
        // debe volver a estar reservable en segundos, no cuando alguien lo note.
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
            // RNF-12: sin Redis se sigue reservando; la barrera es la base.
            log.warn("Redis no disponible: la reserva sigue sin bloqueo distribuido, "
                    + "la restriccion de exclusion es la garantia: {}", e.getMessage());
            return new Adquisicion(Estado.SIN_REDIS, clave, null);
        }
    }

    /**
     * Libera solo si la llave sigue siendo nuestra. Sin la comprobación, un
     * proceso lento cuyo bloqueo ya hubiera caducado borraría el de quien lo tomó
     * después.
     */
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
            // No pasa nada: el TTL lo suelta igual.
            log.warn("No se pudo liberar el bloqueo de franja, caducara solo: {}", e.getMessage());
        }
    }
}
