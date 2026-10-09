package pe.edu.dentalcite.plan.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

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
    @Max(value = 60, message = "Un plan no puede tener más de 60 sesiones")
    private Integer sesionesPrevistas;
}
