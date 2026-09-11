package pe.edu.dentalcite.plan.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code PATCH /api/v1/planes/{id}/suspender} (HU-17 · RF-23).
 *
 * <p>El motivo es obligatorio porque el criterio dice «lo suspenda indicando
 * motivo», y porque la base lo exige: un plan sin marca de actividad y sin motivo
 * viola el CHECK de {@code V19}. Que la regla este en los dos sitios no es
 * duplicarla: aqui evita el viaje y explica el campo, y alli es la garantia.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuspensionRequestDTO {

    @NotBlank(message = "El motivo de la suspension es obligatorio")
    @Size(max = 255, message = "El motivo no puede superar los 255 caracteres")
    private String motivo;
}
