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
    private static final int MAX_INTENTOS_FALLIDOS = 3;
    private static final Duration BLOQUEO_LOGIN = Duration.ofMinutes(5);
    private final JwtService jwtService;
    private final TokenRevocationCache tokenRevocationCache;

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
                : request.getTipoDocumento().trim();
        String documento = request.getDocumento().trim();

        Optional<Ficha> fichaOpt = fichaRepository.findByTipoDocumentoAndDocumento(tipoDocumento, documento);
        Ficha ficha;

        if (fichaOpt.isPresent()) {
            ficha = fichaOpt.get();
            // RN-11: una ficha admite como máximo una cuenta
            final UUID targetFichaId = ficha.getId();
            boolean hasUser = usuarioRepository.existsByFichaId(targetFichaId);

            if (hasUser) {
                throw new IllegalStateException("La ficha ya tiene una cuenta asociada.");
            }
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
            long count = fichaRepository.getNextHistoriaClinica();
            String numeroHistoria = String.format("HC-%05d", count);

            ficha = Ficha.builder()
                    .tipoDocumento(tipoDocumento)
                    .documento(documento)
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
                .ficha(ficha)
                .versionTexto(request.getVersionConsentimiento())
                .build();

        consentimientoRepository.save(consentimiento);
    }

    @Transactional(noRollbackFor = {
            BadCredentialsException.class, LockedException.class, DisabledException.class })
    public String login(LoginRequest request) {
        String correo = normalizarCorreo(request.getCorreo());
        String lockKey = "login_attempts:" + correo;

        if (bloqueadoSegunCache(lockKey)) {
            throw new LockedException("Cuenta bloqueada temporalmente por múltiples intentos fallidos.");
        }

        Usuario usuario = usuarioRepository.findParaLoginByCorreo(correo)
                .orElseThrow(() -> new BadCredentialsException("Credenciales incorrectas"));

        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(OffsetDateTime.now())) {
                throw new LockedException("Cuenta bloqueada temporalmente por múltiples intentos fallidos.");
            }
            resetearIntentosFallidos(usuario, lockKey);
        }

        if (!usuario.getActivo()) {
            throw new DisabledException("La cuenta está desactivada.");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getContrasenaHash())) {
            if (registrarIntentoFallidoYBloquear(usuario, lockKey)) {
                throw new LockedException("Cuenta bloqueada temporalmente por múltiples intentos fallidos.");
            }
            throw new BadCredentialsException("Credenciales incorrectas");
        }
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

    @Transactional
    public void desbloquearCuenta(Usuario usuario) {
        resetearIntentosFallidos(usuario, "login_attempts:" + normalizarCorreo(usuario.getCorreo()));
    }

    @Transactional
    public void logout(UUID usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        usuario.revocarTokensVigentes();
        usuarioRepository.save(usuario);
        tokenRevocationCache.invalidarTrasCommit(usuarioId);
    }
}
