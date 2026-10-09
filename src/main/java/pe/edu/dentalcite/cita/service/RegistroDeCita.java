package pe.edu.dentalcite.cita.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.disponibilidad.service.FranjasCache;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class RegistroDeCita {

    private final CitaRepository citaRepository;
    private final FichaRepository fichaRepository;
    private final OdontologoRepository odontologoRepository;
    private final TratamientoRepository tratamientoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final UsuarioRepository usuarioRepository;
    private final FranjasCache franjasCache;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cita crear(UUID fichaId, UUID odontologoId, UUID tratamientoId, UUID consultorioId,
            OffsetDateTime inicio, OffsetDateTime fin, UUID creadoPorUsuarioId) {

        Cita cita = citaRepository.saveAndFlush(Cita.builder()
                .codigo(String.format("CIT-%06d", citaRepository.getNextCodigoCita()))
                .ficha(fichaRepository.getReferenceById(fichaId))
                .odontologo(odontologoRepository.getReferenceById(odontologoId))
                .tratamiento(tratamientoRepository.getReferenceById(tratamientoId))
                .consultorio(consultorioRepository.getReferenceById(consultorioId))
                .inicio(inicio)
                .fin(fin)
                .estado(Cita.ESTADO_CONFIRMADA)
                .creadoPor(creadoPorUsuarioId == null ? null
                        : usuarioRepository.getReferenceById(creadoPorUsuarioId))
                .build());

        franjasCache.invalidarTrasCommit();
        return cita;
    }
}
