package pe.edu.dentalcite.usuario.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UsuarioReplaceRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotNull(message = "El rol es obligatorio")
    @Pattern(regexp = "^(PACIENTE|RECEPCIONISTA|ODONTOLOGO|ADMINISTRADOR)$", message = "El rol debe ser PACIENTE, RECEPCIONISTA, ODONTOLOGO o ADMINISTRADOR")
    private String rol;

    @NotNull(message = "El estado 'activo' es obligatorio")
    private Boolean activo;
}
