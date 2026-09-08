package pe.edu.dentalcite.usuario.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.auth.service.TokenRevocationCache;
import pe.edu.dentalcite.usuario.api.dto.CambioPasswordRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioReplaceRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioResponseDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioUpdateRequestDTO;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private static final String ROL_ODONTOLOGO = "ODONTOLOGO";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenRevocationCache tokenRevocationCache;
    private final pe.edu.dentalcite.auth.service.AuthService authService;
    private final pe.edu.dentalcite.ficha.repository.FichaRepository fichaRepository;

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<UsuarioResponseDTO> listarUsuarios(
            org.springframework.data.domain.Pageable pageable) {
        return usuarioRepository.findAll(sanitizarPaginacion(pageable))
                .map(this::mapToDTO);
    }

        private org.springframework.data.domain.Pageable sanitizarPaginacion(
            org.springframework.data.domain.Pageable pageable) {
        Set<String> propiedadesPermitidas = new HashSet<>(Set.of(
            "id", "nombre", "correo", "rol", "activo", "requiereCambioPassword", "tokensValidosDesde"));
            List<Sort.Order> ordenes = new ArrayList<>();
            pageable.getSort().forEach(order -> {
                String valor = order.getProperty().replace("[", "").replace("]", "")
                    .replace("\"", "").trim();
                String[] partes = valor.split(",");
                String propiedad = partes[0].trim();
                if (propiedadesPermitidas.contains(propiedad)) {
                Sort.Direction direccion = partes.length > 1
                    ? Sort.Direction.fromOptionalString(partes[1].trim()).orElse(order.getDirection())
                    : order.getDirection();
                ordenes.add(new Sort.Order(direccion, propiedad));
                }
            });
            Sort sort = Sort.by(ordenes);
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
        }

    @Transactional
    public UsuarioResponseDTO crearUsuario(UsuarioRequestDTO request) {
        String correo = normalizarCorreo(request.getCorreo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new IllegalStateException("Ya existe una cuenta con este correo electrónico.");
        }

        boolean laIndicaElAdministrador = request.getPasswordProvisional() != null
                && !request.getPasswordProvisional().isBlank();
        String rawPassword = laIndicaElAdministrador
                ? request.getPasswordProvisional()
                : generarPasswordAleatorio();

        Usuario usuario = Usuario.builder()
                .nombre(request.getNombre().trim())
                .correo(correo)
                .rol(request.getRol())
                .contrasenaHash(passwordEncoder.encode(rawPassword))
                .activo(true)
                .requiereCambioPassword(true) // Al crearlo por admin, requiere cambio obligatorio
                .tokensValidosDesde(OffsetDateTime.now())
                .ficha(resolverFichaDelPersonal(request))
                .build();

        UsuarioResponseDTO respuesta = mapToDTO(usuarioRepository.save(usuario));

        if (!laIndicaElAdministrador) {
            // HU-04 crea la cuenta «con correo, nombre y uno de los cuatro roles», sin
            // contraseña, y exige que «podrá iniciar sesión». La generada solo existe
            // en claro en este instante —después es un hash irreversible—, así que si
            // no se devuelve aquí nadie puede entrar nunca a la cuenta recién creada.
            // Cuando la indica el administrador no se le repite: ya la conoce.
            respuesta.setPasswordProvisional(rawPassword);
        }

        return respuesta;
    }

    /**
     * Ficha de la persona a la que pertenece la cuenta del personal.
     *
     * <p>Sin esto, el alta que HU-04 describe creaba cuentas sin ficha, y RF-10
     * exige una para registrar al profesional: el único camino para dar de alta un
     * odontólogo era que la persona se registrase antes por el portal público
     * —que fuerza rol PACIENTE— y que el administrador le cambiase el rol después.
     * Peor aún, una cuenta sin ficha jamás resuelve a su registro de odontólogo,
     * así que {@code OdontologoOwnershipGuard} la rechazaba siempre y nunca podría
     * declarar su horario ni sus bloqueos (Tabla 10, marcas «propio»).
     *
     * <p>Solo es obligatoria para ODONTOLOGO, que es el rol cuyo permiso depende
     * del vínculo. Recepción y administración no necesitan historia clínica: se
     * les crea la ficha si el administrador indica el documento, y no si lo omite.
     *
     * <p>Aplica la misma regla que {@code AuthService.registrarPaciente}: si ya
     * existe una ficha con ese par (tipo, número) se vincula en lugar de
     * duplicarla (RN-10), y si esa ficha ya tiene cuenta se rechaza (RN-11). La
     * ficha nueva nace sin nombres porque el alta solo recoge un {@code nombre}
     * para la cuenta; los del profesional los aporta {@code POST /odontologos},
     * y los del paciente, el registro del portal, que completa los huecos.
     */
    private pe.edu.dentalcite.ficha.domain.Ficha resolverFichaDelPersonal(UsuarioRequestDTO request) {
        boolean traeDocumento = request.getDocumento() != null && !request.getDocumento().isBlank();

        if (!traeDocumento) {
            if (ROL_ODONTOLOGO.equals(request.getRol())) {
                throw new IllegalArgumentException(
                        "Una cuenta de rol ODONTOLOGO necesita el documento de la persona para crear su ficha (RN-11).");
            }
            return null;
        }

        String tipoDocumento = request.getTipoDocumento() == null || request.getTipoDocumento().isBlank()
                ? "DNI"
                : request.getTipoDocumento();
        String documento = request.getDocumento().trim();

        java.util.Optional<pe.edu.dentalcite.ficha.domain.Ficha> existente =
                fichaRepository.findByTipoDocumentoAndDocumento(tipoDocumento, documento);

        if (existente.isPresent()) {
            pe.edu.dentalcite.ficha.domain.Ficha ficha = existente.get();
            if (usuarioRepository.existsByFichaId(ficha.getId())) {
                throw new IllegalStateException("La ficha de esa persona ya tiene una cuenta asociada (RN-11).");
            }
            return ficha;
        }

        // El correlativo sale de la secuencia, nunca de un conteo (RN-10).
        String numeroHistoria = String.format("HC-%05d", fichaRepository.getNextHistoriaClinica());
        return fichaRepository.save(pe.edu.dentalcite.ficha.domain.Ficha.builder()
                .tipoDocumento(tipoDocumento)
                .documento(documento)
                .numeroHistoria(numeroHistoria)
                .build());
    }

    /**
     * Lectura de una cuenta cualquiera por el ADMINISTRADOR (Tabla 10: {@code R}
     * sobre «cuentas de usuario y roles»). Distinta de
     * {@link #obtenerPerfilPropio(UUID)}, que cada rol ejerce sobre la suya.
     */
    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerUsuario(UUID id) {
        return usuarioRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerPerfilPropio(UUID id) {
        return usuarioRepository.findById(id)
                .map(this::mapToDTO)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
    }

    @Transactional
    public UsuarioResponseDTO actualizarUsuario(UUID id, UsuarioUpdateRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        boolean tokensInvalidos = false;

        if (request.getRol() != null && !request.getRol().equals(usuario.getRol())) {
            usuario.setRol(request.getRol());
            tokensInvalidos = true;
        }

        if (request.getActivo() != null && !request.getActivo().equals(usuario.getActivo())) {
            usuario.setActivo(request.getActivo());
            tokensInvalidos = true;
        }

        // El nombre no afecta a la autorización, así que no invalida los tokens.
        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            usuario.setNombre(request.getNombre().trim());
        }

        if (tokensInvalidos) {
            // Invalida inmediatamente cualquier token emitido previamente
            usuario.revocarTokensVigentes();
            tokenRevocationCache.invalidarTrasCommit(usuario.getId());
        }

        return mapToDTO(usuarioRepository.save(usuario));
    }

    /**
     * PUT: reemplaza el recurso completo. A diferencia de {@link #actualizarUsuario},
     * ambos campos del DTO son obligatorios (ver {@link UsuarioReplaceRequestDTO}) y
     * se asignan siempre, sin el chequeo de "si viene null, no tocar" propio de PATCH.
     */
    @Transactional
    public UsuarioResponseDTO reemplazarUsuario(UUID id, UsuarioReplaceRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        boolean tokensInvalidos = !request.getRol().equals(usuario.getRol())
                || !request.getActivo().equals(usuario.getActivo());

        usuario.setNombre(request.getNombre().trim());
        usuario.setRol(request.getRol());
        usuario.setActivo(request.getActivo());

        if (tokensInvalidos) {
            // Invalida inmediatamente cualquier token emitido previamente
            usuario.revocarTokensVigentes();
            tokenRevocationCache.invalidarTrasCommit(usuario.getId());
        }

        return mapToDTO(usuarioRepository.save(usuario));
    }

    @Transactional
    public void asignarPasswordProvisional(UUID id, String nuevoPasswordTemporal) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));

        usuario.setContrasenaHash(passwordEncoder.encode(nuevoPasswordTemporal));
        usuario.setRequiereCambioPassword(true);
        usuario.revocarTokensVigentes(); // Expulsar sesiones activas
        tokenRevocationCache.invalidarTrasCommit(usuario.getId());
        usuarioRepository.save(usuario);

        // HU-04: «podrá entrar con ella». El caso típico es justamente el de quien
        // perdió su contraseña y la falló tres veces, así que la cuenta suele llegar
        // aquí bloqueada por RNF-05: sin levantar ese bloqueo, la clave nueva seguía
        // recibiendo 423 hasta que expirase la ventana de cinco minutos.
        authService.desbloquearCuenta(usuario);
    }

    @Transactional
    public void cambiarPasswordPropio(UUID id, CambioPasswordRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        if (!passwordEncoder.matches(request.getPasswordActual(), usuario.getContrasenaHash())) {
            throw new IllegalStateException("La contraseña actual es incorrecta.");
        }

        usuario.setContrasenaHash(passwordEncoder.encode(request.getNuevoPassword()));
        usuario.setRequiereCambioPassword(false);
        // Al cambiar contraseña propia se invalidan sesiones previas (por seguridad)
        usuario.revocarTokensVigentes();
        tokenRevocationCache.invalidarTrasCommit(usuario.getId());
        usuarioRepository.save(usuario);
    }

    /**
     * Contraseña provisional aleatoria cuando el administrador no indica una. Se
     * toman 12 caracteres del UUID (antes 8) para no quedar por debajo del mínimo
     * que exige PasswordProvisionalRequestDTO en el resto del flujo.
     */
    private String generarPasswordAleatorio() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    /**
     * Normaliza un correo para comparación/almacenamiento (trim + minúsculas), de
     * forma consistente con AuthService, evitando que "User@x.com" y "user@x.com"
     * se traten como cuentas distintas.
     */
    private String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private UsuarioResponseDTO mapToDTO(Usuario usuario) {
        return UsuarioResponseDTO.builder()
                .id(usuario.getId())
                .nombre(usuario.getNombre())
                .correo(usuario.getCorreo())
                .rol(usuario.getRol())
                .activo(usuario.getActivo())
                .requiereCambioPassword(usuario.getRequiereCambioPassword())
                .tokensValidosDesde(usuario.getTokensValidosDesde())
                // Leer el identificador no inicializa el proxy de la ficha.
                .fichaId(usuario.getFicha() == null ? null : usuario.getFicha().getId())
                .build();
    }
}
