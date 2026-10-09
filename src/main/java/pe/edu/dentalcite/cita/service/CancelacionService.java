package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ReglaIncumplidaException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.disponibilidad.service.FranjasCache;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Slf4j
@Service
public class CancelacionService {

    private static final String ROL_PACIENTE = "SCOPE_PACIENTE";

    private static final String AJENA = "No puede cancelar esta cita (RNF-04).";

    private final CitaRepository citaRepository;
    private final CitaHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final FranjasCache franjasCache;
    private final VentanaDeCancelacion ventana;
    private final ZoneId zona;

    public CancelacionService(CitaRepository citaRepository,
            CitaHistorialRepository historialRepository,
            UsuarioRepository usuarioRepository,
            FranjasCache franjasCache,
            VentanaDeCancelacion ventana,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
        this.franjasCache = franjasCache;
        this.ventana = ventana;
        this.zona = ZoneId.of(zonaHoraria);
    }

    @Transactional
    public CitaResponseDTO cancelar(UUID citaId, String motivo) {
        Usuario responsable = responsable();
        boolean esPaciente = tieneAutoridad(ROL_PACIENTE);

        Cita cita = citaRepository.findParaTransicion(citaId)
                .orElseThrow(() -> esPaciente
                        ? new ResponseStatusException(HttpStatus.FORBIDDEN, AJENA)
                        : new ResourceNotFoundException("Cita no encontrada"));

        if (esPaciente) {
            verificarQueEsSuya(cita, responsable);
        }

        if (!Cita.ESTADO_CONFIRMADA.equals(cita.getEstado())) {
            throw new IllegalStateException("La cita " + cita.getCodigo() + " ya esta en estado "
                    + cita.getEstado() + " y no puede cancelarse (RN-09).");
        }

        OffsetDateTime ahora = OffsetDateTime.now(zona);
        if (!esPaciente && !cita.getInicio().isAfter(ahora)) {
            throw new IllegalStateException("La cita " + cita.getCodigo()
                    + " ya empezó: registre su resultado en lugar de cancelarla.");
        }

        if (esPaciente && !ventana.puedeCancelarElPaciente(cita, ahora)) {
            throw new ReglaIncumplidaException(ventana.motivoDelRechazo());
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
                .usuario(responsable)
                .build());

        franjasCache.invalidarTrasCommit();

        log.info("Cita {} cancelada", cita.getCodigo());
        return mapear(cita);
    }

    private void verificarQueEsSuya(Cita cita, Usuario solicitante) {
        if (solicitante == null || solicitante.getFicha() == null
                || !solicitante.getFicha().getId().equals(cita.getFicha().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AJENA);
        }
    }

    private static boolean tieneAutoridad(String autoridad) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> autoridad.equals(a.getAuthority()));
    }

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
