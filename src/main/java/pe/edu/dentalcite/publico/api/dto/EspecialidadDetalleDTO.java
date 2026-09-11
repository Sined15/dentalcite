package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * Una especialidad con lo que cuelga de ella: sus tratamientos y los odontólogos
 * que la ejercen. Es la pantalla desde la que el visitante decide pedir cita, y
 * por eso responde en una sola petición en vez de obligar al cliente a componer
 * tres.
 */
@Data
@Builder
public class EspecialidadDetalleDTO {
    private UUID id;
    private String nombre;
    private String imagenUrl;
    private List<TratamientoPublicoDTO> tratamientos;
    private List<OdontologoPublicoDTO> odontologos;
}
