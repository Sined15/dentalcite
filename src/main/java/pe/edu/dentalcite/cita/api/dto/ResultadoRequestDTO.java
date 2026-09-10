package pe.edu.dentalcite.cita.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo de {@code PATCH /api/v1/citas/{id}/resultado} (HU-16 · RF-22).
 *
 * <p>Un solo campo, y a proposito: RF-22 dice «registrar el resultado de una cita
 * —atendida o no asistio—», que son dos transiciones de la misma operacion y no
 * dos endpoints. Separarlas en {@code /atender} y {@code /no-asistio} obligaria a
 * duplicar la autorizacion, la comprobacion de estado y la escritura en la
 * bitacora para que la unica diferencia fuese la cadena que se guarda.
 *
 * <p>No lleva motivo. La bitacora admite uno porque la cancelacion lo exige
 * (RF-20), pero aqui ningun criterio lo pide: el resultado se explica solo, y un
 * campo libre en la ficha clinica es justo la frontera que F-04 deja fuera del
 * alcance.
 */
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
