package pe.edu.dentalcite.disponibilidad.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Quién puede alojar una franja (RN-02, RF-14).
 *
 * <p>La pregunta se hace en dos momentos distintos y tiene que responderse igual
 * en los dos: al <em>ofrecer</em> la franja (HU-08 solo necesita saber si queda
 * alguno) y al <em>reservarla</em> (HU-09 necesita cuál). Estaba resuelta dentro
 * de {@code MotorDeFranjas}; extraerla evita que la reserva reimplemente el
 * criterio y acabe divergiendo del motor que se lo ofreció.
 *
 * <p>Un consultorio deja de contar si una cita activa lo ocupa o si un bloqueo lo
 * saca de servicio en ese intervalo; da igual cuál de las dos cosas.
 */
public final class ConsultoriosLibres {

    private ConsultoriosLibres() {
    }

    /** @return los operativos que ni están ocupados ni bloqueados en el intervalo. */
    public static List<UUID> en(List<UUID> operativos,
            Map<UUID, List<Intervalo>> ocupacion,
            Map<UUID, List<Intervalo>> bloqueos,
            OffsetDateTime inicio,
            OffsetDateTime fin) {
        return operativos.stream()
                .filter(id -> !Intervalo.algunoSolapa(ocupacion.get(id), inicio, fin))
                .filter(id -> !Intervalo.algunoSolapa(bloqueos.get(id), inicio, fin))
                .toList();
    }

    public static boolean hayAlguno(List<UUID> operativos,
            Map<UUID, List<Intervalo>> ocupacion,
            Map<UUID, List<Intervalo>> bloqueos,
            OffsetDateTime inicio,
            OffsetDateTime fin) {
        return operativos.stream()
                .anyMatch(id -> !Intervalo.algunoSolapa(ocupacion.get(id), inicio, fin)
                        && !Intervalo.algunoSolapa(bloqueos.get(id), inicio, fin));
    }
}
