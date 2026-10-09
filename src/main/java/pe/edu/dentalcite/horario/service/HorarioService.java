package pe.edu.dentalcite.horario.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import pe.edu.dentalcite.horario.api.dto.HorarioResponseDTO;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.service.OdontologoOwnershipGuard;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.disponibilidad.service.FranjasCache;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HorarioService {

    private final HorarioAtencionRepository horarioRepository;
    private final OdontologoRepository odontologoRepository;
    private final UsuarioRepository usuarioRepository;
    private final FranjasCache franjasCache;

    private static final Set<String> ROLES_ADMIN = Set.of("SCOPE_ADMINISTRADOR");

    private void verificarPropiedadOdontologo(Odontologo odontologo) {
        OdontologoOwnershipGuard.verificar(usuarioRepository, odontologo, ROLES_ADMIN, false, null);
    }

    @Transactional(readOnly = true)
    public List<HorarioResponseDTO> listarHorariosPorOdontologo(UUID odontologoId) {
        Odontologo odontologo = odontologoRepository.findById(odontologoId)
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado"));
        verificarPropiedadOdontologo(odontologo);
        return horarioRepository.findByOdontologoId(odontologoId).stream()
                .map(HorarioService::mapHorario)
                .toList();
    }

    @Transactional
    public HorarioResponseDTO crearHorario(HorarioAtencion horario, UUID odontologoId) {
        if (!horario.getHoraInicio().isBefore(horario.getHoraFin())) {
            throw new IllegalArgumentException("La hora de inicio debe ser anterior a la hora de fin");
        }

        Odontologo odontologo = odontologoRepository.findById(odontologoId)
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado"));
        
        verificarPropiedadOdontologo(odontologo);

        List<HorarioAtencion> overlaps = horarioRepository.findOverlappingHorarios(
                odontologoId, horario.getDiaSemana(), horario.getHoraInicio(), horario.getHoraFin(), null);
        if (!overlaps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El horario se solapa con uno existente");
        }

        horario.setOdontologo(odontologo);
        HorarioResponseDTO creado = mapHorario(horarioRepository.save(horario));
        franjasCache.invalidarTrasCommit();
        return creado;
    }

    private void verificarPertenece(HorarioAtencion horario, UUID odontologoId) {
        if (!horario.getOdontologo().getId().equals(odontologoId)) {
            throw new ResourceNotFoundException("Horario de atención no encontrado");
        }
    }

    @Transactional
    public void eliminarHorario(UUID odontologoId, UUID id) {
        HorarioAtencion horario = horarioRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Horario de atención no encontrado"));
        verificarPertenece(horario, odontologoId);
        verificarPropiedadOdontologo(horario.getOdontologo());
        horarioRepository.deleteById(id);
        franjasCache.invalidarTrasCommit();
    }

    @Transactional
    public HorarioResponseDTO actualizarHorario(UUID odontologoId, UUID horarioId, HorarioAtencion detalles) {
        if (!detalles.getHoraInicio().isBefore(detalles.getHoraFin())) {
            throw new IllegalArgumentException("La hora de inicio debe ser anterior a la hora de fin");
        }

        HorarioAtencion horario = horarioRepository.findById(horarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Horario de atención no encontrado"));
        verificarPertenece(horario, odontologoId);
        verificarPropiedadOdontologo(horario.getOdontologo());

        List<HorarioAtencion> overlaps = horarioRepository.findOverlappingHorarios(
                horario.getOdontologo().getId(), detalles.getDiaSemana(), detalles.getHoraInicio(), detalles.getHoraFin(), horarioId);
        if (!overlaps.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El horario se solapa con uno existente");
        }

        horario.setDiaSemana(detalles.getDiaSemana());
        horario.setHoraInicio(detalles.getHoraInicio());
        horario.setHoraFin(detalles.getHoraFin());

        HorarioResponseDTO actualizado = mapHorario(horarioRepository.save(horario));
        franjasCache.invalidarTrasCommit();
        return actualizado;
    }

    private static HorarioResponseDTO mapHorario(HorarioAtencion entity) {
        return HorarioResponseDTO.builder()
                .id(entity.getId())
                .odontologoId(entity.getOdontologo() == null ? null : entity.getOdontologo().getId())
                .diaSemana(entity.getDiaSemana())
                .horaInicio(entity.getHoraInicio())
                .horaFin(entity.getHoraFin())
                .build();
    }
}
