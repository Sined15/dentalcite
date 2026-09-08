package pe.edu.dentalcite.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import pe.edu.dentalcite.usuario.domain.Usuario;

import javax.crypto.spec.SecretKeySpec;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HU-03: «recibiré un token de ocho horas que transporta mi rol» (RF-02).
 * El token se decodifica de verdad en lugar de inspeccionar la cadena a mano.
 */
class JwtServiceTest {

    private static final String SECRETO = "DENTALCITE_SECRET_KEY_MUST_BE_LONG_ENOUGH_32_BYTES!!";

    private JwtService jwtService;
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        SecretKeySpec key = new SecretKeySpec(SECRETO.getBytes(), "HmacSHA256");
        JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(SECRETO.getBytes()));
        jwtService = new JwtService(encoder);
        jwtDecoder = NimbusJwtDecoder.withSecretKey(key).build();
    }

    private Usuario usuario(String rol, boolean requiereCambio) {
        return Usuario.builder()
                .id(UUID.randomUUID())
                .nombre("Luis Pérez")
                .correo("dr.perez@dentalcite.com")
                .rol(rol)
                .requiereCambioPassword(requiereCambio)
                .build();
    }

    @Test
    void generateToken_emiteTokenDeOchoHoras() {
        Usuario u = usuario("ODONTOLOGO", false);

        var jwt = jwtDecoder.decode(jwtService.generateToken(u));

        assertNotNull(jwt.getIssuedAt());
        assertNotNull(jwt.getExpiresAt());
        long horas = Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()).toHours();
        assertEquals(8, horas);
    }

    @Test
    void generateToken_transportaElSubjectElRolYElIndicadorDeCambioDePassword() {
        Usuario u = usuario("ADMINISTRADOR", true);

        var jwt = jwtDecoder.decode(jwtService.generateToken(u));

        // El subject es el UUID del usuario, no su correo: de ahí depende toda la
        // resolución de propiedad del dato (HU-05).
        assertEquals(u.getId().toString(), jwt.getSubject());
        assertEquals("ADMINISTRADOR", jwt.getClaimAsString("rol"));
        assertEquals(Boolean.TRUE, jwt.getClaim("requiere_cambio_password"));
        assertEquals("dentalcite", jwt.getClaimAsString("iss"));
    }

    @Test
    void generateToken_paraPaciente_llevaSuRolYNoExigeCambioDePassword() {
        Usuario u = usuario("PACIENTE", false);

        var jwt = jwtDecoder.decode(jwtService.generateToken(u));

        assertEquals("PACIENTE", jwt.getClaimAsString("rol"));
        assertEquals(Boolean.FALSE, jwt.getClaim("requiere_cambio_password"));
        assertTrue(jwt.getIssuedAt().isBefore(jwt.getExpiresAt().plus(1, ChronoUnit.SECONDS)));
    }
}
