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

/**
 * Cuerpo de la reserva (HU-09 · RF-15, HU-14 · RF-17).
 *
 * <p><strong>El paciente es opcional y no siempre se puede mandar.</strong> Para
 * el PACIENTE la ficha sale del token y {@code pacienteId} esta prohibido: HU-09
 * es «el paciente para si mismo», y aceptarlo —aunque coincidiera con el suyo—
 * daria una forma de sondear identificadores ajenos. Para RECEPCIONISTA y
 * ADMINISTRADOR es al reves: {@code pacienteId} es obligatorio, porque su cuenta
 * no tiene una ficha para la que reservar. Quien decide es
 * {@code cita.service.AutorDeLaReserva}, no la validacion de este DTO: la regla
 * depende del rol de quien llama y aqui no se conoce.
 *
 * <p>La franja viaja como fecha y hora <em>locales de la clinica</em>, no como un
 * instante ISO. El navegador puede estar en otro huso, y quien posee la zona es
 * el servidor (`app.zona-horaria`): es exactamente el formato en que la consulta
 * de disponibilidad las entrego.
 */
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
