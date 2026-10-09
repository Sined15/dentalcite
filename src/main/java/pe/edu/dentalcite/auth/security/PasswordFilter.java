package pe.edu.dentalcite.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import pe.edu.dentalcite.common.api.dto.MessageResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class PasswordFilter extends OncePerRequestFilter {

    private static final String CUERPO_RECHAZO =
            "{\"message\":\"Debe cambiar su contraseña provisional antes de operar el sistema.\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        
        if (requestURI.equals("/api/v1/usuarios/me/password")
                || requestURI.startsWith("/api/v1/auth/")
                || requestURI.startsWith("/api/v1/publico/")
                || requestURI.equals("/api/v1/disponibilidad")
                || requestURI.startsWith("/swagger-ui")
                || requestURI.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Map<String, Object> claims = jwtAuth.getTokenAttributes();

            Boolean requiereCambio = (Boolean) claims.get("requiere_cambio_password");
            
            if (Boolean.TRUE.equals(requiereCambio)) {
                rechazar(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private void rechazar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getOutputStream().write(CUERPO_RECHAZO.getBytes(StandardCharsets.UTF_8));
    }
}
