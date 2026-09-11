package pe.edu.dentalcite.plan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.paciente.service.PacienteAccessGuard;
import pe.edu.dentalcite.plan.api.dto.PlanRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanResponseDTO;
import pe.edu.dentalcite.plan.domain.Plan;
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas de HU-17, una por prueba. RN-13 la garantiza el índice de la base,
 * así que aquí se fija lo que el servicio hace con su choque; que el índice
 * exista y muerda lo comprueba {@code PlanDeTratamientoIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock private PlanRepository planRepository;
    @Mock private FichaRepository fichaRepository;
    @Mock private TratamientoRepository tratamientoRepository;
    @Mock private OdontologoRepository odontologoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteAccessGuard accessGuard;

    private PlanService servicio;

    private UUID luisUsuarioId;
    private Ficha fichaLuis;
    private Odontologo luis;
    private Ficha fichaPaciente;
    private Tratamiento tratamiento;

    @BeforeEach
    void setUp() {
        servicio = new PlanService(planRepository, fichaRepository, tratamientoRepository,
                odontologoRepository, usuarioRepository, accessGuard);

        luisUsuarioId = UUID.randomUUID();
        fichaLuis = Ficha.builder().id(UUID.randomUUID()).numeroHistoria("HC-00009").build();
        luis = Odontologo.builder().id(UUID.randomUUID()).cop("COP-1")
                .nombres("Luis").apellidos("Perez").ficha(fichaLuis).activo(true).build();

        fichaPaciente = Ficha.builder().id(UUID.randomUUID()).numeroHistoria("HC-00001")
                .nombres("Julio").apellidos("Ramos").build();
        tratamiento = Tratamiento.builder().id(UUID.randomUUID()).codigo("T-001")
                .nombre("Ortodoncia").duracionMinutos(30).activo(true).build();

        autenticar(luisUsuarioId, "SCOPE_ODONTOLOGO");
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    private void autenticar(UUID quien, String autoridad) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(quien.toString(), null,
                        List.of(new SimpleGrantedAuthority(autoridad))));
    }

    /** El camino feliz: la cuenta resuelve a Luis, y el paciente y el tratamiento existen. */
    private void todoEnOrden() {
        lenient().when(usuarioRepository.findById(luisUsuarioId)).thenReturn(Optional.of(
                Usuario.builder().id(luisUsuarioId).rol("ODONTOLOGO").ficha(fichaLuis).build()));
        lenient().when(odontologoRepository.findByFichaId(fichaLuis.getId()))
                .thenReturn(Optional.of(luis));
        lenient().when(fichaRepository.findById(fichaPaciente.getId()))
                .thenReturn(Optional.of(fichaPaciente));
        lenient().when(tratamientoRepository.findById(tratamiento.getId()))
                .thenReturn(Optional.of(tratamiento));
        lenient().when(planRepository.saveAndFlush(any())).thenAnswer(inv -> {
            Plan p = inv.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });
    }

    private PlanRequestDTO peticion(int sesiones) {
        return PlanRequestDTO.builder()
                .pacienteId(fichaPaciente.getId())
                .tratamientoId(tratamiento.getId())
                .sesionesPrevistas(sesiones)
                .build();
    }

    /** El choque contra el índice único parcial de V19. */
    private DataIntegrityViolationException planDuplicado() {
        return new DataIntegrityViolationException("choque",
                new org.hibernate.exception.ConstraintViolationException(
                        "duplicate key value violates unique constraint \"ux_plan_activo_por_tratamiento\"",
                        new java.sql.SQLException("23505"), "ux_plan_activo_por_tratamiento"));
    }

    // ------------------------------------------------------------------
    // Criterio 1 · nace ACTIVO con sus sesiones numeradas y pendientes
    // ------------------------------------------------------------------

    @Test
    void crear_conSeisSesiones_dejaElPlanActivoConSeisSesionesPendientes() {
        todoEnOrden();

        PlanResponseDTO plan = servicio.crear(peticion(6));

        assertTrue(plan.getActivo());
        assertEquals(6, plan.getSesionesPrevistas());
        assertEquals(6, plan.getSesiones().size());
        assertTrue(plan.getSesiones().stream().allMatch(s -> "PENDIENTE".equals(s.getEstado())));
    }

    @Test
    void crear_numeraLasSesionesDesdeUnoYSinHuecos() {
        // HU-18 enlazara cada cita atendida a «la primera sesion pendiente en
        // orden» (RN-15): sin numeracion contigua no hay tal orden.
        todoEnOrden();

        List<Integer> numeros = servicio.crear(peticion(4)).getSesiones().stream()
                .map(PlanResponseDTO.Sesion::getNumero).toList();

        assertEquals(List.of(1, 2, 3, 4), numeros);
    }

    @Test
    void crear_noDevuelveAvance() {
        // RN-14: el avance se deriva de las citas atendidas enlazadas y eso es
        // HU-18. Un campo a cero aqui prometeria un dato que no significa nada.
        todoEnOrden();

        PlanResponseDTO plan = servicio.crear(peticion(3));

        assertEquals(3, plan.getSesionesPrevistas());
        assertTrue(plan.getSesiones().stream().noneMatch(s -> s.getEstado() == null));
    }

    @Test
    void crear_dejaConstanciaDeQuienPlanifica() {
        todoEnOrden();

        assertEquals(luis.getId(), servicio.crear(peticion(3)).getOdontologo().getId());
    }

    // ------------------------------------------------------------------
    // Criterio 2 · RN-13, garantizada por el indice
    // ------------------------------------------------------------------

    @Test
    void crear_conUnPlanActivoDelMismoTratamiento_lanzaIllegalState() {
        todoEnOrden();
        // `doThrow` y no `when(...).thenThrow`: `when` volveria a invocar el
        // `thenAnswer` que dejo `todoEnOrden`, con los argumentos nulos del
        // matcher, y reventaria antes de re-estubar.
        doThrow(planDuplicado()).when(planRepository).saveAndFlush(any());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> servicio.crear(peticion(3)));

        assertTrue(ex.getMessage().contains("Ortodoncia"), ex.getMessage());
        assertTrue(ex.getMessage().contains("RN-13"), ex.getMessage());
    }

    @Test
    void crear_noCompruebaAntesDeInsertar() {
        // El criterio pide que el 409 lo garantice el indice y no una consulta
        // previa: entre mirar y crear cabe otra creacion.
        todoEnOrden();

        servicio.crear(peticion(3));

        verify(planRepository, never()).findByFichaIdOrderByCreadoEnDesc(any());
    }

    @Test
    void crear_conOtroFalloDeIntegridad_loPropaga() {
        // No es el plan duplicado: no hay que disfrazarlo de conflicto de RN-13.
        todoEnOrden();
        doThrow(new DataIntegrityViolationException("otra cosa"))
                .when(planRepository).saveAndFlush(any());

        assertThrows(DataIntegrityViolationException.class, () -> servicio.crear(peticion(3)));
    }

    // ------------------------------------------------------------------
    // Criterio 3 · suspender conserva el registro (RN-12)
    // ------------------------------------------------------------------

    @Test
    void suspender_bajaLaMarcaDeActividadYGuardaElMotivo() {
        todoEnOrden();
        Plan plan = planEnCurso();
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        PlanResponseDTO respuesta = servicio.suspender(plan.getId(), "El paciente se muda");

        assertFalse(respuesta.getActivo());
        assertEquals("El paciente se muda", respuesta.getMotivoSuspension());
        // RN-12: nada se borra.
        verify(planRepository, never()).delete(any());
        verify(planRepository, never()).deleteById(any());
    }

    @Test
    void suspender_conservaLasSesionesDelPlan() {
        // «Su registro se conservara»: el plan suspendido sigue contando lo que
        // se habia previsto.
        todoEnOrden();
        Plan plan = planEnCurso();
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        assertEquals(5, servicio.suspender(plan.getId(), "Motivo").getSesiones().size());
    }

    @Test
    void suspender_unPlanYaSuspendido_lanzaIllegalState() {
        todoEnOrden();
        Plan plan = planEnCurso();
        plan.suspender("El primero");
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        assertThrows(IllegalStateException.class,
                () -> servicio.suspender(plan.getId(), "El segundo"));
    }

    @Test
    void suspender_unPlanInexistente_lanzaResourceNotFound() {
        UUID inventado = UUID.randomUUID();
        when(planRepository.findConDetalleById(inventado)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> servicio.suspender(inventado, "Motivo"));
    }

    @Test
    void suspender_elPlanDeOtroOdontologo_lanzaForbidden() {
        todoEnOrden();
        Plan plan = planEnCurso();
        plan.setOdontologo(Odontologo.builder().id(UUID.randomUUID()).cop("COP-2")
                .nombres("Ana").apellidos("Quispe").activo(true).build());
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.suspender(plan.getId(), "Motivo"));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void suspender_comoAdministrador_puedeConElPlanDeCualquiera() {
        UUID adminId = UUID.randomUUID();
        autenticar(adminId, "SCOPE_ADMINISTRADOR");
        Plan plan = planEnCurso();
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        assertFalse(servicio.suspender(plan.getId(), "Correccion administrativa").getActivo());
    }

    private Plan planEnCurso() {
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .ficha(fichaPaciente).tratamiento(tratamiento).odontologo(luis)
                .sesionesPrevistas(5).activo(true)
                .build();
        plan.generarSesiones();
        return plan;
    }

    // ------------------------------------------------------------------
    // Criterio 4 y el resto de RNF-04
    // ------------------------------------------------------------------

    @Test
    void crear_comoOdontologoIndicandoOtroOdontologo_lanzaForbidden() {
        // Planificar en nombre de otro profesional no es una operacion del alcance.
        todoEnOrden();
        PlanRequestDTO ajeno = peticion(3);
        ajeno.setOdontologoId(UUID.randomUUID());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.crear(ajeno));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void crear_comoOdontologoIndicandoSuPropioRegistro_tambienLanzaForbidden() {
        // Aceptarlo cuando coincide convertiria la respuesta en un oraculo para
        // averiguar identificadores ajenos, como en HU-14.
        todoEnOrden();
        PlanRequestDTO propio = peticion(3);
        propio.setOdontologoId(luis.getId());

        assertThrows(ResponseStatusException.class, () -> servicio.crear(propio));
    }

    @Test
    void crear_comoAdministradorSinIndicarOdontologo_lanzaIllegalArgument() {
        // 400 y no 403: el rol si puede crear planes, lo que falta es a nombre de
        // que profesional. Su cuenta no tiene registro propio.
        autenticar(UUID.randomUUID(), "SCOPE_ADMINISTRADOR");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> servicio.crear(peticion(3)));

        assertTrue(ex.getMessage().contains("odontologoId"), ex.getMessage());
    }

    @Test
    void crear_comoAdministradorConUnOdontologoDeBaja_lanzaResourceNotFound() {
        UUID adminId = UUID.randomUUID();
        autenticar(adminId, "SCOPE_ADMINISTRADOR");
        luis.setActivo(false);
        when(odontologoRepository.findById(luis.getId())).thenReturn(Optional.of(luis));

        PlanRequestDTO conBaja = peticion(3);
        conBaja.setOdontologoId(luis.getId());

        assertThrows(ResourceNotFoundException.class, () -> servicio.crear(conBaja));
    }

    @Test
    void crear_comoPaciente_lanzaForbidden() {
        // Criterio 4. El servicio no se fia de que SecurityConfig ya lo corte.
        autenticar(UUID.randomUUID(), "SCOPE_PACIENTE");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.crear(peticion(3)));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void crear_comoRecepcionista_lanzaForbidden() {
        autenticar(UUID.randomUUID(), "SCOPE_RECEPCIONISTA");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.crear(peticion(3)));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void crear_conCuentaDeOdontologoSinRegistro_lanzaForbidden() {
        when(usuarioRepository.findById(luisUsuarioId)).thenReturn(Optional.of(
                Usuario.builder().id(luisUsuarioId).rol("ODONTOLOGO").build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.crear(peticion(3)));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void crear_sinAutenticacion_lanzaUnauthorized() {
        SecurityContextHolder.clearContext();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.crear(peticion(3)));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // Catalogo
    // ------------------------------------------------------------------

    @Test
    void crear_conPacienteInexistente_lanzaResourceNotFound() {
        todoEnOrden();
        when(fichaRepository.findById(fichaPaciente.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.crear(peticion(3)));
    }

    @Test
    void crear_conTratamientoDadoDeBaja_lanzaResourceNotFound() {
        todoEnOrden();
        tratamiento.setActivo(false);

        assertThrows(ResourceNotFoundException.class, () -> servicio.crear(peticion(3)));
    }

    // ------------------------------------------------------------------
    // RNF-06 · el vinculo con el paciente
    // ------------------------------------------------------------------

    @Test
    void crear_paraUnPacienteQueNoHaAtendido_propagaElForbiddenDelGuard() {
        todoEnOrden();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No ha atendido a este paciente"))
                .when(accessGuard).verificarLectura(fichaPaciente.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.crear(peticion(3)));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        // Y no se planifica nada: el 403 llega antes del INSERT.
        verify(planRepository, never()).saveAndFlush(any());
    }

    @Test
    void crear_compruebaElVinculoDespuesDeLaFichaYAntesDelTratamiento() {
        // El orden es contrato: una ficha inexistente da 404 y no 403, asi que el
        // guard no puede consultarse antes de resolverla.
        todoEnOrden();
        when(fichaRepository.findById(fichaPaciente.getId())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.crear(peticion(3)));

        verify(accessGuard, never()).verificarLectura(any());
    }

    @Test
    void deFicha_deUnPacienteQueNoHaAtendido_propagaElForbiddenDelGuard() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No ha atendido a este paciente"))
                .when(accessGuard).verificarLectura(fichaPaciente.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.deFicha(fichaPaciente.getId()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
        // Ni se llega a consultar: el nombre del paciente no sale de la base.
        verify(planRepository, never()).findByFichaIdOrderByCreadoEnDesc(any());
    }
}
