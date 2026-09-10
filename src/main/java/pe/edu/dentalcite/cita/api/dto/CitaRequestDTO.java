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
 * Cuerpo de la reserva (HU-09 · RF-15).
 *
 * <p><strong>No lleva paciente.</strong> La ficha sale del token: HU-09 es «el
 * paciente para si mismo». Reservar en nombre de otro es HU-14, que anadira un
 * {@code pacienteId} opcional sobre este mismo DTO y ampliara la regla de
 * autorizacion; disenarlo asi evita que ese campo nazca ahora sin nadie que lo
 * valide y que un paciente pueda colar el identificador de otro.
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
