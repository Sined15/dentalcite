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

/**
 * Quién puede leer la ficha de quién (HU-13 · RF-08 · RNF-06).
 *
 * <p>Hermano de {@code OdontologoOwnershipGuard} y con su misma forma, pero no
 * una variante suya: aquel resuelve «este odontólogo es el dueño de este
 * registro», y aquí la pregunta es «este profesional ha tratado a esta
 * persona», que no se contesta comparando fichas sino consultando las citas.
 *
 * <p>Es un {@code @Component} y no una clase de métodos estáticos porque
 * necesita dos repositorios; ahí está la diferencia práctica con su hermano.
 *
 * <p>Vive fuera del controlador a propósito: «sus pacientes» y «la propia
 * ficha» no son expresables como patrón de ruta, así que
 * {@code SecurityConfig} solo puede exigir estar autenticado y la decisión real
 * tiene que ocurrir con la ficha ya en la mano.
 */
@Component
@RequiredArgsConstructor
public class PacienteAccessGuard {

    private static final String RECEPCIONISTA = "SCOPE_RECEPCIONISTA";
    private static final String ADMINISTRADOR = "SCOPE_ADMINISTRADOR";
    private static final String ODONTOLOGO = "SCOPE_ODONTOLOGO";

    private final UsuarioRepository usuarioRepository;
    private final CitaRepository citaRepository;

    /**
     * Rechaza con 403 si quien pide la ficha no puede verla.
     *
     * <p>Se invoca con la ficha ya localizada, para que una ficha inexistente
     * salga como 404 y no como 403: negar el acceso a algo que no existe manda
     * al mostrador a buscar un permiso en vez de un dato mal tecleado.
     */
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
            // El registro de odontólogo comparte la ficha de su cuenta (RN-11), y
            // las citas apuntan al registro, no al usuario: el identificador que
            // hay que preguntar es el del odontólogo, no el de la persona.
            if (usuario.getFicha() != null
                    && citaRepository.atendioAPorFichaDelOdontologo(usuario.getFicha().getId(), fichaId)) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No ha atendido a este paciente, así que no puede ver su ficha (RNF-06).");
        }

        // PACIENTE: solo la propia.
        if (usuario.getFicha() != null && usuario.getFicha().getId().equals(fichaId)) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Solo puede consultar su propia ficha (RNF-06).");
    }

    /**
     * El identificador de odontólogo de quien pide el listado, o {@code null} si
     * no es un odontólogo. Es lo que decide si el listado se restringe a «sus
     * pacientes» (RF-07): recepción y administración buscan en todas las fichas.
     */
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
            // Una cuenta ODONTOLOGO sin ficha no resuelve a ningún registro, así
            // que no tiene pacientes: devolver todas las fichas sería lo contrario
            // de lo que RF-07 concede.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta no está vinculada a un registro de odontólogo.");
        }
        return usuario.getFicha().getId();
    }

    private Usuario usuarioAutenticado(Authentication auth) {
        UUID usuarioId;
        try {
            // El subject del JWT es el UUID del usuario (JwtService.generateToken).
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
