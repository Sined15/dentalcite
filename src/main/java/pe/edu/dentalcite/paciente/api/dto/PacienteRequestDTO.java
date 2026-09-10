package pe.edu.dentalcite.paciente.api.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Alta presencial de un paciente (HU-12, RF-06).
 *
 * <p>No tiene correo ni contraseña a propósito: el criterio dice «se creará su
 * ficha con número de historia único y <em>sin credenciales de acceso</em>», así
 * que el cuerpo ni siquiera ofrece dónde ponerlas. Quien quiera una cuenta se
 * registra por el portal (HU-02), y esa ficha se vincula en lugar de duplicarse.
 */
@Data
public class PacienteRequestDTO {

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    // RN-10: el paciente se identifica por el par (tipo, número) de documento.
    @Pattern(regexp = "^(DNI|CE|PASAPORTE)$", message = "El tipo de documento debe ser DNI, CE o PASAPORTE")
    private String tipoDocumento = "DNI";

    @NotBlank(message = "El documento es obligatorio")
    private String documento;

    private String telefono;

    /**
     * RNF-06. Es lo que produce el 400 del tercer criterio: sin esta marca no hay
     * consentimiento informado del titular, y sin él no hay alta.
     */
    @NotNull(message = "Debe constar el consentimiento informado del titular")
    @AssertTrue(message = "Debe constar el consentimiento informado del titular")
    private Boolean consentimientoAceptado;

    @NotBlank(message = "La versión del consentimiento es obligatoria")
    private String versionConsentimiento;

    public Boolean getConsentimientoAceptado() {
        return consentimientoAceptado;
    }
}
