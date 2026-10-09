package pe.edu.dentalcite.feriado.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.feriado.domain.Feriado;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FeriadoRepository extends JpaRepository<Feriado, UUID> {

    List<Feriado> findByFechaBetween(LocalDate desde, LocalDate hasta);

    List<Feriado> findAllByOrderByFechaAsc();
}
