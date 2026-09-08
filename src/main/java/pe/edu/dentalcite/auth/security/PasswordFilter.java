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

    /** Misma forma que {@link MessageResponse}, el cuerpo de error del resto de la API. */
    private static final String CUERPO_RECHAZO =
            "{\"message\":\"Debe cambiar su contraseña provisional antes de operar el sistema.\"}";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();
        
        // Fail-Fast: Permitir acceso inmediato a rutas públicas o al endpoint de cambio de contraseña
        if (requestURI.equals("/api/v1/usuarios/me/password") 
                || requestURI.startsWith("/api/v1/auth/")
                || requestURI.startsWith("/swagger-ui")
                || requestURI.startsWith("/v3/api-docs")) {
            filterChain.doFilter(request, response);
            return;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            Map<String, Object> claims = jwtAuth.getTokenAttributes();

            Boolean requiereCambio = (Boolean) claims.get("requiere_cambio_password");
            
            // Si requiere cambio, rechazar acceso
            if (Boolean.TRUE.equals(requiereCambio)) {
                rechazar(response);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * El 403 sale con la misma forma que el resto de errores de la API
     * ({@link MessageResponse}, es decir {@code {message}}) y declarando UTF-8.
     *
     * <p>Antes escribia un {@code {"error": ...}} a mano: una cuarta forma de
     * cuerpo que el cliente no contempla, y sin charset en la cabecera, de modo
     * que el contenedor serializaba el mensaje en ISO-8859-1 y en pantalla se
     * leia «contrase?a provisional».
     */
    private void rechazar(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // Se escriben los bytes UTF-8 directamente en vez de pasar por el
        // `Writer` del contenedor: el filtro corre fuera de MVC, asi que no hay
        // convertidor de mensajes que aplique la codificacion por el.
        response.getOutputStream().write(CUERPO_RECHAZO.getBytes(StandardCharsets.UTF_8));
    }
}
