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

    /**
     * El nombre se guarda tal como se escribe, solo recortado.
     *
     * <p>Antes se pasaba a mayúsculas, y desde la revisión v4 eso hacía daño de
     * dos maneras. La primera es que el catálogo dejó de ser una tabla interna:
     * el visitante lee estos nombres bajo cada imagen de la galería, y
     * «ODONTOLOGÍA ESTÉTICA» no es como se presenta una clínica. La segunda es
     * peor: {@code V20} y {@code V21} siembran y enlazan las especialidades
     * <strong>por nombre</strong>, así que bastaba con abrir una en la consola de
     * administración y guardarla para renombrarla y romper la semilla, y con ella
     * el guardián de HU-01.
     *
     * <p>La unicidad no dependía de las mayúsculas: la resuelve
     * {@code existsByNombreIgnoreCase}, que sigue igual. Dos especialidades que
     * solo se distingan por la caja siguen siendo la misma.
     */
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

    /**
     * PUT: reemplaza la especialidad completa (Tabla 10 concede {@code U} sobre el
     * catálogo clínico al administrador).
     */
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
