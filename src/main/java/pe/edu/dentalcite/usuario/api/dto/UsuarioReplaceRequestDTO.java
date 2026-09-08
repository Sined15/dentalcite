package pe.edu.dentalcite.usuario.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Representación completa de un usuario para PUT (reemplazo total del
 * recurso): a diferencia de {@link UsuarioUpdateRequestDTO} (usado por PATCH),
 * ambos campos son obligatorios — un PUT no debe dejar campos "sin tocar".
 */
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
