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

/**
 * Para quién se reserva y quién lo pide (HU-09 · RF-15, HU-14 · RF-17 · RNF-04).
 *
 * <p>Hasta HU-14 esto era un método privado de {@link CitaService} que solo
 * sabía decir «la ficha del token». Con la reserva en nombre de terceros deja de
 * ser una línea y pasa a ser <em>la</em> regla de autorización de la operación,
 * así que vive donde se puede leer y probar sola.
 *
 * <table>
 *   <caption>Quién puede reservar para quién</caption>
 *   <tr><th>Rol</th><th>{@code pacienteId}</th><th>Resultado</th></tr>
 *   <tr><td>PACIENTE</td><td>ausente</td><td>su propia ficha</td></tr>
 *   <tr><td>PACIENTE</td><td>presente</td><td>403</td></tr>
 *   <tr><td>RECEPCIONISTA / ADMINISTRADOR</td><td>presente</td><td>esa ficha, 404 si no existe</td></tr>
 *   <tr><td>RECEPCIONISTA / ADMINISTRADOR</td><td>ausente</td><td>400</td></tr>
 *   <tr><td>ODONTOLOGO</td><td>cualquiera</td><td>403</td></tr>
 * </table>
 *
 * <p>Al PACIENTE se le rechaza el campo <strong>aunque coincida con su propia
 * ficha</strong>. Aceptarlo cuando coincide y negarlo cuando no convierte la
 * respuesta en un oráculo: probando identificadores se averiguaría cuál es el
 * de otro paciente. Sin ese campo no hay nada que sondear.
 *
 * <p>La ficha se resuelve contra {@link FichaRepository} y no contra las
 * cuentas, que es lo que hace que el segundo criterio de HU-14 —«un paciente sin
 * cuenta de acceso»— funcione sin ningún caso especial: una ficha sin
 * {@code Usuario} es exactamente lo que crea el alta presencial de HU-12.
 *
 * <p>Vive en {@code cita.service} y no en {@code config} por la misma razón que
 * {@link ReglasDeReserva}: la regla de cobertura del {@code pom.xml} solo alcanza
 * {@code *.domain} y {@code *.service}, y en {@code config} esta decisión
 * escaparía de la puerta de RNF-10.
 */
@Component
@RequiredArgsConstructor
public class AutorDeLaReserva {

    private static final String PACIENTE = "SCOPE_PACIENTE";
    private static final String RECEPCIONISTA = "SCOPE_RECEPCIONISTA";
    private static final String ADMINISTRADOR = "SCOPE_ADMINISTRADOR";

    private final UsuarioRepository usuarioRepository;
    private final FichaRepository fichaRepository;

    /**
     * Para quién es la cita y quién la encarga.
     *
     * <p>Se devuelven identificadores y no entidades porque quien llama
     * ({@link CitaService}) corre fuera de toda transacción: una entidad quedaría
     * desligada de su sesión y su proxy no sobreviviría al salto a la transacción
     * del INSERT.
     *
     * @param fichaId de quién es la cita
     * @param usuarioId quién la pidió, para {@code citas.creado_por_usuario_id}
     */
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
        // ODONTOLOGO y cualquier rol futuro: la Tabla 10 no les concede reservar.
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Su rol no puede reservar citas (RNF-04).");
    }

    /** HU-09: el paciente reserva para sí mismo, y su ficha sale del token. */
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
        // Leer el identificador no inicializa el proxy, así que es seguro fuera de
        // la sesión de persistencia.
        return new Reserva(usuario.getFicha().getId(), usuario.getId());
    }

    /** HU-14 · RF-17: recepción reserva para cualquier paciente registrado. */
    private Reserva enNombreDeOtro(Authentication auth, UUID pacienteId) {
        if (pacienteId == null) {
            // No es un 403: el rol sí puede reservar, lo que falta es decir para
            // quién. Su cuenta no tiene ficha propia, así que no hay nada que
            // suponer.
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
