package pe.edu.dentalcite.auth.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.dentalcite.auth.api.dto.RegistroRequest;
import pe.edu.dentalcite.auth.api.dto.LoginRequest;
import pe.edu.dentalcite.auth.api.dto.TokenResponse;
import pe.edu.dentalcite.common.api.dto.MessageResponse;
import pe.edu.dentalcite.auth.service.AuthService;

import java.security.Principal;
import java.util.UUID;

@Tag(name = "Acceso", description = "M1 · Registro, inicio y cierre de sesion (HU-02, HU-03)")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "Registrar un paciente desde el portal",
            description = "HU-02 · RF-01. Publico. Crea la cuenta con rol PACIENTE y su ficha con numero de historia unico. Si ya existe una ficha con el mismo tipo y numero de documento y sin cuenta asociada, la vincula en lugar de duplicarla (RN-10, RN-11). Registra el consentimiento informado con su fecha y version (RNF-06).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cuenta creada y consentimiento registrado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos, o consentimiento no aceptado (RNF-06)"),
            @ApiResponse(responseCode = "409", description = "El correo ya existe, o la ficha ya tiene una cuenta asociada (RN-11)")
    })
    @SecurityRequirements
    @PostMapping("/registro")
    public ResponseEntity<MessageResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        authService.registrarPaciente(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("Registro exitoso"));
    }

    @Operation(summary = "Iniciar sesion",
            description = "HU-03 · RF-02. Publico. Devuelve un token de ocho horas que transporta el rol. Tres intentos fallidos consecutivos bloquean la cuenta cinco minutos (RNF-05). La respuesta no revela si fallo el correo o la contrasena.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token emitido"),
            @ApiResponse(responseCode = "401", description = "Credenciales incorrectas, o cuenta desactivada"),
            @ApiResponse(responseCode = "423", description = "Cuenta bloqueada temporalmente por intentos fallidos (RNF-05)")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(new TokenResponse(token));
    }

    @Operation(summary = "Cerrar sesion",
            description = "HU-03 · RF-03. Todos los roles. Invalida de inmediato cualquier token emitido antes de este instante: reutilizar el token anterior devuelve 401 sin esperar a que caduque.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Sesion cerrada y tokens previos invalidados"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token no valido")
    })
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Principal principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        UUID userId = UUID.fromString(principal.getName());
        authService.logout(userId);
        return ResponseEntity.noContent().build(); // 204
    }
}
