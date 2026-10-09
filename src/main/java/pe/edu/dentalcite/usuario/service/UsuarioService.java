package pe.edu.dentalcite.usuario.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private static final String ROL_PACIENTE = "PACIENTE";

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
            respuesta.setPasswordProvisional(rawPassword);
        }

        return respuesta;
    }

    private pe.edu.dentalcite.ficha.domain.Ficha resolverFichaDelPersonal(UsuarioRequestDTO request) {
        boolean traeDocumento = request.getDocumento() != null && !request.getDocumento().isBlank();

        if (!traeDocumento) {
            if (ROL_ODONTOLOGO.equals(request.getRol())) {
                throw new IllegalArgumentException(
                        "Una cuenta de rol ODONTOLOGO necesita el documento de la persona para crear su ficha (RN-11).");
            }
            if (ROL_PACIENTE.equals(request.getRol())) {
                throw new IllegalArgumentException(
                        "Una cuenta de rol PACIENTE necesita el documento del paciente para encontrar su ficha.");
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

        if (ROL_PACIENTE.equals(request.getRol())) {
            throw new IllegalStateException(
                    "No hay ningún paciente registrado con ese documento: regístrelo primero como paciente"
                            + " y vuelva a crear su cuenta.");
        }

        String numeroHistoria = String.format("HC-%05d", fichaRepository.getNextHistoriaClinica());
        return fichaRepository.save(pe.edu.dentalcite.ficha.domain.Ficha.builder()
                .tipoDocumento(tipoDocumento)
                .documento(documento)
                .numeroHistoria(numeroHistoria)
                .build());
    }

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
        verificarQueNoSeRetiraASiMismo(usuario, request.getRol(), request.getActivo());

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

    @Transactional
    public UsuarioResponseDTO reemplazarUsuario(UUID id, UsuarioReplaceRequestDTO request) {
        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con ID: " + id));
        verificarQueNoSeRetiraASiMismo(usuario, request.getRol(), request.getActivo());

        boolean tokensInvalidos = !request.getRol().equals(usuario.getRol())
                || !request.getActivo().equals(usuario.getActivo());

        usuario.setNombre(request.getNombre().trim());
        usuario.setRol(request.getRol());
        usuario.setActivo(request.getActivo());

        if (tokensInvalidos) {
            usuario.revocarTokensVigentes();
            tokenRevocationCache.invalidarTrasCommit(usuario.getId());
        }

        return mapToDTO(usuarioRepository.save(usuario));
    }

    private void verificarQueNoSeRetiraASiMismo(Usuario usuario, String rolNuevo, Boolean activoNuevo) {
        if (!usuario.getId().equals(idDeQuienPide())) {
            return;
        }
        boolean cambiaElRol = rolNuevo != null && !rolNuevo.equals(usuario.getRol());
        boolean seDesactiva = Boolean.FALSE.equals(activoNuevo) && Boolean.TRUE.equals(usuario.getActivo());
        if (cambiaElRol || seDesactiva) {
            throw new IllegalStateException(
                    "No puede desactivar su propia cuenta ni cambiarle el rol: pídaselo a otro administrador.");
        }
    }

    private static UUID idDeQuienPide() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return null;
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            return null;
        }
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

    private String generarPasswordAleatorio() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

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

                .fichaId(usuario.getFicha() == null ? null : usuario.getFicha().getId())
                .build();
    }
}
