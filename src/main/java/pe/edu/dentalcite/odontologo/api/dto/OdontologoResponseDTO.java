package pe.edu.dentalcite.odontologo.api.dto;

import lombok.Builder;
import lombok.Data;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class OdontologoResponseDTO {
    private UUID id;
    private String cop;
    private String nombres;
    private String apellidos;
    private UUID fichaId;
    private Boolean activo;
    private Set<EspecialidadResponseDTO> especialidades;
}
