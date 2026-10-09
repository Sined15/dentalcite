package pe.edu.dentalcite.bloqueo.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface BloqueoRepository extends JpaRepository<Bloqueo, UUID> {

    @EntityGraph(attributePaths = {"odontologo", "consultorio"})
    List<Bloqueo> findAll();

    @EntityGraph(attributePaths = {"odontologo", "consultorio"})
    @Query("""
            SELECT b FROM Bloqueo b
            WHERE b.fechaInicio < :fin
              AND b.fechaFin > :inicio
            """)
    List<Bloqueo> findQueSolapanRango(@Param("inicio") OffsetDateTime inicio, @Param("fin") OffsetDateTime fin);
}
