package pe.edu.dentalcite.paciente.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PacienteAccessGuard {

    private static final String RECEPCIONISTA = "SCOPE_RECEPCIONISTA";
    private static final String ADMINISTRADOR = "SCOPE_ADMINISTRADOR";
    private static final String ODONTOLOGO = "SCOPE_ODONTOLOGO";

    private final UsuarioRepository usuarioRepository;
    private final CitaRepository citaRepository;

    public void verificarLectura(UUID fichaId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // Falla cerrado: sin credenciales no se acredita relación alguna.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        if (tieneAutoridad(auth, RECEPCIONISTA) || tieneAutoridad(auth, ADMINISTRADOR)) {
            return;
        }

        Usuario usuario = usuarioAutenticado(auth);

        if (tieneAutoridad(auth, ODONTOLOGO)) {
            if (usuario.getFicha() != null
                    && citaRepository.atendioAPorFichaDelOdontologo(usuario.getFicha().getId(), fichaId)) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No ha atendido a este paciente, así que no puede ver su ficha (RNF-06).");
        }

        if (usuario.getFicha() != null && usuario.getFicha().getId().equals(fichaId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo puede consultar su propia ficha (RNF-06).");
    }

    public UUID odontologoDelListado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        if (tieneAutoridad(auth, RECEPCIONISTA) || tieneAutoridad(auth, ADMINISTRADOR)) {
            return null;
        }
        Usuario usuario = usuarioAutenticado(auth);
        if (usuario.getFicha() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta no está vinculada a un registro de odontólogo.");
        }
        return usuario.getFicha().getId();
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
