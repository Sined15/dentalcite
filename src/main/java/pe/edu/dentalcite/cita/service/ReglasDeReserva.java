package pe.edu.dentalcite.cita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.Period;

/**
 * Umbrales de reserva: la ventana de RN-05 y la cuota de RN-07.
 *
 * <p><strong>RN-17</strong> exige que estos tres números sean «configuración
 * externa, ajustable sin recompilar». Antes vivían como constantes compiladas en
 * {@code disponibilidad.service.VentanaDeReserva}, de modo que mover la
 * antelación mínima de dos horas a una obligaba a reconstruir el artefacto.
 * Ahora salen de {@code app.citas.*}, con su valor por defecto en
 * {@code application.yml} y una variable de entorno por delante.
 *
 * <p>Vive en {@code cita.service} y no en {@code config} por dos motivos: la
 * Tabla de reglas de negocio sitúa RN-05 y RN-07 en «servicio de citas», y la
 * regla de cobertura del {@code pom.xml} solo alcanza {@code *.domain} y
 * {@code *.service} — en {@code config} estas reglas escaparían de la puerta de
 * RNF-10.
 *
 * <p>Lo usan dos dominios: el motor de disponibilidad, para no ofrecer franjas
 * que la reserva rechazaría (HU-08), y el servicio de citas, para rechazarlas
 * con un 422 (HU-09).
 */
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

    /** RN-05: la franja empieza antes de la antelación mínima. */
    public boolean demasiadoPronto(OffsetDateTime inicio, OffsetDateTime ahora) {
        return inicio.isBefore(ahora.plus(antelacionMinima));
    }

    /** RN-05: la franja cae más allá del horizonte reservable. */
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

    /** RN-07: máximo de citas activas simultáneas de un mismo paciente. */
    public int maximoActivasPorPaciente() {
        return maximoActivasPorPaciente;
    }
}
