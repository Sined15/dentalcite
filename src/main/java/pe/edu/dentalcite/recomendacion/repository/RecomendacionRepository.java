package pe.edu.dentalcite.recomendacion.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;

import java.util.List;
import java.util.UUID;

public interface RecomendacionRepository extends JpaRepository<Recomendacion, UUID> {

    /**
     * El catálogo vigente. Las inactivas se quedan fuera aunque sigan en la base:
     * son las que ya no se indican, pero que las sesiones antiguas todavía nombran.
     */
    List<Recomendacion> findByActivaTrueOrderByDescripcionAsc();

    /** Las vigentes de entre unas cuantas, para comprobar lo que llega en el cierre. */
    List<Recomendacion> findByIdInAndActivaTrue(List<UUID> ids);
}
