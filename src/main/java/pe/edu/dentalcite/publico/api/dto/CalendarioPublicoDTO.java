package pe.edu.dentalcite.publico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Qué días abre la clínica, para que un calendario pueda pintar cerrados los
 * que no.
 *
 * <p>Hasta ahora ese dato no salía de la base: el horario solo se consulta
 * odontólogo a odontólogo y con rol privilegiado, y los feriados no se
 * publicaban. El visitante que pide cita sin cuenta no podía consultar ninguno
 * de los dos, así que su calendario ofrecía domingos y feriados que el motor
 * descarta después, sin explicar por qué.
 *
 * <p>Viaja entero en una sola respuesta, incluidos los días de cada odontólogo,
 * porque el calendario cambia al elegir odontólogo y una consulta por cambio
 * sería un viaje por clic sobre un dato que no se mueve. Son siete odontólogos y
 * un puñado de feriados; si la clínica creciera a varias sedes, esto es lo
 * primero que hay que acotar por rango.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarioPublicoDTO {

    /**
     * Días de la semana en que atiende alguien, de 1 (lunes) a 7 (domingo). Es
     * la unión de los horarios declarados: el día en que no atiende nadie es el
     * día en que la clínica está cerrada.
     */
    private List<Integer> diasDeAtencion;

    /** Días en que no se atiende aunque caigan en día laborable. */
    private List<LocalDate> feriados;

    /**
     * Los días de cada odontólogo por separado. Elegir a uno concreto estrecha
     * el calendario a los suyos: marcar un martes con quien solo atiende jueves
     * devuelve una lista vacía que parece un fallo.
     */
    private List<OdontologoDias> porOdontologo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OdontologoDias {
        private UUID odontologoId;
        private List<Integer> dias;
    }
}
