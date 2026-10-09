package pe.edu.dentalcite.paciente.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteResponseDTO {

    private UUID id;

    private String numeroHistoria;

    private String tipoDocumento;
    private String documento;
    private String nombres;
    private String apellidos;
    private String telefono;
    private boolean tieneCuenta;
}
