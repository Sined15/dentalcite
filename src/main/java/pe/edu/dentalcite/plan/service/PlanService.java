package pe.edu.dentalcite.plan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Planes de tratamiento (HU-17 · RF-23 · RN-12, RN-13 · RNF-04).
 *
 * <h2>Cómo se garantiza RN-13</h2>
 *
 * «Un paciente no puede tener dos planes activos del mismo tratamiento». El
 * criterio pide expresamente que el 409 esté «garantizado por un índice único
 * parcial», y por eso aquí <strong>no se comprueba antes de insertar</strong>:
 * entre mirar si existe y crear el segundo cabe otra creación, exactamente igual
 * que entre consultar la disponibilidad y reservar (HU-10). Se inserta, y si el
 * índice de {@code V19} rechaza la fila, {@link ConflictoDePlanActivo} traduce el
 * choque a un 409 que dice qué hacer.
 *
 * <p>El {@code saveAndFlush} tampoco es decorativo: sin el flush explícito el
 * índice saltaría al hacer commit, fuera del {@code try}, y el error saldría como
 * un 500 en vez del 409 del criterio. Es la misma lección de
 * {@code RegistroDeCita}.
 *
 * <h2>Quién ve y planifica sobre quién</h2>
 *
 * RNF-04 lo resuelve {@code SecurityConfig} por ruta —planificar es del
 * odontólogo y del administrador— y aquí solo queda el «(el suyo)», que no es
 * expresable como patrón: quién puede suspender un plan lo decide su autoría, y
 * sobre qué pacientes se puede planificar y leer lo decide RNF-06, delegado en
 * {@link PacienteAccessGuard}. Son dos preguntas distintas y se contestan en
 * sitios distintos a propósito: la primera mira el plan, la segunda la historia
 * clínica.
 *
 * <h2>Suspender no borra</h2>
 *
 * RN-12: «ningún registro de citas, pacientes o planes se elimina físicamente».
 * Suspender baja la marca de actividad y deja el motivo, y con eso el tratamiento
 * vuelve a poder planificarse <em>sin ningún trabajo extra</em>: el índice es
 * parcial sobre {@code activo}, así que la fila suspendida deja de ocupar el
 * hueco por sí sola.
 */
@Slf4j
@Service
public class PlanService {

    private static final String ODONTOLOGO = "SCOPE_ODONTOLOGO";
    private static final String ADMINISTRADOR = "SCOPE_ADMINISTRADOR";

    private final PlanRepository planRepository;
    private final FichaRepository fichaRepository;
    private final TratamientoRepository tratamientoRepository;
    private final OdontologoRepository odontologoRepository;
    private final UsuarioRepository usuarioRepository;

    /**
     * Quién puede mirar la historia clínica de quién (RNF-06). Se reutiliza el de
     * HU-13 en vez de escribir aquí otra versión: un plan es un dato de la ficha,
     * y dos respuestas distintas a «este profesional puede ver a esta persona»
     * serían dos reglas que se desincronizan. Es el mismo préstamo entre dominios
     * que {@code OdontologoOwnershipGuard} hace con horarios y bloqueos.
     */
    private final PacienteAccessGuard accessGuard;

    /** Las citas que el paciente ya tenía atendidas ocupan sesiones desde el primer día. */
    private final EnlaceDeSesiones enlaceDeSesiones;

    /** El catálogo cerrado del que salen los cuidados que se indican al cerrar una sesión. */
    private final RecomendacionService recomendacionService;

    /**
     * La zona de la clínica. La necesita el cierre de sesión para saber qué día es
     * hoy: la fecha del próximo control se compara con el calendario de quien la
     * escribe, no con el del servidor.
     */
    private final ZoneId zona;

    public PlanService(PlanRepository planRepository, FichaRepository fichaRepository,
            TratamientoRepository tratamientoRepository, OdontologoRepository odontologoRepository,
            UsuarioRepository usuarioRepository, PacienteAccessGuard accessGuard,
            EnlaceDeSesiones enlaceDeSesiones, RecomendacionService recomendacionService,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.planRepository = planRepository;
        this.fichaRepository = fichaRepository;
        this.tratamientoRepository = tratamientoRepository;
        this.odontologoRepository = odontologoRepository;
        this.usuarioRepository = usuarioRepository;
        this.accessGuard = accessGuard;
        this.enlaceDeSesiones = enlaceDeSesiones;
        this.recomendacionService = recomendacionService;
        this.zona = ZoneId.of(zonaHoraria);
    }

    /**
     * Criterio 1: «quedará ACTIVO con sus sesiones numeradas y todas pendientes».
     */
    @Transactional
    public PlanResponseDTO crear(PlanRequestDTO peticion) {
        Odontologo odontologo = odontologoQuePlanifica(peticion.getOdontologoId());

        Ficha ficha = fichaRepository.findById(peticion.getPacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado"));

        // RNF-06, y va justo aquí: después del 404 de la ficha inexistente y antes
        // de tocar nada más. Negar el acceso a una ficha que no existe mandaría a
        // buscar un permiso en vez de una errata —la misma razón que ya está
        // escrita en PacienteService.obtener—, y comprobarlo después de resolver
        // el tratamiento gastaría una consulta para acabar en el mismo 403.
        // «El odontólogo crea los planes de *sus* pacientes»: planificar es
        // escribir una indicación clínica sobre una historia, así que exige el
        // mismo vínculo que abrir esa historia.
        accessGuard.verificarLectura(ficha.getId());

        Tratamiento tratamiento = tratamientoRepository.findById(peticion.getTratamientoId())
                .filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado"));

        Plan plan = Plan.builder()
                .ficha(ficha)
                .tratamiento(tratamiento)
                .odontologo(odontologo)
                .sesionesPrevistas(peticion.getSesionesPrevistas())
                .activo(true)
                .build();
        plan.generarSesiones();

        try {
            // `saveAndFlush` y no `save`: ver el javadoc de la clase.
            plan = planRepository.saveAndFlush(plan);
        } catch (DataIntegrityViolationException e) {
            if (ConflictoDePlanActivo.esPlanActivoDuplicado(e)) {
                throw new IllegalStateException("El paciente ya tiene un plan activo de "
                        + tratamiento.getNombre() + " (RN-13). Suspenda el que está en curso"
                        + " antes de planificar otro.");
            }
            throw e;
        }

        // Después del flush y no antes: si el índice rechaza el plan, no hay nada
        // que enlazar. Las sesiones que ocupe se guardan al confirmar la
        // transacción, con el plan ya en la base.
        enlaceDeSesiones.enlazarRetroactivas(plan, OffsetDateTime.now());

        log.info("Plan {} creado con {} sesiones", plan.getId(), plan.getSesionesPrevistas());
        return mapear(plan);
    }

    /**
     * Criterio 3: «dejará de estar activo, su registro se conservará y ese
     * tratamiento podrá volver a planificarse».
     */
    @Transactional
    public PlanResponseDTO suspender(UUID planId, String motivo) {
        Plan plan = planRepository.findConDetalleById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));

        verificarQuePuedeOperarSobre(plan.getOdontologo());

        if (!Boolean.TRUE.equals(plan.getActivo())) {
            // Suspender dos veces no es idempotente: silenciarlo ocultaría que
            // otro ya lo hizo, y el motivo que quedó registrado no es este.
            throw new IllegalStateException("El plan ya está suspendido y no puede suspenderse"
                    + " de nuevo (RN-12).");
        }

        plan.suspender(motivo);
        planRepository.save(plan);

        log.info("Plan {} suspendido", plan.getId());
        return mapear(plan);
    }

    /**
     * Registra lo que el paciente debe hacer hasta la siguiente sesión y la da por
     * cerrada.
     *
     * <h2>El orden de las comprobaciones</h2>
     *
     * 404 del plan o de la sesión → 403 de la autoría → 409 del estado → 400 de lo
     * que trae el cuerpo. El 403 va antes del estado por lo mismo que en el cierre
     * de la cita: decir «esa sesión ya está cerrada» sobre el plan de otro
     * profesional sería contar de rebote en qué punto va un tratamiento ajeno. Y lo
     * que trae el cuerpo se mira al final porque comprobarlo antes gastaría una
     * consulta al catálogo para acabar en el mismo 403, y respondería que las
     * recomendaciones son inválidas cuando el problema es que esa sesión no se
     * puede cerrar.
     *
     * <p>El plan se carga con su fila bloqueada: dos cierres simultáneos de la misma
     * sesión se serializan, y el segundo la encuentra ya cerrada en vez de sustituir
     * en silencio las recomendaciones del primero.
     *
     * <p>Un plan suspendido sí admite cerrar una sesión atendida: las recomendaciones
     * son los cuidados de una consulta que ya ocurrió, y suspender el plan no deshace
     * lo que se hizo en ella.
     */
    @Transactional
    public PlanResponseDTO cerrarSesion(UUID planId, int numero, CierreSesionRequestDTO peticion) {
        Plan plan = planRepository.findParaCerrarSesion(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan no encontrado"));

        PlanSesion sesion = plan.getSesiones().stream()
                .filter(s -> Integer.valueOf(numero).equals(s.getNumero()))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "El plan no tiene una sesion " + numero));

        verificarQuePuedeOperarSobre(plan.getOdontologo());
        sesion.verificarQueSePuedeCerrar();

        LocalDate hoy = LocalDate.now(zona);
        if (peticion.getProximoControl().isBefore(hoy)) {
            throw new IllegalArgumentException(
                    "La fecha sugerida del proximo control no puede ser anterior a hoy.");
        }

        Set<Recomendacion> indicadas = recomendacionService
                .resolverVigentes(peticion.getRecomendacionIds());

        sesion.cerrar(indicadas, peticion.getProximoControl(), sinEspacios(peticion.getObservacion()));
        planRepository.save(plan);

        log.info("Sesion {} del plan {} cerrada con {} recomendaciones",
                numero, plan.getId(), indicadas.size());
        return mapear(plan);
    }

    /**
     * Una observación en blanco es no haber escrito ninguna, y así se guarda: dejar
     * la cadena vacía haría que el cliente pintara un apartado sin nada dentro.
     */
    private static String sinEspacios(String observacion) {
        if (observacion == null || observacion.isBlank()) {
            return null;
        }
        return observacion.trim();
    }

    /**
     * Los planes de un paciente, para pintarlos en su ficha (HU-13 · RF-08).
     *
     * <p><strong>La autorización se resuelve aquí</strong>, con
     * {@link PacienteAccessGuard#verificarLectura(UUID)}: un plan lleva el nombre
     * del paciente, así que este listado es una lectura de la historia clínica y
     * cae bajo RNF-06 exactamente igual que la ficha. Sin esta línea, cualquier
     * odontólogo podía enumerar los planes de personas a las que nunca atendió,
     * que es justo el 403 que HU-13 devuelve en esa misma situación.
     *
     * <p>Efecto de borde deliberado: para un ODONTOLOGO, un {@code fichaId} que no
     * existe da 403 y no una lista vacía, porque el guard pregunta por el vínculo
     * y no por la existencia. Es lo preferible —una lista vacía frente a un 403
     * diría qué fichas existen—, y es la misma decisión que toma la cancelación
     * del paciente en {@code CancelacionService}.
     */
    @Transactional(readOnly = true)
    public List<PlanResponseDTO> deFicha(UUID fichaId) {
        accessGuard.verificarLectura(fichaId);
        return planRepository.findByFichaIdOrderByCreadoEnDesc(fichaId).stream()
                .map(PlanService::mapear)
                .toList();
    }

    /**
     * Un plan con su avance y todas sus sesiones, que es de donde el cliente saca
     * la línea de tiempo.
     *
     * <h2>El plan que no existe</h2>
     *
     * Solo el administrador recibe 404. El paciente y el odontólogo ven una parte
     * de la clínica, y para ellos un identificador inventado responde lo mismo que
     * el plan de otro, 403: si uno diera 404 y el otro 403, probando
     * identificadores se sabría cuáles existen. El administrador lo ve todo, así
     * que a él el 404 no le cuenta nada que no pudiera consultar, y le ahorra
     * buscar un permiso donde hay una errata.
     *
     * <h2>Quién lo lee</h2>
     *
     * El odontólogo que lo firmó lo lee siempre, aunque la cita que le dio el
     * vínculo con el paciente se cancelara después: es un plan que está a su
     * cargo. Para todos los demás decide {@link PacienteAccessGuard}, con la misma
     * regla que abre la ficha: el paciente, la suya; el odontólogo, la de quien
     * tiene una cita con él sin cancelar.
     */
    @Transactional(readOnly = true)
    public PlanResponseDTO obtener(UUID planId) {
        Authentication auth = autenticado();
        Plan plan = planRepository.findConDetalleById(planId).orElse(null);

        if (plan == null) {
            if (tieneAutoridad(auth, ADMINISTRADOR)) {
                throw new ResourceNotFoundException("Plan no encontrado");
            }
            throw planVedado();
        }

        if (!loFirmoQuienPregunta(auth, plan)) {
            try {
                accessGuard.verificarLectura(plan.getFicha().getId());
            } catch (ResponseStatusException rechazo) {
                // El guard explica el rechazo con palabras de la ficha, y el plan
                // inexistente responde con las del plan: dos textos para el mismo
                // 403 dirían cuál de los dos casos era. Se responde siempre igual.
                if (rechazo.getStatusCode().value() == HttpStatus.FORBIDDEN.value()) {
                    throw planVedado();
                }
                throw rechazo;
            }
        }
        return mapear(plan);
    }

    private static ResponseStatusException planVedado() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "No puede consultar este plan de tratamiento.");
    }

    /**
     * Los planes que sigue el odontólogo autenticado: los que firmó y los de los
     * pacientes que tienen alguna cita suya sin cancelar. Quién entra en esa lista
     * y por qué está escrito en {@link PlanRepository#idsDeSeguimiento}.
     *
     * <p>El orden lo fija la consulta, así que el del {@code Pageable} se descarta:
     * un {@code sort} inventado no puede convertir la petición en un 500.
     */
    @Transactional(readOnly = true)
    public Page<PlanResponseDTO> seguimiento(boolean soloActivos, Pageable pageable) {
        Odontologo yo = registroDelOdontologoAutenticado(autenticado());
        Pageable sinOrden = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());

        Page<UUID> ids = planRepository.idsDeSeguimiento(yo.getId(), soloActivos, sinOrden);
        if (ids.isEmpty()) {
            return ids.map(id -> null);
        }

        Map<UUID, Plan> porId = planRepository.findConDetalleByIdIn(ids.getContent()).stream()
                .collect(Collectors.toMap(Plan::getId, Function.identity()));
        return ids.map(id -> mapear(porId.get(id)));
    }

    /**
     * Si quien pregunta es el odontólogo que firmó el plan. Solo resuelve el
     * registro cuando el rol es ODONTOLOGO: al paciente y al administrador no hay
     * nada que buscarles.
     */
    private boolean loFirmoQuienPregunta(Authentication auth, Plan plan) {
        if (!tieneAutoridad(auth, ODONTOLOGO)) {
            return false;
        }
        return registroDelOdontologoAutenticado(auth).getId().equals(plan.getOdontologo().getId());
    }

    // ------------------------------------------------------------------
    // Autorización · RNF-04
    // ------------------------------------------------------------------

    /**
     * Quién planifica, con la misma forma que {@code AutorDeLaReserva} en HU-14:
     *
     * <table>
     *   <caption>Quién puede planificar en nombre de quién</caption>
     *   <tr><th>Rol</th><th>{@code odontologoId}</th><th>Resultado</th></tr>
     *   <tr><td>ODONTOLOGO</td><td>ausente</td><td>su propio registro</td></tr>
     *   <tr><td>ODONTOLOGO</td><td>presente</td><td>403</td></tr>
     *   <tr><td>ADMINISTRADOR</td><td>presente</td><td>ese odontólogo; 404 si no existe</td></tr>
     *   <tr><td>ADMINISTRADOR</td><td>ausente</td><td>400</td></tr>
     * </table>
     *
     * <p>Al odontólogo se le rechaza el campo aunque coincida con el suyo, por lo
     * mismo que allí: aceptarlo cuando coincide convertiría la respuesta en un
     * oráculo para averiguar identificadores ajenos. PACIENTE y RECEPCIONISTA no
     * llegan aquí —{@code SecurityConfig} los corta por ruta, que es el criterio
     * 4—, y si llegaran tampoco pasarían: no tienen registro de odontólogo.
     */
    private Odontologo odontologoQuePlanifica(UUID odontologoId) {
        Authentication auth = autenticado();

        if (tieneAutoridad(auth, ADMINISTRADOR)) {
            if (odontologoId == null) {
                // No es 403: el rol sí puede crear planes, lo que falta es decir a
                // nombre de qué profesional. Su cuenta no tiene registro propio.
                throw new IllegalArgumentException(
                        "Indique el odontólogo que planifica (odontologoId).");
            }
            return odontologoRepository.findById(odontologoId)
                    .filter(o -> Boolean.TRUE.equals(o.getActivo()))
                    .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado"));
        }

        if (odontologoId != null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo puede planificar en su propio nombre (RNF-04).");
        }
        return registroDelOdontologoAutenticado(auth);
    }

    /**
     * Suspender es del administrador y del odontólogo <strong>dueño del
     * plan</strong>.
     *
     * <p>El criterio 4 solo habla del 403 al crear, así que esta es la lectura
     * conservadora: un plan es una indicación clínica firmada por alguien, y
     * RNF-04 exige que ningún recurso «exponga datos de un tercero» ni responda a
     * quien no le corresponde. Si más adelante se quiere que cualquier odontólogo
     * pueda suspender el plan de otro, es una decisión de producto que hay que
     * escribir, no un descuido que haya que aprovechar.
     */
    private void verificarQuePuedeOperarSobre(Odontologo duenoDelPlan) {
        Authentication auth = autenticado();
        if (tieneAutoridad(auth, ADMINISTRADOR)) {
            return;
        }
        Odontologo suyo = registroDelOdontologoAutenticado(auth);
        if (!suyo.getId().equals(duenoDelPlan.getId())) {
            // El mensaje no dice quién firmó el plan: la autoría es un dato del
            // recurso, y la respuesta de rechazo no debe llevar ninguno.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "No puede modificar este plan de tratamiento.");
        }
    }

    /**
     * El registro de odontólogo de quien llama. Se llega por la ficha, que es lo
     * que RN-11 comparte entre la cuenta y el registro; una cuenta sin ficha no
     * resuelve a ningún profesional.
     */
    private Odontologo registroDelOdontologoAutenticado(Authentication auth) {
        if (!tieneAutoridad(auth, ODONTOLOGO)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Su rol no puede planificar tratamientos (RNF-04).");
        }
        UUID usuarioId;
        try {
            // El subject del JWT es el UUID del usuario (JwtService.generateToken).
            usuarioId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado"));
        if (usuario.getFicha() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta no está vinculada a un registro de odontólogo.");
        }
        return odontologoRepository.findByFichaId(usuario.getFicha().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "La cuenta no está vinculada a un registro de odontólogo."));
    }

    private static Authentication autenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            // Falla cerrado: sin credenciales no se acredita ningún registro.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        return auth;
    }

    private static boolean tieneAutoridad(Authentication auth, String autoridad) {
        return auth.getAuthorities().stream().anyMatch(a -> autoridad.equals(a.getAuthority()));
    }

    // ------------------------------------------------------------------
    // Mapeo
    // ------------------------------------------------------------------

    private static PlanResponseDTO mapear(Plan plan) {
        return PlanResponseDTO.builder()
                .id(plan.getId())
                .paciente(referencia(plan.getFicha().getId(), nombreDe(plan.getFicha())))
                .tratamiento(referencia(plan.getTratamiento().getId(), plan.getTratamiento().getNombre()))
                .odontologo(referencia(plan.getOdontologo().getId(),
                        (plan.getOdontologo().getNombres() + " " + plan.getOdontologo().getApellidos()).trim()))
                .sesionesPrevistas(plan.getSesionesPrevistas())
                .activo(plan.getActivo())
                .motivoSuspension(plan.getMotivoSuspension())
                .creadoEn(plan.getCreadoEn())
                .avance(PlanResponseDTO.Avance.builder()
                        .completadas(plan.sesionesCompletadas())
                        .pendientes(plan.sesionesPendientes())
                        .build())
                .sesiones(plan.getSesiones().stream()
                        .map(PlanService::sesion)
                        .toList())
                .build();
    }

    /**
     * La ficha puede no tener nombre: RF-06 admite darla de alta con el documento
     * mientras se completan los datos. En ese caso identifica el número de
     * historia, que sí es obligatorio. Es la misma regla que usa la agenda.
     */
    private static String nombreDe(Ficha ficha) {
        String nombre = ((ficha.getNombres() == null ? "" : ficha.getNombres()) + " "
                + (ficha.getApellidos() == null ? "" : ficha.getApellidos())).trim();
        return nombre.isEmpty() ? ficha.getNumeroHistoria() : nombre;
    }

    private static PlanResponseDTO.Sesion sesion(PlanSesion s) {
        return PlanResponseDTO.Sesion.builder()
                .id(s.getId()).numero(s.getNumero()).estado(s.getEstado())
                .cita(s.getCita() == null ? null : PlanResponseDTO.CitaEnlazada.builder()
                        .id(s.getCita().getId())
                        .codigo(s.getCita().getCodigo())
                        .inicio(s.getCita().getInicio())
                        .build())
                .recomendaciones(s.getRecomendaciones().stream()
                        .map(RecomendacionService::mapear)
                        .toList())
                .proximoControl(s.getProximoControl())
                .observacion(s.getObservacion())
                .build();
    }

    private static PlanResponseDTO.Referencia referencia(UUID id, String nombre) {
        return PlanResponseDTO.Referencia.builder().id(id).nombre(nombre).build();
    }
}
