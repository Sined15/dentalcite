package pe.edu.dentalcite.cita.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.cita.domain.CitaHistorial;

import java.util.List;
import java.util.UUID;

public interface CitaHistorialRepository extends JpaRepository<CitaHistorial, UUID> {

    /**
     * La bitácora de una cita, en el orden en que ocurrió (RF-21).
     *
     * <p>El {@code @EntityGraph} trae el usuario responsable en la misma consulta:
     * sin él, mostrar «quién» en una lista de transiciones emitiría una consulta
     * por fila.
     */
    @EntityGraph(attributePaths = "usuario")
    List<CitaHistorial> findByCitaIdOrderByOcurridoEnAsc(UUID citaId);
}
