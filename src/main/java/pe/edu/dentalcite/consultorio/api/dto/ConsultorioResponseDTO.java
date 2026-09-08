package pe.edu.dentalcite.consultorio.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsultorioResponseDTO {
    private UUID id;
    private String nombre;
    private Boolean inoperativo;
}
