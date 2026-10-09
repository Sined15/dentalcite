package pe.edu.dentalcite.plan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.paciente.service.PacienteAccessGuard;
import pe.edu.dentalcite.plan.api.dto.CierreSesionRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanResponseDTO;
import pe.edu.dentalcite.plan.domain.Plan;
import pe.edu.dentalcite.plan.domain.PlanSesion;
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;
import pe.edu.dentalcite.recomendacion.service.RecomendacionService;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
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

    /** La de la clínica: «hoy» se decide con su calendario, no con el del servidor. */
    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Mock private PlanRepository planRepository;
    @Mock private FichaRepository fichaRepository;
    @Mock private TratamientoRepository tratamientoRepository;
    @Mock private OdontologoRepository odontologoRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private PacienteAccessGuard accessGuard;
    @Mock private EnlaceDeSesiones enlaceDeSesiones;
    @Mock private RecomendacionService recomendacionService;

    private PlanService servicio;

    private UUID luisUsuarioId;
    private Ficha fichaLuis;
    private Odontologo luis;
    private Ficha fichaPaciente;
    private Tratamiento tratamiento;

    @BeforeEach
    void setUp() {
        servicio = new PlanService(planRepository, fichaRepository, tratamientoRepository,
                odontologoRepository, usuarioRepository, accessGuard, enlaceDeSesiones,
                recomendacionService, ZONA.getId());

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
    // Las citas ya atendidas ocupan sesiones al crear el plan
    // ------------------------------------------------------------------

    @Test
    void crear_pideEnlazarLasCitasYaAtendidasSobreElPlanGuardado() {
        todoEnOrden();

        servicio.crear(peticion(3));

        ArgumentCaptor<Plan> plan = ArgumentCaptor.forClass(Plan.class);
        verify(enlaceDeSesiones).enlazarRetroactivas(plan.capture(), any(OffsetDateTime.class));
        // Con el plan ya guardado: si el índice lo hubiera rechazado no habría a
        // qué enlazar nada.
        assertNotNull(plan.getValue().getId());
        assertEquals(fichaPaciente, plan.getValue().getFicha());
    }

    @Test
    void crear_conUnPlanActivoDuplicado_noIntentaEnlazarNada() {
        todoEnOrden();
        doThrow(planDuplicado()).when(planRepository).saveAndFlush(any());

        assertThrows(IllegalStateException.class, () -> servicio.crear(peticion(3)));
        verify(enlaceDeSesiones, never()).enlazarRetroactivas(any(), any());
    }

    @Test
    void crear_devuelveLaCitaQueOcupaCadaSesion() {
        todoEnOrden();
        Cita atendida = Cita.builder().id(UUID.randomUUID()).codigo("CIT-000042")
                .inicio(OffsetDateTime.parse("2026-09-01T09:00:00-05:00"))
                .estado(Cita.ESTADO_ATENDIDA).build();
        doAnswer(inv -> ((Plan) inv.getArgument(0)).enlazarRetroactivas(List.of(atendida)))
                .when(enlaceDeSesiones).enlazarRetroactivas(any(Plan.class), any(OffsetDateTime.class));

        PlanResponseDTO plan = servicio.crear(peticion(3));

        assertEquals("ATENDIDA", plan.getSesiones().get(0).getEstado());
        assertEquals("CIT-000042", plan.getSesiones().get(0).getCita().getCodigo());
        assertEquals(atendida.getInicio(), plan.getSesiones().get(0).getCita().getInicio());
        assertEquals("PENDIENTE", plan.getSesiones().get(1).getEstado());
        assertNull(plan.getSesiones().get(1).getCita());
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
    void crear_sinCitasPrevias_devuelveElAvanceConTodasPendientes() {
        todoEnOrden();

        PlanResponseDTO plan = servicio.crear(peticion(3));

        assertEquals(3, plan.getSesionesPrevistas());
        assertEquals(0, plan.getAvance().getCompletadas());
        assertEquals(3, plan.getAvance().getPendientes());
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
    // El cierre de la sesión con sus recomendaciones
    // ------------------------------------------------------------------

    private Recomendacion dieta;

    /** Un plan cuya primera sesión ya la ocupa una cita atendida: la que se puede cerrar. */
    private Plan planConLaPrimeraAtendida() {
        Plan plan = planEnCurso();
        plan.getSesiones().get(0).enlazar(Cita.builder().id(UUID.randomUUID())
                .codigo("CIT-000007").estado(Cita.ESTADO_ATENDIDA).build());
        when(planRepository.findParaCerrarSesion(plan.getId())).thenReturn(Optional.of(plan));
        return plan;
    }

    private CierreSesionRequestDTO cierre(LocalDate proximoControl, String observacion) {
        dieta = Recomendacion.builder().id(UUID.randomUUID())
                .descripcion("Mantener dieta blanda").activa(true).build();
        return CierreSesionRequestDTO.builder()
                .recomendacionIds(List.of(dieta.getId()))
                .proximoControl(proximoControl)
                .observacion(observacion)
                .build();
    }

    /** El catálogo reconoce lo que se le manda. */
    private void catalogoEnOrden() {
        when(recomendacionService.resolverVigentes(List.of(dieta.getId())))
                .thenReturn(Set.of(dieta));
    }

    @Test
    void cerrarSesion_conTodoEnOrden_dejaLaSesionCerradaConLoIndicado() {
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        LocalDate control = LocalDate.now(ZONA).plusDays(30);
        CierreSesionRequestDTO peticion = cierre(control, "Volver antes si hay dolor");
        catalogoEnOrden();

        PlanResponseDTO respuesta = servicio.cerrarSesion(plan.getId(), 1, peticion);

        PlanResponseDTO.Sesion primera = respuesta.getSesiones().get(0);
        assertEquals(PlanSesion.ESTADO_CERRADA, primera.getEstado());
        assertEquals(1, primera.getRecomendaciones().size());
        assertEquals("Mantener dieta blanda", primera.getRecomendaciones().get(0).getDescripcion());
        assertEquals(control, primera.getProximoControl());
        assertEquals("Volver antes si hay dolor", primera.getObservacion());
        // La cita que la ocupaba sigue siendo la suya.
        assertEquals("CIT-000007", primera.getCita().getCodigo());
        // Y las demás no se han tocado.
        assertEquals("PENDIENTE", respuesta.getSesiones().get(1).getEstado());
    }

    @Test
    void cerrarSesion_conObservacionEnBlanco_laGuardaComoAusente() {
        // Una observación vacía es no haber escrito ninguna: guardar la cadena haría
        // que el cliente pintara un apartado sin nada dentro.
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), "   ");
        catalogoEnOrden();

        assertNull(servicio.cerrarSesion(plan.getId(), 1, peticion).getSesiones().get(0)
                .getObservacion());
    }

    @Test
    void cerrarSesion_conLaFechaDeHoy_seAdmite() {
        // El límite es «no anterior a hoy»: el mismo día vale.
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA), null);
        catalogoEnOrden();

        assertEquals(PlanSesion.ESTADO_CERRADA,
                servicio.cerrarSesion(plan.getId(), 1, peticion).getSesiones().get(0).getEstado());
    }

    @Test
    void cerrarSesion_conUnaFechaAnteriorAHoy_lanzaIllegalArgument() {
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).minusDays(1), null);

        assertThrows(IllegalArgumentException.class,
                () -> servicio.cerrarSesion(plan.getId(), 1, peticion));

        // Y no se llega a consultar el catálogo.
        verify(recomendacionService, never()).resolverVigentes(any());
    }

    @Test
    void cerrarSesion_conUnaRecomendacionFueraDelCatalogo_propagaElIllegalArgument() {
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);
        when(recomendacionService.resolverVigentes(List.of(dieta.getId())))
                .thenThrow(new IllegalArgumentException("Alguna recomendacion no esta en el catalogo"));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.cerrarSesion(plan.getId(), 1, peticion));

        // La sesión se queda como estaba: el cierre es todo o nada.
        assertEquals(PlanSesion.ESTADO_ATENDIDA, plan.getSesiones().get(0).getEstado());
    }

    @Test
    void cerrarSesion_sobreUnaSesionSinCitaAtendida_lanzaIllegalState() {
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);

        // La segunda sigue pendiente: no hay ninguna consulta de la que hablar.
        assertThrows(IllegalStateException.class,
                () -> servicio.cerrarSesion(plan.getId(), 2, peticion));

        verify(recomendacionService, never()).resolverVigentes(any());
    }

    @Test
    void cerrarSesion_sobreUnaSesionYaCerrada_lanzaIllegalState() {
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), "La primera");
        catalogoEnOrden();
        servicio.cerrarSesion(plan.getId(), 1, peticion);

        assertThrows(IllegalStateException.class,
                () -> servicio.cerrarSesion(plan.getId(), 1, peticion));

        // Y lo indicado la primera vez sigue ahí.
        assertEquals("La primera", plan.getSesiones().get(0).getObservacion());
    }

    @Test
    void cerrarSesion_enElPlanDeOtroOdontologo_lanzaForbidden() {
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        plan.setOdontologo(Odontologo.builder().id(UUID.randomUUID()).cop("COP-2")
                .nombres("Ana").apellidos("Quispe").activo(true).build());
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.cerrarSesion(plan.getId(), 1, peticion));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
        assertEquals(PlanSesion.ESTADO_ATENDIDA, plan.getSesiones().get(0).getEstado());
    }

    @Test
    void cerrarSesion_enElPlanDeOtroOdontologo_respondeAntesDeMirarElEstado() {
        // El orden es contrato: decir «esa sesión ya está cerrada» sobre un plan
        // ajeno contaría de rebote en qué punto va un tratamiento de otro.
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        plan.setOdontologo(Odontologo.builder().id(UUID.randomUUID()).cop("COP-2")
                .nombres("Ana").apellidos("Quispe").activo(true).build());
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);

        // La sesión 2 está pendiente, así que el 409 sería lo siguiente que saldría.
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> servicio.cerrarSesion(plan.getId(), 2, peticion));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void cerrarSesion_comoAdministrador_puedeConElPlanDeCualquiera() {
        autenticar(UUID.randomUUID(), "SCOPE_ADMINISTRADOR");
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);
        catalogoEnOrden();

        assertEquals(PlanSesion.ESTADO_CERRADA,
                servicio.cerrarSesion(plan.getId(), 1, peticion).getSesiones().get(0).getEstado());
    }

    @Test
    void cerrarSesion_conElPlanSuspendido_igualmenteSeCierra() {
        // Las recomendaciones son los cuidados de una consulta que ya ocurrió, y
        // suspender el plan no deshace lo que se hizo en ella.
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        plan.suspender("Se replantea el tratamiento");
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);
        catalogoEnOrden();

        assertEquals(PlanSesion.ESTADO_CERRADA,
                servicio.cerrarSesion(plan.getId(), 1, peticion).getSesiones().get(0).getEstado());
    }

    @Test
    void cerrarSesion_conUnNumeroQueElPlanNoTiene_lanzaResourceNotFound() {
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);

        assertThrows(ResourceNotFoundException.class,
                () -> servicio.cerrarSesion(plan.getId(), 99, peticion));
    }

    @Test
    void cerrarSesion_deUnPlanInexistente_lanzaResourceNotFound() {
        UUID inventado = UUID.randomUUID();
        when(planRepository.findParaCerrarSesion(inventado)).thenReturn(Optional.empty());
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);

        assertThrows(ResourceNotFoundException.class,
                () -> servicio.cerrarSesion(inventado, 1, peticion));
    }

    @Test
    void cerrarSesion_tomaElPlanConLaFilaBloqueada() {
        // Dos cierres simultáneos de la misma sesión se serializan con ese bloqueo;
        // sin él, el segundo sustituiría en silencio lo que indicó el primero.
        todoEnOrden();
        Plan plan = planConLaPrimeraAtendida();
        CierreSesionRequestDTO peticion = cierre(LocalDate.now(ZONA).plusDays(7), null);
        catalogoEnOrden();

        servicio.cerrarSesion(plan.getId(), 1, peticion);

        verify(planRepository).findParaCerrarSesion(plan.getId());
        verify(planRepository, never()).findConDetalleById(plan.getId());
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

    @Test
    void deFicha_devuelveElAvanceDeCadaPlan() {
        Plan plan = planEnCurso();
        plan.getSesiones().get(0).enlazar(Cita.builder().id(UUID.randomUUID())
                .codigo("CIT-000001").estado(Cita.ESTADO_ATENDIDA).build());
        when(planRepository.findByFichaIdOrderByCreadoEnDesc(fichaPaciente.getId()))
                .thenReturn(List.of(plan));

        PlanResponseDTO.Avance avance = servicio.deFicha(fichaPaciente.getId()).get(0).getAvance();

        assertEquals(1, avance.getCompletadas());
        assertEquals(4, avance.getPendientes());
    }

    // ------------------------------------------------------------------
    // El detalle del plan, con su avance
    // ------------------------------------------------------------------

    /** Un plan de seis sesiones con dos citas atendidas: la segunda, sin cerrar todavía. */
    private Plan planDeSeisConDosAtendidas() {
        Plan plan = Plan.builder()
                .id(UUID.randomUUID())
                .ficha(fichaPaciente).tratamiento(tratamiento).odontologo(luis)
                .sesionesPrevistas(6).activo(true)
                .build();
        plan.generarSesiones();
        plan.getSesiones().get(0).enlazar(Cita.builder().id(UUID.randomUUID()).codigo("CIT-000001")
                .inicio(OffsetDateTime.parse("2026-08-01T09:00:00-05:00"))
                .estado(Cita.ESTADO_ATENDIDA).build());
        plan.getSesiones().get(0).cerrar(Set.of(Recomendacion.builder().id(UUID.randomUUID())
                .descripcion("Mantener dieta blanda").activa(true).build()),
                LocalDate.of(2026, 8, 15), null);
        plan.getSesiones().get(1).enlazar(Cita.builder().id(UUID.randomUUID()).codigo("CIT-000002")
                .inicio(OffsetDateTime.parse("2026-08-20T09:00:00-05:00"))
                .estado(Cita.ESTADO_ATENDIDA).build());
        return plan;
    }

    @Test
    void obtener_deSeisConDosAtendidas_devuelveDosCompletadasYCuatroPendientes() {
        autenticar(UUID.randomUUID(), "SCOPE_PACIENTE");
        Plan plan = planDeSeisConDosAtendidas();
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        PlanResponseDTO respuesta = servicio.obtener(plan.getId());

        assertEquals(2, respuesta.getAvance().getCompletadas());
        assertEquals(4, respuesta.getAvance().getPendientes());
        assertEquals("CERRADA", respuesta.getSesiones().get(0).getEstado());
        // La atendida sin cerrar es la que la linea de tiempo señala.
        assertEquals("ATENDIDA", respuesta.getSesiones().get(1).getEstado());
        verify(accessGuard).verificarLectura(fichaPaciente.getId());
    }

    @Test
    void obtener_elPlanDeOtroPaciente_propagaElForbiddenDelGuard() {
        autenticar(UUID.randomUUID(), "SCOPE_PACIENTE");
        Plan plan = planDeSeisConDosAtendidas();
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo la propia"))
                .when(accessGuard).verificarLectura(fichaPaciente.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.obtener(plan.getId()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void obtener_unPlanInexistenteComoPaciente_respondeForbiddenYNoNotFound() {
        autenticar(UUID.randomUUID(), "SCOPE_PACIENTE");
        UUID inventado = UUID.randomUUID();
        when(planRepository.findConDetalleById(inventado)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.obtener(inventado));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void obtener_unPlanInexistenteComoOdontologo_respondeForbidden() {
        UUID inventado = UUID.randomUUID();
        when(planRepository.findConDetalleById(inventado)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.obtener(inventado));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void obtener_unPlanInexistenteComoAdministrador_lanzaResourceNotFound() {
        autenticar(UUID.randomUUID(), "SCOPE_ADMINISTRADOR");
        UUID inventado = UUID.randomUUID();
        when(planRepository.findConDetalleById(inventado)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.obtener(inventado));
    }

    @Test
    void obtener_elOdontologoQueLoFirmo_loLeeSinPreguntarPorElVinculo() {
        // Esta a su cargo aunque la cita que le dio el vinculo se cancelara.
        todoEnOrden();
        Plan plan = planDeSeisConDosAtendidas();
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        assertEquals(plan.getId(), servicio.obtener(plan.getId()).getId());
        verify(accessGuard, never()).verificarLectura(any());
    }

    @Test
    void obtener_unOdontologoQueNoLoFirmo_dependeDelVinculoConElPaciente() {
        todoEnOrden();
        Plan plan = planDeSeisConDosAtendidas();
        plan.setOdontologo(Odontologo.builder().id(UUID.randomUUID()).cop("COP-2")
                .nombres("Ana").apellidos("Quispe").activo(true).build());
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No ha atendido a este paciente"))
                .when(accessGuard).verificarLectura(fichaPaciente.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.obtener(plan.getId()));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void obtener_ajenoEInexistente_respondenConElMismoMensaje() {
        todoEnOrden();
        Plan plan = planDeSeisConDosAtendidas();
        plan.setOdontologo(Odontologo.builder().id(UUID.randomUUID()).cop("COP-2")
                .nombres("Ana").apellidos("Quispe").activo(true).build());
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));
        UUID inventado = UUID.randomUUID();
        when(planRepository.findConDetalleById(inventado)).thenReturn(Optional.empty());
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "No ha atendido a este paciente"))
                .when(accessGuard).verificarLectura(fichaPaciente.getId());

        ResponseStatusException ajeno = assertThrows(ResponseStatusException.class,
                () -> servicio.obtener(plan.getId()));
        ResponseStatusException inexistente = assertThrows(ResponseStatusException.class,
                () -> servicio.obtener(inventado));

        // El texto del guard habla de la ficha; si pasara tal cual, el mensaje
        // diría cuál de los dos casos era.
        assertEquals(inexistente.getReason(), ajeno.getReason());
    }

    @Test
    void obtener_unRechazoDelGuardQueNoEsForbidden_seDejaPasarTalCual() {
        todoEnOrden();
        Plan plan = planDeSeisConDosAtendidas();
        plan.setOdontologo(Odontologo.builder().id(UUID.randomUUID()).cop("COP-2")
                .nombres("Ana").apellidos("Quispe").activo(true).build());
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"))
                .when(accessGuard).verificarLectura(fichaPaciente.getId());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.obtener(plan.getId()));

        assertEquals(HttpStatus.UNAUTHORIZED, e.getStatusCode());
    }

    @Test
    void obtener_comoAdministrador_pasaPorElGuardQueLeDejaVerlo() {
        autenticar(UUID.randomUUID(), "SCOPE_ADMINISTRADOR");
        Plan plan = planDeSeisConDosAtendidas();
        when(planRepository.findConDetalleById(plan.getId())).thenReturn(Optional.of(plan));

        assertEquals(2, servicio.obtener(plan.getId()).getAvance().getCompletadas());
    }

    // ------------------------------------------------------------------
    // El seguimiento del odontólogo
    // ------------------------------------------------------------------

    @Test
    void seguimiento_respetaElOrdenDeLosIdentificadoresYNoElDeLaCarga() {
        todoEnOrden();
        Plan primero = planDeSeisConDosAtendidas();
        Plan segundo = planEnCurso();
        Pageable pagina = PageRequest.of(0, 20, Sort.by("inexistente"));
        when(planRepository.idsDeSeguimiento(luis.getId(), true, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(primero.getId(), segundo.getId()),
                        PageRequest.of(0, 20), 2));
        when(planRepository.findConDetalleByIdIn(List.of(primero.getId(), segundo.getId())))
                .thenReturn(List.of(segundo, primero));

        Page<PlanResponseDTO> respuesta = servicio.seguimiento(true, pagina);

        assertEquals(List.of(primero.getId(), segundo.getId()),
                respuesta.getContent().stream().map(PlanResponseDTO::getId).toList());
        assertEquals(2, respuesta.getContent().get(0).getAvance().getCompletadas());
        assertEquals(2, respuesta.getTotalElements());
    }

    @Test
    void seguimiento_sinPlanes_noCargaNingunDetalle() {
        todoEnOrden();
        when(planRepository.idsDeSeguimiento(luis.getId(), false, PageRequest.of(0, 20)))
                .thenReturn(Page.empty(PageRequest.of(0, 20)));

        assertTrue(servicio.seguimiento(false, PageRequest.of(0, 20)).isEmpty());
        verify(planRepository, never()).findConDetalleByIdIn(any());
    }

    @Test
    void seguimiento_comoPaciente_lanzaForbidden() {
        autenticar(UUID.randomUUID(), "SCOPE_PACIENTE");

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> servicio.seguimiento(true, PageRequest.of(0, 20)));

        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }
}
