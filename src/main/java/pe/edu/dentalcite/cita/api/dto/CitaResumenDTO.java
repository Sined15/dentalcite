package pe.edu.dentalcite.cita.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

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
    private String estado;
    private String motivoCancelacion;

    private Boolean cancelablePorPaciente;

    private Paciente paciente;
    private CitaResponseDTO.Referencia odontologo;
    private CitaResponseDTO.Referencia tratamiento;
    private CitaResponseDTO.Referencia consultorio;

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
