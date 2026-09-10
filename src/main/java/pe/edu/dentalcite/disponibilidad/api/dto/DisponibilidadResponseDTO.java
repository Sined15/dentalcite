package pe.edu.dentalcite.disponibilidad.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Respuesta del motor de disponibilidad (HU-08, RF-13).
 *
 * <p>Se agrupa por odontólogo en vez de por franja: es la forma más compacta para
 * un rango de catorce días, y el portal arma «cualquier odontólogo» uniendo las
 * listas. La agrupación visual en pasos de treinta minutos que pide el criterio de
 * aceptación es del cliente, no del servidor: aquí la granularidad es la del motor,
 * quince minutos (RN-04).
 *
 * <p>Las horas son locales de la clínica; {@code zonaHoraria} las ancla para que el
 * cliente no tenga que suponerla.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisponibilidadResponseDTO {
    private UUID tratamientoId;
    private Integer duracionMinutos;
    private LocalDate desde;
    private LocalDate hasta;
    private String zonaHoraria;
    private List<AgendaOdontologoDTO> odontologos;
}
