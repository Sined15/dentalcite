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

    public void invalidarTodo() {
        try {
            redisTemplate.opsForValue().increment(CLAVE_VERSION);
        } catch (Exception e) {
            log.warn("No se pudo invalidar la caché de franjas, seguirá vigente hasta su TTL: {}", e.getMessage());
        }
    }

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
