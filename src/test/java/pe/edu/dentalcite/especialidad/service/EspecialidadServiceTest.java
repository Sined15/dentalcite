package pe.edu.dentalcite.especialidad.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadRequestDTO;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import java.util.UUID;
import java.util.Optional;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EspecialidadServiceTest {

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private pe.edu.dentalcite.tratamiento.repository.TratamientoRepository tratamientoRepository;

    @InjectMocks
    private EspecialidadService especialidadService;

    @Test
    void crearEspecialidad_conNombreDuplicado_arrojaIllegalStateException() {
        EspecialidadRequestDTO req = new EspecialidadRequestDTO();
        req.setNombre("Ortodoncia");

        when(especialidadRepository.existsByNombreIgnoreCase("Ortodoncia")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> especialidadService.crearEspecialidad(req));
        verify(especialidadRepository, never()).save(any());
    }

    @Test
    void crearEspecialidad_conNombreNuevo_creaEspecialidadEnMayusculas() {
        EspecialidadRequestDTO req = new EspecialidadRequestDTO();
        req.setNombre("ortodoncia");
        req.setDescripcion("Corrección dental");

        when(especialidadRepository.existsByNombreIgnoreCase("ortodoncia")).thenReturn(false);
        when(especialidadRepository.save(any(Especialidad.class))).thenAnswer(i -> i.getArgument(0));

        var response = especialidadService.crearEspecialidad(req);

        assertEquals("ORTODONCIA", response.getNombre());
        assertTrue(response.getActivo());
        verify(especialidadRepository).save(any(Especialidad.class));
    }

    @Test
    void actualizarEspecialidad_conNombreNuevo_reemplazaElRecurso() {
        UUID id = UUID.randomUUID();
        Especialidad existente = Especialidad.builder()
                .id(id).nombre("ORTODONCIA").activo(true).build();

        when(especialidadRepository.findById(id)).thenReturn(Optional.of(existente));
        when(especialidadRepository.existsByNombreIgnoreCase("ENDODONCIA")).thenReturn(false);
        when(especialidadRepository.save(any(Especialidad.class))).thenAnswer(i -> i.getArgument(0));

        EspecialidadRequestDTO req = new EspecialidadRequestDTO();
        req.setNombre("Endodoncia");
        req.setDescripcion("Tratamiento de conductos");

        EspecialidadResponseDTO res = especialidadService.actualizarEspecialidad(id, req);

        assertEquals("ENDODONCIA", res.getNombre());
    }

    @Test
    void actualizarEspecialidad_conservandoSuPropioNombre_noLoTomaComoDuplicado() {
        UUID id = UUID.randomUUID();
        Especialidad existente = Especialidad.builder()
                .id(id).nombre("ORTODONCIA").activo(true).build();

        when(especialidadRepository.findById(id)).thenReturn(Optional.of(existente));
        when(especialidadRepository.save(any(Especialidad.class))).thenAnswer(i -> i.getArgument(0));

        EspecialidadRequestDTO req = new EspecialidadRequestDTO();
        req.setNombre("Ortodoncia");
        req.setDescripcion("Descripcion nueva");

        EspecialidadResponseDTO res = especialidadService.actualizarEspecialidad(id, req);

        assertEquals("ORTODONCIA", res.getNombre());
        verify(especialidadRepository, never()).existsByNombreIgnoreCase(any());
    }

    @Test
    void actualizarEspecialidad_conNombreDeOtra_arrojaConflicto() {
        UUID id = UUID.randomUUID();
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(
                Especialidad.builder().id(id).nombre("ORTODONCIA").activo(true).build()));
        when(especialidadRepository.existsByNombreIgnoreCase("ENDODONCIA")).thenReturn(true);

        EspecialidadRequestDTO req = new EspecialidadRequestDTO();
        req.setNombre("Endodoncia");

        assertThrows(IllegalStateException.class, () -> especialidadService.actualizarEspecialidad(id, req));
        verify(especialidadRepository, never()).save(any());
    }

    @Test
    void actualizarEspecialidad_conIdInexistente_arrojaResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(especialidadRepository.findById(id)).thenReturn(Optional.empty());

        EspecialidadRequestDTO req = new EspecialidadRequestDTO();
        req.setNombre("Endodoncia");

        assertThrows(ResourceNotFoundException.class, () -> especialidadService.actualizarEspecialidad(id, req));
    }

    @Test
    void darDeBajaEspecialidad_sinTratamientosActivos_laDesactiva() {
        UUID id = UUID.randomUUID();
        Especialidad especialidad = Especialidad.builder().id(id).nombre("ORTODONCIA").activo(true).build();

        when(especialidadRepository.findById(id)).thenReturn(Optional.of(especialidad));
        when(tratamientoRepository.existsByEspecialidadIdAndActivoTrue(id)).thenReturn(false);

        especialidadService.darDeBajaEspecialidad(id);

        assertFalse(especialidad.getActivo());
    }

    @Test
    void darDeBajaEspecialidad_conTratamientosActivos_arrojaConflicto() {
        // RN-08: un tratamiento vigente exige una especialidad vigente.
        UUID id = UUID.randomUUID();
        Especialidad especialidad = Especialidad.builder().id(id).nombre("ORTODONCIA").activo(true).build();

        when(especialidadRepository.findById(id)).thenReturn(Optional.of(especialidad));
        when(tratamientoRepository.existsByEspecialidadIdAndActivoTrue(id)).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> especialidadService.darDeBajaEspecialidad(id));
        assertTrue(especialidad.getActivo());
    }

    @Test
    void darDeBajaEspecialidad_yaInactiva_esIdempotente() {
        UUID id = UUID.randomUUID();
        Especialidad especialidad = Especialidad.builder().id(id).nombre("ORTODONCIA").activo(false).build();
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(especialidad));

        especialidadService.darDeBajaEspecialidad(id);

        verify(tratamientoRepository, never()).existsByEspecialidadIdAndActivoTrue(any());
    }

    @Test
    void darDeBajaEspecialidad_conIdInexistente_arrojaResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(especialidadRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> especialidadService.darDeBajaEspecialidad(id));
    }
}
