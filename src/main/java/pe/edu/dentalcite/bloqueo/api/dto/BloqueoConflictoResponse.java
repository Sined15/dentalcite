package pe.edu.dentalcite.bloqueo.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Cuerpo del 409 con el que se rechaza un bloqueo que alcanza citas activas. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloqueoConflictoResponse {
    private String message;
    private List<CitaAfectadaDTO> citasActivas;
}
