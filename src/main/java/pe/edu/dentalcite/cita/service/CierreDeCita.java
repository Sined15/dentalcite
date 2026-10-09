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
import pe.edu.dentalcite.odontologo.service.OdontologoOwnershipGuard;
import pe.edu.dentalcite.plan.service.EnlaceDeSesiones;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class CierreDeCita {

    private static final Set<String> RESULTADOS =
            Set.of(Cita.ESTADO_ATENDIDA, Cita.ESTADO_NO_ASISTIO);

    private static final Set<String> ROLES_PRIVILEGIADOS =
            Set.of("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR");

    private final CitaRepository citaRepository;
    private final CitaHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;

    private final EnlaceDeSesiones enlaceDeSesiones;
    private final ZoneId zona;

    public CierreDeCita(CitaRepository citaRepository,
            CitaHistorialRepository historialRepository,
            UsuarioRepository usuarioRepository,
            EnlaceDeSesiones enlaceDeSesiones,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
        this.enlaceDeSesiones = enlaceDeSesiones;
        this.zona = ZoneId.of(zonaHoraria);
    }

    @Transactional
    public CitaResponseDTO registrarResultado(UUID citaId, String resultado) {
        String estadoNuevo = normalizarResultado(resultado);

        Cita cita = citaRepository.findParaTransicion(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada"));

        OdontologoOwnershipGuard.verificar(usuarioRepository, cita.getOdontologo(),
                ROLES_PRIVILEGIADOS, true,
                "No puede registrar el resultado de esta cita.");

        if (!Cita.ESTADO_CONFIRMADA.equals(cita.getEstado())) {
            throw new IllegalStateException("La cita " + cita.getCodigo() + " ya esta en estado "
                    + cita.getEstado() + ", que es final, y su resultado no puede cambiarse (RN-09).");
        }

        OffsetDateTime ahora = OffsetDateTime.now(zona);
        if (!cita.getFin().isBefore(ahora)) {
            throw new IllegalStateException("La cita " + cita.getCodigo()
                    + " todavia no ha terminado, asi que aun no hay resultado que registrar."
                    + " Estara pendiente de cierre a partir de su hora de fin (RN-09).");
        }

        String estadoAnterior = cita.getEstado();
        cita.setEstado(estadoNuevo);
        citaRepository.save(cita);

        historialRepository.save(CitaHistorial.builder()
                .cita(cita)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(estadoNuevo)
                .usuario(responsable())
                .build());

        CitaResponseDTO respuesta = mapear(cita);

        if (Cita.ESTADO_ATENDIDA.equals(estadoNuevo)) {
            enlaceDeSesiones.enlazarCitaAtendida(cita).ifPresent(ocupada ->
                    respuesta.setSesionEnlazada(CitaResponseDTO.SesionEnlazada.builder()
                            .planId(ocupada.planId())
                            .numero(ocupada.numero())
                            .tratamiento(ocupada.tratamiento())
                            .build()));
        }

        log.info("Cita {} cerrada como {}", cita.getCodigo(), estadoNuevo);
        return respuesta;
    }

    private static String normalizarResultado(String resultado) {
        if (resultado == null || resultado.isBlank()) {
            throw new IllegalArgumentException("El resultado es obligatorio");
        }
        String normalizado = resultado.trim().toUpperCase();
        if (!RESULTADOS.contains(normalizado)) {
            throw new IllegalArgumentException("Resultado no valido: " + resultado
                    + ". Los resultados son " + Cita.ESTADO_ATENDIDA + " y "
                    + Cita.ESTADO_NO_ASISTIO + " (RF-22).");
        }
        return normalizado;
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
