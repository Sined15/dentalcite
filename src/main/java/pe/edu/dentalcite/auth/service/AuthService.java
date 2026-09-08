package pe.edu.dentalcite.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.auth.api.dto.LoginRequest;
import pe.edu.dentalcite.auth.api.dto.RegistroRequest;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.consentimiento.repository.ConsentimientoRepository;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final FichaRepository fichaRepository;
    private final ConsentimientoRepository consentimientoRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;

    /** RNF-05: tres intentos fallidos consecutivos bloquean la cuenta cinco minutos. */
    private static final int MAX_INTENTOS_FALLIDOS = 3;
    private static final Duration BLOQUEO_LOGIN = Duration.ofMinutes(5);
    private final JwtService jwtService;
    private final TokenRevocationCache tokenRevocationCache;

    /**
     * Normaliza un correo para comparación/almacenamiento (trim + minúsculas), de
     * forma que "User@Example.com" y "user@example.com" se traten como la misma
     * cuenta en vez de crear dos registros distintos bajo la restricción UNIQUE
     * (case-sensitive) de la columna.
     */
    private String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }

    @Transactional
    public void registrarPaciente(RegistroRequest request) {
        String correo = normalizarCorreo(request.getCorreo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new IllegalStateException("Ya existe un usuario con ese correo.");
        }

        String tipoDocumento = request.getTipoDocumento() == null || request.getTipoDocumento().isBlank()
                ? "DNI"
                : request.getTipoDocumento();

        Optional<Ficha> fichaOpt = fichaRepository.findByTipoDocumentoAndDocumento(tipoDocumento,
                request.getDocumento());
        Ficha ficha;

        if (fichaOpt.isPresent()) {
            ficha = fichaOpt.get();
            // RN-11: una ficha admite como máximo una cuenta
            final UUID targetFichaId = ficha.getId();
            boolean hasUser = usuarioRepository.existsByFichaId(targetFichaId);

            if (hasUser) {
                // RN-11: Una ficha que ya tiene cuenta no puede usarse para otro registro ->
                // 409
                throw new IllegalStateException("La ficha ya tiene una cuenta asociada.");
            }

            // La ficha que la clínica ya tenía suele venir de un alta presencial con
            // datos incompletos: el registro es la ocasión de completarla. Antes se
            // descartaba en silencio todo lo que el visitante enviaba.
            if (request.getTelefono() != null && !request.getTelefono().isBlank()) {
                ficha.setTelefono(request.getTelefono());
            }
            if (ficha.getNombres() == null || ficha.getNombres().isBlank()) {
                ficha.setNombres(request.getNombres().trim());
            }
            if (ficha.getApellidos() == null || ficha.getApellidos().isBlank()) {
                ficha.setApellidos(request.getApellidos().trim());
            }
            ficha = fichaRepository.save(ficha);
        } else {
            // Generar nuevo numero de historia correlativo usando secuencia de base de
            // datos
            // para evitar condiciones de carrera.
            long count = fichaRepository.getNextHistoriaClinica();
            String numeroHistoria = String.format("HC-%05d", count);

            ficha = Ficha.builder()
                    .tipoDocumento(tipoDocumento)
                    .documento(request.getDocumento())
                    .nombres(request.getNombres().trim())
                    .apellidos(request.getApellidos().trim())
                    .telefono(request.getTelefono())
                    .numeroHistoria(numeroHistoria)
                    .build();
            ficha = fichaRepository.save(ficha);
        }

        Usuario nuevoUsuario = Usuario.builder()
                .nombre((request.getNombres().trim() + " " + request.getApellidos().trim()).trim())
                .correo(correo)
                .contrasenaHash(passwordEncoder.encode(request.getPassword()))
                .rol("PACIENTE")
                .activo(true)
                .ficha(ficha)
                .build();

        nuevoUsuario = usuarioRepository.save(nuevoUsuario);

        Consentimiento consentimiento = Consentimiento.builder()
                .usuario(nuevoUsuario)
                .versionTexto(request.getVersionConsentimiento())
                .build();

        consentimientoRepository.save(consentimiento);
    }

    /**
     * {@code noRollbackFor} no es un detalle: sin él, RNF-05 quedaba solo en Redis.
     * El conteo de intentos fallidos se escribe y a continuación el método lanza
     * {@link BadCredentialsException} o {@link LockedException}; al ser
     * excepciones no verificadas, el interceptor transaccional revertía la
     * transacción y el {@code UPDATE} sobre {@code intentos_fallidos} y
     * {@code bloqueado_hasta} se perdía. PostgreSQL nunca llegaba a ser la fuente
     * de verdad que RNF-05 exige, y con Redis detenido no había bloqueo alguno.
     * Todo lo que este método escribe —el contador al fallar, su reinicio al
     * acertar— es precisamente lo que debe sobrevivir al rechazo del login.
     */
    @Transactional(noRollbackFor = {
            BadCredentialsException.class, LockedException.class, DisabledException.class })
    public String login(LoginRequest request) {
        String correo = normalizarCorreo(request.getCorreo());
        String lockKey = "login_attempts:" + correo;

        // 1. Camino rápido: bloqueo cacheado en Redis (RNF-05)
        if (bloqueadoSegunCache(lockKey)) {
            throw new LockedException("Cuenta bloqueada temporalmente por múltiples intentos fallidos.");
        }

        Usuario usuario = usuarioRepository.findByCorreo(correo)
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

        // 2. Fuente de verdad persistente: el bloqueo sigue vigente aunque Redis
        // esté caído (a diferencia del chequeo anterior, que fallaba abierto).
        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(OffsetDateTime.now())) {
                throw new LockedException("Cuenta bloqueada temporalmente por múltiples intentos fallidos.");
            }
            // El bloqueo caducó: RNF-05 concede tres intentos, no uno. Sin este
            // reinicio el contador seguía en el máximo pasada la ventana, de modo
            // que el primer fallo posterior volvía a bloquear la cuenta otros cinco
            // minutos, y así indefinidamente.
            resetearIntentosFallidos(usuario, lockKey);
        }

        if (!usuario.getActivo()) {
            throw new DisabledException("La cuenta está desactivada.");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getContrasenaHash())) {
            if (registrarIntentoFallidoYBloquear(usuario, lockKey)) {
                throw new LockedException("Cuenta bloqueada temporalmente por múltiples intentos fallidos.");
            }
            // Retorna 401 sin decir si falló correo o contraseña
            throw new BadCredentialsException("Credenciales incorrectas");
        }

        // Éxito: resetear contador
        resetearIntentosFallidos(usuario, lockKey);

        return jwtService.generateToken(usuario);
    }

    private boolean bloqueadoSegunCache(String lockKey) {
        try {
            return "LOCKED".equals(redisTemplate.opsForValue().get(lockKey));
        } catch (Exception e) {
            log.warn("Redis no disponible para leer intentos de login, se usará PostgreSQL como respaldo: {}",
                    e.getMessage());
            return false;
        }
    }

    /**
     * Registra un intento fallido de login. PostgreSQL es la fuente de verdad
     * (RNF-05): el conteo y el bloqueo siguen vigentes aunque Redis esté caído,
     * a diferencia del chequeo original que fallaba abierto sin ningún respaldo.
     * Redis se usa además como caché rápida best-effort, con INCR atómico para
     * evitar la condición de carrera de un read-then-write entre solicitudes
     * concurrentes.
     *
     * @return true si la cuenta quedó bloqueada como resultado de este intento.
     */
    private boolean registrarIntentoFallidoYBloquear(Usuario usuario, String lockKey) {
        int intentos = usuario.getIntentosFallidos() + 1;
        usuario.setIntentosFallidos(intentos);

        boolean bloqueado = intentos >= MAX_INTENTOS_FALLIDOS;
        if (bloqueado) {
            usuario.setBloqueadoHasta(OffsetDateTime.now().plus(BLOQUEO_LOGIN));
        }
        usuarioRepository.save(usuario);

        try {
            Long attempts = redisTemplate.opsForValue().increment(lockKey);
            if (attempts != null && attempts == 1L) {
                // Primer intento fallido de la ventana: fijar el TTL del bloqueo.
                redisTemplate.expire(lockKey, BLOQUEO_LOGIN);
            }
            if (bloqueado) {
                redisTemplate.opsForValue().set(lockKey, "LOCKED", BLOQUEO_LOGIN);
            }
        } catch (Exception e) {
            log.warn("Redis no disponible para cachear intento fallido: {}", e.getMessage());
        }

        return bloqueado;
    }

    private void resetearIntentosFallidos(Usuario usuario, String lockKey) {
        if (usuario.getIntentosFallidos() != 0 || usuario.getBloqueadoHasta() != null) {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuarioRepository.save(usuario);
        }

        try {
            redisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Redis no disponible para limpiar intentos de login: {}", e.getMessage());
        }
    }

    /**
     * Levanta el bloqueo por intentos fallidos de una cuenta, en PostgreSQL y en la
     * caché. Lo usa el administrador al entregar una contraseña provisional (HU-04:
     * «podrá entrar con ella»).
     *
     * <p>Vive aquí, y no en {@code UsuarioService}, porque la clave
     * {@code login_attempts:{correo}} es un detalle de este servicio: limpiar solo
     * las columnas de PostgreSQL dejaría la cuenta rechazada con 423 hasta que
     * expirase el TTL de la marca en Redis, que {@code login} consulta primero.
     */
    @Transactional
    public void desbloquearCuenta(Usuario usuario) {
        resetearIntentosFallidos(usuario, "login_attempts:" + normalizarCorreo(usuario.getCorreo()));
    }

    @Transactional
    public void logout(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        // RF-03: Invalidar instantáneamente cualquier token emitido actualizando la
        // fecha
        usuario.revocarTokensVigentes();
        usuarioRepository.save(usuario);
        // Sin esto la caché positiva de vigencia seguiría aceptando el token
        // cerrado hasta cinco minutos (HU-03).
        tokenRevocationCache.invalidarTrasCommit(usuarioId);
    }
}
