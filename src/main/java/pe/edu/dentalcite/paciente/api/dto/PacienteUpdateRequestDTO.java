package pe.edu.dentalcite.paciente.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PacienteUpdateRequestDTO {

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    private String telefono;

    private String alergias;
}
