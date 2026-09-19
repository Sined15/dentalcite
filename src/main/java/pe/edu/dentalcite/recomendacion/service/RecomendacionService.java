package pe.edu.dentalcite.recomendacion.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.recomendacion.api.dto.RecomendacionDTO;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;
import pe.edu.dentalcite.recomendacion.repository.RecomendacionRepository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * El catálogo cerrado de cuidados: leerlo para elegir, y comprobar lo elegido.
 */
@Service
@RequiredArgsConstructor
public class RecomendacionService {

    private final RecomendacionRepository recomendacionRepository;

    /** Las vigentes, en el orden en que se leen. Son pocas: sin paginar. */
    @Transactional(readOnly = true)
    public List<RecomendacionDTO> catalogo() {
        return recomendacionRepository.findByActivaTrueOrderByDescripcionAsc().stream()
                .map(RecomendacionService::mapear)
                .toList();
    }

    /**
     * Traduce los identificadores que llegan en un cierre a las recomendaciones que
     * nombran, y rechaza la petición si alguno no resuelve.
     *
     * <p>Rechazar en bloque y no ignorar lo que no existe: quien cierra la sesión
     * está diciendo qué cuidados indicó, y guardar solo los que se reconocen dejaría
     * la indicación a medias sin que nadie se enterara. Que el identificador no
     * exista y que nombre una recomendación retirada del catálogo se tratan igual:
     * en los dos casos no es algo que hoy se pueda indicar.
     */
    @Transactional(readOnly = true)
    public Set<Recomendacion> resolverVigentes(List<UUID> ids) {
        List<UUID> pedidos = ids.stream().distinct().toList();
        List<Recomendacion> encontradas = recomendacionRepository.findByIdInAndActivaTrue(pedidos);
        if (encontradas.size() != pedidos.size()) {
            throw new IllegalArgumentException(
                    "Alguna recomendacion no esta en el catalogo vigente.");
        }
        return new LinkedHashSet<>(encontradas);
    }

    public static RecomendacionDTO mapear(Recomendacion recomendacion) {
        return RecomendacionDTO.builder()
                .id(recomendacion.getId())
                .descripcion(recomendacion.getDescripcion())
                .build();
    }
}
