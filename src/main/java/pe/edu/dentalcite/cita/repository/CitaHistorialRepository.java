package pe.edu.dentalcite.cita.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.cita.domain.CitaHistorial;

import java.util.List;
import java.util.UUID;

public interface CitaHistorialRepository extends JpaRepository<CitaHistorial, UUID> {

    @EntityGraph(attributePaths = "usuario")
    List<CitaHistorial> findByCitaIdOrderByOcurridoEnAsc(UUID citaId);
}
