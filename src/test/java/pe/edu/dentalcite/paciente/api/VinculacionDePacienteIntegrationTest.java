package pe.edu.dentalcite.paciente.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cuarto criterio de HU-12: «dada esa ficha, cuando el paciente se registre
 * después en el portal con el mismo documento, entonces quedará vinculada en
 * lugar de duplicarse» (HU-02, RN-10).
 *
 * <p>La lógica ya vivía en {@code AuthService.registrarPaciente}; lo que no
 * existía era la otra mitad del recorrido —una ficha nacida en el mostrador— ni
 * prueba alguna de que las dos vías converjan en la misma persona.
 *
 * <p>Sin {@code @Transactional}: son dos peticiones HTTP distintas, y la segunda
 * tiene que ver lo que la primera confirmó.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class VinculacionDePacienteIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private FichaRepository fichaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void elRegistroEnElPortalVinculaLaFichaDelAltaPresencialEnVezDeDuplicarla() throws Exception {
        String documento = String.valueOf(ThreadLocalRandom.current().nextLong(40_000_000L, 79_999_999L));

        // 1. La recepción da de alta al paciente que llegó al mostrador, sin
        //    teléfono y con los datos que dictó de viva voz.
        String alta = mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Rosa","apellidos":"Huaman","tipoDocumento":"DNI",
                                 "documento":"%s","consentimientoAceptado":true,
                                 "versionConsentimiento":"1.0"}
                                """.formatted(documento)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode presencial = objectMapper.readTree(alta);
        UUID fichaId = UUID.fromString(presencial.path("id").asText());
        String numeroHistoria = presencial.path("numeroHistoria").asText();

        // 2. Semanas después, la misma persona se crea una cuenta en el portal.
        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Rosa","apellidos":"Huaman","tipoDocumento":"DNI",
                                 "documento":"%s","correo":"rosa-%s@demo.com","telefono":"987654321",
                                 "password":"Password123","consentimientoAceptado":true,
                                 "versionConsentimiento":"1.0"}
                                """.formatted(documento, documento)))
                .andExpect(status().isCreated());

        // 3. Es la misma ficha, con su número de historia intacto: RN-10 lo
        //    declara inmutable, y duplicarla partiría en dos la historia clínica.
        Ficha ficha = fichaRepository.findByTipoDocumentoAndDocumento("DNI", documento).orElseThrow();
        assertEquals(fichaId, ficha.getId());
        assertEquals(numeroHistoria, ficha.getNumeroHistoria());

        // 4. Y ahora sí tiene cuenta: la ficha del mostrador es la que la recibe.
        assertTrue(usuarioRepository.existsByFichaId(fichaId));

        // 5. El registro completó el hueco que el alta presencial dejó.
        assertEquals("987654321", ficha.getTelefono());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void elRegistroConEspaciosAlrededorDelDocumentoTambienVinculaLaFicha() throws Exception {
        // Un espacio que se cuela al escribir —o que añade el autocompletado del
        // móvil— hacía que el documento no coincidiera y naciera una segunda ficha.
        String documento = String.valueOf(ThreadLocalRandom.current().nextLong(40_000_000L, 79_999_999L));

        String alta = mockMvc.perform(post("/api/v1/pacientes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Hugo","apellidos":"Salcedo","tipoDocumento":"DNI",
                                 "documento":"%s","consentimientoAceptado":true,
                                 "versionConsentimiento":"1.0"}
                                """.formatted(documento)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID fichaId = UUID.fromString(objectMapper.readTree(alta).path("id").asText());

        mockMvc.perform(post("/api/v1/auth/registro")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombres":"Hugo","apellidos":"Salcedo","tipoDocumento":"DNI",
                                 "documento":"  %s ","correo":"hugo-%s@demo.com",
                                 "password":"Password123","consentimientoAceptado":true,
                                 "versionConsentimiento":"1.0"}
                                """.formatted(documento, documento)))
                .andExpect(status().isCreated());

        assertTrue(usuarioRepository.existsByFichaId(fichaId), "la cuenta debe colgar de la ficha del mostrador");
        assertTrue(fichaRepository.findByTipoDocumentoAndDocumento("DNI", "  " + documento + " ").isEmpty(),
                "no debe nacer una segunda ficha con el documento sin recortar");
    }
}
