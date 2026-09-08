package pe.edu.dentalcite.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import pe.edu.dentalcite.auth.service.TokenRevocationCache;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomJwtAuthenticationValidator implements OAuth2TokenValidator<Jwt> {

    private final UsuarioRepository usuarioRepository;
    private final TokenRevocationCache revocationCache;

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String userIdStr = jwt.getSubject();
        Instant issuedAt = jwt.getIssuedAt();

        if (userIdStr == null || issuedAt == null) {
            return OAuth2TokenValidatorResult
                    .failure(new OAuth2Error("invalid_token", "Faltan claims en el token", null));
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "El subject del token no es un identificador válido", null));
        }

        // 1. Caché de la marca de vigencia. Se cachea el valor, no un "OK", para que
        //    la comparación siga haciéndose contra la marca real del usuario: así un
        //    logout invalida la clave y el token deja de aceptarse en la petición
        //    siguiente, sin esperar al TTL (HU-03, HU-04).
        Instant cacheada = revocationCache.leer(userId);
        if (cacheada != null) {
            return decidir(issuedAt, cacheada);
        }

        // 2. PostgreSQL es la fuente de autoridad (RNF-12).
        Optional<Instant> tokensValidFromOpt = usuarioRepository.findTokensValidosDesdeById(userId)
                .map(OffsetDateTime::toInstant);

        if (tokensValidFromOpt.isEmpty()) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error("user_not_found", "Usuario no encontrado", null));
        }

        Instant tokensValidFrom = tokensValidFromOpt.get();
        revocationCache.guardar(userId, tokensValidFrom);
        return decidir(issuedAt, tokensValidFrom);
    }

    private OAuth2TokenValidatorResult decidir(Instant issuedAt, Instant tokensValidFrom) {
        if (issuedAt.isBefore(tokensValidFrom)) {
            return OAuth2TokenValidatorResult
                    .failure(new OAuth2Error("token_revoked", "El token ha sido revocado", null));
        }
        return OAuth2TokenValidatorResult.success();
    }
}
