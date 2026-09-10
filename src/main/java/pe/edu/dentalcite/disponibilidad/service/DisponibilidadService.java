package pe.edu.dentalcite.disponibilidad.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;
import pe.edu.dentalcite.bloqueo.repository.BloqueoRepository;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.cita.service.ReglasDeReserva;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.disponibilidad.api.dto.AgendaOdontologoDTO;
import pe.edu.dentalcite.disponibilidad.api.dto.DiaDisponibleDTO;
import pe.edu.dentalcite.disponibilidad.api.dto.DisponibilidadResponseDTO;
import pe.edu.dentalcite.feriado.domain.Feriado;
import pe.edu.dentalcite.feriado.repository.FeriadoRepository;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Motor de disponibilidad (HU-08 · RF-13, RF-14).
 *
 * <p>Orquesta: valida el rango, resuelve el tratamiento y los odontólogos que
 * pueden atenderlo (RN-08), carga la agenda del rango en un número fijo de
 * consultas y delega el cálculo en {@link MotorDeFranjas}. La separación no es
 * decorativa: las reglas de calendario se prueban sin base de datos y el coste de
 * la consulta no crece con el número de días, que es lo que pide RNF-01.
 */
@Slf4j
@Service
public class DisponibilidadService {

    private final TratamientoRepository tratamientoRepository;
    private final OdontologoRepository odontologoRepository;
    private final HorarioAtencionRepository horarioRepository;
    private final CitaRepository citaRepository;
    private final BloqueoRepository bloqueoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final FeriadoRepository feriadoRepository;
    private final FranjasCache franjasCache;
    /** RN-05 con umbrales configurables (RN-17); viaja dentro de la agenda. */
    private final ReglasDeReserva reglas;

    private final ZoneId zona;
    private final int diasMaximos;

    public DisponibilidadService(TratamientoRepository tratamientoRepository,
            OdontologoRepository odontologoRepository,
            HorarioAtencionRepository horarioRepository,
            CitaRepository citaRepository,
            BloqueoRepository bloqueoRepository,
            ConsultorioRepository consultorioRepository,
            FeriadoRepository feriadoRepository,
            FranjasCache franjasCache,
            ReglasDeReserva reglas,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria,
            @Value("${app.disponibilidad.dias-maximos:14}") int diasMaximos) {
        this.tratamientoRepository = tratamientoRepository;
        this.odontologoRepository = odontologoRepository;
        this.horarioRepository = horarioRepository;
        this.citaRepository = citaRepository;
        this.bloqueoRepository = bloqueoRepository;
        this.consultorioRepository = consultorioRepository;
        this.feriadoRepository = feriadoRepository;
        this.franjasCache = franjasCache;
        this.reglas = reglas;
        this.zona = ZoneId.of(zonaHoraria);
        this.diasMaximos = diasMaximos;
    }

    @Transactional(readOnly = true)
    public DisponibilidadResponseDTO consultar(UUID tratamientoId, UUID odontologoId,
            LocalDate desde, LocalDate hasta) {
        validarRango(desde, hasta);

        Tratamiento tratamiento = tratamientoRepository.findWithEspecialidadById(tratamientoId)
                .filter(t -> Boolean.TRUE.equals(t.getActivo()))
                .orElseThrow(() -> new ResourceNotFoundException("Tratamiento no encontrado"));

        String clave = franjasCache.clave(tratamientoId, odontologoId, desde, hasta);
        DisponibilidadResponseDTO cacheada = franjasCache.leer(clave);
        if (cacheada != null) {
            return cacheada;
        }

        List<Odontologo> candidatos = candidatos(tratamiento, odontologoId);
        DisponibilidadResponseDTO respuesta = candidatos.isEmpty()
                ? respuestaVacia(tratamiento, desde, hasta)
                : calcular(tratamiento, candidatos, desde, hasta);

        franjasCache.guardar(clave, respuesta);
        return respuesta;
    }

    /**
     * El rango consultable se acota porque RNF-01 fija su umbral sobre catorce
     * días: aceptar un año convertiría una consulta de portal en un barrido. El
     * límite es configuración, no constante, porque la contingencia de R-01
     * consiste precisamente en bajarlo de catorce a siete.
     */
    private void validarRango(LocalDate desde, LocalDate hasta) {
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha inicial debe ser anterior o igual a la final");
        }
        long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
        if (dias > diasMaximos) {
            throw new IllegalArgumentException(
                    "El rango consultable es de " + diasMaximos + " días como máximo; se pidieron " + dias);
        }
    }

    /**
     * RN-08: el odontólogo asignado debe poseer la especialidad que exige el
     * tratamiento. Sin {@code odontologoId} —el «cualquier odontólogo» del
     * portal— se proponen todos los activos que la tengan; con él, se comprueba
     * que la tenga. Que no la tenga no es un error del cliente: es una agenda sin
     * franjas, y así se responde.
     */
    private List<Odontologo> candidatos(Tratamiento tratamiento, UUID odontologoId) {
        UUID especialidadId = tratamiento.getEspecialidad().getId();

        if (odontologoId == null) {
            return odontologoRepository.findActivosConEspecialidad(especialidadId);
        }

        Odontologo odontologo = odontologoRepository.findConEspecialidadesById(odontologoId)
                .orElseThrow(() -> new ResourceNotFoundException("Odontólogo no encontrado"));

        boolean apto = Boolean.TRUE.equals(odontologo.getActivo())
                && odontologo.getEspecialidades().stream().anyMatch(e -> e.getId().equals(especialidadId));
        return apto ? List.of(odontologo) : List.of();
    }

    private DisponibilidadResponseDTO calcular(Tratamiento tratamiento, List<Odontologo> candidatos,
            LocalDate desde, LocalDate hasta) {
        OffsetDateTime inicioRango = desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime finRango = hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();
        List<UUID> ids = candidatos.stream().map(Odontologo::getId).toList();

        Map<UUID, List<HorarioAtencion>> horarios = horarioRepository.findByOdontologoIdIn(ids).stream()
                .collect(Collectors.groupingBy(h -> h.getOdontologo().getId()));

        // Una sola lectura de citas sirve para las dos ocupaciones que el motor
        // necesita: la del odontólogo (RN-03) y la del pool de consultorios (RN-02).
        List<Cita> citas = citaRepository.findActivasEnRango(inicioRango, finRango);
        Map<UUID, List<Intervalo>> citasPorOdontologo = agrupar(citas,
                c -> c.getOdontologo().getId(), c -> new Intervalo(c.getInicio(), c.getFin()));
        Map<UUID, List<Intervalo>> citasPorConsultorio = agrupar(citas,
                c -> c.getConsultorio().getId(), c -> new Intervalo(c.getInicio(), c.getFin()));

        // Un bloqueo puede llevar odontólogo, consultorio o ambos: cuenta en cada
        // índice donde aparezca.
        List<Bloqueo> bloqueos = bloqueoRepository.findQueSolapanRango(inicioRango, finRango);
        Map<UUID, List<Intervalo>> bloqueosPorOdontologo = new HashMap<>();
        Map<UUID, List<Intervalo>> bloqueosPorConsultorio = new HashMap<>();
        for (Bloqueo bloqueo : bloqueos) {
            Intervalo intervalo = new Intervalo(bloqueo.getFechaInicio(), bloqueo.getFechaFin());
            if (bloqueo.getOdontologo() != null) {
                bloqueosPorOdontologo.computeIfAbsent(bloqueo.getOdontologo().getId(), k -> new ArrayList<>())
                        .add(intervalo);
            }
            if (bloqueo.getConsultorio() != null) {
                bloqueosPorConsultorio.computeIfAbsent(bloqueo.getConsultorio().getId(), k -> new ArrayList<>())
                        .add(intervalo);
            }
        }

        List<UUID> consultorios = consultorioRepository.findByInoperativoFalse().stream()
                .map(Consultorio::getId)
                .toList();

        Set<LocalDate> feriados = feriadoRepository.findByFechaBetween(desde, hasta).stream()
                .map(Feriado::getFecha)
                .collect(Collectors.toSet());

        AgendaDelRango agenda = new AgendaDelRango(
                tratamiento.getDuracionMinutos(), desde, hasta, zona, OffsetDateTime.now(zona), reglas,
                candidatos, horarios, citasPorOdontologo, citasPorConsultorio,
                bloqueosPorOdontologo, bloqueosPorConsultorio, consultorios, feriados);

        Map<UUID, Map<LocalDate, List<LocalTime>>> franjas = MotorDeFranjas.calcular(agenda);

        List<AgendaOdontologoDTO> agendas = candidatos.stream()
                .filter(o -> franjas.containsKey(o.getId()))
                .map(o -> AgendaOdontologoDTO.builder()
                        .id(o.getId())
                        .nombres(o.getNombres())
                        .apellidos(o.getApellidos())
                        .dias(franjas.get(o.getId()).entrySet().stream()
                                .map(e -> DiaDisponibleDTO.builder()
                                        .fecha(e.getKey())
                                        .inicios(e.getValue())
                                        .build())
                                .toList())
                        .build())
                .toList();

        return respuesta(tratamiento, desde, hasta, agendas);
    }

    private static <T> Map<UUID, List<Intervalo>> agrupar(List<T> origen,
            java.util.function.Function<T, UUID> clave, java.util.function.Function<T, Intervalo> valor) {
        return origen.stream().collect(Collectors.groupingBy(clave,
                Collectors.mapping(valor, Collectors.toList())));
    }

    private DisponibilidadResponseDTO respuestaVacia(Tratamiento tratamiento, LocalDate desde, LocalDate hasta) {
        return respuesta(tratamiento, desde, hasta, List.of());
    }

    private DisponibilidadResponseDTO respuesta(Tratamiento tratamiento, LocalDate desde, LocalDate hasta,
            List<AgendaOdontologoDTO> agendas) {
        return DisponibilidadResponseDTO.builder()
                .tratamientoId(tratamiento.getId())
                .duracionMinutos(tratamiento.getDuracionMinutos())
                .desde(desde)
                .hasta(hasta)
                .zonaHoraria(zona.getId())
                .odontologos(agendas)
                .build();
    }

    /**
     * Consultorios libres en un intervalo concreto (RN-02, RF-14).
     *
     * <p>Lo usa la reserva (HU-09) para asignar uno al confirmar. Se expone aquí y
     * no se recalcula en el servicio de citas porque el criterio debe ser el mismo
     * que el que decidió ofrecer la franja: si divergieran, el motor propondría
     * huecos que la reserva no sabría alojar, o al revés.
     *
     * @return los identificadores libres, en el orden estable del listado de
     *         consultorios. La elección de cuál tomar —y el reintento cuando otro
     *         se adelanta— es de HU-10 (RF-16).
     */
    @Transactional(readOnly = true)
    public List<UUID> consultoriosLibres(OffsetDateTime inicio, OffsetDateTime fin) {
        List<UUID> operativos = consultorioRepository.findByInoperativoFalse().stream()
                .map(Consultorio::getId)
                .toList();

        Map<UUID, List<Intervalo>> ocupacion = agrupar(citaRepository.findActivasEnRango(inicio, fin),
                c -> c.getConsultorio().getId(), c -> new Intervalo(c.getInicio(), c.getFin()));

        Map<UUID, List<Intervalo>> bloqueados = new HashMap<>();
        for (Bloqueo bloqueo : bloqueoRepository.findQueSolapanRango(inicio, fin)) {
            if (bloqueo.getConsultorio() != null) {
                bloqueados.computeIfAbsent(bloqueo.getConsultorio().getId(), k -> new ArrayList<>())
                        .add(new Intervalo(bloqueo.getFechaInicio(), bloqueo.getFechaFin()));
            }
        }

        return ConsultoriosLibres.en(operativos, ocupacion, bloqueados, inicio, fin);
    }
}
