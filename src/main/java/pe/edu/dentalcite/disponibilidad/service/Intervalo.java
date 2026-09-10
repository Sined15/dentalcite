package pe.edu.dentalcite.disponibilidad.service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Tramo ocupado de la agenda: una cita activa o un bloqueo, ya reducidos a lo
 * único que el motor necesita de ellos.
 *
 * <p>El solapamiento es el de intervalos semiabiertos que ya usan
 * {@code CitaRepository} y {@code HorarioAtencionRepository.findOverlappingHorarios}:
 * dos tramos chocan cuando cada uno empieza antes de que el otro acabe. Que una
 * cita termine a las 10:00 y otra empiece a las 10:00 no es un choque.
 */
public record Intervalo(OffsetDateTime inicio, OffsetDateTime fin) {

    public boolean solapa(OffsetDateTime otroInicio, OffsetDateTime otroFin) {
        return inicio.isBefore(otroFin) && fin.isAfter(otroInicio);
    }

    /** {@code true} si alguno de los tramos choca con el candidato. Lista nula = libre. */
    public static boolean algunoSolapa(List<Intervalo> tramos, OffsetDateTime inicio, OffsetDateTime fin) {
        if (tramos == null || tramos.isEmpty()) {
            return false;
        }
        return tramos.stream().anyMatch(t -> t.solapa(inicio, fin));
    }
}
