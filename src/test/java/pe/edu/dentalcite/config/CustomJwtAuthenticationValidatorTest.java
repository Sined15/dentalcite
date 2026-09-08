package pe.edu.dentalcite.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import pe.edu.dentalcite.auth.service.TokenRevocationCache;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-05: la validación de vigencia consulta la marca {@code tokens_validos_desde}
 * en PostgreSQL, con Redis como caché positiva de vigencia breve.
 */
@ExtendWith(MockitoExtension.class)
class CustomJwtAuthenticationValidatorTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private TokenRevocationCache revocationCache;

    @InjectMocks
    private CustomJwtAuthenticationValidator validator;

    private Jwt tokenEmitidoEn(UUID sub, Instant issuedAt) {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn(sub == null ? null : sub.toString());
        when(jwt.getIssuedAt()).thenReturn(issuedAt);
        return jwt;
    }

    @Test
    void validate_conSubjectInvalido_retornaFallo() {
        Jwt jwt = mock(Jwt.class);
        when(jwt.getSubject()).thenReturn("correo@invalido.com"); // No es un UUID
        when(jwt.getIssuedAt()).thenReturn(Instant.now());

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getDescription().contains("identificador válido")));
    }

    @Test
    void validate_sinClaimIssuedAt_retornaFallo() {
        Jwt jwt = tokenEmitidoEn(UUID.randomUUID(), null);

        OAuth2TokenValidatorResult result = validator.validate(jwt);

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getDescription().contains("Faltan claims")));
    }

    @Test
    void validate_conCacheVigente_noConsultaPostgreSQL() {
        // El acierto de caché evita el viaje a la base: es la razón de ser de la caché.
        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        when(revocationCache.leer(userId)).thenReturn(issuedAt.minusSeconds(3600));

        OAuth2TokenValidatorResult result = validator.validate(tokenEmitidoEn(userId, issuedAt));

        assertFalse(result.hasErrors());
        verify(usuarioRepository, never()).findTokensValidosDesdeById(any());
    }

    @Test
    void validate_conCacheQueYaRevocaElToken_rechazaSinConsultarPostgreSQL() {
        // Tras un logout la clave se invalida; mientras la caché tenga una marca
        // posterior a la emisión, el token debe rechazarse igualmente.
        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(60);
        when(revocationCache.leer(userId)).thenReturn(issuedAt.plusSeconds(30));

        OAuth2TokenValidatorResult result = validator.validate(tokenEmitidoEn(userId, issuedAt));

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "token_revoked".equals(e.getErrorCode())));
        verify(usuarioRepository, never()).findTokensValidosDesdeById(any());
    }

    @Test
    void validate_sinCache_consultaPostgreSQLYCacheaLaMarca() {
        // RNF-12: PostgreSQL es la fuente de autoridad cuando Redis no responde.
        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now();
        Instant validosDesde = issuedAt.minusSeconds(3600);

        when(revocationCache.leer(userId)).thenReturn(null);
        when(usuarioRepository.findTokensValidosDesdeById(userId))
                .thenReturn(Optional.of(validosDesde.atOffset(ZoneOffset.UTC)));

        OAuth2TokenValidatorResult result = validator.validate(tokenEmitidoEn(userId, issuedAt));

        assertFalse(result.hasErrors());
        verify(revocationCache).guardar(userId, validosDesde);
    }

    @Test
    void validate_conTokenAnteriorALaMarcaEnPostgreSQL_loRevoca() {
        UUID userId = UUID.randomUUID();
        Instant issuedAt = Instant.now().minusSeconds(600);
        OffsetDateTime validosDesde = OffsetDateTime.now(ZoneOffset.UTC);

        when(revocationCache.leer(userId)).thenReturn(null);
        when(usuarioRepository.findTokensValidosDesdeById(userId)).thenReturn(Optional.of(validosDesde));

        OAuth2TokenValidatorResult result = validator.validate(tokenEmitidoEn(userId, issuedAt));

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "token_revoked".equals(e.getErrorCode())));
    }

    @Test
    void validate_conUsuarioInexistente_retornaFallo() {
        UUID userId = UUID.randomUUID();
        when(revocationCache.leer(userId)).thenReturn(null);
        when(usuarioRepository.findTokensValidosDesdeById(userId)).thenReturn(Optional.empty());

        OAuth2TokenValidatorResult result = validator.validate(tokenEmitidoEn(userId, Instant.now()));

        assertTrue(result.hasErrors());
        assertTrue(result.getErrors().stream().anyMatch(e -> "user_not_found".equals(e.getErrorCode())));
        verify(revocationCache, never()).guardar(any(), any());
    }
}
