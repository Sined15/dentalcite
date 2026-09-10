package pe.edu.dentalcite.disponibilidad.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/** Las franjas libres de un odontólogo dentro del rango consultado. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgendaOdontologoDTO {
    private UUID id;
    private String nombres;
    private String apellidos;
    private List<DiaDisponibleDTO> dias;
}
