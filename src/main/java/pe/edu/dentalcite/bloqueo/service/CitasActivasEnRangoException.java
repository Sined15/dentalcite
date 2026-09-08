package pe.edu.dentalcite.bloqueo.service;

import lombok.Getter;
import pe.edu.dentalcite.bloqueo.api.dto.CitaAfectadaDTO;

import java.util.List;

/**
 * RN-03: un bloqueo que alcanza citas activas no se aplica hasta que estas se
 * cancelen con motivo. Lleva consigo la lista de citas afectadas porque HU-07
 * exige mostrárselas a quien intentó registrar el bloqueo — un 409 con solo un
 * mensaje no le diría qué tiene que cancelar.
 */
@Getter
public class CitasActivasEnRangoException extends RuntimeException {

    private final transient List<CitaAfectadaDTO> citasActivas;

    public CitasActivasEnRangoException(List<CitaAfectadaDTO> citasActivas) {
        super("El rango alcanza " + citasActivas.size()
                + " cita(s) activa(s): cancélelas con motivo antes de aplicar el bloqueo (RN-03).");
        this.citasActivas = citasActivas;
    }
}
