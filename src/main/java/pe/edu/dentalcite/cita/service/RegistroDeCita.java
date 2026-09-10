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

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * El INSERT de la cita, aislado en su propia transacción (HU-10).
 *
 * <p>Existe por una razón concreta y no por gusto de separar: cuando la
 * restricción de exclusión rechaza el INSERT, la transacción queda marcada como
 * <em>rollback-only</em> y ya no admite nada más. El reintento con otro
 * consultorio que pide RF-16 necesita, por tanto, una transacción nueva por
 * intento, de ahí {@code REQUIRES_NEW}.
 *
 * <p>Y va en su propio <em>bean</em> porque una llamada interna no pasaría por el
 * proxy de Spring: {@code this.crear(...)} desde {@code CitaService} ignoraría la
 * propagación en silencio y volveríamos al mismo problema sin que nada avisara.
 *
 * <p>Recibe identificadores y no entidades: quien llama corre fuera de toda
 * transacción, así que sus entidades están desligadas y sus proxies no
 * sobrevivirían al salto de sesión. Aquí se resuelven con {@code getReferenceById},
 * que no hace ninguna consulta extra —solo hace falta la clave ajena.
 */
@Component
@RequiredArgsConstructor
public class RegistroDeCita {

    private final CitaRepository citaRepository;
    private final FichaRepository fichaRepository;
    private final OdontologoRepository odontologoRepository;
    private final TratamientoRepository tratamientoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final FranjasCache franjasCache;

    /**
     * @throws org.springframework.dao.DataIntegrityViolationException si otra
     *         reserva se adelantó. Quien llama decide si reintentar, según qué
     *         restricción haya saltado ({@link ConflictoDeSolape}).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cita crear(UUID fichaId, UUID odontologoId, UUID tratamientoId, UUID consultorioId,
            OffsetDateTime inicio, OffsetDateTime fin) {

        // `saveAndFlush` y no `save`: sin el flush explícito, la restricción de
        // exclusión saltaría al hacer commit —fuera del try/catch de quien
        // llama— y el reintento de RF-16 nunca llegaría a ocurrir.
        Cita cita = citaRepository.saveAndFlush(Cita.builder()
                .codigo(String.format("CIT-%06d", citaRepository.getNextCodigoCita()))
                .ficha(fichaRepository.getReferenceById(fichaId))
                .odontologo(odontologoRepository.getReferenceById(odontologoId))
                .tratamiento(tratamientoRepository.getReferenceById(tratamientoId))
                .consultorio(consultorioRepository.getReferenceById(consultorioId))
                .inicio(inicio)
                .fin(fin)
                .estado(Cita.ESTADO_CONFIRMADA)
                .build());

        // El alta NO se escribe en `citas_historial`, y no es un olvido.
        //
        // Se intentó: la bitácora quedaba más completa, pero la fila hija toma un
        // bloqueo sobre la fila de `citas` recién insertada y lo mantiene hasta el
        // commit, dentro de la misma transacción que sostiene la comprobación de
        // la restricción de exclusión. Con varias reservas disputando el pool de
        // consultorios, eso cierra un ciclo de espera y PostgreSQL aborta una de
        // ellas con «deadlock detected» —un error que no es ni 201 ni 409, y que
        // rompía el criterio 2 de HU-10 bajo carga.
        //
        // No se pierde nada: el alta es deducible de `citas.creado_en`, y lo que
        // RF-21 necesita registrar es quién ejerce una transición sobre una cita
        // —cancelarla, cerrarla—, que es precisamente lo que no se puede deducir.
        // Esas sí van a la bitácora, y ninguna corre en un camino disputado.

        // La franja acaba de dejar de estar libre: sin esto la caché la seguiría
        // ofreciendo hasta que caducase su TTL.
        franjasCache.invalidarTrasCommit();
        return cita;
    }
}
