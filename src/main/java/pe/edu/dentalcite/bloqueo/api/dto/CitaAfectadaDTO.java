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
public class CitaAfectadaDTO {
    private UUID id;
    private String codigo;
    private OffsetDateTime inicio;
    private OffsetDateTime fin;
    private String paciente;
    private String numeroHistoria;
}
