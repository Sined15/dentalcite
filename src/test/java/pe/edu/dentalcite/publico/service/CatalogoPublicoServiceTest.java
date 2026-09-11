package pe.edu.dentalcite.publico.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.publico.api.dto.EspecialidadDetalleDTO;
import pe.edu.dentalcite.publico.api.dto.OdontologoPublicoDTO;
import pe.edu.dentalcite.publico.api.dto.TratamientoPublicoDTO;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CatalogoPublicoServiceTest {

    @Mock private EspecialidadRepository especialidadRepository;
    @Mock private TratamientoRepository tratamientoRepository;
    @Mock private OdontologoRepository odontologoRepository;

    @InjectMocks private CatalogoPublicoService servicio;

    private Especialidad especialidad(String nombre, boolean activo) {
        return Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre(nombre)
                .descripcion("descripción interna")
                .imagenUrl("/img/catalogo/" + nombre + ".svg")
                .activo(activo)
                .build();
    }

    private Tratamiento tratamiento(Especialidad especialidad) {
        return Tratamiento.builder()
                .id(UUID.randomUUID())
                .codigo("TRT-999")
                .nombre("Profilaxis")
                .descripcion("Limpieza")
                .duracionMinutos(45)
                .imagenUrl("/img/catalogo/profilaxis.svg")
                .especialidad(especialidad)
                .activo(true)
                .build();
    }

    private Odontologo odontologo(Especialidad... especialidades) {
        return Odontologo.builder()
                .id(UUID.randomUUID())
                .cop("COP-10001")
                .nombres("Luis")
                .apellidos("Pérez")
                .ficha(Ficha.builder().id(UUID.randomUUID()).build())
                .especialidades(new LinkedHashSet<>(List.of(especialidades)))
                .imagenUrl("/img/catalogo/odontologo.svg")
                .activo(true)
                .build();
    }

    @Test
    void listarEspecialidades_devuelveSoloNombreEImagen() {
        Especialidad ortodoncia = especialidad("Ortodoncia", true);
        when(especialidadRepository.findByActivoTrueOrderByNombreAsc()).thenReturn(List.of(ortodoncia));

        var resultado = servicio.listarEspecialidades();

        assertEquals(1, resultado.size());
        assertEquals("Ortodoncia", resultado.get(0).getNombre());
        assertEquals("/img/catalogo/Ortodoncia.svg", resultado.get(0).getImagenUrl());
    }

    @Test
    void listarTratamientos_llevaLaEspecialidadParaPoderAgruparlos() {
        Especialidad general = especialidad("Odontología General", true);
        when(tratamientoRepository.findByActivoTrueOrderByNombreAsc())
                .thenReturn(List.of(tratamiento(general)));

        TratamientoPublicoDTO dto = servicio.listarTratamientos().get(0);

        assertEquals("Profilaxis", dto.getNombre());
        assertEquals(45, dto.getDuracionMinutos());
        assertEquals(general.getId(), dto.getEspecialidadId());
        assertEquals("Odontología General", dto.getEspecialidadNombre());
    }

    @Test
    void listarOdontologos_ordenaSusEspecialidadesYNoExponeLaFicha() {
        Especialidad ortodoncia = especialidad("Ortodoncia", true);
        Especialidad general = especialidad("Odontología General", true);
        when(odontologoRepository.findByActivoTrueOrderByApellidosAscNombresAsc())
                .thenReturn(List.of(odontologo(ortodoncia, general)));

        OdontologoPublicoDTO dto = servicio.listarOdontologos().get(0);

        assertEquals("COP-10001", dto.getCop());
        assertEquals(List.of("Odontología General", "Ortodoncia"), dto.getEspecialidades());
    }

    @Test
    void detalleDeEspecialidad_componeTratamientosYOdontologosDeEsaEspecialidad() {
        Especialidad endodoncia = especialidad("Endodoncia", true);
        UUID id = endodoncia.getId();
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(endodoncia));
        when(tratamientoRepository.findByEspecialidadIdAndActivoTrueOrderByNombreAsc(id))
                .thenReturn(List.of(tratamiento(endodoncia)));
        when(odontologoRepository.findActivosConEspecialidad(id))
                .thenReturn(List.of(odontologo(endodoncia)));

        EspecialidadDetalleDTO dto = servicio.detalleDeEspecialidad(id);

        assertEquals("Endodoncia", dto.getNombre());
        assertEquals(1, dto.getTratamientos().size());
        assertEquals(1, dto.getOdontologos().size());
        assertTrue(dto.getOdontologos().get(0).getEspecialidades().contains("Endodoncia"));
    }

    /**
     * Los odontólogos del detalle salen de la misma consulta que RN-08 usa para
     * resolver «cualquier odontólogo» en el motor de disponibilidad. Si aquí se
     * escribiera otra, la web podría anunciar a alguien que el motor no propone.
     */
    @Test
    void detalleDeEspecialidad_preguntaPorLosOdontologosComoLoHaceRn08() {
        Especialidad endodoncia = especialidad("Endodoncia", true);
        UUID id = endodoncia.getId();
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(endodoncia));
        when(tratamientoRepository.findByEspecialidadIdAndActivoTrueOrderByNombreAsc(id))
                .thenReturn(List.of());
        when(odontologoRepository.findActivosConEspecialidad(id)).thenReturn(List.of());

        servicio.detalleDeEspecialidad(id);

        verify(odontologoRepository).findActivosConEspecialidad(id);
        verify(odontologoRepository, never()).findByActivoTrueOrderByApellidosAscNombresAsc();
    }

    @Test
    void detalleDeEspecialidad_dadaDeBaja_lanzaNotFound() {
        Especialidad retirada = especialidad("Retirada", false);
        UUID id = retirada.getId();
        when(especialidadRepository.findById(id)).thenReturn(Optional.of(retirada));

        assertThrows(ResourceNotFoundException.class, () -> servicio.detalleDeEspecialidad(id));
        verify(tratamientoRepository, never()).findByEspecialidadIdAndActivoTrueOrderByNombreAsc(any());
    }

    @Test
    void detalleDeEspecialidad_inexistente_lanzaNotFound() {
        UUID id = UUID.randomUUID();
        when(especialidadRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> servicio.detalleDeEspecialidad(id));
    }
}
