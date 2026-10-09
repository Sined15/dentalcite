package pe.edu.dentalcite.cita.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CancelacionRequestDTO {

    @Schema(description = "Por que se cancela la cita", example = "El paciente reprograma por viaje")
    @NotBlank(message = "El motivo de la cancelacion es obligatorio")
    @Size(max = 255, message = "El motivo no puede exceder 255 caracteres")
    private String motivo;
}
