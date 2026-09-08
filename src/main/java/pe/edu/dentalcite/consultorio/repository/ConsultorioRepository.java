package pe.edu.dentalcite.consultorio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;

import java.util.UUID;

public interface ConsultorioRepository extends JpaRepository<Consultorio, UUID> {
}
