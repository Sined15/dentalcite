package pe.edu.dentalcite.odontologo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.especialidad.service.EspecialidadMapper;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.api.dto.OdontologoRequestDTO;
import pe.edu.dentalcite.odontologo.api.dto.OdontologoResponseDTO;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OdontologoService {

    private static final String ROL_ODONTOLOGO = "ODONTOLOGO";

    private final OdontologoRepository odontologoRepository;
    private final FichaRepository fichaRepository;
    private final EspecialidadRepository especialidadRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional(readOnly = true)
    public Page<OdontologoResponseDTO> listarOdontologos(Pageable pageable) {
        return odontologoRepository.findAll(pageable).map(this::mapOdontologo);
    }

    @Transactional
    public OdontologoResponseDTO registrarOdontologo(OdontologoRequestDTO request) {
        String cop = request.getCop().trim();
        if (odontologoRepository.existsByCop(cop)) {
            throw new IllegalStateException("Ya existe un odontólogo con ese COP.");
        }

        if (odontologoRepository.existsByFichaId(request.getFichaId())) {
            throw new IllegalStateException("Esta ficha de usuario ya está asignada a otro odontólogo (RN-11).");
        }

        Ficha ficha = fichaRepository.findById(request.getFichaId())
                .orElseThrow(() -> new ResourceNotFoundException("Ficha de usuario no encontrada."));

        // RF-10 / RN-11: el odontólogo se vincula a una única cuenta *de rol
        // ODONTOLOGO*. Sin esta comprobación se podía registrar un odontólogo contra
        // la ficha de un paciente, o contra una ficha sin cuenta alguna, y esa cuenta
        // nunca resolvería a su identificador para las operaciones marcadas «propio».
        Usuario cuenta = usuarioRepository.findByFichaId(ficha.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "La ficha no tiene una cuenta asociada: primero cree la cuenta de rol ODONTOLOGO (RN-11)."));

        if (!ROL_ODONTOLOGO.equals(cuenta.getRol())) {
            throw new IllegalStateException(
                    "La cuenta vinculada debe tener rol ODONTOLOGO, pero tiene " + cuenta.getRol() + " (RN-11).");
        }

        Set<Especialidad> especialidades = resolverEspecialidades(request.getEspecialidadesIds());

        Odontologo odontologo = Odontologo.builder()
                .id(UUID.randomUUID())
                .cop(cop)
                .nombres(request.getNombres().trim())
                .apellidos(request.getApellidos().trim())
                .ficha(ficha)
                .especialidades(especialidades)
                .activo(true)
                .build();

        return mapOdontologo(odontologoRepository.save(odontologo));
    }

    /**
     * PUT: reemplaza el odontólogo completo (Tabla 10 concede {@code U} sobre el
     * catálogo clínico al administrador). Revalida la colegiatura y las
     * especialidades igual que el alta. No toca la marca de actividad —darlo de
     * baja es {@link #darDeBajaOdontologo(UUID)}, que además comprueba RN-12—, en
     * la misma línea que {@code TratamientoService.actualizarTratamiento}.
     */
    @Transactional
    public OdontologoResponseDTO actualizarOdontologo(UUID id, OdontologoRequestDTO request) {
        Odontologo odontologo = odontologoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado."));

        String cop = request.getCop().trim();
        if (!cop.equals(odontologo.getCop()) && odontologoRepository.existsByCop(cop)) {
            throw new IllegalStateException("Ya existe un odontólogo con ese COP.");
        }

        // RN-11: la ficha no es un dato descriptivo más. Repuntarla trasladaría a
        // otra persona la agenda del profesional y las operaciones marcadas
        // «propio», que resuelven precisamente por este vínculo. El reemplazo la
        // exige idéntica en vez de aceptar el cambio en silencio.
        if (!odontologo.getFicha().getId().equals(request.getFichaId())) {
            throw new IllegalStateException(
                    "La ficha vinculada a un odontólogo no se puede cambiar (RN-11).");
        }

        odontologo.setCop(cop);
        odontologo.setNombres(request.getNombres().trim());
        odontologo.setApellidos(request.getApellidos().trim());
        odontologo.setEspecialidades(resolverEspecialidades(request.getEspecialidadesIds()));

        return mapOdontologo(odontologoRepository.save(odontologo));
    }

    /** RF-10: «con su colegiatura y especialidades»; todas deben existir. */
    private Set<Especialidad> resolverEspecialidades(Set<UUID> especialidadesIds) {
        Set<Especialidad> especialidades = new HashSet<>(especialidadRepository.findAllById(especialidadesIds));
        if (especialidades.size() != especialidadesIds.size()) {
            throw new IllegalArgumentException("Una o más especialidades no son válidas.");
        }
        return especialidades;
    }

    @Transactional
    public void darDeBajaOdontologo(UUID id) {
        Odontologo odontologo = odontologoRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado."));

        if (!odontologo.getActivo()) {
            return;
        }

        if (odontologoRepository.hasCitasActivas(id)) {
            throw new IllegalStateException("No se puede dar de baja un odontólogo con citas activas (RN-12).");
        }

        odontologo.setActivo(false);
    }

    private OdontologoResponseDTO mapOdontologo(Odontologo entity) {
        return OdontologoResponseDTO.builder()
                .id(entity.getId())
                .cop(entity.getCop())
                .nombres(entity.getNombres())
                .apellidos(entity.getApellidos())
                .fichaId(entity.getFicha().getId())
                .activo(entity.getActivo())
                .especialidades(
                        entity.getEspecialidades().stream()
                                .map(EspecialidadMapper::toResponseDTO)
                                .collect(Collectors.toSet()))
                .build();
    }
}
