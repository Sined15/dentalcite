package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.disponibilidad.service.FranjasCache;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Cancelación de una cita desde recepción (HU-11 · RF-20, RF-21).
 *
 * <p><strong>Sin ventana.</strong> El criterio es explícito: «dada cualquier cita
 * activa, <em>incluso dentro de las veinticuatro horas previas a su inicio</em>».
 * La ventana de RN-06 solo limita al paciente cancelando lo suyo, que es HU-15;
 * aplicarla aquí rompería la razón de ser de esta operación, porque la
 * cancelación de última hora es precisamente la que recepción atiende.
 *
 * <p>RN-12: nada se borra. La cita conserva su fila íntegra y su baja es el
 * estado CANCELADA, más una entrada en la bitácora con fecha, responsable y
 * motivo (RF-21).
 *
 * <p>Es {@code @Transactional} de escritura, al contrario que {@link CitaService}:
 * aquí no hay nada que reintentar y las tres escrituras —estado, motivo y
 * bitácora— tienen que caer juntas o no caer.
 */
@Slf4j
@Service
public class CancelacionService {

    private final CitaRepository citaRepository;
    private final CitaHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final FranjasCache franjasCache;
    private final ZoneId zona;

    public CancelacionService(CitaRepository citaRepository,
            CitaHistorialRepository historialRepository,
            UsuarioRepository usuarioRepository,
            FranjasCache franjasCache,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
        this.franjasCache = franjasCache;
        this.zona = ZoneId.of(zonaHoraria);
    }

    @Transactional
    public CitaResponseDTO cancelar(UUID citaId, String motivo) {
        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada"));

        // RN-09 es una máquina sin retorno: lo que ya salió de CONFIRMADA no
        // vuelve a entrar. Cancelar dos veces no es idempotente, es un error de
        // quien opera, y silenciarlo ocultaría que otro ya lo hizo.
        if (!Cita.ESTADO_CONFIRMADA.equals(cita.getEstado())) {
            throw new IllegalStateException("La cita " + cita.getCodigo() + " ya esta en estado "
                    + cita.getEstado() + " y no puede cancelarse (RN-09).");
        }

        String estadoAnterior = cita.getEstado();
        cita.setEstado(Cita.ESTADO_CANCELADA);
        cita.setMotivoCancelacion(motivo);
        citaRepository.save(cita);

        historialRepository.save(CitaHistorial.builder()
                .cita(cita)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(Cita.ESTADO_CANCELADA)
                .motivo(motivo)
                .usuario(responsable())
                .build());

        // «La franja volverá a ofrecerse de inmediato» (RN-06). La base ya la
        // liberó —las restricciones de exclusión de V13 son parciales sobre
        // CONFIRMADA—, pero la caché seguiría sin ofrecerla hasta que caducase su
        // TTL, y «de inmediato» no admite un minuto de espera.
        franjasCache.invalidarTrasCommit();

        log.info("Cita {} cancelada", cita.getCodigo());
        return mapear(cita);
    }

    /**
     * Quién cancela (RF-21). Devuelve {@code null} en vez de fallar si el token no
     * resuelve a un usuario: perder la bitácora entera por no poder nombrar al
     * responsable sería peor que registrarla sin él, y la autorización ya la
     * resolvió el filtro de seguridad antes de llegar aquí.
     */
    private Usuario responsable() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return usuarioRepository.findById(UUID.fromString(auth.getName())).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("El subject del token no es un UUID; la transicion se registra sin responsable");
            return null;
        }
    }

    private CitaResponseDTO mapear(Cita cita) {
        OffsetDateTime local = cita.getInicio().atZoneSameInstant(zona).toOffsetDateTime();
        return CitaResponseDTO.builder()
                .id(cita.getId())
                .codigo(cita.getCodigo())
                .fecha(local.toLocalDate())
                .hora(local.toLocalTime())
                .duracionMinutos((int) Duration.between(cita.getInicio(), cita.getFin()).toMinutes())
                .zonaHoraria(zona.getId())
                .estado(cita.getEstado())
                .tratamiento(referencia(cita.getTratamiento().getId(), cita.getTratamiento().getNombre()))
                .odontologo(referencia(cita.getOdontologo().getId(),
                        (cita.getOdontologo().getNombres() + " " + cita.getOdontologo().getApellidos()).trim()))
                .consultorio(referencia(cita.getConsultorio().getId(), cita.getConsultorio().getNombre()))
                .build();
    }

    private static CitaResponseDTO.Referencia referencia(UUID id, String nombre) {
        return CitaResponseDTO.Referencia.builder().id(id).nombre(nombre).build();
    }
}
