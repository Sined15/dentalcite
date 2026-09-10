package pe.edu.dentalcite.disponibilidad.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import pe.edu.dentalcite.disponibilidad.api.dto.DisponibilidadResponseDTO;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Caché de franjas (RNF-01): evita recalcular el rango completo en consultas
 * repetidas. Sigue el mismo contrato que {@code TokenRevocationCache}: Redis es
 * best-effort y PostgreSQL sigue siendo la autoridad, así que toda llamada va
 * envuelta en {@code try/catch} y su fallo solo cuesta un recálculo (RNF-12).
 *
 * <p>La clave lleva un número de versión que vive en Redis. Invalidar no es
 * enumerar y borrar claves —que con cinco odontólogos, catorce días y todos los
 * tratamientos serían miles—, sino un único {@code INCR}: las claves anteriores
 * dejan de consultarse en el acto y caducan solas por TTL. Eso es lo que permite
 * que una cancelación devuelva la franja a la oferta «de inmediato» (HU-11) sin
 * que la caché la siga escondiendo durante el resto de su vida.
 */
@Slf4j
@Component
public class FranjasCache {

    private static final String PREFIJO = "disponibilidad:";
    private static final String CLAVE_VERSION = PREFIJO + "version";
    private static final String SIN_ODONTOLOGO = "todos";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public FranjasCache(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            @Value("${app.disponibilidad.cache-ttl-segundos:60}") long ttlSegundos) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSegundos);
    }

    /**
     * @return la clave de esta consulta, o {@code null} si Redis no responde; en
     *         ese caso el servicio calcula sin pasar por la caché.
     */
    public String clave(UUID tratamientoId, UUID odontologoId, LocalDate desde, LocalDate hasta) {
        String version = version();
        if (version == null) {
            return null;
        }
        return PREFIJO + "v" + version + ":" + tratamientoId + ":"
                + (odontologoId == null ? SIN_ODONTOLOGO : odontologoId) + ":" + desde + ":" + hasta;
    }

    private String version() {
        try {
            String valor = redisTemplate.opsForValue().get(CLAVE_VERSION);
            return valor == null ? "0" : valor;
        } catch (Exception e) {
            log.warn("Redis no disponible: la disponibilidad se calculará sin caché: {}", e.getMessage());
            return null;
        }
    }

    /** @return la respuesta cacheada, o {@code null} si no está o Redis no responde. */
    public DisponibilidadResponseDTO leer(String clave) {
        if (clave == null) {
            return null;
        }
        try {
            String valor = redisTemplate.opsForValue().get(clave);
            return valor == null ? null : objectMapper.readValue(valor, DisponibilidadResponseDTO.class);
        } catch (Exception e) {
            log.warn("No se pudo leer la disponibilidad cacheada, se recalcula: {}", e.getMessage());
            return null;
        }
    }

    public void guardar(String clave, DisponibilidadResponseDTO respuesta) {
        if (clave == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(clave, objectMapper.writeValueAsString(respuesta), ttl);
        } catch (Exception e) {
            log.warn("No se pudo cachear la disponibilidad: {}", e.getMessage());
        }
    }

    /**
     * Deja obsoleta toda la caché. Debe llamarse en <strong>todo</strong> punto
     * que cambie la agenda: hoy son los horarios y los bloqueos (HU-07), la
     * reserva (HU-09) y la cancelación (HU-11). Sin esto, una franja ocupada
     * seguiría ofreciéndose —y una liberada seguiría sin ofrecerse— hasta que
     * caducara el TTL.
     */
    public void invalidarTodo() {
        try {
            redisTemplate.opsForValue().increment(CLAVE_VERSION);
        } catch (Exception e) {
            log.warn("No se pudo invalidar la caché de franjas, seguirá vigente hasta su TTL: {}", e.getMessage());
        }
    }

    /**
     * Variante para usar dentro de un método {@code @Transactional}: espera al
     * commit. Invalidar antes dejaría una ventana en la que otra petición
     * recalcularía con la agenda todavía sin cambiar y volvería a cachear ese
     * resultado bajo la versión nueva, deshaciendo la invalidación.
     */
    public void invalidarTrasCommit() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            invalidarTodo();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                invalidarTodo();
            }
        });
    }
}
