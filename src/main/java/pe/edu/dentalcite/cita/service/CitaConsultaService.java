package pe.edu.dentalcite.cita.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
import pe.edu.dentalcite.common.api.Ordenacion;
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

@Service
public class CitaConsultaService {

    private static final Set<String> ESTADOS = Set.of(
            "CONFIRMADA", "ATENDIDA", "NO_ASISTIO", "CANCELADA");

    private static final Set<String> ORDENABLES = Set.of("inicio", "fin", "estado", "codigo");

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

        boolean esOdontologo = esOdontologoSinPrivilegios();
        UUID fichaDelOdontologo = esOdontologo ? fichaDelUsuarioAutenticado() : null;
        UUID odontologoPedido = esOdontologo ? null : odontologoId;

        return citaRepository
                .buscar(inicio, fin, null, odontologoPedido, fichaDelOdontologo,
                        estadoNormalizado, Ordenacion.cribar(pageable, ORDENABLES, Sort.by("inicio")))
                .map(this::resumen);
    }

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

        Pageable cribada = Ordenacion.cribar(pageable, ORDENABLES, Sort.by(Sort.Direction.DESC, "inicio"));
        return citaRepository.buscar(inicio, fin, fichaId, null, null, estadoNormalizado, cribada)
                .map(this::resumen);
    }

    @Transactional(readOnly = true)
    public List<CitaResumenDTO> deFicha(UUID fichaId) {
        return citaRepository.findByFichaIdOrderByInicioDesc(fichaId).stream()
                .map(this::resumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<CitaResumenDTO> pendientesDeCierre(Pageable pageable) {
        UUID fichaDelOdontologo = esOdontologoSinPrivilegios() ? fichaDelUsuarioAutenticado() : null;
        return citaRepository.pendientesDeCierre(fichaDelOdontologo, Ordenacion.sinOrden(pageable))
                .map(this::resumen);
    }

    @Transactional(readOnly = true)
    public List<CitaHistorialDTO> historial(UUID citaId) {
        if (!citaRepository.existsById(citaId)) {
            throw new ResourceNotFoundException("Cita no encontrada");
        }
        return historialRepository.findByCitaIdOrderByOcurridoEnAsc(citaId).stream()
                .map(CitaConsultaService::transicion)
                .toList();
    }

    private static boolean esOdontologoSinPrivilegios() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No autenticado");
        }
        boolean privilegiado = auth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_RECEPCIONISTA".equals(a.getAuthority())
                        || "SCOPE_ADMINISTRADOR".equals(a.getAuthority()));
        if (privilegiado) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> "SCOPE_ODONTOLOGO".equals(a.getAuthority()));
    }

    private UUID fichaDelPacienteAutenticado() {
        return fichaDelUsuarioAutenticado();
    }

    private UUID fichaDelUsuarioAutenticado() {
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
