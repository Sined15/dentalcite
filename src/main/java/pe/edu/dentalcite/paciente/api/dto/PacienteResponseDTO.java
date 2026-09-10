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

    /** Correlativo e inmutable, tomado de la secuencia {@code sq_historia_clinica} (RN-10). */
    private String numeroHistoria;

    private String tipoDocumento;
    private String documento;
    private String nombres;
    private String apellidos;
    private String telefono;

    /**
     * Si la persona tiene además cuenta de acceso. El alta presencial siempre
     * devuelve {@code false} —es lo que distingue a este paciente—, y deja de
     * serlo si más tarde se registra en el portal con el mismo documento.
     */
    private boolean tieneCuenta;
}
