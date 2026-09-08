package pe.edu.dentalcite.odontologo.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class OdontologoRequestDTO {

    @NotBlank(message = "El COP (Colegio Odontológico) es obligatorio")
    private String cop;

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    @NotNull(message = "La ficha de usuario es obligatoria")
    private UUID fichaId;

    @NotEmpty(message = "Debe asignar al menos una especialidad al odontólogo")
    private Set<UUID> especialidadesIds;
}
