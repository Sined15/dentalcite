package pe.edu.dentalcite.cita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.cita.api.dto.CitaHistorialDTO;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.api.dto.CitaResumenDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.domain.CitaHistorial;
import pe.edu.dentalcite.cita.repository.CitaHistorialRepository;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta de la agenda (HU-11 · RF-18) y de la bitácora de una cita (RF-21).
 *
 * <p>Vive aparte de {@link CitaService} a propósito: aquel no es
 * {@code @Transactional} porque el reintento de RF-16 necesita abrir una
 * transacción nueva por intento, y una consulta sí quiere una transacción de
 * lectura que mantenga la sesión abierta mientras se mapea. Son dos contratos
 * transaccionales incompatibles en la misma clase.
 */
@Service
public class CitaConsultaService {

    /** RN-09: los estados por los que tiene sentido filtrar. */
    private static final Set<String> ESTADOS = Set.of(
            "CONFIRMADA", "ATENDIDA", "NO_ASISTIO", "CANCELADA");

    private final CitaRepository citaRepository;
    private final CitaHistorialRepository historialRepository;
    private final ZoneId zona;

    public CitaConsultaService(CitaRepository citaRepository,
            CitaHistorialRepository historialRepository,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.zona = ZoneId.of(zonaHoraria);
    }

    /**
     * RF-18: las citas cuyo inicio cae entre {@code desde} y {@code hasta}, ambos
     * inclusive y expresados como <em>días</em> de la clínica.
     *
     * <p>El rango se traduce a instantes en la zona de la clínica y el extremo
     * superior se abre al día siguiente: pedir el 14 al 14 tiene que devolver la
     * cita de las 19:00 de ese día, y comparar contra el inicio del 14 la habría
     * dejado fuera.
     */
    @Transactional(readOnly = true)
    public Page<CitaResumenDTO> consultar(LocalDate desde, LocalDate hasta, UUID odontologoId,
            String estado, Pageable pageable) {

        if (desde == null || hasta == null) {
            throw new IllegalArgumentException("Las fechas 'desde' y 'hasta' son obligatorias");
        }
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        String estadoNormalizado = normalizarEstado(estado);

        OffsetDateTime inicio = desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin = hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();

        return citaRepository.buscar(inicio, fin, odontologoId, estadoNormalizado, pageable)
                .map(this::resumen);
    }

    /** RF-21: la bitácora completa de una cita, en orden cronológico. */
    @Transactional(readOnly = true)
    public List<CitaHistorialDTO> historial(UUID citaId) {
        if (!citaRepository.existsById(citaId)) {
            throw new ResourceNotFoundException("Cita no encontrada");
        }
        return historialRepository.findByCitaIdOrderByOcurridoEnAsc(citaId).stream()
                .map(CitaConsultaService::transicion)
                .toList();
    }

    /**
     * Un estado desconocido no devuelve una página vacía: eso convertiría una
     * errata en «no hay citas», que es la respuesta más difícil de depurar.
     */
    private static String normalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            return null;
        }
        String normalizado = estado.trim().toUpperCase();
        if (!ESTADOS.contains(normalizado)) {
            throw new IllegalArgumentException("Estado no valido: " + estado
                    + ". Los estados son " + String.join(", ", ESTADOS) + " (RN-09).");
        }
        return normalizado;
    }

    private CitaResumenDTO resumen(Cita cita) {
        OffsetDateTime local = cita.getInicio().atZoneSameInstant(zona).toOffsetDateTime();
        return CitaResumenDTO.builder()
                .id(cita.getId())
                .codigo(cita.getCodigo())
                .fecha(local.toLocalDate())
                .hora(local.toLocalTime())
                .duracionMinutos((int) Duration.between(cita.getInicio(), cita.getFin()).toMinutes())
                .zonaHoraria(zona.getId())
                .estado(cita.getEstado())
                .motivoCancelacion(cita.getMotivoCancelacion())
                .paciente(paciente(cita.getFicha()))
                .odontologo(referencia(cita.getOdontologo().getId(),
                        (cita.getOdontologo().getNombres() + " " + cita.getOdontologo().getApellidos()).trim()))
                .tratamiento(referencia(cita.getTratamiento().getId(), cita.getTratamiento().getNombre()))
                .consultorio(referencia(cita.getConsultorio().getId(), cita.getConsultorio().getNombre()))
                .build();
    }

    /**
     * La ficha puede no tener nombre: RF-06 admite darla de alta con el documento
     * mientras se completan los datos. En ese caso identifica el número de
     * historia, que sí es obligatorio.
     */
    private static CitaResumenDTO.Paciente paciente(Ficha ficha) {
        String nombre = ((ficha.getNombres() == null ? "" : ficha.getNombres()) + " "
                + (ficha.getApellidos() == null ? "" : ficha.getApellidos())).trim();
        return CitaResumenDTO.Paciente.builder()
                .fichaId(ficha.getId())
                .nombre(nombre.isEmpty() ? ficha.getNumeroHistoria() : nombre)
                .numeroHistoria(ficha.getNumeroHistoria())
                .build();
    }

    private static CitaHistorialDTO transicion(CitaHistorial fila) {
        Usuario usuario = fila.getUsuario();
        return CitaHistorialDTO.builder()
                .id(fila.getId())
                .estadoAnterior(fila.getEstadoAnterior())
                .estadoNuevo(fila.getEstadoNuevo())
                .motivo(fila.getMotivo())
                .ocurridoEn(fila.getOcurridoEn())
                .responsable(usuario == null ? null : CitaHistorialDTO.Responsable.builder()
                        .id(usuario.getId())
                        .nombre(usuario.getNombre())
                        .correo(usuario.getCorreo())
                        .rol(usuario.getRol())
                        .build())
                .build();
    }

    private static CitaResponseDTO.Referencia referencia(UUID id, String nombre) {
        return CitaResponseDTO.Referencia.builder().id(id).nombre(nombre).build();
    }
}
