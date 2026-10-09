package pe.edu.dentalcite.recomendacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;

import java.util.List;
import java.util.UUID;

public interface RecomendacionRepository extends JpaRepository<Recomendacion, UUID> {
    List<Recomendacion> findByActivaTrueOrderByDescripcionAsc();
    List<Recomendacion> findByIdInAndActivaTrue(List<UUID> ids);
}
