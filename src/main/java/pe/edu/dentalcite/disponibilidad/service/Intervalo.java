package pe.edu.dentalcite.disponibilidad.service;

import java.time.OffsetDateTime;
import java.util.List;

public record Intervalo(OffsetDateTime inicio, OffsetDateTime fin) {

    public boolean solapa(OffsetDateTime otroInicio, OffsetDateTime otroFin) {
        return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
    }

    public static boolean algunoSolapa(List<Intervalo> tramos, OffsetDateTime inicio, OffsetDateTime fin) {
        if (tramos == null || tramos.isEmpty()) {
            return false;
        }
        return tramos.stream().anyMatch(t -> t.solapa(inicio, fin));
    }
}
