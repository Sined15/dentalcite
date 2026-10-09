package pe.edu.dentalcite.usuario.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsuarioResponseDTO {
    private UUID id;
    private String nombre;
    private String correo;
    private String rol;
    private Boolean activo;
    private Boolean requiereCambioPassword;
    private OffsetDateTime tokensValidosDesde;

    private UUID fichaId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String passwordProvisional;
}
