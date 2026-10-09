package pe.edu.dentalcite.paciente.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.dentalcite.cita.api.dto.CitaResumenDTO;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PacienteDetalleDTO {

    private UUID id;
    private String numeroHistoria;
    private String tipoDocumento;
    private String documento;
    private String nombres;
    private String apellidos;
    private String telefono;
    private boolean tieneCuenta;

    private String alergias;

    private List<CitaResumenDTO> citas;
}
