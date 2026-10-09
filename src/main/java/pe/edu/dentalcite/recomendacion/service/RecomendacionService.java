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

@Service
@RequiredArgsConstructor
public class RecomendacionService {

    private final RecomendacionRepository recomendacionRepository;

    @Transactional(readOnly = true)
    public List<RecomendacionDTO> catalogo() {
        return recomendacionRepository.findByActivaTrueOrderByDescripcionAsc().stream()
                .map(RecomendacionService::mapear)
                .toList();
    }

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
