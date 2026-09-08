package pe.edu.dentalcite.odontologo.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.Set;
import java.util.UUID;

/**
 * Verificación de propiedad/autorización sobre un {@link Odontologo}, compartida
 * por HorarioService y BloqueoService (antes duplicada casi textualmente en
 * ambos): los roles privilegiados indicados siempre pueden operar; cualquier
 * otro usuario autenticado solo puede operar sobre su propio registro de
 * odontólogo (misma ficha).
 */
public final class OdontologoOwnershipGuard {

    private OdontologoOwnershipGuard() {
    }

    /**
     * @param odontologo         recurso sobre el que se quiere operar; puede ser
     *                           {@code null} cuando la operación no está atada a
     *                           un odontólogo específico (p. ej. un bloqueo de
     *                           consultorio).
     * @param rolesPrivilegiados autoridades SCOPE_* que pueden operar sin ser el
     *                           dueño del recurso.
     * @param exigirOdontologo   si es {@code true}, un usuario no privilegiado no
     *                           puede operar cuando {@code odontologo} es
     *                           {@code null}.
     * @param mensajeSinOdontologo mensaje a usar cuando se rechaza por
     *                           {@code exigirOdontologo}.
     */
    public static void verificar(UsuarioRepository usuarioRepository, Odontologo odontologo,
            Set<String> rolesPrivilegiados, boolean exigirOdontologo, String mensajeSinOdontologo) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // Falla cerrado: sin credenciales no se puede acreditar propiedad sobre
            // ningun recurso, asi que la ausencia de autenticacion se rechaza en vez
            // de dejar pasar la operacion.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }

        boolean esPrivilegiado = auth.getAuthorities().stream()
                .anyMatch(a -> rolesPrivilegiados.contains(a.getAuthority()));
        if (esPrivilegiado) {
            return;
        }

        if (odontologo == null) {
            if (exigirOdontologo) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensajeSinOdontologo);
            }
            return;
        }

        // El subject del JWT es el UUID del usuario (JwtService.generateToken), no su
        // correo: resolverlo por correo dejaba esta comprobacion muerta y devolvia 401
        // a todo odontologo que operase sobre su propio registro.
        UUID usuarioId;
        try {
            usuarioId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        if (usuario.getFicha() == null || !usuario.getFicha().getId().equals(odontologo.getFicha().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No tiene permisos para operar sobre este odontólogo");
        }
    }
}
