package pe.edu.dentalcite.consentimiento.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;

import java.util.UUID;

public interface ConsentimientoRepository extends JpaRepository<Consentimiento, UUID> {
}
