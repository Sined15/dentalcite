package pe.edu.dentalcite.cita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
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
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Consulta de la agenda de la clínica (HU-11 · RF-18), de las citas del propio
 * paciente (HU-15 · RF-18) y de la bitácora de una cita (RF-21).
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
    private final UsuarioRepository usuarioRepository;
    private final VentanaDeCancelacion ventana;
    private final ZoneId zona;

    public CitaConsultaService(CitaRepository citaRepository,
            CitaHistorialRepository historialRepository,
            UsuarioRepository usuarioRepository,
            VentanaDeCancelacion ventana,
            @Value("${app.zona-horaria:America/Lima}") String zonaHoraria) {
        this.citaRepository = citaRepository;
        this.historialRepository = historialRepository;
        this.usuarioRepository = usuarioRepository;
        this.ventana = ventana;
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

        return citaRepository.buscar(inicio, fin, null, odontologoId, estadoNormalizado, pageable)
                .map(this::resumen);
    }

    /**
     * HU-15 · RF-18 ampliado al paciente: «veré las futuras y las pasadas con su
     * estado, y ninguna de otro paciente».
     *
     * <p>La ficha <strong>se resuelve aquí dentro, a partir del token</strong>, y
     * no se recibe como parámetro. Es deliberado: si fuera un argumento, el
     * «ninguna de otro paciente» dependería de que cada llamante se acordase de
     * pasar la correcta, y bastaría un controlador descuidado para convertirlo en
     * una fuga. Así no hay forma de pedir la de otro.
     *
     * <p>El rango es opcional aquí, al revés que en la agenda de la clínica: el
     * criterio pide ver las futuras <em>y</em> las pasadas, así que omitirlo
     * significa «todas» y no un error.
     */
    @Transactional(readOnly = true)
    public Page<CitaResumenDTO> mias(LocalDate desde, LocalDate hasta,
            String estado, Pageable pageable) {

        UUID fichaId = fichaDelPacienteAutenticado();

        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new IllegalArgumentException("La fecha 'desde' no puede ser posterior a 'hasta'");
        }
        String estadoNormalizado = normalizarEstado(estado);

        OffsetDateTime inicio = desde == null ? null : desde.atStartOfDay(zona).toOffsetDateTime();
        OffsetDateTime fin = hasta == null ? null
                : hasta.plusDays(1).atStartOfDay(zona).toOffsetDateTime();

        return citaRepository.buscar(inicio, fin, fichaId, null, estadoNormalizado, pageable)
                .map(this::resumen);
    }

    /**
     * RF-08, HU-13: las citas de una ficha, pasadas y futuras, de la más reciente
     * a la más antigua.
     *
     * <p>Lo consume {@code PacienteService} para pintar la ficha. Vive aquí y no
     * allí porque lo que tiene trampa no es leer las filas sino traducirlas: la
     * cita se guarda en UTC y se muestra en la hora local de la clínica, y esa
     * conversión —con {@code app.zona-horaria}— ya está resuelta en
     * {@link #resumen}. Una segunda copia del mapeador sería una segunda copia de
     * la zona horaria, que es exactamente donde aparecen los desfases de una hora.
     */
    @Transactional(readOnly = true)
    public List<CitaResumenDTO> deFicha(UUID fichaId) {
        return citaRepository.findByFichaIdOrderByInicioDesc(fichaId).stream()
                .map(this::resumen)
                .toList();
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
     * La ficha de quien pregunta. Falla cerrado: sin credenciales, sin cuenta o
     * sin ficha no hay «mis citas» que devolver, y una lista vacía sería peor
     * que un error porque parecería que no tiene ninguna.
     */
    private UUID fichaDelPacienteAutenticado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        UUID usuarioId;
        try {
            // El subject del JWT es el UUID del usuario (JwtService.generateToken).
            usuarioId = UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no encontrado");
        }
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Usuario no encontrado"));
        if (usuario.getFicha() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "La cuenta no tiene una ficha clínica asociada");
        }
        return usuario.getFicha().getId();
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
                // RN-06, calculada una sola vez y en un solo sitio (HU-15).
                .cancelablePorPaciente(ventana.puedeCancelarElPaciente(cita, OffsetDateTime.now(zona)))
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
