package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/**
 * La ficha del odontólogo que ve el visitante: imagen, nombre, especialidad y
 * COP, que es lo que RF-10 (v4) enumera.
 *
 * <p><strong>No lleva {@code fichaId}</strong>, y esa ausencia es la razón de que
 * el catálogo público tenga rutas propias. {@code OdontologoResponseDTO} sí lo
 * expone porque el formulario de mantenimiento lo necesita para saber qué fichas
 * están ocupadas; publicar ese identificador a cualquiera que pase por la web
 * sería regalarlo sin que nadie lo pida.
 */
@Data
@Builder
public class OdontologoPublicoDTO {
    private UUID id;
    private String nombres;
    private String apellidos;
    private String cop;
    private String imagenUrl;
    private List<String> especialidades;
}
