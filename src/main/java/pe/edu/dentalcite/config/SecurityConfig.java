package pe.edu.dentalcite.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.SecurityFilterChain;

import org.springframework.security.config.Customizer;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
    private String jwtSecret;

    /** HS256 exige una clave de al menos 256 bits. */
    private static final int LONGITUD_MINIMA_SECRETO = 32;

    @jakarta.annotation.PostConstruct
    void validarSecreto() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException(
                    "Falta la propiedad jwt.secret (variable de entorno JWT_SECRET).");
        }
        int longitud = jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (longitud < LONGITUD_MINIMA_SECRETO) {
            throw new IllegalStateException(
                    "jwt.secret debe tener al menos " + LONGITUD_MINIMA_SECRETO
                            + " bytes para firmar con HS256; tiene " + longitud + ".");
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12); // RNF-03: Coste 12
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, pe.edu.dentalcite.auth.security.PasswordFilter passwordFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAfter(passwordFilter, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/registro").permitAll()
                .requestMatchers("/api/v1/auth/logout").authenticated()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/error").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/publico/**").permitAll()
                .requestMatchers("/api/v1/publico/**").denyAll()
                .requestMatchers("/api/v1/usuarios/me/password").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/usuarios/me").authenticated()
                .requestMatchers("/api/v1/odontologos/*/horarios/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/bloqueos/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/disponibilidad").permitAll()
                .requestMatchers("/api/v1/disponibilidad").denyAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/citas").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/citas").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/citas/mias").hasAuthority("SCOPE_PACIENTE")
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/citas/*/cancelar").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/citas/pendientes-cierre").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/citas/*/resultado").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/citas/**").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/planes").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/planes/seguimiento").hasAuthority("SCOPE_ODONTOLOGO")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/planes/*").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/planes/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/recomendaciones").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pacientes").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pacientes").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR", "SCOPE_ODONTOLOGO")
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/pacientes/*").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pacientes/*").authenticated()
                .requestMatchers("/api/v1/pacientes/**").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/especialidades/**", "/api/v1/tratamientos/**", "/api/v1/odontologos/**", "/api/v1/consultorios/**").authenticated()
                .requestMatchers("/api/v1/especialidades/**", "/api/v1/tratamientos/**", "/api/v1/odontologos/**", "/api/v1/consultorios/**").hasAuthority("SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/usuarios/**").hasAuthority("SCOPE_ADMINISTRADOR") // En JWT por defecto el prefix es SCOPE_ si no configuramos un converter
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {
                org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter();
                grantedAuthoritiesConverter.setAuthorityPrefix("SCOPE_");
                grantedAuthoritiesConverter.setAuthoritiesClaimName("rol");
                
                org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter jwtAuthenticationConverter = new org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter();
                jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
                
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter);
            }));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value("${cors.allowed-origins:http://localhost:4200}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    @Bean
    public JwtDecoder jwtDecoder(CustomJwtAuthenticationValidator customValidator) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(new SecretKeySpec(jwtSecret.getBytes(), "HmacSHA256")).build();
        
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer("dentalcite");
        OAuth2TokenValidator<Jwt> withCustom = new DelegatingOAuth2TokenValidator<>(withIssuer, customValidator);
        
        jwtDecoder.setJwtValidator(withCustom);
        return jwtDecoder;
    }

    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtSecret.getBytes()));
    }
}
