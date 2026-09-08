package pe.edu.dentalcite.auth.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegistroRequest {
    
    @NotBlank(message = "Los nombres no pueden estar vacíos")
    private String nombres;

    @NotBlank(message = "Los apellidos no pueden estar vacíos")
    private String apellidos;

    // RN-10: el paciente se identifica por su tipo y número de documento.
    @Pattern(regexp = "^(DNI|CE|PASAPORTE)$", message = "El tipo de documento debe ser DNI, CE o PASAPORTE")
    private String tipoDocumento = "DNI";

    @NotBlank(message = "El documento no puede estar vacío")
    private String documento;

    @Email(message = "Correo inválido")
    @NotBlank(message = "El correo no puede estar vacío")
    private String correo;

    private String telefono;

    @NotBlank(message = "La contraseña no puede estar vacía")
    @Size(min = 8, message = "La contraseña debe tener al menos 8 caracteres")
    private String password;

    @NotNull(message = "Debe aceptar el consentimiento de tratamiento de datos")
    @AssertTrue(message = "Debe aceptar el consentimiento de tratamiento de datos")
    private Boolean consentimientoAceptado;

    @NotBlank(message = "La versión del consentimiento es obligatoria")
    private String versionConsentimiento;

    public Boolean getConsentimientoAceptado() {
        return consentimientoAceptado;
    }
}
