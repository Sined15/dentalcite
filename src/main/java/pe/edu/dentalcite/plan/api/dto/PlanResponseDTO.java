package pe.edu.dentalcite.plan.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Un plan de tratamiento con sus sesiones (HU-17 · RF-23).
 *
 * <p><strong>No lleva avance.</strong> RN-14 lo deriva de las citas atendidas
 * enlazadas y eso es HU-18, del Sprint 4; anadir aqui un campo a cero seria
 * prometer un dato que todavia no significa nada.
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
    /** Numeradas desde 1. En este Sprint todas nacen PENDIENTE (RN-15). */
    private List<Sesion> sesiones;

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
    }
}
