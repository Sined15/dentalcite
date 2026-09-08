package pe.edu.dentalcite.usuario.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.dentalcite.usuario.api.dto.CambioPasswordRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioResponseDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioUpdateRequestDTO;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private pe.edu.dentalcite.auth.service.TokenRevocationCache tokenRevocationCache;

    @Mock
    private pe.edu.dentalcite.auth.service.AuthService authService;

    @Mock
    private pe.edu.dentalcite.ficha.repository.FichaRepository fichaRepository;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuario;
    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        usuario = Usuario.builder()
                .id(usuarioId)
                .correo("test@dentalcite.com")
                .rol("RECEPCIONISTA")
                .contrasenaHash("hash123")
                .activo(true)
                .requiereCambioPassword(false)
                .tokensValidosDesde(OffsetDateTime.now().minusDays(1))
                .build();
    }

    @Test
    void actualizarUsuario_cambiarRol_invalidaTokens() {
        // Arrange
        UsuarioUpdateRequestDTO req = new UsuarioUpdateRequestDTO();
        req.setRol("ADMINISTRADOR");

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        OffsetDateTime beforeUpdate = usuario.getTokensValidosDesde();

        // Act
        UsuarioResponseDTO res = usuarioService.actualizarUsuario(usuarioId, req);

        // Assert
        assertEquals("ADMINISTRADOR", res.getRol());
        assertTrue(res.getTokensValidosDesde().isAfter(beforeUpdate));
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void crearUsuario_conCorreoEnMayusculas_normalizaAntesDeGuardar() {
        pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO req = new pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO();
        req.setNombre("Persona de Prueba"); // HU-04: la cuenta lleva nombre
        req.setCorreo("Nuevo.Admin@Dentalcite.COM");
        req.setRol("ADMINISTRADOR");

        when(usuarioRepository.existsByCorreo("nuevo.admin@dentalcite.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO res = usuarioService.crearUsuario(req);

        assertEquals("nuevo.admin@dentalcite.com", res.getCorreo());
        verify(usuarioRepository).existsByCorreo("nuevo.admin@dentalcite.com");
    }

    @Test
    void crearUsuario_sinPasswordProvisional_devuelveLaGenerada() {
        // HU-04: la cuenta se crea «con correo, nombre y uno de los cuatro roles» y
        // «podrá iniciar sesión». La generada solo existe en claro en la respuesta
        // del alta; sin devolverla, la cuenta nacía inaccesible para siempre.
        pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO req = new pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO();
        req.setNombre("Recepcion Nueva");
        req.setCorreo("recepcion.nueva@dentalcite.com");
        req.setRol("RECEPCIONISTA");

        when(usuarioRepository.existsByCorreo("recepcion.nueva@dentalcite.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO res = usuarioService.crearUsuario(req);

        org.mockito.ArgumentCaptor<String> enClaro = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(enClaro.capture());

        assertNotNull(res.getPasswordProvisional());
        assertEquals(enClaro.getValue(), res.getPasswordProvisional(),
                "debe devolverse exactamente la contraseña que se hasheó");
        assertTrue(res.getPasswordProvisional().length() >= 8,
                "no puede quedar por debajo del mínimo que exige el resto del flujo");
    }

    @Test
    void crearUsuario_conPasswordProvisionalIndicada_noLaRepiteEnLaRespuesta() {
        pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO req = new pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO();
        req.setNombre("Recepcion Nueva");
        req.setCorreo("odontologo.nuevo@dentalcite.com");
        req.setRol("RECEPCIONISTA"); // sin ficha: el documento solo lo exige ODONTOLOGO
        req.setPasswordProvisional("temporal123");

        when(usuarioRepository.existsByCorreo("odontologo.nuevo@dentalcite.com")).thenReturn(false);
        when(passwordEncoder.encode("temporal123")).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO res = usuarioService.crearUsuario(req);

        assertNull(res.getPasswordProvisional(), "el administrador ya la conoce: no hay por qué devolvérsela");
    }

    private pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO altaDePersonal(String rol, String documento) {
        pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO req = new pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO();
        req.setNombre("Persona de Alta");
        req.setCorreo("alta@dentalcite.com");
        req.setRol(rol);
        req.setDocumento(documento);
        return req;
    }

    @Test
    void crearUsuario_conRolOdontologoYDocumento_creaSuFichaYLaVincula() {
        // RF-10 y RN-11: sin ficha, esta cuenta no puede llegar a registrarse como
        // odontólogo ni resolver las operaciones marcadas «propio» de la Tabla 10.
        when(usuarioRepository.existsByCorreo("alta@dentalcite.com")).thenReturn(false);
        when(fichaRepository.findByTipoDocumentoAndDocumento("DNI", "40123456")).thenReturn(Optional.empty());
        when(fichaRepository.getNextHistoriaClinica()).thenReturn(42L);
        when(fichaRepository.save(any(pe.edu.dentalcite.ficha.domain.Ficha.class)))
                .thenAnswer(i -> {
                    pe.edu.dentalcite.ficha.domain.Ficha f = i.getArgument(0);
                    f.setId(UUID.randomUUID());
                    return f;
                });
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO res = usuarioService.crearUsuario(altaDePersonal("ODONTOLOGO", "40123456"));

        org.mockito.ArgumentCaptor<pe.edu.dentalcite.ficha.domain.Ficha> creada =
                org.mockito.ArgumentCaptor.forClass(pe.edu.dentalcite.ficha.domain.Ficha.class);
        verify(fichaRepository).save(creada.capture());
        assertEquals("DNI", creada.getValue().getTipoDocumento());
        assertEquals("40123456", creada.getValue().getDocumento());
        assertEquals("HC-00042", creada.getValue().getNumeroHistoria(), "el correlativo sale de la secuencia");

        assertNotNull(res.getFichaId(), "POST /odontologos necesita este identificador");
    }

    @Test
    void crearUsuario_conRolOdontologoSinDocumento_arrojaIllegalArgumentException() {
        when(usuarioRepository.existsByCorreo("alta@dentalcite.com")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> usuarioService.crearUsuario(altaDePersonal("ODONTOLOGO", null)));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void crearUsuario_conRolRecepcionistaSinDocumento_noLeCreaFicha() {
        // Recepción y administración no necesitan historia clínica.
        when(usuarioRepository.existsByCorreo("alta@dentalcite.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO res = usuarioService.crearUsuario(altaDePersonal("RECEPCIONISTA", null));

        assertNull(res.getFichaId());
        verify(fichaRepository, never()).save(any());
    }

    @Test
    void crearUsuario_conFichaExistenteSinCuenta_laVinculaSinDuplicarla() {
        // RN-10: la persona puede ser ya paciente de la clínica; su historia no se
        // duplica, se adopta.
        UUID fichaId = UUID.randomUUID();
        pe.edu.dentalcite.ficha.domain.Ficha existente = pe.edu.dentalcite.ficha.domain.Ficha.builder()
                .id(fichaId).tipoDocumento("DNI").documento("40123456").numeroHistoria("HC-00007").build();

        when(usuarioRepository.existsByCorreo("alta@dentalcite.com")).thenReturn(false);
        when(fichaRepository.findByTipoDocumentoAndDocumento("DNI", "40123456")).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByFichaId(fichaId)).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        UsuarioResponseDTO res = usuarioService.crearUsuario(altaDePersonal("ODONTOLOGO", "40123456"));

        assertEquals(fichaId, res.getFichaId());
        verify(fichaRepository, never()).save(any());
    }

    @Test
    void crearUsuario_conFichaQueYaTieneCuenta_arrojaIllegalStateException() {
        // RN-11: una ficha admite como máximo una cuenta.
        UUID fichaId = UUID.randomUUID();
        pe.edu.dentalcite.ficha.domain.Ficha existente = pe.edu.dentalcite.ficha.domain.Ficha.builder()
                .id(fichaId).tipoDocumento("DNI").documento("40123456").numeroHistoria("HC-00007").build();

        when(usuarioRepository.existsByCorreo("alta@dentalcite.com")).thenReturn(false);
        when(fichaRepository.findByTipoDocumentoAndDocumento("DNI", "40123456")).thenReturn(Optional.of(existente));
        when(usuarioRepository.existsByFichaId(fichaId)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> usuarioService.crearUsuario(altaDePersonal("ODONTOLOGO", "40123456")));
        assertTrue(ex.getMessage().contains("RN-11"));
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void obtenerUsuario_noExponeNingunaContrasena() {
        // El campo solo lo rellena el alta: ninguna lectura posterior puede traerlo.
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        assertNull(usuarioService.obtenerUsuario(usuarioId).getPasswordProvisional());
    }

    @Test
    void asignarPasswordProvisional_levantaElBloqueoPorIntentosFallidos() {
        // HU-04: «podrá entrar con ella». El caso típico es el de quien perdió la
        // contraseña y la falló tres veces, así que la cuenta llega bloqueada por
        // RNF-05 y la clave nueva chocaría con un 423.
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("temporal")).thenReturn("hashTemporal");

        usuarioService.asignarPasswordProvisional(usuarioId, "temporal");

        verify(authService).desbloquearCuenta(usuario);
    }

    @Test
    void reemplazarUsuario_conAmbosCampos_reemplazaTodoElRecurso() {
        // Arrange: PUT debe fijar ambos campos, incluso 'activo' aunque no cambie.
        pe.edu.dentalcite.usuario.api.dto.UsuarioReplaceRequestDTO req = new pe.edu.dentalcite.usuario.api.dto.UsuarioReplaceRequestDTO();
        req.setNombre("Persona de Prueba"); // HU-04: la cuenta lleva nombre
        req.setRol("ADMINISTRADOR");
        req.setActivo(true); // mismo valor que ya tenía 'usuario' (true)

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        UsuarioResponseDTO res = usuarioService.reemplazarUsuario(usuarioId, req);

        // Assert
        assertEquals("ADMINISTRADOR", res.getRol());
        assertTrue(res.getActivo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void reemplazarUsuario_conIdInvalido_arrojaResourceNotFound() {
        // Arrange
        pe.edu.dentalcite.usuario.api.dto.UsuarioReplaceRequestDTO req = new pe.edu.dentalcite.usuario.api.dto.UsuarioReplaceRequestDTO();
        req.setNombre("Persona de Prueba"); // HU-04: la cuenta lleva nombre
        req.setRol("ADMINISTRADOR");
        req.setActivo(true);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class,
                () -> usuarioService.reemplazarUsuario(usuarioId, req));
    }

    @Test
    void asignarPasswordProvisional_actualizaHashYRequiereCambioYInvalidaTokens() {
        // Arrange
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.encode("temporal")).thenReturn("newhash");
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));

        OffsetDateTime beforeUpdate = usuario.getTokensValidosDesde();

        // Act
        usuarioService.asignarPasswordProvisional(usuarioId, "temporal");

        // Assert
        assertTrue(usuario.getRequiereCambioPassword());
        assertEquals("newhash", usuario.getContrasenaHash());
        assertTrue(usuario.getTokensValidosDesde().isAfter(beforeUpdate));
    }

    @Test
    void cambiarPasswordPropio_conDatosCorrectos_actualizaPassword() {
        // Arrange
        CambioPasswordRequestDTO req = new CambioPasswordRequestDTO();
        req.setPasswordActual("password123");
        req.setNuevoPassword("nuevoPassword123");

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("password123", "hash123")).thenReturn(true);
        when(passwordEncoder.encode("nuevoPassword123")).thenReturn("nuevo_hashed_password");

        // Act
        usuarioService.cambiarPasswordPropio(usuarioId, req);

        // Assert
        assertFalse(usuario.getRequiereCambioPassword());
        assertEquals("nuevo_hashed_password", usuario.getContrasenaHash());
    }

    @Test
    void cambiarPasswordPropio_conPasswordIncorrecto_arrojaConflicto() {
        // Arrange
        CambioPasswordRequestDTO req = new CambioPasswordRequestDTO();
        req.setPasswordActual("wrong_password");
        req.setNuevoPassword("nuevoPassword123");

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("wrong_password", "hash123")).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalStateException.class,
                () -> usuarioService.cambiarPasswordPropio(usuarioId, req));
    }

    @Test
    void obtenerPerfilPropio_conIdValido_retornaDto() {
        // Arrange
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        // Act
        UsuarioResponseDTO res = usuarioService.obtenerPerfilPropio(usuarioId);

        // Assert
        assertEquals(usuario.getCorreo(), res.getCorreo());
        assertEquals(usuario.getRol(), res.getRol());
    }

    @Test
    void obtenerPerfilPropio_conIdInvalido_arrojaResourceNotFound() {
        // Arrange
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class,
                () -> usuarioService.obtenerPerfilPropio(usuarioId));
    }

    @Test
    void actualizarUsuario_conIdInvalido_arrojaResourceNotFound() {
        // Arrange
        UsuarioUpdateRequestDTO req = new UsuarioUpdateRequestDTO();
        req.setActivo(false);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class,
                () -> usuarioService.actualizarUsuario(usuarioId, req));
    }

    @Test
    void asignarPasswordProvisional_conIdInvalido_arrojaResourceNotFound() {
        // Arrange
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class,
                () -> usuarioService.asignarPasswordProvisional(usuarioId, "temporal"));
    }

    @Test
    void obtenerUsuario_conIdValido_retornaLaCuenta() {
        // Tabla 10: el ADMINISTRADOR lee cualquier cuenta, no solo la suya.
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        UsuarioResponseDTO res = usuarioService.obtenerUsuario(usuarioId);

        assertEquals(usuarioId, res.getId());
        assertEquals(usuario.getCorreo(), res.getCorreo());
    }

    @Test
    void obtenerUsuario_conIdInvalido_arrojaResourceNotFound() {
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> usuarioService.obtenerUsuario(usuarioId));
    }
}
