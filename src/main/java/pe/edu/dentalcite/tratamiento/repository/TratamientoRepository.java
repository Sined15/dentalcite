package pe.edu.dentalcite.tratamiento.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;

import java.util.Optional;
import java.util.UUID;

public interface TratamientoRepository extends JpaRepository<Tratamiento, UUID> {
    Optional<Tratamiento> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
    
    @EntityGraph(attributePaths = {"especialidad"})
    Page<Tratamiento> findAll(Pageable pageable);
    
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM citas WHERE tratamiento_id = :id AND estado = 'CONFIRMADA' AND fin > CURRENT_TIMESTAMP", nativeQuery = true)
    boolean hasCitasActivas(@Param("id") UUID id);

    boolean existsByEspecialidadIdAndActivoTrue(UUID especialidadId);
}
