package pe.edu.dentalcite.publico.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.publico.api.dto.EspecialidadDetalleDTO;
import pe.edu.dentalcite.publico.api.dto.EspecialidadPublicaDTO;
import pe.edu.dentalcite.publico.api.dto.OdontologoPublicoDTO;
import pe.edu.dentalcite.publico.api.dto.TratamientoPublicoDTO;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * El catálogo clínico tal como lo recorre quien todavía no tiene cuenta (HU-06,
 * criterios de la revisión v4).
 *
 * <p>El dominio {@code publico} no tiene entidad propia, como {@code
 * disponibilidad} y {@code paciente}: los datos son de {@code especialidad},
 * {@code tratamiento} y {@code odontologo}, y se leen por sus repositorios.
 *
 * <p><strong>Solo filas activas.</strong> Lo que la clínica dio de baja deja de
 * anunciarse en el acto, sin ningún trabajo extra: es la misma propiedad que en
 * las rutas autenticadas resuelve el administrador a ojo, y aquí la resuelve la
 * consulta.
 *
 * <p><strong>Sin paginación, a propósito.</strong> Son ocho especialidades, ocho
 * tratamientos y siete odontólogos de una clínica de una sede: paginar la portada
 * sería inventar un problema. Si el caso creciera —varias sedes, un catálogo de
 * decenas de tratamientos—, esto es lo primero que hay que revisar.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogoPublicoService {

    private final EspecialidadRepository especialidadRepository;
    private final TratamientoRepository tratamientoRepository;
    private final OdontologoRepository odontologoRepository;

    public List<EspecialidadPublicaDTO> listarEspecialidades() {
        return especialidadRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(CatalogoPublicoService::aPublica)
                .toList();
    }

    public List<TratamientoPublicoDTO> listarTratamientos() {
        return tratamientoRepository.findByActivoTrueOrderByNombreAsc().stream()
                .map(CatalogoPublicoService::aPublico)
                .toList();
    }

    public List<OdontologoPublicoDTO> listarOdontologos() {
        return odontologoRepository.findByActivoTrueOrderByApellidosAscNombresAsc().stream()
                .map(CatalogoPublicoService::aPublico)
                .toList();
    }

    /**
     * La especialidad con sus tratamientos y sus odontólogos.
     *
     * <p>Los odontólogos salen de {@code findActivosConEspecialidad}, que ya
     * existía: es exactamente la pregunta que RN-08 le hace al motor de
     * disponibilidad para resolver «cualquier odontólogo». Escribir aquí una
     * segunda consulta equivalente sería tener la regla con dos respuestas, y la
     * web anunciaría a alguien que el motor no propondría.
     *
     * <p>Una especialidad dada de baja responde 404 y no 403: no hay nada que
     * proteger: simplemente ya no se ofrece.
     */
    public EspecialidadDetalleDTO detalleDeEspecialidad(UUID id) {
        Especialidad especialidad = especialidadRepository.findById(id)
                .filter(Especialidad::getActivo)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No se ofrece ninguna especialidad con ese identificador"));

        List<TratamientoPublicoDTO> tratamientos =
                tratamientoRepository.findByEspecialidadIdAndActivoTrueOrderByNombreAsc(id).stream()
                        .map(CatalogoPublicoService::aPublico)
                        .toList();

        List<OdontologoPublicoDTO> odontologos =
                odontologoRepository.findActivosConEspecialidad(id).stream()
                        .map(CatalogoPublicoService::aPublico)
                        .toList();

        return EspecialidadDetalleDTO.builder()
                .id(especialidad.getId())
                .nombre(especialidad.getNombre())
                .imagenUrl(especialidad.getImagenUrl())
                .tratamientos(tratamientos)
                .odontologos(odontologos)
                .build();
    }

    private static EspecialidadPublicaDTO aPublica(Especialidad entity) {
        return EspecialidadPublicaDTO.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .imagenUrl(entity.getImagenUrl())
                .build();
    }

    private static TratamientoPublicoDTO aPublico(Tratamiento entity) {
        return TratamientoPublicoDTO.builder()
                .id(entity.getId())
                .nombre(entity.getNombre())
                .descripcion(entity.getDescripcion())
                .duracionMinutos(entity.getDuracionMinutos())
                .imagenUrl(entity.getImagenUrl())
                .especialidadId(entity.getEspecialidad().getId())
                .especialidadNombre(entity.getEspecialidad().getNombre())
                .build();
    }

    private static OdontologoPublicoDTO aPublico(Odontologo entity) {
        return OdontologoPublicoDTO.builder()
                .id(entity.getId())
                .nombres(entity.getNombres())
                .apellidos(entity.getApellidos())
                .cop(entity.getCop())
                .imagenUrl(entity.getImagenUrl())
                .especialidades(entity.getEspecialidades().stream()
                        .map(Especialidad::getNombre)
                        .sorted(Comparator.naturalOrder())
                        .toList())
                .build();
    }
}
