package pe.edu.dentalcite.cita.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class CitaResponseDTO {

    private UUID id;
    private String codigo;
    private LocalDate fecha;
    private LocalTime hora;
    private Integer duracionMinutos;
    private String zonaHoraria;
    private String estado;

    private Referencia tratamiento;
    private Referencia odontologo;
    private Referencia consultorio;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private SesionEnlazada sesionEnlazada;

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
    public static class SesionEnlazada {
        private UUID planId;
        private Integer numero;
        private String tratamiento;
    }
}
