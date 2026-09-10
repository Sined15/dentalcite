package pe.edu.dentalcite.cita.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * El motivo de una cancelacion (HU-11 · RF-20).
 *
 * <p>El motivo es obligatorio y esa obligacion vive aqui, no en el servicio: el
 * criterio de aceptacion pide un 400 ante una cancelacion sin motivo, y eso es
 * exactamente lo que {@code @NotBlank} produce a traves del manejador de
 * {@code MethodArgumentNotValidException} que ya existe. {@code @NotBlank} y no
 * {@code @NotNull} porque un motivo en blanco no es un motivo.
 */
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
