package pe.edu.dentalcite.paciente.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;
import pe.edu.dentalcite.consentimiento.repository.ConsentimientoRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.paciente.api.dto.PacienteRequestDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteResponseDTO;

/**
 * Alta presencial del paciente (HU-12, RF-06).
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

    private final FichaRepository fichaRepository;
    private final ConsentimientoRepository consentimientoRepository;

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
