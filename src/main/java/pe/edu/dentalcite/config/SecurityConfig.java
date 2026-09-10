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

    /**
     * Clave de firma de los tokens. <b>Sin valor por defecto a propósito.</b>
     *
     * <p>Con un valor por defecto en el código, un despliegue que olvidase
     * inyectar {@code JWT_SECRET} arrancaba igualmente y firmaba con una clave
     * presente en el repositorio: cualquiera que lo leyera podía forjar un token
     * de ADMINISTRADOR. Ahora la aplicación no arranca si falta la propiedad, que
     * es el fallo que se quiere: ruidoso y en el despliegue, no silencioso y en
     * producción.
     */
    @org.springframework.beans.factory.annotation.Value("${jwt.secret}")
    private String jwtSecret;

    /** HS256 exige una clave de al menos 256 bits. */
    private static final int LONGITUD_MINIMA_SECRETO = 32;

    /**
     * Comprueba la clave al construir el contexto. Sin esto, una clave corta no
     * fallaba al arrancar sino al emitir el primer token, con un error de Nimbus
     * que no señala la causa.
     */
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
            // `cors.configure(http)` no registraba ninguna fuente de configuracion:
            // la API no emitia cabeceras CORS y el cliente Angular no podia
            // consumirla desde el navegador. `withDefaults()` recoge el bean
            // corsConfigurationSource de mas abajo.
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .addFilterAfter(passwordFilter, org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/registro").permitAll()
                .requestMatchers("/api/v1/auth/logout").authenticated()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html", "/error").permitAll()
                .requestMatchers("/api/v1/usuarios/me/password").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/usuarios/me").authenticated()
                .requestMatchers("/api/v1/odontologos/*/horarios/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/bloqueos/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-08: la disponibilidad la consulta cualquier rol autenticado, el
                // paciente incluido. Caeria igualmente en el anyRequest() del final,
                // pero RNF-04 se audita leyendo este metodo: una ruta que no aparece
                // aqui obliga a deducir su permiso en vez de leerlo.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/disponibilidad").authenticated()
                // HU-09: solo el PACIENTE reserva, y solo para si mismo. HU-14
                // ampliara esta regla a RECEPCIONISTA y ADMINISTRADOR cuando
                // exista la reserva en nombre de terceros.
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/citas").hasAuthority("SCOPE_PACIENTE")
                // HU-11: la agenda de la clinica, su bitacora y la cancelacion sin
                // ventana son de recepcion y administracion. La regla comodin va al
                // final y es deliberada: sin ella, cualquier ruta nueva bajo
                // /api/v1/citas caeria en el anyRequest() del final, que solo exige
                // estar autenticado, y quedaria abierta a los cuatro roles. Que el
                // paciente consulte y cancele *lo suyo* es HU-15, y necesitara sus
                // propias rutas o una comprobacion de propiedad en el servicio.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/citas").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/citas/*/cancelar").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/citas/**").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-12: el alta presencial es de recepcion y administracion.
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/pacientes").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-13 · RF-07: la busqueda es del «personal». El PACIENTE no
                // busca pacientes; que el odontologo solo vea a los suyos lo
                // resuelve PacienteAccessGuard, restringiendo la consulta.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pacientes").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR", "SCOPE_ODONTOLOGO")
                // HU-13 · RF-08: «Recepcionista y Administrador (edicion)».
                .requestMatchers(org.springframework.http.HttpMethod.PUT, "/api/v1/pacientes/*").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // La lectura de UNA ficha la alcanzan los cuatro roles, pero no
                // sobre cualquiera: «sus pacientes» y «la propia» no son
                // expresables como patron de ruta, asi que la decision vive en
                // PacienteAccessGuard, con la ficha ya en la mano.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/pacientes/*").authenticated()
                // El comodin va al final por la misma razon que en citas: sin el,
                // cualquier ruta nueva bajo /api/v1/pacientes caeria en el
                // anyRequest() y quedaria abierta a los cuatro roles.
                .requestMatchers("/api/v1/pacientes/**").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // Catálogo clínico (Tabla 10): lectura para todo rol autenticado,
                // escritura solo ADMINISTRADOR. La segunda regla no enumera métodos a
                // propósito: enumerarlos dejaba PUT y DELETE cayendo en el
                // `anyRequest().authenticated()` del final, es decir, abiertos a
                // cualquier rol. Así, todo método que no sea GET nace restringido.
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

    /**
     * Origenes permitidos para el cliente. El JWT viaja en la cabecera
     * `Authorization`, no en cookie, asi que no se habilitan credenciales.
     */
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
