package pe.edu.dentalcite.especialidad.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class EspecialidadResponseDTO {
    private UUID id;
    private String nombre;
    private String descripcion;
    private Boolean activo;
}
