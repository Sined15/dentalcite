package pe.edu.dentalcite.especialidad.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadRequestDTO;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EspecialidadService {

    private final EspecialidadRepository especialidadRepository;
    private final TratamientoRepository tratamientoRepository;

    @Transactional(readOnly = true)
    public Page<EspecialidadResponseDTO> listarEspecialidades(Pageable pageable) {
        return especialidadRepository.findAll(pageable).map(EspecialidadMapper::toResponseDTO);
    }

    @Transactional
    public EspecialidadResponseDTO crearEspecialidad(EspecialidadRequestDTO request) {
        String nombre = request.getNombre().trim();
        if (especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            throw new IllegalStateException("La especialidad ya existe.");
        }

        Especialidad especialidad = Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre(nombre.toUpperCase())
                .descripcion(request.getDescripcion())
                .activo(true)
                .build();

        return EspecialidadMapper.toResponseDTO(especialidadRepository.save(especialidad));
    }

    /**
     * PUT: reemplaza la especialidad completa (Tabla 10 concede {@code U} sobre el
     * catálogo clínico al administrador).
     */
    @Transactional
    public EspecialidadResponseDTO actualizarEspecialidad(UUID id, EspecialidadRequestDTO request) {
        Especialidad especialidad = especialidadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada."));

        String nombre = request.getNombre().trim().toUpperCase();
        if (!nombre.equalsIgnoreCase(especialidad.getNombre())
                && especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            throw new IllegalStateException("La especialidad ya existe.");
        }

        especialidad.setNombre(nombre);
        especialidad.setDescripcion(request.getDescripcion());

        return EspecialidadMapper.toResponseDTO(especialidadRepository.save(especialidad));
    }

    /**
     * Baja lógica (Tabla 10, {@code D}). Se rechaza mientras queden tratamientos
     * activos que la exijan: RN-08 obliga a que el odontólogo posea la especialidad
     * del tratamiento, así que dejarla inactiva bajo un tratamiento vigente
     * describiría una oferta que el motor no puede satisfacer.
     */
    @Transactional
    public void darDeBajaEspecialidad(UUID id) {
        Especialidad especialidad = especialidadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada."));

        if (!especialidad.getActivo()) {
            return;
        }

        if (tratamientoRepository.existsByEspecialidadIdAndActivoTrue(id)) {
            throw new IllegalStateException(
                    "No se puede dar de baja una especialidad con tratamientos activos: dé de baja primero esos tratamientos.");
        }

        especialidad.setActivo(false);
    }
}
