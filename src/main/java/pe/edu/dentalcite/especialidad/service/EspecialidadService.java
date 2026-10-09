package pe.edu.dentalcite.especialidad.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadRequestDTO;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.common.api.Ordenacion;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EspecialidadService {

    
    private static final Set<String> ORDENABLES = Set.of("nombre", "activo", "creadoEn");

    private final EspecialidadRepository especialidadRepository;
    private final TratamientoRepository tratamientoRepository;

    @Transactional(readOnly = true)
    public Page<EspecialidadResponseDTO> listarEspecialidades(Pageable pageable) {
        return especialidadRepository.findAll(Ordenacion.cribar(pageable, ORDENABLES, Sort.unsorted()))
                .map(EspecialidadMapper::toResponseDTO);
    }

    @Transactional
    public EspecialidadResponseDTO crearEspecialidad(EspecialidadRequestDTO request) {
        String nombre = request.getNombre().trim();
        if (especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            throw new IllegalStateException("La especialidad ya existe.");
        }

        Especialidad especialidad = Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre(nombre)
                .descripcion(request.getDescripcion())
                .imagenUrl(request.getImagenUrl())
                .activo(true)
                .build();

        return EspecialidadMapper.toResponseDTO(especialidadRepository.save(especialidad));
    }


    @Transactional
    public EspecialidadResponseDTO actualizarEspecialidad(UUID id, EspecialidadRequestDTO request) {
        Especialidad especialidad = especialidadRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada."));

        String nombre = request.getNombre().trim();
        if (!nombre.equalsIgnoreCase(especialidad.getNombre())
                && especialidadRepository.existsByNombreIgnoreCase(nombre)) {
            throw new IllegalStateException("La especialidad ya existe.");
        }

        especialidad.setNombre(nombre);
        especialidad.setDescripcion(request.getDescripcion());
        especialidad.setImagenUrl(request.getImagenUrl());

        return EspecialidadMapper.toResponseDTO(especialidadRepository.save(especialidad));
    }

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
