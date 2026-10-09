package pe.edu.dentalcite.cita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Period;

@Component
public class ReglasDeReserva {

    private final Duration antelacionMinima;
    private final Period horizonteMaximo;
    private final int maximoActivasPorPaciente;

    public ReglasDeReserva(
            @Value("${app.citas.antelacion-minima-horas:2}") int antelacionMinimaHoras,
            @Value("${app.citas.horizonte-maximo-dias:90}") int horizonteMaximoDias,
            @Value("${app.citas.maximo-activas-por-paciente:3}") int maximoActivasPorPaciente) {
        this.antelacionMinima = Duration.ofHours(antelacionMinimaHoras);
        this.horizonteMaximo = Period.ofDays(horizonteMaximoDias);
        this.maximoActivasPorPaciente = maximoActivasPorPaciente;
    }

    public boolean demasiadoPronto(OffsetDateTime inicio, OffsetDateTime ahora) {
        return inicio.isBefore(ahora.plus(antelacionMinima));
    }

    public boolean demasiadoTarde(OffsetDateTime inicio, OffsetDateTime ahora) {
        return inicio.isAfter(ahora.plus(horizonteMaximo));
    }

    public boolean dentroDeVentana(OffsetDateTime inicio, OffsetDateTime ahora) {
        return !demasiadoPronto(inicio, ahora) && !demasiadoTarde(inicio, ahora);
    }

    public Duration antelacionMinima() {
        return antelacionMinima;
    }

    public Period horizonteMaximo() {
        return horizonteMaximo;
    }

    public int maximoActivasPorPaciente() {
        return maximoActivasPorPaciente;
    }
}
