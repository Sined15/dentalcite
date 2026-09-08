package pe.edu.dentalcite.usuario.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pe.edu.dentalcite.usuario.api.dto.CambioPasswordRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.PasswordProvisionalRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioReplaceRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioResponseDTO;
import pe.edu.dentalcite.usuario.api.dto.UsuarioUpdateRequestDTO;
import pe.edu.dentalcite.usuario.service.UsuarioService;

import java.util.UUID;

@Tag(name = "Cuentas y roles", description = "M1 · Alta, edicion y baja de cuentas del personal (HU-04); perfil propio (HU-05)")
@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @Operation(summary = "Consultar la cuenta propia",
            description = "HU-05 · Todos los roles, sobre su propia cuenta. Resuelve el sujeto del token a su perfil.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Perfil de la cuenta autenticada"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado")
    })
    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obtenerPerfilPropio(
            @AuthenticationPrincipal(expression = "name") String userId) {
        return ResponseEntity.ok(usuarioService.obtenerPerfilPropio(UUID.fromString(userId)));
    }

    @Operation(summary = "Listar cuentas",
            description = "HU-04 · ADMINISTRADOR. Resultados paginados.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de cuentas"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)")
    })
    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<UsuarioResponseDTO>> listarUsuarios(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(pageable));
    }

    @Operation(summary = "Consultar una cuenta por identificador",
            description = "HU-04 · ADMINISTRADOR. Corresponde a la R de la Tabla 10 sobre cuentas de usuario y roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta encontrada"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cuenta con ese identificador")
    })
    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuario(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.obtenerUsuario(id));
    }

    @Operation(summary = "Dar de alta una cuenta del personal",
            description = "HU-04 · RF-04 · ADMINISTRADOR. Crea la cuenta con correo, nombre y uno de los cuatro roles. Nace exigiendo el cambio de la contrasena provisional en el primer acceso (F-06); si no se indica una, el sistema la genera y la devuelve una unica vez en esta respuesta, porque despues solo existe como hash. Con el tipo y numero de documento crea o vincula la ficha de la persona (RN-10, RN-11) y devuelve su identificador: el documento es obligatorio para el rol ODONTOLOGO, del que depende el registro de RF-10 y las operaciones marcadas «propio».")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos, o falta el documento de una cuenta ODONTOLOGO"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "409", description = "Ya existe una cuenta con ese correo, o la ficha de esa persona ya tiene cuenta (RN-11)")
    })
    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> crearUsuario(@Valid @RequestBody UsuarioRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crearUsuario(request));
    }

    @Operation(summary = "Reemplazar una cuenta",
            description = "HU-04 · RF-04 · ADMINISTRADOR. Reemplazo total: nombre, rol y estado son obligatorios. Cambiar el rol o desactivar la cuenta invalida sus tokens vigentes en la peticion siguiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta reemplazada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cuenta con ese identificador")
    })
    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> reemplazarUsuario(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioReplaceRequestDTO request) {
        return ResponseEntity.ok(usuarioService.reemplazarUsuario(id, request));
    }

    @Operation(summary = "Actualizar o desactivar una cuenta",
            description = "HU-04 · RF-04 · ADMINISTRADOR. Actualizacion parcial. La baja es logica: enviar activo=false conserva el registro y le impide iniciar sesion (RN-12), e invalida sus tokens vigentes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cuenta actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cuenta con ese identificador")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> actualizarUsuario(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioUpdateRequestDTO request) {
        return ResponseEntity.ok(usuarioService.actualizarUsuario(id, request));
    }

    @Operation(summary = "Asignar una contrasena provisional",
            description = "HU-04 · RF-04 · ADMINISTRADOR. Sustituye a F-06, que excluye el autoservicio: el administrador entrega la clave en la clinica y el sistema obliga a cambiarla antes de operar. Expulsa las sesiones activas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contrasena provisional asignada"),
            @ApiResponse(responseCode = "400", description = "La contrasena no cumple el minimo"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cuenta con ese identificador")
    })
    @PostMapping("/{id}/password-provisional")
    public ResponseEntity<Void> asignarPasswordProvisional(
            @PathVariable UUID id,
            @Valid @RequestBody PasswordProvisionalRequestDTO request) {
        usuarioService.asignarPasswordProvisional(id, request.getPassword());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Cambiar la contrasena propia",
            description = "HU-04 · Todos los roles, sobre su propia cuenta. Unica ruta permitida mientras el token exija cambio de contrasena provisional. Al cambiarla se invalidan las sesiones previas.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contrasena cambiada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "409", description = "La contrasena actual no coincide")
    })
    @PostMapping("/me/password")
    public ResponseEntity<Void> cambiarPasswordPropio(
            @AuthenticationPrincipal(expression = "name") String userId,
            @Valid @RequestBody CambioPasswordRequestDTO request) {
        usuarioService.cambiarPasswordPropio(UUID.fromString(userId), request);
        return ResponseEntity.noContent().build();
    }
}
