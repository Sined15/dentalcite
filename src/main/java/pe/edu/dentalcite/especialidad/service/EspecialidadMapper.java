package pe.edu.dentalcite.especialidad.service;

import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;
import pe.edu.dentalcite.especialidad.domain.Especialidad;

/**
 * Mapeo de {@link Especialidad} a su DTO de respuesta, reutilizado por
 * TratamientoService y OdontologoService al componer sus propios DTOs
 * (que embeben la especialidad relacionada).
 */
public final class EspecialidadMapper {

    private EspecialidadMapper() {
    }

    public static EspecialidadResponseDTO toResponseDTO(Especialidad entity) {
        return EspecialidadResponseDTO.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .descripcion(entity.getDescripcion())
                .activo(entity.getActivo())
                .build();
    }
}
