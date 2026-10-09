package pe.edu.dentalcite.usuario.api;

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

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    public ResponseEntity<UsuarioResponseDTO> obtenerPerfilPropio(
            @AuthenticationPrincipal(expression = "name") String userId) {
        return ResponseEntity.ok(usuarioService.obtenerPerfilPropio(UUID.fromString(userId)));
    }

    @GetMapping
    public ResponseEntity<org.springframework.data.domain.Page<UsuarioResponseDTO>> listarUsuarios(
            @org.springframework.data.web.PageableDefault(size = 20) org.springframework.data.domain.Pageable pageable) {
        return ResponseEntity.ok(usuarioService.listarUsuarios(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> obtenerUsuario(@PathVariable UUID id) {
        return ResponseEntity.ok(usuarioService.obtenerUsuario(id));
    }

    @PostMapping
    public ResponseEntity<UsuarioResponseDTO> crearUsuario(@Valid @RequestBody UsuarioRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.crearUsuario(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> reemplazarUsuario(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioReplaceRequestDTO request) {
        return ResponseEntity.ok(usuarioService.reemplazarUsuario(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<UsuarioResponseDTO> actualizarUsuario(
            @PathVariable UUID id,
            @Valid @RequestBody UsuarioUpdateRequestDTO request) {
        return ResponseEntity.ok(usuarioService.actualizarUsuario(id, request));
    }

    @PostMapping("/{id}/password-provisional")
    public ResponseEntity<Void> asignarPasswordProvisional(
            @PathVariable UUID id,
            @Valid @RequestBody PasswordProvisionalRequestDTO request) {
        usuarioService.asignarPasswordProvisional(id, request.getPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/password")
    public ResponseEntity<Void> cambiarPasswordPropio(
            @AuthenticationPrincipal(expression = "name") String userId,
            @Valid @RequestBody CambioPasswordRequestDTO request) {
        usuarioService.cambiarPasswordPropio(UUID.fromString(userId), request);
        return ResponseEntity.noContent().build();
    }
}
