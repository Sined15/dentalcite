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
                // HU-06 (v4): el catalogo clinico se recorre sin sesion, que es lo
                // que RNF-04 exceptua junto con la disponibilidad. Son dos reglas y
                // no una, por la leccion del comodin de citas: sin la segunda,
                // cualquier ruta bajo este prefijo que no fuera GET caeria en el
                // anyRequest().authenticated() del final y quedaria abierta a los
                // cuatro roles. El prefijo es de solo lectura, asi que lo correcto
                // es negar todo lo demas.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/publico/**").permitAll()
                .requestMatchers("/api/v1/publico/**").denyAll()
                .requestMatchers("/api/v1/usuarios/me/password").authenticated()
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/usuarios/me").authenticated()
                .requestMatchers("/api/v1/odontologos/*/horarios/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/bloqueos/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-08: la disponibilidad la consulta cualquier rol autenticado, el
                // paciente incluido. Caeria igualmente en el anyRequest() del final,
                // pero RNF-04 se audita leyendo este metodo: una ruta que no aparece
                // aqui obliga a deducir su permiso en vez de leerlo.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/disponibilidad").authenticated()
                // HU-09 y HU-14: reservan el PACIENTE (para si mismo, sin
                // `pacienteId`) y recepcion o administracion (en nombre de otro,
                // con `pacienteId`). La ruta no distingue esos dos casos porque
                // «para si mismo» no es un patron de ruta: lo decide
                // AutorDeLaReserva, que es quien tiene delante el cuerpo y el rol.
                .requestMatchers(org.springframework.http.HttpMethod.POST, "/api/v1/citas").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-11: la agenda de la clinica, su bitacora y la cancelacion sin
                // ventana son de recepcion y administracion. La regla comodin va al
                // final y es deliberada: sin ella, cualquier ruta nueva bajo
                // /api/v1/citas caeria en el anyRequest() del final, que solo exige
                // estar autenticado, y quedaria abierta a los cuatro roles. Que el
                // paciente consulte y cancele *lo suyo* es HU-15, y necesitara sus
                // propias rutas o una comprobacion de propiedad en el servicio.
                // La agenda la consultan tres audiencias, no dos: recepcion y
                // administracion la ven entera, y el odontologo solo la suya. El
                // paciente entra por /mias. Que el odontologo solo vea las suyas no
                // es un patron de ruta, lo fija CitaConsultaService desde el token.
                // Cancelar y ver la bitacora siguen siendo de recepcion: los cubre
                // el comodin de mas abajo.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/citas").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-15: las citas del propio paciente. Va **antes** del comodin
                // de mas abajo, que exige recepcion o administracion: declarada
                // despues, el paciente recibiria 403 en su propia consulta.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/citas/mias").hasAuthority("SCOPE_PACIENTE")
                // HU-15: el paciente cancela **la suya** y dentro de la ventana de
                // RN-06. Que sea suya y que la ventana lo permita no son patrones
                // de ruta: los decide CancelacionService, con la cita delante.
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/citas/*/cancelar").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-16: el cierre de la cita suma al ODONTOLOGO, que el comodin de
                // mas abajo dejaria fuera. Ambas van antes que el, como /mias.
                // «(la propia)» no es un patron de ruta: que la cita sea de su
                // registro lo comprueba CierreDeCita, y que la cola solo traiga las
                // suyas, CitaConsultaService.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/citas/pendientes-cierre").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.PATCH, "/api/v1/citas/*/resultado").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/citas/**").hasAnyAuthority("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR")
                // HU-17 · RF-23: planificar es del odontologo y del administrador.
                // El comodin cubre las dos operaciones y todo lo que se anada
                // bajo el prefijo, para que ninguna ruta nueva caiga en el
                // anyRequest() del final. Que el odontologo solo pueda suspender
                // lo suyo no es un patron de ruta: lo decide PlanService.
                //
                // El paciente consulta los planes que son suyos, y por eso la
                // lectura va **antes** del comodin: declarada despues recibiria 403
                // sobre lo propio, como le pasaria a /citas/mias. Que solo alcance su
                // ficha no hace falta escribirlo aqui: PlanService.deFicha se lo
                // pregunta a PacienteAccessGuard, que al paciente solo le concede la
                // suya.
                //
                // Lo mismo con el detalle de un plan, que es de donde sale su
                // linea de tiempo: tambien va antes del comodin, y quien lee cual
                // lo decide PlanService.obtener con el plan en la mano. El
                // seguimiento se declara antes que el detalle porque es solo del
                // odontologo, y el patron de un segmento tambien lo cubriria.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/planes").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/planes/seguimiento").hasAuthority("SCOPE_ODONTOLOGO")
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/planes/*").hasAnyAuthority("SCOPE_PACIENTE", "SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                .requestMatchers("/api/v1/planes/**").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
                // El catalogo cerrado de recomendaciones lo lee quien cierra una
                // sesion, que es el unico sitio desde donde se elige de el.
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/recomendaciones").hasAnyAuthority("SCOPE_ODONTOLOGO", "SCOPE_ADMINISTRADOR")
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
