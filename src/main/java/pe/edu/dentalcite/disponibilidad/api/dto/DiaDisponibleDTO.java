package pe.edu.dentalcite.disponibilidad.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiaDisponibleDTO {
    private LocalDate fecha;
    private List<LocalTime> inicios;
}
