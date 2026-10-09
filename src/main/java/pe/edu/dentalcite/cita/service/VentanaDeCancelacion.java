package pe.edu.dentalcite.cita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.dentalcite.cita.domain.Cita;

import java.time.Duration;
import java.time.OffsetDateTime;

@Component
public class VentanaDeCancelacion {

    private final Duration antelacionCancelacion;
    private final ReglasDeReserva reglas;

    public VentanaDeCancelacion(
            @Value("${app.citas.antelacion-cancelacion-horas:24}") int antelacionCancelacionHoras,
            ReglasDeReserva reglas) {
        this.antelacionCancelacion = Duration.ofHours(antelacionCancelacionHoras);
        this.reglas = reglas;
    }

    public boolean puedeCancelarElPaciente(Cita cita, OffsetDateTime ahora) {
        if (!Cita.ESTADO_CONFIRMADA.equals(cita.getEstado())) {
            return false;
        }
        if (!cita.getInicio().isBefore(ahora.plus(antelacionCancelacion))) {
            return true;
        }
        return laReservoElPropioPaciente(cita)
                && seReservoDentroDeLaVentana(cita)
                && !reglas.demasiadoPronto(cita.getInicio(), ahora);
    }

    public String motivoDelRechazo() {
        return "Solo puede cancelar por su cuenta hasta " + antelacionCancelacion.toHours()
                + " horas antes del inicio (RN-06). Contacte con la recepción de la clínica"
                + " para cancelar esta cita.";
    }

    public Duration antelacion() {
        return antelacionCancelacion;
    }

    private static boolean laReservoElPropioPaciente(Cita cita) {
        return cita.getCreadoPor() != null
                && cita.getCreadoPor().getFicha() != null
                && cita.getCreadoPor().getFicha().getId().equals(cita.getFicha().getId());
    }

    private boolean seReservoDentroDeLaVentana(Cita cita) {
        return cita.getCreadoEn() != null
                && cita.getCreadoEn().isAfter(cita.getInicio().minus(antelacionCancelacion));
    }
}
