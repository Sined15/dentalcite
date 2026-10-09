package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
@Builder
public class OdontologoPublicoDTO {
    private UUID id;
    private String nombres;
    private String apellidos;
    private String cop;
    private String imagenUrl;
    private List<String> especialidades;
    private List<UUID> especialidadIds;
}
