package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class TratamientoPublicoDTO {
    private UUID id;
    private String nombre;
    private String descripcion;
    private Integer duracionMinutos;
    private String imagenUrl;
    private UUID especialidadId;
    private String especialidadNombre;
}
