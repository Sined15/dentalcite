package pe.edu.dentalcite.paciente.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.dentalcite.cita.api.dto.CitaResumenDTO;

import java.util.List;
import java.util.UUID;

/**
 * La ficha completa (HU-13 · RF-08): «sus datos, sus alergias y sus citas
 * pasadas y futuras».
 *
 * <p>No amplía {@link PacienteResponseDTO} por herencia: aquel es una fila de
 * resultados y este una pantalla, y atarlos obligaría a que el listado
 * arrastrase para siempre lo que la ficha añada.
 */
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

    /** RF-08. Texto libre; nulo mientras nadie lo haya registrado. */
    private String alergias;

    /**
     * Pasadas y futuras en una sola lista, de la más reciente a la más antigua:
     * separarlas en el servidor obligaría a fijar aquí qué es «ahora», y el
     * cliente ya tiene la fecha y el estado de cada una para agruparlas.
     *
     * <p>Cada fila repite el paciente porque reutiliza {@code CitaResumenDTO},
     * el mismo de la agenda. Es redundante dentro de su propia ficha y se acepta:
     * el precio de no duplicar la conversión a la hora local de la clínica.
     */
    private List<CitaResumenDTO> citas;
}
