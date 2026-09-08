package pe.edu.dentalcite.horario.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

/**
 * Tramo de horario tal como lo devuelve la API.
 *
 * <p>El controlador devolvía la entidad {@code HorarioAtencion}. Con
 * {@code open-in-view: false} la sesión de persistencia se cierra antes de que
 * Jackson serialice, de modo que su asociación LAZY {@code odontologo} —y la
 * {@code ficha} que cuelga de ella— rompían la respuesta con un 500. Aquí solo
 * viaja el identificador del odontólogo, que el proxy resuelve sin inicializarse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioResponseDTO {
    private UUID id;
    private UUID odontologoId;
    private Integer diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
}
