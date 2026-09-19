package pe.edu.dentalcite.plan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pe.edu.dentalcite.recomendacion.api.dto.RecomendacionDTO;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un plan de tratamiento con sus sesiones (HU-17 · RF-23).
 *
 * <p><strong>El avance viaja, pero no se guarda.</strong> {@link #avance} se
 * cuenta sobre las sesiones —cuántas ocupa una cita atendida— al construir cada
 * respuesta, y no hay columna de la que leerlo: si la hubiera, sería un dato más
 * que mantener de acuerdo con ellas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponseDTO {

    private UUID id;
    private Referencia paciente;
    private Referencia tratamiento;
    private Referencia odontologo;
    private Integer sesionesPrevistas;
    /** RN-12: la baja logica. Falso desde que se suspende. */
    private Boolean activo;
    /** Solo presente en los suspendidos. */
    private String motivoSuspension;
    private OffsetDateTime creadoEn;
    /** Cuántas sesiones se han cumplido y cuántas faltan, contado sobre {@link #sesiones}. */
    private Avance avance;
    /** Numeradas desde 1. Las ocupadas traen la cita que las ocupa. */
    private List<Sesion> sesiones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Avance {
        /** Sesiones que ocupa una cita atendida, estén cerradas o no. */
        private long completadas;
        /** Sesiones que aún esperan su cita. */
        private long pendientes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Referencia {
        private UUID id;
        private String nombre;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sesion {
        private UUID id;
        private Integer numero;
        /** PENDIENTE, ATENDIDA o CERRADA (RN-15). */
        private String estado;
        /** La cita atendida que la ocupa; nula mientras la sesión está pendiente. */
        private CitaEnlazada cita;

        /**
         * Los cuidados indicados al cerrarla. Vacía mientras no se ha cerrado, y
         * nunca nula: una lista ausente y una vacía se pintan igual, y quien la
         * recorre no tiene que distinguirlas.
         */
        private List<RecomendacionDTO> recomendaciones;

        /** La fecha sugerida del próximo control; nula hasta que la sesión se cierra. */
        private LocalDate proximoControl;

        /** El cuidado escrito a mano, si lo hubo. */
        private String observacion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitaEnlazada {
        private UUID id;
        private String codigo;
        /**
         * Instante con desfase, igual que {@link PlanResponseDTO#creadoEn}: la
         * fecha que se lee en pantalla la pone el cliente en la zona de la clínica.
         */
        private OffsetDateTime inicio;
    }
}
