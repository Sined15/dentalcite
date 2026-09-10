package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ReglaIncumplidaException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.disponibilidad.service.FranjasCache;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

/**
 * Cancelación de una cita, desde recepción (HU-11 · RF-20, RF-21) y por el
 * propio paciente (HU-15 · RF-19 · RN-06).
 *
 * <p><strong>Recepción no tiene ventana.</strong> Su criterio es explícito: «dada
 * cualquier cita activa, <em>incluso dentro de las veinticuatro horas previas a
 * su inicio</em>». La ventana de RN-06 limita solo al paciente cancelando lo
 * suyo; aplicarla a recepción rompería la razón de ser de esa operación, porque
 * la cancelación de última hora es precisamente la que el mostrador atiende.
 *
 * <p><strong>Al paciente se le responde 403 también cuando la cita no
 * existe.</strong> El criterio pide 403 «sin que la respuesta revele que la cita
 * existe», y un 404 para el identificador inventado frente a un 403 para el
 * ajeno distinguiría precisamente eso: probando identificadores se sabría cuáles
 * corresponden a citas reales. Para recepción, que puede verlas todas, el 404
 * sigue siendo lo correcto.
 *
 * <p>RN-12: nada se borra. La cita conserva su fila íntegra y su baja es el
 * estado CANCELADA, más una entrada en la bitácora con fecha, responsable y
 * motivo (RF-21).
 *
 * <p>Es {@code @Transactional} de escritura, al contrario que {@link CitaService}:
 * aquí no hay nada que reintentar y las tres escrituras —estado, motivo y
 * bitácora— tienen que caer juntas o no caer.
 */
@Slf4j
@Service
public class CancelacionService {

    private static final String ROL_PACIENTE = "SCOPE_PACIENTE";

    /**
     * El mismo mensaje para la cita de otro y para la que no existe. Que sean
     * indistinguibles es el requisito, no una simplificación: si difirieran,
     * probar identificadores diría cuáles corresponden a citas reales.
     */
    private static final String AJENA = "No puede cancelar esta cita (RNF-04).";

    private final CitaRepository citaRepository;
    private final CitaHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final FranjasCache franjasCache;
    private final VentanaDeCancelacion ventana;
    private final ZoneId zona;

    public CancelacionService(CitaRepository citaRepository,
            CitaHistorialRepository historialRepository,
            UsuarioRepository usuarioRepository,
            FranjasCache franjasCache,
            VentanaDeCancelacion ventana,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
        this.franjasCache = franjasCache;
        this.ventana = ventana;
        this.zona = ZoneId.of(zonaHoraria);
    }

    /**
     * El orden de las comprobaciones es contrato:
     *
     * <ol>
     *   <li>propiedad, que decide entre 403 y seguir;</li>
     *   <li>estado, que da el 409 de RN-09;</li>
     *   <li>ventana, que da el 422 de RN-06.</li>
     * </ol>
     *
     * Invertir las dos últimas haría que cancelar dos veces una cita fuera de
     * ventana saliera con el 422 de «contacte con recepción» en vez de con el 409
     * que dice lo que de verdad pasa, que es que ya está cancelada.
     */
    @Transactional
    public CitaResponseDTO cancelar(UUID citaId, String motivo) {
        Usuario responsable = responsable();
        boolean esPaciente = tieneAutoridad(ROL_PACIENTE);

        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> esPaciente
                        // Un 404 aquí frente al 403 de la cita ajena revelaría
                        // cuáles identificadores existen.
                        ? new ResponseStatusException(HttpStatus.FORBIDDEN, AJENA)
                        : new ResourceNotFoundException("Cita no encontrada"));

        if (esPaciente) {
            verificarQueEsSuya(cita, responsable);
        }

        // RN-09 es una máquina sin retorno: lo que ya salió de CONFIRMADA no
        // vuelve a entrar. Cancelar dos veces no es idempotente, es un error de
        // quien opera, y silenciarlo ocultaría que otro ya lo hizo.
        if (!Cita.ESTADO_CONFIRMADA.equals(cita.getEstado())) {
            throw new IllegalStateException("La cita " + cita.getCodigo() + " ya esta en estado "
                    + cita.getEstado() + " y no puede cancelarse (RN-09).");
        }

        // RN-06, solo para el paciente: recepción cancela sin ventana.
        if (esPaciente && !ventana.puedeCancelarElPaciente(cita, OffsetDateTime.now(zona))) {
            throw new ReglaIncumplidaException(ventana.motivoDelRechazo());
        }

        String estadoAnterior = cita.getEstado();
        cita.setEstado(Cita.ESTADO_CANCELADA);
        cita.setMotivoCancelacion(motivo);
        citaRepository.save(cita);

        historialRepository.save(CitaHistorial.builder()
                .cita(cita)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(Cita.ESTADO_CANCELADA)
                .motivo(motivo)
                .usuario(responsable)
                .build());

        // «La franja volverá a ofrecerse de inmediato» (RN-06). La base ya la
        // liberó —las restricciones de exclusión de V13 son parciales sobre
        // CONFIRMADA—, pero la caché seguiría sin ofrecerla hasta que caducase su
        // TTL, y «de inmediato» no admite un minuto de espera.
        franjasCache.invalidarTrasCommit();

        log.info("Cita {} cancelada", cita.getCodigo());
        return mapear(cita);
    }

    /**
     * HU-15: la cita tiene que ser suya. Se compara por ficha, que es lo que une
     * una cuenta con su historia clínica (RN-11).
     */
    private void verificarQueEsSuya(Cita cita, Usuario solicitante) {
        if (solicitante == null || solicitante.getFicha() == null
                || !solicitante.getFicha().getId().equals(cita.getFicha().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, AJENA);
        }
    }

    private static boolean tieneAutoridad(String autoridad) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().anyMatch(a -> autoridad.equals(a.getAuthority()));
    }

    /**
     * Quién cancela (RF-21). Devuelve {@code null} en vez de fallar si el token no
     * resuelve a un usuario: perder la bitácora entera por no poder nombrar al
     * responsable sería peor que registrarla sin él, y la autorización ya la
     * resolvió el filtro de seguridad antes de llegar aquí.
     */
    private Usuario responsable() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        try {
            return usuarioRepository.findById(UUID.fromString(auth.getName())).orElse(null);
        } catch (IllegalArgumentException e) {
            log.warn("El subject del token no es un UUID; la transicion se registra sin responsable");
            return null;
        }
    }

    private CitaResponseDTO mapear(Cita cita) {
        OffsetDateTime local = cita.getInicio().atZoneSameInstant(zona).toOffsetDateTime();
        return CitaResponseDTO.builder()
                .id(cita.getId())
                .codigo(cita.getCodigo())
                .fecha(local.toLocalDate())
                .hora(local.toLocalTime())
                .duracionMinutos((int) Duration.between(cita.getInicio(), cita.getFin()).toMinutes())
                .zonaHoraria(zona.getId())
                .estado(cita.getEstado())
                .tratamiento(referencia(cita.getTratamiento().getId(), cita.getTratamiento().getNombre()))
                .odontologo(referencia(cita.getOdontologo().getId(),
                        (cita.getOdontologo().getNombres() + " " + cita.getOdontologo().getApellidos()).trim()))
                .consultorio(referencia(cita.getConsultorio().getId(), cita.getConsultorio().getNombre()))
                .build();
    }

    private static CitaResponseDTO.Referencia referencia(UUID id, String nombre) {
        return CitaResponseDTO.Referencia.builder().id(id).nombre(nombre).build();
    }
}
