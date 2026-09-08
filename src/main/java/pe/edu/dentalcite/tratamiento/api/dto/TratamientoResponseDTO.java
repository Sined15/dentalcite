package pe.edu.dentalcite.tratamiento.api.dto;

import lombok.Builder;
import lombok.Data;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;

import java.util.UUID;

@Data
@Builder
public class TratamientoResponseDTO {
    private UUID id;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Integer duracionMinutos;
    private EspecialidadResponseDTO especialidad;
    private Boolean activo;
}
