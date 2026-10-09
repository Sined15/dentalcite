package pe.edu.dentalcite.paciente.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.cita.service.CitaConsultaService;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;
import pe.edu.dentalcite.consentimiento.repository.ConsentimientoRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.paciente.api.dto.PacienteDetalleDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteRequestDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteResponseDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteUpdateRequestDTO;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Alta presencial del paciente (HU-12, RF-06), búsqueda y ficha (HU-13, RF-07 y
 * RF-08).
 *
 * <p>El dominio {@code paciente} no tiene entidad propia —como
 * {@code disponibilidad}—: la tabla es {@code fichas} y su dueño sigue siendo el
 * dominio {@code ficha}, que auth y usuario comparten. Aquí vive solo lo que la
 * recepción hace con ella.
 */
@Service
@RequiredArgsConstructor
public class PacienteService {

    private static final String TIPO_DOCUMENTO_POR_DEFECTO = "DNI";

    /** Columnas de la ficha por las que tiene sentido ordenar el listado. */
    private static final Set<String> ORDENABLES = Set.of(
            "apellidos", "nombres", "documento", "numeroHistoria");

    private final FichaRepository fichaRepository;
    private final ConsentimientoRepository consentimientoRepository;
    private final UsuarioRepository usuarioRepository;
    private final CitaRepository citaRepository;
    private final CitaConsultaService citaConsultaService;
    private final PacienteAccessGuard accessGuard;
    private final OdontologoRepository odontologoRepository;

    @Transactional
    public PacienteResponseDTO registrar(PacienteRequestDTO request) {
        String tipoDocumento = normalizarTipoDocumento(request.getTipoDocumento());
        String documento = request.getDocumento().trim();

        if (fichaRepository.existsByTipoDocumentoAndDocumento(tipoDocumento, documento)) {
            throw new IllegalStateException(
                    "Ya existe un paciente registrado con ese tipo y número de documento (RN-10).");
        }

        String numeroHistoria = String.format("HC-%05d", fichaRepository.getNextHistoriaClinica());

        Ficha ficha = fichaRepository.save(Ficha.builder()
                .tipoDocumento(tipoDocumento)
                .documento(documento)
                .nombres(request.getNombres().trim())
                .apellidos(request.getApellidos().trim())
                .telefono(normalizarOpcional(request.getTelefono()))
                .numeroHistoria(numeroHistoria)
                .build());

        consentimientoRepository.save(Consentimiento.builder()
                .ficha(ficha)
                .versionTexto(request.getVersionConsentimiento())
                .build());

        return mapToDTO(ficha, false);
    }

    @Transactional(readOnly = true)
    public Page<PacienteResponseDTO> buscar(String q, Pageable pageable) {
        String termino = normalizarOpcional(q);
        String prefijo = termino == null ? null : termino.toLowerCase(Locale.ROOT) + "%";

        UUID fichaDelOdontologo = accessGuard.odontologoDelListado();
        Page<Ficha> pagina = fichaDelOdontologo == null
                ? fichaRepository.buscar(termino, prefijo, sanitizarPaginacion(pageable))
                : citaRepository.buscarPacientesDeOdontologo(termino, prefijo, fichaDelOdontologo,
                        sanitizarPaginacion(pageable));

        Set<UUID> conCuenta = pagina.isEmpty()
                ? Set.of()
                : usuarioRepository.fichasConCuenta(pagina.getContent().stream().map(Ficha::getId).toList());

        return pagina.map(ficha -> mapToDTO(ficha, conCuenta.contains(ficha.getId())));
    }

    @Transactional(readOnly = true)
    public PacienteDetalleDTO obtener(UUID id) {
        Ficha ficha = fichaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + id));
        if (odontologoRepository.existsByFichaId(id)) {
            throw new ResourceNotFoundException("Paciente no encontrado con ID: " + id);
        }
        accessGuard.verificarLectura(id);

        return PacienteDetalleDTO.builder()
                .id(ficha.getId())
                .numeroHistoria(ficha.getNumeroHistoria())
                .tipoDocumento(ficha.getTipoDocumento())
                .documento(ficha.getDocumento())
                .nombres(ficha.getNombres())
                .apellidos(ficha.getApellidos())
                .telefono(ficha.getTelefono())
                .alergias(ficha.getAlergias())
                .tieneCuenta(usuarioRepository.existsByFichaId(id))
                .citas(citaConsultaService.deFicha(id))
                .build();
    }

    @Transactional
    public PacienteDetalleDTO actualizar(UUID id, PacienteUpdateRequestDTO request) {
        Ficha ficha = fichaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + id));

        ficha.setNombres(request.getNombres().trim());
        ficha.setApellidos(request.getApellidos().trim());
        ficha.setTelefono(normalizarOpcional(request.getTelefono()));
        ficha.setAlergias(normalizarOpcional(request.getAlergias()));
        fichaRepository.save(ficha);

        return obtener(id);
    }

    private Pageable sanitizarPaginacion(Pageable pageable) {
        List<Sort.Order> ordenes = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            if (ORDENABLES.contains(order.getProperty())) {
                ordenes.add(order);
            }
        });
        if (ordenes.isEmpty()) {
            ordenes.add(Sort.Order.asc("apellidos"));
            ordenes.add(Sort.Order.asc("nombres"));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by(ordenes));
    }

    private String normalizarTipoDocumento(String tipoDocumento) {
        return tipoDocumento == null || tipoDocumento.isBlank()
                ? TIPO_DOCUMENTO_POR_DEFECTO
                : tipoDocumento.trim();
    }

    private String normalizarOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    private PacienteResponseDTO mapToDTO(Ficha ficha, boolean tieneCuenta) {
        return PacienteResponseDTO.builder()
                .id(ficha.getId())
                .numeroHistoria(ficha.getNumeroHistoria())
                .tipoDocumento(ficha.getTipoDocumento())
                .documento(ficha.getDocumento())
                .nombres(ficha.getNombres())
                .apellidos(ficha.getApellidos())
                .telefono(ficha.getTelefono())
                .tieneCuenta(tieneCuenta)
                .build();
    }
}
