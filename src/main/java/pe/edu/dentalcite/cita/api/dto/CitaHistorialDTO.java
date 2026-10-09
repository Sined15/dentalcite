package pe.edu.dentalcite.cita.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaHistorialDTO {

    private UUID id;
    private String estadoAnterior;
    private String estadoNuevo;
    private String motivo;
    private OffsetDateTime ocurridoEn;
    private Responsable responsable;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Responsable {
        private UUID id;
        private String nombre;
        private String correo;
        private String rol;
    }
}
