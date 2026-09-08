package pe.edu.dentalcite.usuario.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import pe.edu.dentalcite.TestcontainersConfiguration;
import pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class UsuarioControllerIntegrationTest {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    private ObjectMapper objectMapper = new ObjectMapper();


    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void getUsuarios_conRolPaciente_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void getUsuarios_sinParametros_retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void getUsuarios_conPaginacion_retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios").param("page", "0").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void getUsuarios_conSortPorDefectoDeSwagger_retorna200() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios")
                        .param("page", "0")
                        .param("size", "1")
                        .param("sort", "[\"string\"]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void getUsuarios_conSort_ordenaPorCorreoEnAmbosSentidos() throws Exception {
        // Se comprueba el ORDEN, no qué cuentas ocupan cada posición. Afirmar
        // posiciones absolutas ataba el test a los datos semilla: bastaba con que
        // otra prueba dejase una cuenta que ordenara antes —y
        // AltaDePersonalIntegrationTest lo hace a propósito, porque no es
        // @Transactional— para romperlo sin que nada estuviera mal.
        List<String> ascendente = correosOrdenadosPor("correo,asc");
        assertTrue(ascendente.size() >= 2,
                "hacen falta al menos dos cuentas para que comprobar el orden signifique algo");

        List<String> esperadoAsc = new ArrayList<>(ascendente);
        esperadoAsc.sort(Comparator.naturalOrder());
        assertEquals(esperadoAsc, ascendente, "sort=correo,asc no devolvió los correos en orden");

        // El sentido inverso no es redundante: sin él, un `sort` que el servidor
        // ignorase pasaría igual siempre que el orden natural de la tabla fuese
        // ascendente. Que ambos sentidos respondan es lo que prueba que el
        // parámetro se aplica de verdad.
        List<String> descendente = correosOrdenadosPor("correo,desc");
        List<String> esperadoDesc = new ArrayList<>(descendente);
        esperadoDesc.sort(Comparator.reverseOrder());
        assertEquals(esperadoDesc, descendente,
                "sort=correo,desc no devolvió los correos en orden inverso");
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void getUsuarios_conSortEntreCorchetesDeSwagger_noRompeElEndpoint() throws Exception {
        // Swagger UI serializa `sort` como un array JSON. Spring no lo interpreta
        // como criterio de orden —lo ignora en silencio, sin error—, así que aquí
        // solo se fija que la petición no reviente: comprobar que ordena sería
        // afirmar algo que no ocurre.
        //
        // Ordenar desde Swagger UI, por tanto, no funciona; el cliente Angular usa
        // la forma plana `correo,asc`, que sí.
        List<String> conCorchetes = correosOrdenadosPor("[\"correo,desc\"]");
        assertTrue(conCorchetes.size() >= 2, "el endpoint debe responder con datos");
    }

    /** Pide una página con el `sort` dado y devuelve sus correos, en el orden recibido. */
    private List<String> correosOrdenadosPor(String sort) throws Exception {
        String cuerpo = mockMvc.perform(get("/api/v1/usuarios")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", sort))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andReturn().getResponse().getContentAsString();

        List<String> correos = new ArrayList<>();
        objectMapper.readTree(cuerpo).get("content")
                .forEach(nodo -> correos.add(nodo.get("correo").asText()));
        return correos;
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void crearUsuario_conRolAdmin_retorna201() throws Exception {
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setNombre("Recepcion Demo"); // HU-04: correo, nombre y rol
        request.setCorreo("nuevo.odonto@dentalcite.com");
        request.setRol("ODONTOLOGO");
        // RN-11: una cuenta ODONTOLOGO necesita ficha, o nunca podría registrarse
        // como odontólogo ni declarar su horario.
        request.setDocumento("40555001");
        request.setPasswordProvisional("temporal123");

        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    // Nota: El test del filtro de Password requiere un token JWT real generado,
    // por lo que WithMockUser no pasará por el PasswordFilter (que lee claims del
    // JWT).
    // Para probarlo end-to-end deberíamos autenticarnos y usar el Bearer.

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void obtenerUsuarioPorId_conRolAdmin_retornaLaCuenta() throws Exception {
        // Tabla 10: el ADMINISTRADOR tiene C, R, U, D sobre cuentas de usuario.
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setNombre("Odontologo Demo");
        request.setCorreo("get-por-id@dentalcite.com");
        request.setRol("ODONTOLOGO");
        request.setDocumento("40555002");
        request.setPasswordProvisional("temporal123");

        String creado = mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(creado).get("id").asText();

        mockMvc.perform(get("/api/v1/usuarios/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value("get-por-id@dentalcite.com"))
                .andExpect(jsonPath("$.nombre").value("Odontologo Demo"));
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ADMINISTRADOR")
    void obtenerUsuarioPorId_conIdInexistente_retorna404() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/" + java.util.UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void obtenerUsuarioPorId_conRolPaciente_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/" + java.util.UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void obtenerUsuarioPorId_conRolRecepcionista_retorna403() throws Exception {
        mockMvc.perform(get("/api/v1/usuarios/" + java.util.UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    // HU-04: «Dado que no soy ADMINISTRADOR, cuando invoque cualquier operación
    // sobre cuentas, entonces recibiré 403» (RNF-04). Hasta aquí solo estaban
    // cubiertas las lecturas; la Definición de Terminado pide las negativas de
    // todas las operaciones propias de la historia.

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void crearUsuario_conRolRecepcionista_retorna403() throws Exception {
        UsuarioRequestDTO request = new UsuarioRequestDTO();
        request.setNombre("Intruso");
        request.setCorreo("intruso@dentalcite.com");
        request.setRol("ADMINISTRADOR");

        mockMvc.perform(post("/api/v1/usuarios")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_ODONTOLOGO")
    void actualizarUsuario_conRolOdontologo_retorna403() throws Exception {
        mockMvc.perform(patch("/api/v1/usuarios/" + java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"activo\": false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_PACIENTE")
    void reemplazarUsuario_conRolPaciente_retorna403() throws Exception {
        mockMvc.perform(put("/api/v1/usuarios/" + java.util.UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\": \"X\", \"rol\": \"ADMINISTRADOR\", \"activo\": true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "SCOPE_RECEPCIONISTA")
    void asignarPasswordProvisional_conRolRecepcionista_retorna403() throws Exception {
        // Es la operación de mayor privilegio de la historia: concede el acceso a
        // una cuenta ajena, así que su negativa importa más que la de las demás.
        mockMvc.perform(post("/api/v1/usuarios/" + java.util.UUID.randomUUID() + "/password-provisional")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"provisional123\"}"))
                .andExpect(status().isForbidden());
    }
}
