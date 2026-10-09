package pe.edu.dentalcite.cita.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class AutorDeLaReserva {

    private static final String PACIENTE = "SCOPE_PACIENTE";
    private static final String RECEPCIONISTA = "SCOPE_RECEPCIONISTA";
    private static final String ADMINISTRADOR = "SCOPE_ADMINISTRADOR";

    private final UsuarioRepository usuarioRepository;
    private final FichaRepository fichaRepository;

    public record Reserva(UUID fichaId, UUID usuarioId) {
    }

    public Reserva resolver(UUID pacienteId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        if (tieneAutoridad(auth, PACIENTE)) {
            return paraSiMismo(auth, pacienteId);
        }
        if (tieneAutoridad(auth, RECEPCIONISTA) || tieneAutoridad(auth, ADMINISTRADOR)) {
            return enNombreDeOtro(auth, pacienteId);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Su rol no puede reservar citas (RNF-04).");
    }

    private Reserva paraSiMismo(Authentication auth, UUID pacienteId) {
        if (pacienteId != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puede reservar para usted mismo; para reservar en nombre de otra persona"
                            + " diríjase a recepción (RNF-04).");
        }
        Usuario usuario = usuarioAutenticado(auth);
        if (usuario.getFicha() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta no tiene una ficha clínica asociada");
        }
        return new Reserva(usuario.getFicha().getId(), usuario.getId());
    }

    private Reserva enNombreDeOtro(Authentication auth, UUID pacienteId) {
        if (pacienteId == null) {
            throw new IllegalArgumentException(
                    "Indique el paciente para el que reserva (pacienteId).");
        }
        if (!fichaRepository.existsById(pacienteId)) {
            throw new ResourceNotFoundException("Paciente no encontrado");
        }
        return new Reserva(pacienteId, usuarioAutenticado(auth).getId());
    }

    private Usuario usuarioAutenticado(Authentication auth) {
        UUID usuarioId;
        try {
            usuarioId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado");
        }
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));
    }

    private static boolean tieneAutoridad(Authentication auth, String autoridad) {
        return auth.getAuthorities().stream().anyMatch(a -> autoridad.equals(a.getAuthority()));
    }
}
