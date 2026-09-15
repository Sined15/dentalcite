package pe.edu.dentalcite.cita.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * La cita recien creada. Lo que el paciente ve al confirmar (RF-15).
 *
 * <p>Las referencias van reducidas a lo que se muestra en pantalla en lugar de
 * anidar los DTO completos del catalogo: una confirmacion de reserva no necesita
 * la especialidad del tratamiento ni la colegiatura del odontologo, y con
 * {@code open-in-view: false} arrastrarlos obligaria a inicializar proxies que no
 * se van a leer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaResponseDTO {

    private UUID id;
    /** RF-15: el codigo unico que se muestra al confirmar. */
    private String codigo;
    private LocalDate fecha;
    private LocalTime hora;
    private Integer duracionMinutos;
    private String zonaHoraria;
    /** RN-09: toda cita nace CONFIRMADA. */
    private String estado;

    private Referencia tratamiento;
    private Referencia odontologo;
    private Referencia consultorio;

    /**
     * La sesión del plan que ha ocupado esta cita al registrarse como atendida.
     * Solo la trae esa respuesta, y solo cuando de verdad ha ocupado una: en
     * cualquier otro caso el campo no aparece, para que ninguna pantalla lea su
     * ausencia en otra operación como «no tiene plan».
     */
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
        /** El tratamiento del plan, para poder decirlo sin otra consulta. */
        private String tratamiento;
    }
}
