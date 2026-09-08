package pe.edu.dentalcite.especialidad.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;

import java.util.Optional;
import java.util.UUID;

public interface EspecialidadRepository extends JpaRepository<Especialidad, UUID> {
    Optional<Especialidad> findByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCase(String nombre);
}
