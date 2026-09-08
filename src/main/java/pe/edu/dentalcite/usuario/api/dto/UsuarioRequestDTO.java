package pe.edu.dentalcite.usuario.api.dto;

import jakarta.validation.constraints.Email;
import pe.edu.dentalcite.usuario.api.validation.DocumentoDeOdontologo;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@DocumentoDeOdontologo
public class UsuarioRequestDTO {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "El correo es obligatorio")
    @Email(message = "El formato del correo es inválido")
    private String correo;

    @NotBlank(message = "El rol es obligatorio")
    @Pattern(regexp = "^(PACIENTE|RECEPCIONISTA|ODONTOLOGO|ADMINISTRADOR)$", message = "El rol debe ser PACIENTE, RECEPCIONISTA, ODONTOLOGO o ADMINISTRADOR")
    private String rol;

    // RN-10: la persona se identifica por el par (tipo, número) de documento. Con
    // él, el alta crea o vincula su ficha, que es lo que después permite
    // registrarla como odontólogo (RF-10) y resolver las operaciones marcadas
    // «propio» de la Tabla 10. Obligatorio solo para el rol ODONTOLOGO.
    @Pattern(regexp = "^(DNI|CE|PASAPORTE)$", message = "El tipo de documento debe ser DNI, CE o PASAPORTE")
    private String tipoDocumento;

    private String documento;

    // Opcional, si el admin quiere setear una contraseña específica
    // Si viene nulo, el servicio generará una aleatoria.
    @Size(min = 8, message = "La contraseña provisional debe tener al menos 8 caracteres")
    private String passwordProvisional;

}
