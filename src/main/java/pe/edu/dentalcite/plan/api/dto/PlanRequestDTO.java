package pe.edu.dentalcite.plan.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Cuerpo de {@code POST /api/v1/planes} (HU-17 · RF-23).
 *
 * <p>{@code odontologoId} sigue la misma forma que {@code pacienteId} en la
 * reserva (HU-14): quien tiene registro de odontologo no lo manda —sale de su
 * token— y quien no lo tiene, el ADMINISTRADOR, esta obligado a indicarlo,
 * porque su cuenta no planifica en nombre de nadie por si sola. Un ODONTOLOGO
 * que lo envie recibe 403: planificar en nombre de otro profesional no es una
 * operacion del alcance.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRequestDTO {

    @Schema(description = "Ficha del paciente al que se le planifica el tratamiento")
    @NotNull(message = "El paciente es obligatorio")
    private UUID pacienteId;

    @NotNull(message = "El tratamiento es obligatorio")
    private UUID tratamientoId;

    @Schema(description = "Odontologo que planifica. Solo lo manda el ADMINISTRADOR, y para el es"
            + " obligatorio; el ODONTOLOGO lo tiene en su token y recibe 403 si lo envia")
    private UUID odontologoId;

    @Schema(description = "En cuantas sesiones se dividira el tratamiento", example = "6")
    @NotNull(message = "El numero de sesiones previstas es obligatorio")
    @Min(value = 1, message = "El plan debe tener al menos una sesion")
    private Integer sesionesPrevistas;
}
