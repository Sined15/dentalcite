package pe.edu.dentalcite.paciente.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PacienteRequestDTO {

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    @Pattern(regexp = "^(DNI|CE|PASAPORTE)$", message = "El tipo de documento debe ser DNI, CE o PASAPORTE")
    private String tipoDocumento = "DNI";

    @NotBlank(message = "El documento es obligatorio")
    private String documento;

    private String telefono;

    @NotNull(message = "Debe constar el consentimiento informado del titular")
    @AssertTrue(message = "Debe constar el consentimiento informado del titular")
    private Boolean consentimientoAceptado;

    @NotBlank(message = "La versión del consentimiento es obligatoria")
    private String versionConsentimiento;

    public Boolean getConsentimientoAceptado() {
        return consentimientoAceptado;
    }
}
