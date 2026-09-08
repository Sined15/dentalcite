package pe.edu.dentalcite.tratamiento.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoRequestDTO;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoResponseDTO;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TratamientoServiceTest {

    @Mock
    private TratamientoRepository tratamientoRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @InjectMocks
    private TratamientoService tratamientoService;

    private Especialidad especialidad;
    private final UUID especialidadId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        especialidad = Especialidad.builder()
                .id(especialidadId)
                .nombre("ORTODONCIA")
                .activo(true)
                .build();
    }

    @Test
    void crearTratamiento_conDuracionInvalida_arrojaIllegalArgumentException() {
        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TRAT-01");
        req.setNombre("Limpieza");
        req.setDuracionMinutos(20); // No es múltiplo de 15
        req.setEspecialidadId(especialidadId);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> tratamientoService.crearTratamiento(req));
        assertTrue(ex.getMessage().contains("múltiplo de 15"));
    }

    @Test
    void crearTratamiento_conDuracionValida_creaTratamiento() {
        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TRAT-02");
        req.setNombre("Extracción");
        req.setDuracionMinutos(45); // Múltiplo de 15
        req.setEspecialidadId(especialidadId);

        when(tratamientoRepository.existsByCodigo("TRAT-02")).thenReturn(false);
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.of(especialidad));
        when(tratamientoRepository.save(any(Tratamiento.class))).thenAnswer(i -> i.getArgument(0));

        var response = tratamientoService.crearTratamiento(req);

        assertEquals("Extracción", response.getNombre());
        assertEquals(45, response.getDuracionMinutos());
        verify(tratamientoRepository).save(any(Tratamiento.class));
    }

    @Test
    void darDeBajaTratamiento_conCitasActivas_arrojaExcepcion() {
        UUID tratamientoId = UUID.randomUUID();
        Tratamiento tratamiento = new Tratamiento();
        tratamiento.setId(tratamientoId);
        tratamiento.setActivo(true);

        when(tratamientoRepository.findById(tratamientoId)).thenReturn(Optional.of(tratamiento));
        when(tratamientoRepository.hasCitasActivas(tratamientoId)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> tratamientoService.darDeBajaTratamiento(tratamientoId));
        assertTrue(ex.getMessage().contains("citas activas"));
    }

    @Test
    void darDeBajaTratamiento_sinCitasActivas_desactivaTratamiento() {
        UUID tratamientoId = UUID.randomUUID();
        Tratamiento tratamiento = new Tratamiento();
        tratamiento.setId(tratamientoId);
        tratamiento.setActivo(true);

        when(tratamientoRepository.findById(tratamientoId)).thenReturn(Optional.of(tratamiento));
        when(tratamientoRepository.hasCitasActivas(tratamientoId)).thenReturn(false);

        tratamientoService.darDeBajaTratamiento(tratamientoId);

        assertFalse(tratamiento.getActivo());
        verify(tratamientoRepository).findById(tratamientoId);
        verify(tratamientoRepository).hasCitasActivas(tratamientoId);
    }

    @Test
    void actualizarTratamiento_conDatosValidos_reemplazaElRecurso() {
        UUID id = UUID.randomUUID();
        UUID especialidadId = UUID.randomUUID();
        Especialidad especialidad = Especialidad.builder().id(especialidadId).nombre("ORTODONCIA").activo(true).build();
        Tratamiento existente = Tratamiento.builder()
                .id(id).codigo("TR-001").nombre("Limpieza").duracionMinutos(30)
                .especialidad(especialidad).activo(true).build();

        when(tratamientoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.of(especialidad));
        when(tratamientoRepository.save(any(Tratamiento.class))).thenAnswer(i -> i.getArgument(0));

        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TR-002");
        req.setNombre("Limpieza profunda");
        req.setDuracionMinutos(45);
        req.setEspecialidadId(especialidadId);

        TratamientoResponseDTO res = tratamientoService.actualizarTratamiento(id, req);

        assertEquals("TR-002", res.getCodigo());
        assertEquals("Limpieza profunda", res.getNombre());
        assertEquals(45, res.getDuracionMinutos());
        // La baja tiene su propia operacion: actualizar no toca la marca de actividad.
        assertTrue(res.getActivo());
    }

    @Test
    void actualizarTratamiento_conDuracionNoMultiploDe15_arrojaIllegalArgument() {
        // RN-04 se aplica igual al actualizar que al crear.
        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TR-001");
        req.setNombre("Limpieza");
        req.setDuracionMinutos(20);
        req.setEspecialidadId(UUID.randomUUID());

        assertThrows(IllegalArgumentException.class,
                () -> tratamientoService.actualizarTratamiento(UUID.randomUUID(), req));
        verify(tratamientoRepository, never()).save(any());
    }

    @Test
    void actualizarTratamiento_conCodigoDeOtroTratamiento_arrojaConflicto() {
        UUID id = UUID.randomUUID();
        Tratamiento existente = Tratamiento.builder()
                .id(id).codigo("TR-001").nombre("Limpieza").duracionMinutos(30).activo(true).build();

        when(tratamientoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(tratamientoRepository.existsByCodigo("TR-999")).thenReturn(true);

        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TR-999");
        req.setNombre("Limpieza");
        req.setDuracionMinutos(30);
        req.setEspecialidadId(UUID.randomUUID());

        assertThrows(IllegalStateException.class, () -> tratamientoService.actualizarTratamiento(id, req));
        verify(tratamientoRepository, never()).save(any());
    }

    @Test
    void actualizarTratamiento_conservandoSuPropioCodigo_noLoTomaComoDuplicado() {
        UUID id = UUID.randomUUID();
        UUID especialidadId = UUID.randomUUID();
        Especialidad especialidad = Especialidad.builder().id(especialidadId).nombre("ORTODONCIA").activo(true).build();
        Tratamiento existente = Tratamiento.builder()
                .id(id).codigo("TR-001").nombre("Limpieza").duracionMinutos(30)
                .especialidad(especialidad).activo(true).build();

        when(tratamientoRepository.findById(id)).thenReturn(Optional.of(existente));
        when(especialidadRepository.findById(especialidadId)).thenReturn(Optional.of(especialidad));
        when(tratamientoRepository.save(any(Tratamiento.class))).thenAnswer(i -> i.getArgument(0));

        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TR-001");
        req.setNombre("Limpieza dental");
        req.setDuracionMinutos(60);
        req.setEspecialidadId(especialidadId);

        assertEquals("Limpieza dental", tratamientoService.actualizarTratamiento(id, req).getNombre());
        verify(tratamientoRepository, never()).existsByCodigo(any());
    }

    @Test
    void actualizarTratamiento_conIdInexistente_arrojaResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(tratamientoRepository.findById(id)).thenReturn(Optional.empty());

        TratamientoRequestDTO req = new TratamientoRequestDTO();
        req.setCodigo("TR-001");
        req.setNombre("Limpieza");
        req.setDuracionMinutos(30);
        req.setEspecialidadId(UUID.randomUUID());

        assertThrows(ResourceNotFoundException.class, () -> tratamientoService.actualizarTratamiento(id, req));
    }
}
