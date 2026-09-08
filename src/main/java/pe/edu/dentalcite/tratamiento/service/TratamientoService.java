package pe.edu.dentalcite.tratamiento.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.especialidad.service.EspecialidadMapper;
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoRequestDTO;
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoResponseDTO;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TratamientoService {

    private final TratamientoRepository tratamientoRepository;
    private final EspecialidadRepository especialidadRepository;

    @Transactional(readOnly = true)
    public Page<TratamientoResponseDTO> listarTratamientos(Pageable pageable) {
        return tratamientoRepository.findAll(pageable).map(this::mapTratamiento);
    }

    /** RN-04: la duración de la cita es la del tratamiento, múltiplo de quince minutos. */
    private void validarDuracion(int duracion) {
        if (duracion < 15 || duracion > 240 || duracion % 15 != 0) {
            throw new IllegalArgumentException("La duración debe ser múltiplo de 15 minutos entre 15 y 240 (RN-04).");
        }
    }

    @Transactional
    public TratamientoResponseDTO crearTratamiento(TratamientoRequestDTO request) {
        validarDuracion(request.getDuracionMinutos());

        String codigo = request.getCodigo().trim();
        if (tratamientoRepository.existsByCodigo(codigo)) {
            throw new IllegalStateException("El código de tratamiento ya existe.");
        }

        Especialidad especialidad = especialidadRepository.findById(request.getEspecialidadId())
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada."));

        Tratamiento tratamiento = Tratamiento.builder()
                .id(UUID.randomUUID())
                .codigo(codigo)
                .nombre(request.getNombre().trim())
                .descripcion(request.getDescripcion())
                .duracionMinutos(request.getDuracionMinutos())
                .especialidad(especialidad)
                .activo(true)
                .build();

        return mapTratamiento(tratamientoRepository.save(tratamiento));
    }

    /**
     * PUT: reemplaza el tratamiento completo (Tabla 10 concede {@code U} sobre el
     * catálogo clínico al administrador). No toca la marca de actividad: darlo de
     * baja es {@link #darDeBajaTratamiento(UUID)}, que además comprueba RN-12.
     */
    @Transactional
    public TratamientoResponseDTO actualizarTratamiento(UUID id, TratamientoRequestDTO request) {
        validarDuracion(request.getDuracionMinutos());

        Tratamiento tratamiento = tratamientoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado."));

        String codigo = request.getCodigo().trim();
        if (!codigo.equals(tratamiento.getCodigo()) && tratamientoRepository.existsByCodigo(codigo)) {
            throw new IllegalStateException("El código de tratamiento ya existe.");
        }

        Especialidad especialidad = especialidadRepository.findById(request.getEspecialidadId())
                .orElseThrow(() -> new ResourceNotFoundException("Especialidad no encontrada."));

        tratamiento.setCodigo(codigo);
        tratamiento.setNombre(request.getNombre().trim());
        tratamiento.setDescripcion(request.getDescripcion());
        tratamiento.setDuracionMinutos(request.getDuracionMinutos());
        tratamiento.setEspecialidad(especialidad);

        return mapTratamiento(tratamientoRepository.save(tratamiento));
    }

    @Transactional
    public void darDeBajaTratamiento(UUID id) {
        Tratamiento tratamiento = tratamientoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado."));

        if (!tratamiento.getActivo()) {
            return;
        }

        if (tratamientoRepository.hasCitasActivas(id)) {
            throw new IllegalStateException("No se puede dar de baja un tratamiento con citas activas (RN-12).");
        }

        tratamiento.setActivo(false);
    }

    private TratamientoResponseDTO mapTratamiento(Tratamiento entity) {
        return TratamientoResponseDTO.builder()
                .id(entity.getId())
                .codigo(entity.getCodigo())
                .nombre(entity.getNombre())
                .descripcion(entity.getDescripcion())
                .duracionMinutos(entity.getDuracionMinutos())
                .especialidad(EspecialidadMapper.toResponseDTO(entity.getEspecialidad()))
                .activo(entity.getActivo())
                .build();
    }
}
