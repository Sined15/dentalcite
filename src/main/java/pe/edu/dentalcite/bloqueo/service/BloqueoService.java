package pe.edu.dentalcite.bloqueo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoResponseDTO;
import pe.edu.dentalcite.bloqueo.api.dto.CitaAfectadaDTO;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.service.OdontologoOwnershipGuard;
import pe.edu.dentalcite.bloqueo.repository.BloqueoRepository;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BloqueoService {

    private final BloqueoRepository bloqueoRepository;
    private final OdontologoRepository odontologoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final CitaRepository citaRepository;

    private static final Set<String> ROLES_ADMIN_O_RECEPCION = Set.of("SCOPE_ADMINISTRADOR", "SCOPE_RECEPCIONISTA");

    private void verificarPropiedadOdontologoONivel(Odontologo odontologo) {
        OdontologoOwnershipGuard.verificar(usuarioRepository, odontologo, ROLES_ADMIN_O_RECEPCION, true,
                "No tiene permisos para bloquear consultorios");
    }

    private static boolean esPrivilegiado(Authentication auth) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> ROLES_ADMIN_O_RECEPCION.contains(a.getAuthority()));
    }

    /**
     * Un bloqueo de consultorio deja fuera de servicio un recurso de toda la
     * clínica, así que la marca (p) que la Tabla 10 concede al odontólogo —«los
     * datos propios del usuario»— no lo alcanza: solo recepción y administración
     * pueden tocarlo.
     *
     * <p>La comprobación es independiente de la de propiedad y no puede fundirse
     * con ella. {@link OdontologoOwnershipGuard} solo negaba el consultorio cuando
     * el bloqueo <em>no</em> llevaba odontólogo; bastaba con enviar además el
     * identificador propio para que la verificación saliera por la rama de
     * propiedad y nadie mirase el consultorio, de modo que cualquier odontólogo
     * podía dejar un consultorio inoperativo para la clínica entera.
     */
    private void verificarPuedeBloquearConsultorio(Consultorio consultorio) {
        if (consultorio == null) {
            return;
        }
        if (!esPrivilegiado(SecurityContextHolder.getContext().getAuthentication())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene permisos para bloquear consultorios");
        }
    }

    /**
     * RNF-04: un odontólogo solo ve los bloqueos que le incumben —los suyos y los
     * de consultorio, que afectan a toda la clínica—, no la agenda de sus colegas.
     * Recepción y administración sí ven el conjunto completo (Tabla 10).
     */
    @Transactional(readOnly = true)
    public List<BloqueoResponseDTO> listarBloqueos() {
        List<Bloqueo> todos = bloqueoRepository.findAll();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (esPrivilegiado(auth)) {
            return todos.stream().map(BloqueoService::mapBloqueo).toList();
        }

        UUID fichaPropia = fichaDelSolicitante(auth);
        return todos.stream()
                .filter(b -> b.getOdontologo() == null
                        || (fichaPropia != null && fichaPropia.equals(b.getOdontologo().getFicha().getId())))
                .map(BloqueoService::mapBloqueo)
                .toList();
    }

    private UUID fichaDelSolicitante(Authentication auth) {
        if (auth == null) {
            return null;
        }
        try {
            return usuarioRepository.findById(UUID.fromString(auth.getName()))
                    .map(u -> u.getFicha() == null ? null : u.getFicha().getId())
                    .orElse(null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * RN-03 / HU-07: un bloqueo que alcanza citas activas no se aplica; se listan
     * para que quien lo registra sepa qué debe cancelarse antes, con motivo.
     */
    private void verificarSinCitasActivas(Bloqueo bloqueo) {
        List<Cita> afectadas = new ArrayList<>();
        OffsetDateTime inicio = bloqueo.getFechaInicio();
        OffsetDateTime fin = bloqueo.getFechaFin();

        if (bloqueo.getOdontologo() != null) {
            afectadas.addAll(citaRepository.findActivasDeOdontologoEnRango(
                    bloqueo.getOdontologo().getId(), inicio, fin));
        }
        if (bloqueo.getConsultorio() != null) {
            afectadas.addAll(citaRepository.findActivasDeConsultorioEnRango(
                    bloqueo.getConsultorio().getId(), inicio, fin));
        }

        if (afectadas.isEmpty()) {
            return;
        }

        List<CitaAfectadaDTO> detalle = afectadas.stream()
                .distinct()
                .sorted(Comparator.comparing(Cita::getInicio))
                .map(c -> CitaAfectadaDTO.builder()
                        .id(c.getId())
                        .codigo(c.getCodigo())
                        .inicio(c.getInicio())
                        .fin(c.getFin())
                        .paciente(nombreCompleto(c))
                        .numeroHistoria(c.getFicha().getNumeroHistoria())
                        .build())
                .toList();

        throw new CitasActivasEnRangoException(detalle);
    }

    private String nombreCompleto(Cita cita) {
        return nombreCompleto(cita.getFicha().getNombres(), cita.getFicha().getApellidos());
    }

    private static String nombreCompleto(String nombres, String apellidos) {
        return ((nombres == null ? "" : nombres) + " " + (apellidos == null ? "" : apellidos)).trim();
    }

    /**
     * El mapeo corre dentro de la transacción del servicio, así que resolver el
     * nombre del odontólogo y del consultorio inicializa sus proxies aquí y no
     * durante la serialización, cuando la sesión ya está cerrada
     * ({@code open-in-view: false}). {@code listarBloqueos} evita el N+1 con el
     * grafo declarado en {@code BloqueoRepository.findAll()}.
     */
    private static BloqueoResponseDTO mapBloqueo(Bloqueo entity) {
        Odontologo odontologo = entity.getOdontologo();
        Consultorio consultorio = entity.getConsultorio();
        return BloqueoResponseDTO.builder()
                .id(entity.getId())
                .odontologoId(odontologo == null ? null : odontologo.getId())
                .odontologo(odontologo == null ? null
                        : nombreCompleto(odontologo.getNombres(), odontologo.getApellidos()))
                .consultorioId(consultorio == null ? null : consultorio.getId())
                .consultorio(consultorio == null ? null : consultorio.getNombre())
                .motivo(entity.getMotivo())
                .fechaInicio(entity.getFechaInicio())
                .fechaFin(entity.getFechaFin())
                .build();
    }

    @Transactional
    public BloqueoResponseDTO crearBloqueo(Bloqueo bloqueo, UUID odontologoId, UUID consultorioId) {
        if (!bloqueo.getFechaInicio().isBefore(bloqueo.getFechaFin())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }

        if (odontologoId == null && consultorioId == null) {
            throw new IllegalArgumentException("Debe especificar un odontólogo o un consultorio para el bloqueo");
        }

        if (odontologoId != null) {
            Odontologo odontologo = odontologoRepository.findById(odontologoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado"));
            bloqueo.setOdontologo(odontologo);
        }

        if (consultorioId != null) {
            Consultorio consultorio = consultorioRepository.findById(consultorioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Consultorio no encontrado"));
            bloqueo.setConsultorio(consultorio);
        }

        verificarPropiedadOdontologoONivel(bloqueo.getOdontologo());
        verificarPuedeBloquearConsultorio(bloqueo.getConsultorio());
        verificarSinCitasActivas(bloqueo);

        return mapBloqueo(bloqueoRepository.save(bloqueo));
    }

    @Transactional
    public void eliminarBloqueo(UUID id) {
        Bloqueo bloqueo = bloqueoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado"));
        verificarPropiedadOdontologoONivel(bloqueo.getOdontologo());
        // Levantar el bloqueo de un consultorio lo devuelve al servicio para toda la
        // clínica: es la misma decisión que aplicarlo, y pide el mismo permiso.
        verificarPuedeBloquearConsultorio(bloqueo.getConsultorio());
        bloqueoRepository.deleteById(id);
    }

    @Transactional
    public BloqueoResponseDTO actualizarBloqueo(UUID bloqueoId, Bloqueo detalles, UUID odontologoId,
            UUID consultorioId) {
        if (!detalles.getFechaInicio().isBefore(detalles.getFechaFin())) {
            throw new IllegalArgumentException("La fecha de inicio debe ser anterior a la fecha de fin");
        }

        Bloqueo bloqueo = bloqueoRepository.findById(bloqueoId)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueo no encontrado"));
        // Se comprueban los dos estados: hay que poder tocar el bloqueo tal como
        // está y también tal como quedará. Sin lo primero, un odontólogo podría
        // desactivar el bloqueo de un consultorio reescribiéndolo como suyo.
        verificarPropiedadOdontologoONivel(bloqueo.getOdontologo());
        verificarPuedeBloquearConsultorio(bloqueo.getConsultorio());

        if (odontologoId == null && consultorioId == null) {
            throw new IllegalArgumentException("Debe especificar un odontólogo o un consultorio para el bloqueo");
        }

        if (odontologoId != null) {
            Odontologo odontologo = odontologoRepository.findById(odontologoId)
                    .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado"));
            bloqueo.setOdontologo(odontologo);
        } else {
            bloqueo.setOdontologo(null);
        }

        if (consultorioId != null) {
            Consultorio consultorio = consultorioRepository.findById(consultorioId)
                    .orElseThrow(() -> new ResourceNotFoundException("Consultorio no encontrado"));
            bloqueo.setConsultorio(consultorio);
        } else {
            bloqueo.setConsultorio(null);
        }

        verificarPropiedadOdontologoONivel(bloqueo.getOdontologo());
        verificarPuedeBloquearConsultorio(bloqueo.getConsultorio());

        bloqueo.setMotivo(detalles.getMotivo());
        bloqueo.setFechaInicio(detalles.getFechaInicio());
        bloqueo.setFechaFin(detalles.getFechaFin());

        verificarSinCitasActivas(bloqueo);

        return mapBloqueo(bloqueoRepository.save(bloqueo));
    }
}
