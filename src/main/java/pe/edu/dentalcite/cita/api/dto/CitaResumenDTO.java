package pe.edu.dentalcite.cita.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Una fila de la agenda (HU-11 · RF-18): «cada una con su paciente, su hora y su
 * consultorio».
 *
 * <p>No reutiliza {@link CitaResponseDTO} porque las dos vistas responden a
 * preguntas distintas: aquella es el comprobante que ve el paciente al reservar
 * —y por eso no lleva paciente, que es él mismo—, y esta es la lista que ve
 * recepción, donde el paciente es la primera columna. Fundirlas dejaría un campo
 * nulo en cada uso.
 *
 * <p>{@code fecha} y {@code hora} van en hora local de la clínica, la misma que
 * {@code zonaHoraria} declara, y no como el instante UTC que guarda la base.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaResumenDTO {

    private UUID id;
    private String codigo;
    private LocalDate fecha;
    private LocalTime hora;
    private Integer duracionMinutos;
    private String zonaHoraria;
    /** RN-09: CONFIRMADA, ATENDIDA, NO_ASISTIO o CANCELADA. */
    private String estado;
    /** Solo presente en las canceladas (RF-20). */
    private String motivoCancelacion;

    /**
     * Si el paciente titular puede cancelarla ahora por su cuenta (HU-15 ·
     * RN-06).
     *
     * <p>Lo calcula el servidor y no el cliente porque la regla tiene una
     * excepción —la cita que el propio paciente reservó con menos de la
     * antelación de la ventana— que depende de quién la creó y de cuándo, y
     * reimplementarla en el navegador sería tener la misma regla en dos sitios y
     * con dos relojes. El portal solo obedece: pinta el botón cuando esto es
     * cierto.
     *
     * <p>No depende de quién consulta: dice lo que puede hacer el titular, de
     * modo que la agenda de recepción lo trae igual aunque no lo use.
     */
    private Boolean cancelablePorPaciente;

    private Paciente paciente;
    private CitaResponseDTO.Referencia odontologo;
    private CitaResponseDTO.Referencia tratamiento;
    private CitaResponseDTO.Referencia consultorio;

    /**
     * El paciente con su número de historia: recepción identifica por historia
     * clínica, no por el identificador interno de la ficha.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Paciente {
        private UUID fichaId;
        private String nombre;
        private String numeroHistoria;
    }
}
