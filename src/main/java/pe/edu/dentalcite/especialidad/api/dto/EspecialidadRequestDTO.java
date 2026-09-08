package pe.edu.dentalcite.especialidad.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EspecialidadRequestDTO {
    @NotBlank(message = "El nombre de la especialidad es obligatorio")
    private String nombre;
    private String descripcion;
}
