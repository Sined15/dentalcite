package pe.edu.dentalcite.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.auth.api.dto.LoginRequest;
import pe.edu.dentalcite.auth.api.dto.RegistroRequest;
import java.util.UUID;
import java.time.OffsetDateTime;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;
import pe.edu.dentalcite.ficha.repository.FichaRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional // Revierte los cambios en BD al final de cada test para mantener el aislamiento
public class AuthControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private FichaRepository fichaRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private pe.edu.dentalcite.consentimiento.repository.ConsentimientoRepository consentimientoRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void registrar_flujoCompleto_retorna201yGuardaEnBD() throws Exception {
        RegistroRequest request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setDocumento("99887766");
        request.setCorreo("nuevo@dentalcite.com");
        request.setTelefono("987654321");
        request.setPassword("mipassword123");
        request.setConsentimientoAceptado(true);
        request.setVersionConsentimiento("v1.0");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registro exitoso"));

        // Verificamos que realmente se insertó en la base de datos de prueba
        assertTrue(usuarioRepository.existsByCorreo("nuevo@dentalcite.com"));
        assertTrue(fichaRepository.existsByTipoDocumentoAndDocumento("DNI", "99887766"));
    }

    @Test
    void registrar_almacenaLaContrasenaComoHashBcryptDeCoste12() throws Exception {
        // HU-02: «Dado cualquier registro aceptado, cuando se inspeccione la base
        // de datos, entonces la contraseña estará almacenada como hash BCrypt de
        // coste 12» (RNF-03). El coste solo constaba en la configuración del
        // codificador: nada impedía bajarlo sin que ninguna prueba se enterase.
        String correo = "hash@dentalcite.com";
        String enClaro = "mipassword123";

        RegistroRequest request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setDocumento("31415926");
        request.setCorreo(correo);
        request.setTelefono("987654321");
        request.setPassword(enClaro);
        request.setConsentimientoAceptado(true);
        request.setVersionConsentimiento("v1.0");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        String hash = usuarioRepository.findByCorreo(correo).orElseThrow().getContrasenaHash();

        // El prefijo `$2a$12$` es el coste; `matches` descarta que sea un literal
        // con la forma correcta pero ajeno a la contraseña enviada.
        assertTrue(hash.startsWith("$2a$12$"), "debe ser BCrypt de coste 12, pero es: " + hash);
        assertFalse(hash.contains(enClaro), "la contraseña no puede quedar en claro");
        assertTrue(passwordEncoder.matches(enClaro, hash), "el hash debe corresponder a la contraseña enviada");
    }

    @Test
    void registrar_conDocumentoDuplicado_retornaConflicto409() throws Exception {
        // 1. Registramos a alguien exitosamente
        RegistroRequest request1 = new RegistroRequest();
        request1.setNombres("Ana");
        request1.setApellidos("Quispe");
        request1.setDocumento("55555555");
        request1.setCorreo("uno@dentalcite.com");
        request1.setTelefono("987654321");
        request1.setPassword("pwd12345");
        request1.setConsentimientoAceptado(true);
        request1.setVersionConsentimiento("v1.0");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isCreated());

        // 2. Intentamos registrar a OTRA persona, pero con la misma Ficha (Documento) que ya tiene cuenta
        RegistroRequest request2 = new RegistroRequest();
        request2.setNombres("Ana");
        request2.setApellidos("Quispe");
        request2.setDocumento("55555555");
        request2.setCorreo("dos@dentalcite.com");
        request2.setTelefono("987654321");
        request2.setPassword("pwd12345");
        request2.setConsentimientoAceptado(true);
        request2.setVersionConsentimiento("v1.0");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                // Spring Security AHORA DEJARÁ PASAR EL ERROR porque habilitamos /error
                // y GlobalExceptionHandler devolverá 409 Conflict
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("La ficha ya tiene una cuenta asociada."));
    }

    @Test
    void login_credencialesCorrectas_retorna200YToken() throws Exception {
        // 1. Preparamos la BD registrando un usuario
        RegistroRequest request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setDocumento("77777777");
        request.setCorreo("testlogin@dentalcite.com");
        request.setTelefono("987654321");
        request.setPassword("secreta123");
        request.setConsentimientoAceptado(true);
        request.setVersionConsentimiento("v1.0");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // 2. Intentamos Iniciar Sesión con Spring Security habilitado
        LoginRequest loginReq = new LoginRequest("testlogin@dentalcite.com", "secreta123");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void registrar_almacenaElConsentimientoConSuFechaYSuVersion() throws Exception {
        // RNF-06: «Cada alta registrara el consentimiento informado con su fecha y
        // la version del texto». Hasta ahora los tests fijaban el campo en la
        // peticion pero nadie comprobaba que la fila quedara escrita.
        RegistroRequest request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setDocumento("31415926");
        request.setCorreo("consentimiento@dentalcite.com");
        request.setTelefono("987654321");
        request.setPassword("mipassword123");
        request.setConsentimientoAceptado(true);
        request.setVersionConsentimiento("v2.3");

        OffsetDateTime antes = OffsetDateTime.now().minusMinutes(1);

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        UUID usuarioId = usuarioRepository.findByCorreo("consentimiento@dentalcite.com")
                .orElseThrow().getId();

        Consentimiento consentimiento = consentimientoRepository.findAll().stream()
                .filter(c -> c.getUsuario().getId().equals(usuarioId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no se registro el consentimiento del alta"));

        assertEquals("v2.3", consentimiento.getVersionTexto());
        assertNotNull(consentimiento.getFecha());
        assertTrue(consentimiento.getFecha().isAfter(antes),
                "la fecha del consentimiento debe ser la del alta");
    }

    @Test
    void registrar_sinAceptarElConsentimiento_noCreaNiCuentaNiConsentimiento() throws Exception {
        // RNF-06: «cuando envie el formulario, entonces recibire 400 y no se creara
        // el registro».
        long consentimientosAntes = consentimientoRepository.count();

        RegistroRequest request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setDocumento("27182818");
        request.setCorreo("sin-consentimiento@dentalcite.com");
        request.setTelefono("987654321");
        request.setPassword("mipassword123");
        request.setConsentimientoAceptado(false);
        request.setVersionConsentimiento("v2.3");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertFalse(usuarioRepository.existsByCorreo("sin-consentimiento@dentalcite.com"));
        assertFalse(fichaRepository.existsByTipoDocumentoAndDocumento("DNI", "27182818"));
        assertEquals(consentimientosAntes, consentimientoRepository.count());
    }
}

