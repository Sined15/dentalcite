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

    /**
     * El listado compone el nombre del odontólogo y del consultorio de cada
     * bloqueo; sin el grafo, cada fila dispararía sus propias consultas para
     * resolver esas dos asociaciones LAZY.
     */
    @EntityGraph(attributePaths = {"odontologo", "consultorio"})
    List<Bloqueo> findAll();

    /**
     * Bloqueos vigentes que solapan el rango consultado, de odontólogo y de
     * consultorio a la vez (RN-02, RN-03). El solapamiento es el mismo de
     * intervalos semiabiertos que ya usan {@code CitaRepository} y
     * {@code HorarioAtencionRepository.findOverlappingHorarios}.
     */
    @EntityGraph(attributePaths = {"odontologo", "consultorio"})
    @Query("""
            SELECT b FROM Bloqueo b
            WHERE b.fechaInicio < :fin
              AND b.fechaFin > :inicio
            """)
    List<Bloqueo> findQueSolapanRango(@Param("inicio") OffsetDateTime inicio, @Param("fin") OffsetDateTime fin);
}
