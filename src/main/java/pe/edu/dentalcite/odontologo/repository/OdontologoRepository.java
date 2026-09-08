package pe.edu.dentalcite.odontologo.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pe.edu.dentalcite.odontologo.domain.Odontologo;

import java.util.Optional;
import java.util.UUID;

public interface OdontologoRepository extends JpaRepository<Odontologo, UUID> {
    Optional<Odontologo> findByCop(String cop);
    boolean existsByCop(String cop);
    boolean existsByFichaId(UUID fichaId);
    
    @EntityGraph(attributePaths = {"ficha", "especialidades"})
    Page<Odontologo> findAll(Pageable pageable);
    
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM citas WHERE odontologo_id = :id AND estado = 'CONFIRMADA' AND fin > CURRENT_TIMESTAMP", nativeQuery = true)
    boolean hasCitasActivas(@Param("id") UUID id);
}
