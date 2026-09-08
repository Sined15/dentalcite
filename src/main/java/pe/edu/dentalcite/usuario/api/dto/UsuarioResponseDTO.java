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

    /**
     * Ficha de la persona a la que pertenece la cuenta, cuando la tiene (RN-11).
     * Es el identificador que {@code POST /api/v1/odontologos} exige para
     * registrarla, así que sin exponerlo aquí el alta de personal no encadenaría
     * con el registro de odontólogo sin consultar la base de datos a mano.
     */
    private UUID fichaId;

    /**
     * Contraseña provisional en claro. Solo la rellena el alta de una cuenta, y
     * solo cuando el administrador no indicó ninguna y el servicio tuvo que
     * generarla: es el único instante en que existe fuera del hash, así que o se
     * entrega aquí o la cuenta nace inaccesible (HU-04, «quedará activa y podrá
     * iniciar sesión»). {@code mapToDTO} nunca la asigna, de modo que ninguna
     * lectura posterior de la cuenta la expone; el campo se omite del JSON cuando
     * es nula.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String passwordProvisional;
}
