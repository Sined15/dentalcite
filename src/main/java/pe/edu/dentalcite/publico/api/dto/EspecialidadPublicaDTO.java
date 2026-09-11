package pe.edu.dentalcite.publico.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * La especialidad tal como la ve quien no ha iniciado sesión: una imagen y un
 * nombre, nada más.
 *
 * <p>No es {@code EspecialidadResponseDTO} recortado por casualidad. El criterio
 * de HU-06 dice que el visitante «no verá la descripción ni el estado de la
 * especialidad ni ninguna acción de mantenimiento», así que lo que no aparece
 * aquí es lo que la API no publica, en vez de confiar en que el cliente lo
 * oculte.
 */
@Data
@Builder
public class EspecialidadPublicaDTO {
    private UUID id;
    private String nombre;
    private String imagenUrl;
}
