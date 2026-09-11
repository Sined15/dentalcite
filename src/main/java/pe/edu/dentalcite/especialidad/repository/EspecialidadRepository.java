package pe.edu.dentalcite.especialidad.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EspecialidadRepository extends JpaRepository<Especialidad, UUID> {
    Optional<Especialidad> findByNombreIgnoreCase(String nombre);
    boolean existsByNombreIgnoreCase(String nombre);

    /**
     * HU-06 (v4): la galería pública. Solo las activas, porque el visitante no
     * tiene por qué ver lo que la clínica dio de baja.
     */
    List<Especialidad> findByActivoTrueOrderByNombreAsc();
}
