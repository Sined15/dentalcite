package pe.edu.dentalcite.auth.api;

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

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/registro")
    public ResponseEntity<MessageResponse> registrar(@Valid @RequestBody RegistroRequest request) {
        authService.registrarPaciente(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new MessageResponse("Registro exitoso"));
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ResponseEntity.ok(new TokenResponse(token));
    }

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
