package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import pe.edu.dentalcite.cita.api.dto.CitaRequestDTO;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ReglaIncumplidaException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.disponibilidad.api.dto.DisponibilidadResponseDTO;
import pe.edu.dentalcite.disponibilidad.service.DisponibilidadService;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class CitaService {

    private static final int REINTENTOS_TRAS_INTERBLOQUEO = 1;

    private final CitaRepository citaRepository;
    private final AutorDeLaReserva autorDeLaReserva;
    private final TratamientoRepository tratamientoRepository;
    private final OdontologoRepository odontologoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final DisponibilidadService disponibilidadService;
    private final BloqueoDeFranja bloqueoDeFranja;
    private final RegistroDeCita registroDeCita;
    private final ReglasDeReserva reglas;
    private final ZoneId zona;

    public CitaService(CitaRepository citaRepository,
            AutorDeLaReserva autorDeLaReserva,
            TratamientoRepository tratamientoRepository,
            OdontologoRepository odontologoRepository,
            ConsultorioRepository consultorioRepository,
            DisponibilidadService disponibilidadService,
            BloqueoDeFranja bloqueoDeFranja,
            RegistroDeCita registroDeCita,
            ReglasDeReserva reglas,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.autorDeLaReserva = autorDeLaReserva;
        this.tratamientoRepository = tratamientoRepository;
        this.odontologoRepository = odontologoRepository;
        this.consultorioRepository = consultorioRepository;
        this.disponibilidadService = disponibilidadService;
        this.bloqueoDeFranja = bloqueoDeFranja;
        this.registroDeCita = registroDeCita;
        this.reglas = reglas;
        this.zona = ZoneId.of(zonaHoraria);
    }

    public CitaResponseDTO reservar(CitaRequestDTO peticion) {
        AutorDeLaReserva.Reserva autor = autorDeLaReserva.resolver(peticion.getPacienteId());
        UUID fichaId = autor.fichaId();

        Tratamiento tratamiento = tratamientoRepository.findWithEspecialidadById(peticion.getTratamientoId())
                .filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado"));

        Odontologo odontologo = odontologoRepository.findConEspecialidadesById(peticion.getOdontologoId())
                .filter(o -> Boolean.TRUE.equals(o.getActivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado"));

        OffsetDateTime inicio = peticion.getFecha().atTime(peticion.getHora()).atZone(zona).toOffsetDateTime();
        OffsetDateTime fin = inicio.plusMinutes(tratamiento.getDuracionMinutos());

        verificarVentana(inicio);
        verificarCuota(fichaId);

        BloqueoDeFranja.Adquisicion bloqueo = bloqueoDeFranja.tomar(odontologo.getId(), inicio);
        if (!bloqueo.permiteSeguir()) {
            throw new IllegalStateException(
                    "Otro paciente está reservando esa misma franja en este momento. "
                            + "Vuelve a consultar la disponibilidad.");
        }

        try {
            verificarFranjaOfrecida(peticion);
            return crearReintentandoConsultorio(fichaId, autor.usuarioId(), odontologo, tratamiento,
                    inicio, fin, peticion.getHora());
        } finally {
            bloqueoDeFranja.liberar(bloqueo);
        }
    }

    private CitaResponseDTO crearReintentandoConsultorio(UUID fichaId, UUID autorId,
            Odontologo odontologo, Tratamiento tratamiento, OffsetDateTime inicio,
            OffsetDateTime fin, LocalTime hora) {

        List<UUID> libres = disponibilidadService.consultoriosLibres(inicio, fin);
        if (libres.isEmpty()) {
            throw new IllegalStateException(
                    "No queda ningún consultorio libre en esa franja. Vuelve a consultar la disponibilidad.");
        }

        for (UUID consultorioId : libres) {
            Consultorio consultorio = consultorioRepository.findById(consultorioId).orElse(null);
            if (consultorio == null) {
                continue;
            }
            Cita cita = intentarCrear(fichaId, autorId, odontologo, tratamiento, consultorioId, inicio, fin);
            if (cita != null) {
                return mapear(cita, tratamiento, odontologo, consultorio, hora);
            }
        }

        throw new IllegalStateException(
                "Los consultorios libres se ocuparon mientras confirmabas. "
                        + "Vuelve a consultar la disponibilidad.");
    }

    private Cita intentarCrear(UUID fichaId, UUID autorId, Odontologo odontologo,
            Tratamiento tratamiento, UUID consultorioId, OffsetDateTime inicio, OffsetDateTime fin) {

        for (int intento = 0; intento <= REINTENTOS_TRAS_INTERBLOQUEO; intento++) {
            try {
                return registroDeCita.crear(fichaId, odontologo.getId(), tratamiento.getId(),
                        consultorioId, inicio, fin, autorId);

            } catch (DataAccessException e) {
                if (ConflictoDeSolape.esInterbloqueo(e)) {
                    log.debug("Interbloqueo al insertar en el consultorio {}; intento {}",
                            consultorioId, intento);
                    continue;
                }
                if (!(e instanceof DataIntegrityViolationException violacion)) {
                    throw e;
                }
                if (ConflictoDeSolape.esSolapeDeOdontologo(violacion)) {
                    // Nadie va a liberar esa agenda: reintentar con otro
                    // consultorio no cambiaría nada.
                    throw new IllegalStateException(
                            "Ese odontólogo acaba de ocuparse en esa franja. "
                                    + "Vuelve a consultar la disponibilidad.");
                }
                if (!ConflictoDeSolape.esSolapeDeConsultorio(violacion)) {
                    throw e;
                }
                log.debug("Consultorio {} ocupado entre la consulta y la confirmación; se prueba otro",
                        consultorioId);
                return null;
            }
        }
        return null;
    }

    private void verificarVentana(OffsetDateTime inicio) {
        OffsetDateTime ahora = OffsetDateTime.now(zona);
        if (reglas.demasiadoPronto(inicio, ahora)) {
            throw new ReglaIncumplidaException("La cita debe reservarse con al menos "
                    + reglas.antelacionMinima().toHours() + " horas de antelación (RN-05).");
        }
        if (reglas.demasiadoTarde(inicio, ahora)) {
            throw new ReglaIncumplidaException("No se puede reservar a más de "
                    + reglas.horizonteMaximo().getDays() + " días vista (RN-05).");
        }
    }

    private void verificarCuota(UUID fichaId) {
        long activas = citaRepository.countActivasDeFicha(fichaId);
        if (activas >= reglas.maximoActivasPorPaciente()) {
            throw new IllegalStateException("Ya tienes " + activas
                    + " citas activas, el máximo permitido (RN-07). Cancela una antes de reservar otra.");
        }
    }

    private void verificarFranjaOfrecida(CitaRequestDTO peticion) {
        DisponibilidadResponseDTO oferta = disponibilidadService.consultar(
                peticion.getTratamientoId(), peticion.getOdontologoId(), peticion.getFecha(), peticion.getFecha());

        boolean ofrecida = oferta.getOdontologos().stream()
                .filter(a -> a.getId().equals(peticion.getOdontologoId()))
                .flatMap(a -> a.getDias().stream())
                .filter(d -> d.getFecha().equals(peticion.getFecha()))
                .flatMap(d -> d.getInicios().stream())
                .anyMatch(h -> h.equals(peticion.getHora()));

        if (!ofrecida) {
            throw new IllegalStateException(
                    "Esa franja ya no está disponible. Vuelve a consultar la disponibilidad.");
        }
    }

    private CitaResponseDTO mapear(Cita cita, Tratamiento tratamiento, Odontologo odontologo,
            Consultorio consultorio, LocalTime hora) {
        return CitaResponseDTO.builder()
                .id(cita.getId())
                .codigo(cita.getCodigo())
                .fecha(cita.getInicio().atZoneSameInstant(zona).toLocalDate())
                .hora(hora)
                .duracionMinutos(tratamiento.getDuracionMinutos())
                .zonaHoraria(zona.getId())
                .estado(cita.getEstado())
                .tratamiento(referencia(tratamiento.getId(), tratamiento.getNombre()))
                .odontologo(referencia(odontologo.getId(),
                        (odontologo.getNombres() + " " + odontologo.getApellidos()).trim()))
                .consultorio(referencia(consultorio.getId(), consultorio.getNombre()))
                .build();
    }

    private static CitaResponseDTO.Referencia referencia(UUID id, String nombre) {
        return CitaResponseDTO.Referencia.builder().id(id).nombre(nombre).build();
    }
}
