package pe.edu.dentalcite.bloqueo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueoResponseDTO {
    private UUID id;
    private UUID odontologoId;
    private String odontologo;
    private UUID consultorioId;
    private String consultorio;
    private String motivo;
    private OffsetDateTime fechaInicio;
    private OffsetDateTime fechaFin;
}
