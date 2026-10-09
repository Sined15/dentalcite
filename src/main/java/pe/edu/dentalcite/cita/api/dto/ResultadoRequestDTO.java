package pe.edu.dentalcite.cita.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoRequestDTO {

    @Schema(description = "ATENDIDA si el paciente vino y se le atendio, NO_ASISTIO si no se presento",
            example = "ATENDIDA", allowableValues = {"ATENDIDA", "NO_ASISTIO"})
    @NotBlank(message = "El resultado es obligatorio")
    private String resultado;
}
