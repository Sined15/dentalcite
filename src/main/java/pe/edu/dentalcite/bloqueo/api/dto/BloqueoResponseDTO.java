package pe.edu.dentalcite.bloqueo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Bloqueo de agenda tal como lo devuelve la API.
 *
 * <p>El controlador devolvía la entidad {@code Bloqueo}, cuyas asociaciones LAZY
 * {@code odontologo} y {@code consultorio} rompían la serialización con
 * {@code open-in-view: false}. Además, serializar el odontólogo arrastraba su
 * {@code ficha} —documento y número de historia— hasta la respuesta que ve la
 * recepción, que no necesita ese dato (RNF-06). Aquí solo viajan el
 * identificador y el nombre, que ya son públicos en {@code GET /odontologos} y
 * {@code GET /consultorios}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueoResponseDTO {
    private UUID id;
    private UUID odontologoId;
    private String odontologo;
    private UUID consultorioId;
    private String consultorio;
    private String motivo;
    private OffsetDateTime fechaInicio;
    private OffsetDateTime fechaFin;
}
