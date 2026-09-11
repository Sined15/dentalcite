package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * El tratamiento tal como se anuncia en la portada y dentro de su especialidad.
 *
 * <p>Lleva la duración porque es lo que el visitante necesita para decidir, y el
 * nombre de la especialidad para poder agruparlos; no lleva el código interno ni
 * el estado.
 */
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
