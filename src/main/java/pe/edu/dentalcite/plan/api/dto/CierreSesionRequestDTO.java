package pe.edu.dentalcite.plan.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CierreSesionRequestDTO {

    @NotEmpty(message = "Indique al menos una recomendacion")
    private List<UUID> recomendacionIds;

    @NotNull(message = "La fecha del proximo control es obligatoria")
    private LocalDate proximoControl;

    @Size(max = 300, message = "La observacion no puede superar los 300 caracteres")
    private String observacion;
}
