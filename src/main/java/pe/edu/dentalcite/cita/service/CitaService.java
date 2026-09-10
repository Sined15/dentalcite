package pe.edu.dentalcite.cita.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
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
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Reserva de cita desde el portal (HU-09 · RF-15) con exclusión mutua (HU-10 ·
 * RF-16, RN-01, RN-02).
 *
 * <p>El paciente reserva <strong>para sí mismo</strong>: la ficha sale del token,
 * nunca del cuerpo. Reservar en nombre de otro es HU-14.
 *
 * <p>La comprobación de que la franja existe <em>no se reimplementa aquí</em>: se
 * le pregunta al motor de disponibilidad. Horario, bloqueos, feriados, ocupación y
 * especialidad son reglas de calendario y ya viven en un sitio.
 *
 * <h2>Cómo se evita la doble reserva</h2>
 *
 * Dos capas, y el orden importa:
 *
 * <ol>
 *   <li>{@link BloqueoDeFranja} toma en Redis un bloqueo por odontólogo y franja.
 *       Es lo que impide que dos pacientes elijan al mismo odontólogo a la misma
 *       hora en el mismo instante: el segundo recibe 409 sin llegar a tocar la
 *       base.</li>
 *   <li>Las restricciones de exclusión de PostgreSQL
 *       ({@code V13__exclusion_de_citas.sql}) rechazan el solapamiento pase lo que
 *       pase. <strong>Son la garantía</strong>; Redis solo evita el trabajo
 *       inútil. RNF-12 lo exige así, y por eso con Redis detenido se sigue
 *       creando exactamente una cita.</li>
 * </ol>
 *
 * <p>Cuando la que choca es la restricción del <em>consultorio</em>, la franja
 * sigue siendo del paciente: se reintenta con otro consultorio libre (RF-16). Si
 * la que choca es la del <em>odontólogo</em>, no hay nada que reintentar.
 */
@Slf4j
@Service
public class CitaService {

    private static final String ROL_PACIENTE = "SCOPE_PACIENTE";

    /**
     * Cuántas veces se repite el INSERT sobre el mismo consultorio cuando
     * PostgreSQL aborta la transacción por interbloqueo. Uno basta: el ciclo de
     * espera lo forman dos reservas y al romperlo la otra ya ha terminado.
     */
    private static final int REINTENTOS_TRAS_INTERBLOQUEO = 1;

    private final CitaRepository citaRepository;
    private final UsuarioRepository usuarioRepository;
    private final TratamientoRepository tratamientoRepository;
    private final OdontologoRepository odontologoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final DisponibilidadService disponibilidadService;
    private final BloqueoDeFranja bloqueoDeFranja;
    private final RegistroDeCita registroDeCita;
    private final ReglasDeReserva reglas;
    private final ZoneId zona;

    public CitaService(CitaRepository citaRepository,
            UsuarioRepository usuarioRepository,
            TratamientoRepository tratamientoRepository,
            OdontologoRepository odontologoRepository,
            ConsultorioRepository consultorioRepository,
            DisponibilidadService disponibilidadService,
            BloqueoDeFranja bloqueoDeFranja,
            RegistroDeCita registroDeCita,
            ReglasDeReserva reglas,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.usuarioRepository = usuarioRepository;
        this.tratamientoRepository = tratamientoRepository;
        this.odontologoRepository = odontologoRepository;
        this.consultorioRepository = consultorioRepository;
        this.disponibilidadService = disponibilidadService;
        this.bloqueoDeFranja = bloqueoDeFranja;
        this.registroDeCita = registroDeCita;
        this.reglas = reglas;
        this.zona = ZoneId.of(zonaHoraria);
    }

    /**
     * <strong>No es {@code @Transactional} a propósito.</strong> El reintento de
     * RF-16 necesita una transacción nueva por intento, porque un
     * {@code DataIntegrityViolationException} marca la suya como rollback-only;
     * envolver todo esto en una sola transacción haría imposible reintentar. Cada
     * lectura abre la suya y el INSERT vive en {@link RegistroDeCita}.
     *
     * <p>El orden de las comprobaciones es contrato: RN-05 y RN-07 se evalúan
     * <em>antes</em> de mirar la disponibilidad, porque si no una franja fuera de
     * la ventana saldría como «ya no disponible» en vez de con el 422 que el
     * criterio de aceptación exige.
     */
    public CitaResponseDTO reservar(CitaRequestDTO peticion) {
        UUID fichaId = fichaDelPacienteAutenticado();

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

        // HU-10: nadie más puede estar reservando este odontólogo a esta hora.
        BloqueoDeFranja.Adquisicion bloqueo = bloqueoDeFranja.tomar(odontologo.getId(), inicio);
        if (!bloqueo.permiteSeguir()) {
            throw new IllegalStateException(
                    "Otro paciente está reservando esa misma franja en este momento. "
                            + "Vuelve a consultar la disponibilidad.");
        }

        try {
            verificarFranjaOfrecida(peticion);
            return crearReintentandoConsultorio(fichaId, odontologo, tratamiento, inicio, fin, peticion.getHora());
        } finally {
            bloqueoDeFranja.liberar(bloqueo);
        }
    }

    /**
     * RF-16: «si la franja se ha ocupado entre la consulta y la confirmación, el
     * sistema reintenta con otro consultorio libre y, si no queda ninguno, informa
     * y recalcula la disponibilidad».
     *
     * <p>El bucle está acotado por el número de consultorios de la clínica, así
     * que no puede degenerar. Cada intento va en su propia transacción
     * ({@link RegistroDeCita}); si no, el primer rechazo dejaría la transacción
     * inservible para el segundo intento.
     */
    private CitaResponseDTO crearReintentandoConsultorio(UUID fichaId, Odontologo odontologo,
            Tratamiento tratamiento, OffsetDateTime inicio, OffsetDateTime fin, LocalTime hora) {

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
            Cita cita = intentarCrear(fichaId, odontologo, tratamiento, consultorioId, inicio, fin);
            if (cita != null) {
                return mapear(cita, tratamiento, odontologo, consultorio, hora);
            }
        }

        throw new IllegalStateException(
                "Los consultorios libres se ocuparon mientras confirmabas. "
                        + "Vuelve a consultar la disponibilidad.");
    }

    /**
     * Un intento sobre un consultorio concreto.
     *
     * @return la cita creada, o {@code null} si ese consultorio ya no sirve y hay
     *         que probar el siguiente.
     * @throws IllegalStateException si quien se adelantó fue en la agenda del
     *         odontólogo, donde no hay nada que reintentar.
     */
    private Cita intentarCrear(UUID fichaId, Odontologo odontologo, Tratamiento tratamiento,
            UUID consultorioId, OffsetDateTime inicio, OffsetDateTime fin) {

        for (int intento = 0; intento <= REINTENTOS_TRAS_INTERBLOQUEO; intento++) {
            try {
                return registroDeCita.crear(fichaId, odontologo.getId(), tratamiento.getId(),
                        consultorioId, inicio, fin);

            } catch (DataAccessException e) {
                if (ConflictoDeSolape.esInterbloqueo(e)) {
                    // Carrera perdida, no petición inválida: quien la pierde debe
                    // acabar con su 201 si aún queda sitio, o con un 409 si no,
                    // nunca con un 500 (criterio 2 de HU-10). Se reintenta el
                    // mismo consultorio, porque el interbloqueo no dice qué
                    // restricción lo provocó y descartarlo de entrada podría tirar
                    // el único hueco libre.
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

    /**
     * RNF-04 y HU-14: la ficha sale del token.
     *
     * @return el identificador de la ficha. Se devuelve el id y no la entidad
     *         porque este método corre fuera de toda transacción: la entidad
     *         quedaría desligada de su sesión y su proxy no sobreviviría al salto
     *         a la transacción del INSERT.
     */
    private UUID fichaDelPacienteAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        boolean esPaciente = auth.getAuthorities().stream()
                .anyMatch(a -> ROL_PACIENTE.equals(a.getAuthority()));
        if (!esPaciente) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Solo el paciente reserva para sí mismo; la reserva en nombre de otro no está disponible");
        }

        UUID usuarioId;
        try {
            usuarioId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado");
        }

        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado"));

        if (usuario.getFicha() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta no tiene una ficha clínica asociada");
        }
        // Leer el identificador no inicializa el proxy, así que es seguro fuera de
        // la sesión de persistencia.
        return usuario.getFicha().getId();
    }

    /** RN-05, con los umbrales que RN-17 saca a configuración. */
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

    /** RN-07: la cuota cuenta activas, así que una cita vencida deja de pesar. */
    private void verificarCuota(UUID fichaId) {
        long activas = citaRepository.countActivasDeFicha(fichaId);
        if (activas >= reglas.maximoActivasPorPaciente()) {
            throw new IllegalStateException("Ya tienes " + activas
                    + " citas activas, el máximo permitido (RN-07). Cancela una antes de reservar otra.");
        }
    }

    /**
     * Se le pregunta al motor por ese día y ese odontólogo: si la hora pedida no
     * está entre las que ofrece, no se reserva. Es lo que hace imposible reservar
     * fuera de horario, sobre un bloqueo, en feriado o sin consultorio, sin
     * reimplementar ninguna de esas reglas.
     */
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
