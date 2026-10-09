package pe.edu.dentalcite.bloqueo.service;

import lombok.Getter;
import pe.edu.dentalcite.bloqueo.api.dto.CitaAfectadaDTO;

import java.util.List;

@Getter
public class CitasActivasEnRangoException extends RuntimeException {

    private final transient List<CitaAfectadaDTO> citasActivas;

    public CitasActivasEnRangoException(List<CitaAfectadaDTO> citasActivas) {
        super("El rango alcanza " + citasActivas.size()
                + " cita(s) activa(s): cancélelas con motivo antes de aplicar el bloqueo (RN-03).");
        this.citasActivas = citasActivas;
    }
}
