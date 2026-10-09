package pe.edu.dentalcite.disponibilidad.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ConsultoriosLibres {

    private ConsultoriosLibres() {
    }

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
