package pe.edu.dentalcite.usuario.api.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UsuarioUpdateRequestDTO {

    private String nombre;

    @Pattern(regexp = "^(PACIENTE|RECEPCIONISTA|ODONTOLOGO|ADMINISTRADOR)$", message = "El rol debe ser PACIENTE, RECEPCIONISTA, ODONTOLOGO o ADMINISTRADOR")
    private String rol;
    
    private Boolean activo;
}
