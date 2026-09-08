package pe.edu.dentalcite.auth.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import pe.edu.dentalcite.auth.api.dto.LoginRequest;
import pe.edu.dentalcite.auth.api.dto.RegistroRequest;
import pe.edu.dentalcite.auth.service.AuthService;
import pe.edu.dentalcite.common.api.GlobalExceptionHandler;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AuthControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
        // Configuramos MockMvc con el manejador global de excepciones
        mockMvc = MockMvcBuilders.standaloneSetup(authController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    public void registrarUsuario_ConDatosValidos_DeberiaRetornarCreated() throws Exception {
        RegistroRequest request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setDocumento("12345678");
        request.setCorreo("test@test.com");
        request.setTelefono("987654321");
        request.setPassword("miPassword123");
        request.setConsentimientoAceptado(true);
        request.setVersionConsentimiento("v1.0");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Registro exitoso"));
    }

    @Test
    public void registrarUsuario_SinAceptarConsentimiento_DeberiaRetornarBadRequest() throws Exception {
        RegistroRequest request = new RegistroRequest();
        request.setNombres("Ana");
        request.setApellidos("Quispe");
        request.setDocumento("12345678");
        request.setCorreo("test@test.com");
        request.setTelefono("987654321");
        request.setPassword("miPassword123");
        request.setConsentimientoAceptado(false); // null o false fallará en @Valid o en Controller
        request.setVersionConsentimiento("v1.0");

        mockMvc.perform(post("/api/v1/auth/registro")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void login_ConDatosValidos_DeberiaRetornarOkConToken() throws Exception {
        LoginRequest request = new LoginRequest("test@test.com", "miPassword123");
        
        Mockito.when(authService.login(Mockito.any())).thenReturn("fake-jwt-token");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("fake-jwt-token"));
    }

    @Test
    public void login_ConCuentaBloqueada_DeberiaRetornar423() throws Exception {
        LoginRequest request = new LoginRequest("test@test.com", "miPassword123");
        
        Mockito.when(authService.login(Mockito.any()))
               .thenThrow(new org.springframework.security.authentication.LockedException("Bloqueado"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.message").value("Bloqueado"));
    }
}
