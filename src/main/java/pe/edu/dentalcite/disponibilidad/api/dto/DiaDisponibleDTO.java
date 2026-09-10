package pe.edu.dentalcite.disponibilidad.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Un día con las horas de inicio que admite el tratamiento consultado.
 *
 * <p>Solo viaja el inicio: el fin de toda franja es {@code inicio + duracionMinutos},
 * que la respuesta ya declara una vez. Los días sin ninguna franja no se incluyen.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaDisponibleDTO {
    private LocalDate fecha;
    private List<LocalTime> inicios;
}
