package pe.edu.dentalcite.cita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pe.edu.dentalcite.cita.domain.Cita;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * La ventana de RN-06: hasta cuándo puede el paciente cancelar lo suyo (HU-15 ·
 * RF-19).
 *
 * <p>Hermano de {@link ReglasDeReserva} y por los mismos motivos: la regla
 * aparece en dos sitios —el servicio de cancelación, que la aplica, y la agenda
 * del paciente, que decide si pintar el botón— y tenerla dos veces sería tenerla
 * con dos relojes. El umbral sale de {@code app.citas.antelacion-cancelacion-horas}
 * porque <strong>RN-17</strong> exige que sea «configuración externa, ajustable
 * sin recompilar».
 *
 * <h2>La excepción, que es lo único con enjundia</h2>
 *
 * RN-06 dice: «el paciente cancela hasta veinticuatro horas antes del inicio;
 * después corresponde a la recepción o al administrador. <em>Se exceptúa la cita
 * que él mismo reservó con menos antelación</em>, que puede cancelar mientras se
 * conserve el mínimo de RN-05».
 *
 * <p>La razón de la excepción es que el autoservicio no puede permitir reservar
 * lo que no permite deshacer: RN-05 admite reservar con dos horas de antelación,
 * de modo que sin la excepción una cita reservada para dentro de tres horas
 * nacería ya incancelable por quien acaba de pedirla.
 *
 * <p>«Él mismo la reservó» se resuelve comparando la ficha de quien la creó con
 * la ficha de la cita, y no con el usuario que está pidiendo cancelarla: así la
 * respuesta es la misma se pregunte desde donde se pregunte, que es lo que
 * permite que el listado marque cada fila sin saber quién mira. Una cita
 * anterior a HU-14 no tiene autor, y entonces no es «suya» a estos efectos: solo
 * le vale la ventana de veinticuatro horas.
 */
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

    /**
     * Si el paciente titular puede cancelar esta cita ahora mismo.
     *
     * <p>No comprueba quién pregunta: eso es autorización y vive en
     * {@link CancelacionService}. Aquí solo está la regla de calendario, que es
     * la misma para el listado y para la operación.
     */
    public boolean puedeCancelarElPaciente(Cita cita, OffsetDateTime ahora) {
        if (!Cita.ESTADO_CONFIRMADA.equals(cita.getEstado())) {
            // RN-09 no admite retornos: lo que ya salió de CONFIRMADA no se
            // cancela, y eso es un 409, no un asunto de ventana.
            return false;
        }
        if (!cita.getInicio().isBefore(ahora.plus(antelacionCancelacion))) {
            return true;
        }
        return laReservoElPropioPaciente(cita)
                && seReservoDentroDeLaVentana(cita)
                // «Mientras se conserve el mínimo de RN-05»: a menos de dos horas
                // ya no se cancela por autoservicio ni siendo suya.
                && !reglas.demasiadoPronto(cita.getInicio(), ahora);
    }

    /** El mensaje del 422, que tiene que decir qué hacer y no solo que no. */
    public String motivoDelRechazo() {
        return "Solo puede cancelar por su cuenta hasta " + antelacionCancelacion.toHours()
                + " horas antes del inicio (RN-06). Contacte con la recepción de la clínica"
                + " para cancelar esta cita.";
    }

    public Duration antelacion() {
        return antelacionCancelacion;
    }

    /**
     * La creó la cuenta del propio titular. Se compara por ficha porque es lo que
     * une a una cuenta con su historia clínica (RN-11); una cita que reservó
     * recepción tiene ahí a otra persona, y una anterior a HU-14 no tiene a nadie.
     */
    private static boolean laReservoElPropioPaciente(Cita cita) {
        return cita.getCreadoPor() != null
                && cita.getCreadoPor().getFicha() != null
                && cita.getCreadoPor().getFicha().getId().equals(cita.getFicha().getId());
    }

    /** Se reservó con menos antelación de la que la ventana exige para cancelar. */
    private boolean seReservoDentroDeLaVentana(Cita cita) {
        return cita.getCreadoEn() != null
                && cita.getCreadoEn().isAfter(cita.getInicio().minus(antelacionCancelacion));
    }
}
