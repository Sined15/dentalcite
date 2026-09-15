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

        // RN-10: el par (tipo, número) es la identidad del paciente. Esta
        // comprobación es la que devuelve el 409 con un mensaje entendible; la
        // garantía frente a dos altas simultáneas la da uq_ficha_documento, cuya
        // violación el manejador global traduce al mismo 409.
        if (fichaRepository.existsByTipoDocumentoAndDocumento(tipoDocumento, documento)) {
            throw new IllegalStateException(
                    "Ya existe un paciente registrado con ese tipo y número de documento (RN-10).");
        }

        // El correlativo sale de la secuencia, nunca de un conteo (RN-10).
        String numeroHistoria = String.format("HC-%05d", fichaRepository.getNextHistoriaClinica());

        Ficha ficha = fichaRepository.save(Ficha.builder()
                .tipoDocumento(tipoDocumento)
                .documento(documento)
                .nombres(request.getNombres().trim())
                .apellidos(request.getApellidos().trim())
                .telefono(normalizarOpcional(request.getTelefono()))
                .numeroHistoria(numeroHistoria)
                .build());

        // RNF-06: el alta registra el consentimiento con su fecha y la versión del
        // texto. Cuelga de la ficha porque este paciente no tiene cuenta de la que
        // colgar.
        consentimientoRepository.save(Consentimiento.builder()
                .ficha(ficha)
                .versionTexto(request.getVersionConsentimiento())
                .build());

        // Recién creada, y el alta presencial no crea cuenta: HU-13, que lee fichas
        // que sí pueden tenerla, resolverá el dato con existsByFichaId.
        return mapToDTO(ficha, false);
    }

    /**
     * RF-07: «buscar pacientes por documento, apellidos o número de historia, con
     * resultados paginados».
     *
     * <p>Quién ve qué lo decide {@link PacienteAccessGuard#odontologoDelListado()}:
     * recepción y administración buscan en todo el padrón, y el odontólogo solo
     * entre las personas a las que ha atendido. Por eso son dos consultas y no un
     * filtro opcional: la del odontólogo lleva su {@code EXISTS} sobre citas, y
     * arrastrarlo en la de recepción sería pagar un semijoin que nunca filtra.
     *
     * <p><strong>Ninguna de las dos devuelve fichas de odontólogo.</strong> Esa
     * ficha existe para vincular su cuenta con su registro, no porque la persona
     * se atienda aquí, y en el padrón se lee como un error: con la semilla del
     * caso simulado había siete odontólogos y un paciente. El odontólogo vive en
     * su registro y en las cuentas de acceso. La exclusión está en las dos
     * consultas y no aquí, porque filtrarla después rompería el recuento de la
     * página.
     */
    @Transactional(readOnly = true)
    public Page<PacienteResponseDTO> buscar(String q, Pageable pageable) {
        String termino = normalizarOpcional(q);
        // El prefijo se calcula aquí y no en la consulta: concatenar dentro del
        // LIKE impide que PostgreSQL use ix_fichas_apellidos.
        String prefijo = termino == null ? null : termino.toLowerCase(Locale.ROOT) + "%";

        UUID fichaDelOdontologo = accessGuard.odontologoDelListado();
        Page<Ficha> pagina = fichaDelOdontologo == null
                ? fichaRepository.buscar(termino, prefijo, sanitizarPaginacion(pageable))
                : citaRepository.buscarPacientesDeOdontologo(termino, prefijo, fichaDelOdontologo,
                        sanitizarPaginacion(pageable));

        // Una consulta para toda la página, no una por fila (RNF-02).
        Set<UUID> conCuenta = pagina.isEmpty()
                ? Set.of()
                : usuarioRepository.fichasConCuenta(pagina.getContent().stream().map(Ficha::getId).toList());

        return pagina.map(ficha -> mapToDTO(ficha, conCuenta.contains(ficha.getId())));
    }

    /**
     * RF-08: la ficha con sus datos, sus alergias y sus citas pasadas y futuras.
     *
     * <p>El 404 va antes que el 403: negar el acceso a una ficha que no existe
     * mandaría al mostrador a buscar un permiso en vez de una errata. La ficha de
     * un odontólogo cuenta aquí como inexistente, por lo mismo que el listado no
     * la ofrece: no es una historia clínica.
     */
    @Transactional(readOnly = true)
    public PacienteDetalleDTO obtener(UUID id) {
        Ficha ficha = fichaRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado con ID: " + id));
        // Como paciente no existe, y decirlo aquí es lo que impide alcanzarla
        // escribiendo la URL cuando el listado ya no la ofrece. Va antes del 403
        // por la misma razón que el 404 de arriba.
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

    /**
     * RF-08: «editar los datos de contacto». Reemplazo total de lo editable —es
     * un PUT—, de modo que un teléfono ausente borra el que hubiera.
     *
     * <p>Tipo y número de documento no se tocan: RN-10 los declara la identidad
     * del paciente. No hace falta comprobar el permiso aquí porque esta operación
     * es solo de recepción y administración, y eso sí es expresable como regla de
     * ruta en {@code SecurityConfig}.
     */
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

    /**
     * Solo se ordena por columnas de la ficha. Sin esto, un {@code sort} inventado
     * en la barra de direcciones revienta la consulta con un 500 en vez de
     * ignorarse; es la misma criba que {@code UsuarioService} aplica a su listado.
     */
    private Pageable sanitizarPaginacion(Pageable pageable) {
        List<Sort.Order> ordenes = new ArrayList<>();
        pageable.getSort().forEach(order -> {
            if (ORDENABLES.contains(order.getProperty())) {
                ordenes.add(order);
            }
        });
        if (ordenes.isEmpty()) {
            // Por apellidos: es como se lee una lista de personas.
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
