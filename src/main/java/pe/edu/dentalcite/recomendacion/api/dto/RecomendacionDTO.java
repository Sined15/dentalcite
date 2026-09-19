package pe.edu.dentalcite.recomendacion.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Un cuidado del catálogo cerrado.
 *
 * <p>No lleva la marca de actividad: el listado solo devuelve las vigentes, y en una
 * sesión ya cerrada lo que importa es qué se indicó, no si hoy se seguiría
 * indicando.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecomendacionDTO {

    private UUID id;
    private String descripcion;
}
