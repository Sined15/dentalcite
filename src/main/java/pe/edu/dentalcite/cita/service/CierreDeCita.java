package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.odontologo.service.OdontologoOwnershipGuard;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

/**
 * Registro del resultado de una cita (HU-16 · RF-22 · RN-09).
 *
 * <p>Clase aparte de {@link CitaService} por el mismo motivo que
 * {@link CancelacionService}: aquel <strong>no</strong> es {@code @Transactional}
 * a propósito, porque el reintento de RF-16 necesita abrir una transacción nueva
 * por intento, y aquí las dos escrituras —el estado y la bitácora— tienen que
 * caer juntas o no caer. Son dos contratos transaccionales incompatibles en la
 * misma clase.
 *
 * <h2>Por qué no se puede cerrar una cita que aún no ha terminado</h2>
 *
 * Las restricciones de exclusión de {@code V13} son <em>parciales sobre
 * CONFIRMADA</em>: es lo que hace que cancelar libere la franja en el acto. Pero
 * eso mismo significa que sacar una cita de CONFIRMADA la retira de la
 * comprobación de solapamiento, y si la cita todavía no ha terminado su franja
 * sigue ocupada de verdad: otra reserva podría colocarse encima del paciente que
 * está en el sillón.
 *
 * <p>No es una restricción inventada para tapar eso: RF-22 empareja esta
 * operación con «listar las citas pendientes de cierre», y RN-09 define
 * pendiente de cierre como «la confirmada cuya hora de fin ya pasó». Registrar el
 * resultado de algo que no ha ocurrido no es un caso de uso, y el 409 lo dice.
 */
@Slf4j
@Service
public class CierreDeCita {

    /** Los dos únicos resultados que RF-22 admite. */
    private static final Set<String> RESULTADOS =
            Set.of(Cita.ESTADO_ATENDIDA, Cita.ESTADO_NO_ASISTIO);

    /**
     * Quién puede cerrar la cita de cualquier odontólogo. El ODONTOLOGO no está:
     * a él lo deja pasar el guard por ser el dueño del registro, y solo del suyo.
     */
    private static final Set<String> ROLES_PRIVILEGIADOS =
            Set.of("SCOPE_RECEPCIONISTA", "SCOPE_ADMINISTRADOR");

    private final CitaRepository citaRepository;
    private final CitaHistorialRepository historialRepository;
    private final UsuarioRepository usuarioRepository;
    private final ZoneId zona;

    public CierreDeCita(CitaRepository citaRepository,
            CitaHistorialRepository historialRepository,
            UsuarioRepository usuarioRepository,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
        this.zona = ZoneId.of(zonaHoraria);
    }

    /**
     * El orden de las comprobaciones es contrato:
     *
     * <ol>
     *   <li>el resultado es uno de los dos válidos, que da el 400;</li>
     *   <li>la cita existe, que da el 404;</li>
     *   <li>quién pregunta puede tocarla, que da el 403 — <em>antes</em> de mirar
     *       su estado, para no confirmar de rebote en qué estado está una cita
     *       ajena (RNF-04);</li>
     *   <li>sigue CONFIRMADA, que da el 409 de RN-09;</li>
     *   <li>ya ha terminado, que da el otro 409.</li>
     * </ol>
     */
    @Transactional
    public CitaResponseDTO registrarResultado(UUID citaId, String resultado) {
        String estadoNuevo = normalizarResultado(resultado);

        Cita cita = citaRepository.findById(citaId)
                .orElseThrow(() -> new ResourceNotFoundException("Cita no encontrada"));

        // «ODONTOLOGO (la propia)»: recepción y administración cierran cualquiera;
        // el odontólogo, solo las de su propio registro. El PACIENTE no llega
        // hasta aquí —SecurityConfig lo corta por ruta—, pero si llegara tampoco
        // pasaría: su ficha no es la de ningún odontólogo.
        OdontologoOwnershipGuard.verificar(usuarioRepository, cita.getOdontologo(),
                ROLES_PRIVILEGIADOS, true,
                "No puede registrar el resultado de citas de otro odontólogo (RNF-04).");

        if (!Cita.ESTADO_CONFIRMADA.equals(cita.getEstado())) {
            // RN-09 es una máquina sin retorno: un estado final no se reescribe.
            throw new IllegalStateException("La cita " + cita.getCodigo() + " ya esta en estado "
                    + cita.getEstado() + ", que es final, y su resultado no puede cambiarse (RN-09).");
        }

        OffsetDateTime ahora = OffsetDateTime.now(zona);
        if (!cita.getFin().isBefore(ahora)) {
            throw new IllegalStateException("La cita " + cita.getCodigo()
                    + " todavia no ha terminado, asi que aun no hay resultado que registrar."
                    + " Estara pendiente de cierre a partir de su hora de fin (RN-09).");
        }

        String estadoAnterior = cita.getEstado();
        cita.setEstado(estadoNuevo);
        citaRepository.save(cita);

        // RF-21: la transición, con su fecha, su responsable y su marca temporal.
        // Aquí sí se escribe en la bitácora, al contrario que en el alta: esta
        // operación no corre en el camino disputado de HU-10 —la cita ya existe y
        // nadie compite por su fila—, así que no puede cerrar el ciclo de espera
        // que obligó a dejar el alta fuera.
        historialRepository.save(CitaHistorial.builder()
                .cita(cita)
                .estadoAnterior(estadoAnterior)
                .estadoNuevo(estadoNuevo)
                .usuario(responsable())
                .build());

        // No se toca `franjasCache`: la franja de una cita que ya terminó no la
        // ofrece nadie —el motor solo mira desde ahora hacia adelante—, así que
        // invalidar la caché aquí sería trabajo inútil en cada cierre.

        log.info("Cita {} cerrada como {}", cita.getCodigo(), estadoNuevo);
        return mapear(cita);
    }

    /**
     * Un resultado desconocido es un 400 y no una página vacía ni un 500: quien
     * escribe «ATENDIDO» o «asistio» tiene una errata, y decírselo es más útil
     * que dejar la cita como estaba sin explicación.
     */
    private static String normalizarResultado(String resultado) {
        if (resultado == null || resultado.isBlank()) {
            throw new IllegalArgumentException("El resultado es obligatorio");
        }
        String normalizado = resultado.trim().toUpperCase();
        if (!RESULTADOS.contains(normalizado)) {
            throw new IllegalArgumentException("Resultado no valido: " + resultado
                    + ". Los resultados son " + Cita.ESTADO_ATENDIDA + " y "
                    + Cita.ESTADO_NO_ASISTIO + " (RF-22).");
        }
        return normalizado;
    }

    /**
     * Quién cierra (RF-21). Devuelve {@code null} en vez de fallar si el token no
     * resuelve a un usuario, por lo mismo que {@link CancelacionService}: perder
     * la bitácora entera por no poder nombrar al responsable sería peor que
     * registrarla sin él, y la autorización ya se resolvió más arriba.
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
