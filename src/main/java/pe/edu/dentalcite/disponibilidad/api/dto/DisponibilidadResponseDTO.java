package pe.edu.dentalcite.disponibilidad.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
