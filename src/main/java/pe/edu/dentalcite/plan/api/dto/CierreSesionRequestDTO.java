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

/**
 * Cuerpo de {@code POST /api/v1/planes/{id}/sesiones/{numero}/cierre}.
 *
 * <p>Al menos una recomendacion, y por eso {@code @NotEmpty} y no {@code @NotNull}:
 * una lista vacia es exactamente el caso que hay que rechazar. Esta aqui y no en la
 * base porque una tabla padre no puede obligar a que existan filas hijas; la
 * migracion lo dice tambien, para que nadie lo busque alli.
 *
 * <p>La observacion es opcional y son cuidados escritos a mano, no una indicacion
 * clinica: de ahi el limite corto, que el cliente ademas advierte junto al campo.
 */
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
