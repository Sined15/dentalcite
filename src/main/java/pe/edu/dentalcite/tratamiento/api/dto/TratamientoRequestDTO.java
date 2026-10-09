package pe.edu.dentalcite.tratamiento.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import pe.edu.dentalcite.tratamiento.api.validation.DuracionSegunRn04;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
@DuracionSegunRn04
public class TratamientoRequestDTO {

    @NotBlank(message = "El código es obligatorio")
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    private String descripcion;

    @NotNull(message = "La duración es obligatoria")
    @Min(value = 15, message = "La duración mínima es de 15 minutos")
    @Max(value = 240, message = "La duración máxima es de 240 minutos")
    @Schema(minimum = "15", maximum = "240", multipleOf = 15,
            description = "Duración en minutos: múltiplo de 15, entre 15 y 240 (RN-04)")
    private Integer duracionMinutos;


    @NotNull(message = "La especialidad es obligatoria")
    private UUID especialidadId;

    @Size(max = 500, message = "La ruta de la imagen no puede pasar de 500 caracteres")
    private String imagenUrl;
}
