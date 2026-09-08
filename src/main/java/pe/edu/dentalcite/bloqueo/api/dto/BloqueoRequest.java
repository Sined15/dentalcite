package pe.edu.dentalcite.bloqueo.api.dto;

import jakarta.validation.constraints.NotBlank;
import pe.edu.dentalcite.bloqueo.api.validation.AmbitoDeBloqueo;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@AmbitoDeBloqueo
public class BloqueoRequest {

    private UUID odontologoId;

    private UUID consultorioId;

    @NotBlank(message = "El motivo es obligatorio")
    private String motivo;

    @NotNull(message = "La fecha de inicio es obligatoria")
    private OffsetDateTime fechaInicio;

    @NotNull(message = "La fecha de fin es obligatoria")
    private OffsetDateTime fechaFin;

}
