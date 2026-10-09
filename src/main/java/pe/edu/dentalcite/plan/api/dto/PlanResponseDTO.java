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
    private Boolean activo;
    private String motivoSuspension;
    private OffsetDateTime creadoEn;
    private Avance avance;
    private List<Sesion> sesiones;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Avance {
        private long completadas;
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
        private String estado;
        private CitaEnlazada cita;

        private List<RecomendacionDTO> recomendaciones;

        private LocalDate proximoControl;

        private String observacion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CitaEnlazada {
        private UUID id;
        private String codigo;
        private OffsetDateTime inicio;
    }
}
