package pe.edu.dentalcite.horario.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HorarioResponseDTO {
    private UUID id;
    private UUID odontologoId;
    private Integer diaSemana;
    private LocalTime horaInicio;
    private LocalTime horaFin;
}
