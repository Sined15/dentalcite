package pe.edu.dentalcite.plan.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.plan.domain.Plan;
import pe.edu.dentalcite.plan.domain.PlanSesion;
import pe.edu.dentalcite.plan.repository.PlanRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Decide qué sesión de qué plan ocupa una cita atendida. Es la única pieza que
 * lo decide, y la usan los dos caminos por los que puede ocurrir: registrar el
 * resultado de una cita y crear un plan cuando el paciente ya venía atendiéndose.
 *
 * <h2>Por qué bloquea el plan</h2>
 *
 * Dos cierres simultáneos de citas del mismo paciente y tratamiento leerían a la
 * vez el mismo plan, verían la misma primera sesión pendiente y la ocuparían los
 * dos: el segundo pisaría al primero sin error, porque son citas distintas y la
 * unicidad de la base no salta. Bloquear la fila del plan antes de mirar sus
 * sesiones hace que el segundo espere y, al entrar, ya vea la sesión ocupada.
 *
 * <h2>Por qué exige una transacción ya abierta</h2>
 *
 * La cita y la sesión que ocupa tienen que guardarse juntas o no guardarse: una
 * cita atendida sin su sesión dejaría el plan contando de menos, y una sesión
 * ocupada por una cita cuyo cierre se deshizo contaría de más. Por eso no abre la
 * suya, y el bloqueo solo tiene sentido dentro de la de quien llama.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnlaceDeSesiones {

    /** Hasta dónde mira atrás la creación de un plan en busca de citas ya atendidas. */
    public static final int DIAS_DE_HISTORIAL = 90;

    private final PlanRepository planRepository;
    private final CitaRepository citaRepository;

    /** La sesión que acaba de ocupar una cita, para decírselo a quien la cerró. */
    public record SesionOcupada(UUID planId, int numero, String tratamiento) {
    }

    /**
     * Ocupa con la cita la primera sesión pendiente del plan activo de su paciente
     * y su tratamiento. No hace nada si no hay plan —incluido el caso de que el
     * plan sea de otro tratamiento— o si el plan ya tiene todas sus sesiones.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SesionOcupada> enlazarCitaAtendida(Cita cita) {
        Optional<Plan> activo = planRepository.findActivoParaEnlazar(
                cita.getFicha().getId(), cita.getTratamiento().getId());
        if (activo.isEmpty()) {
            return Optional.empty();
        }

        Plan plan = activo.get();
        Optional<PlanSesion> libre = plan.primeraSesionPendiente();
        if (libre.isEmpty()) {
            // No es un error: el plan ya cumplió lo previsto, y la cita sigue
            // atendida aunque no avance nada.
            log.info("Cita {} sin sesion: el plan {} ya tiene todas ocupadas", cita.getCodigo(), plan.getId());
            return Optional.empty();
        }

        PlanSesion sesion = libre.get();
        sesion.enlazar(cita);
        log.info("Cita {} enlazada a la sesion {} del plan {}", cita.getCodigo(), sesion.getNumero(), plan.getId());
        return Optional.of(new SesionOcupada(plan.getId(), sesion.getNumero(),
                plan.getTratamiento().getNombre()));
    }

    /**
     * Enlaza a un plan recién creado las citas de su paciente y tratamiento que
     * se atendieron en los últimos {@value #DIAS_DE_HISTORIAL} días y no ocupan
     * todavía ninguna sesión, de la más antigua a la más reciente. Cuántas caben
     * lo decide {@link Plan#enlazarRetroactivas(List)}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public int enlazarRetroactivas(Plan plan, OffsetDateTime ahora) {
        List<Cita> atendidas = citaRepository.atendidasSinSesion(plan.getFicha().getId(),
                plan.getTratamiento().getId(), ahora.minusDays(DIAS_DE_HISTORIAL), ahora);
        int enlazadas = plan.enlazarRetroactivas(atendidas);
        if (enlazadas > 0) {
            log.info("Plan {} nace con {} sesiones ocupadas por citas anteriores", plan.getId(), enlazadas);
        }
        return enlazadas;
    }
}
