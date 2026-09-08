package pe.edu.dentalcite.usuario.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PasswordProvisionalRequestDTO {

    @NotBlank(message = "La contraseña provisional es obligatoria")
    @Size(min = 8, message = "La contraseña provisional debe tener al menos 8 caracteres")
    private String password;
}
