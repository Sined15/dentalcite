package pe.edu.dentalcite.cita.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una transicion de la cita (HU-11 · RF-21): «con fecha, usuario responsable y
 * motivo».
 *
 * <p>{@code ocurridoEn} viaja como instante con desfase, no como fecha y hora
 * locales sueltas: una bitacora es un registro de auditoria y su valor esta en
 * ser inequivoca, no en ser comoda de leer. El resto de la API usa hora local de
 * clinica porque describe una agenda, que es otra cosa.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaHistorialDTO {

    private UUID id;
    /** Nulo en el alta: antes de nacer, la cita no tenia estado. */
    private String estadoAnterior;
    private String estadoNuevo;
    private String motivo;
    private OffsetDateTime ocurridoEn;
    /** Nulo cuando la transicion no la origino una persona identificada. */
    private Responsable responsable;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Responsable {
        private UUID id;
        private String nombre;
        private String correo;
        /** El rol con el que actuo, que es lo que hace auditable la transicion. */
        private String rol;
    }
}
