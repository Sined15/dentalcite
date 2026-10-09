package pe.edu.dentalcite.cita.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaRequestDTO {

    @Schema(description = "Ficha del paciente para el que se reserva (HU-14 · RF-17)."
            + " Solo lo admiten RECEPCIONISTA y ADMINISTRADOR, y para ellos es obligatorio."
            + " Un PACIENTE que lo envie recibe 403: el reserva para si mismo y su ficha sale del token")
    private UUID pacienteId;

    @NotNull(message = "El tratamiento es obligatorio")
    private UUID tratamientoId;

    @NotNull(message = "El odontologo es obligatorio")
    private UUID odontologoId;

    @Schema(description = "Dia de la cita, AAAA-MM-DD", example = "2026-10-12")
    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @Schema(description = "Hora local de inicio, HH:mm", example = "09:15")
    @NotNull(message = "La hora es obligatoria")
    private LocalTime hora;
}
