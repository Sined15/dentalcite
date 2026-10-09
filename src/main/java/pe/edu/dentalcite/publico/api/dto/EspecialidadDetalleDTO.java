package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class EspecialidadDetalleDTO {
    private UUID id;
    private String nombre;
    private String imagenUrl;
    private List<TratamientoPublicoDTO> tratamientos;
    private List<OdontologoPublicoDTO> odontologos;
}
