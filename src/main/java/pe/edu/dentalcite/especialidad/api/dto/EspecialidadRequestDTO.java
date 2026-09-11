package pe.edu.dentalcite.especialidad.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EspecialidadRequestDTO {
    @NotBlank(message = "El nombre de la especialidad es obligatorio")
    private String nombre;
    private String descripcion;

    /** RF-09 (v4): opcional. Una especialidad sin imagen se pinta con un marcador. */
    @Size(max = 500, message = "La ruta de la imagen no puede pasar de 500 caracteres")
    private String imagenUrl;
}
