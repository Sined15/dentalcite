package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class EspecialidadPublicaDTO {
    private UUID id;
    private String nombre;
    private String imagenUrl;
}
