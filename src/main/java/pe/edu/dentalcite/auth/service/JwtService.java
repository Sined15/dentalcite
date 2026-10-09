package pe.edu.dentalcite.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtEncoder jwtEncoder;

    public String generateToken(Usuario usuario) {
        Instant now = Instant.now();
        Instant marcaVigencia = usuario.getTokensValidosDesde().toInstant();
        Instant issuedAt = now.isBefore(marcaVigencia) ? marcaVigencia : now;

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("dentalcite")
                .issuedAt(issuedAt)
                .expiresAt(issuedAt.plus(8, ChronoUnit.HOURS))
                .subject(usuario.getId().toString())
                .claim("rol", usuario.getRol())
                .claim("requiere_cambio_password", usuario.getRequiereCambioPassword())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
