package pe.edu.dentalcite.odontologo.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import pe.edu.dentalcite.odontologo.domain.Odontologo;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OdontologoRepository extends JpaRepository<Odontologo, UUID> {
    Optional<Odontologo> findByCop(String cop);
    boolean existsByCop(String cop);
    boolean existsByFichaId(UUID fichaId);

    /**
     * El registro de odontólogo de una cuenta, por la ficha que RN-11 comparte
     * entre ambos. Es el camino de «quién soy» a «qué odontólogo soy», y lo usa
     * HU-17 para saber a nombre de quién se planifica.
     */
    Optional<Odontologo> findByFichaId(UUID fichaId);
    
    @EntityGraph(attributePaths = {"ficha", "especialidades"})
    Page<Odontologo> findAll(Pageable pageable);
    
    @Query(value = "SELECT CASE WHEN COUNT(*) > 0 THEN true ELSE false END FROM citas WHERE odontologo_id = :id AND estado = 'CONFIRMADA' AND fin > CURRENT_TIMESTAMP", nativeQuery = true)
    boolean hasCitasActivas(@Param("id") UUID id);

    /**
     * RN-08: candidatos de «cualquier odontólogo». Solo los activos que poseen la
     * especialidad que el tratamiento exige.
     */
    @Query("""
            SELECT DISTINCT o FROM Odontologo o
            JOIN o.especialidades e
            WHERE o.activo = true
              AND e.id = :especialidadId
            ORDER BY o.apellidos, o.nombres
            """)
    List<Odontologo> findActivosConEspecialidad(@Param("especialidadId") UUID especialidadId);

    /** El mismo dato para un odontólogo concreto, con sus especialidades cargadas. */
    @EntityGraph(attributePaths = {"especialidades"})
    Optional<Odontologo> findConEspecialidadesById(UUID id);

    /**
     * HU-06 (v4): el equipo, tal como se presenta al visitante. Solo los activos
     * y sin la ficha en el grafo: el DTO público no la expone.
     */
    @EntityGraph(attributePaths = {"especialidades"})
    List<Odontologo> findByActivoTrueOrderByApellidosAscNombresAsc();
}
