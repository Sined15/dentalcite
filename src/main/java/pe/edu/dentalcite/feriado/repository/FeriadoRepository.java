package pe.edu.dentalcite.feriado.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.feriado.domain.Feriado;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FeriadoRepository extends JpaRepository<Feriado, UUID> {

    /**
     * Feriados del rango consultado, ambos extremos incluidos. El motor de
     * disponibilidad los pide una sola vez por consulta y no una vez por día.
     */
    List<Feriado> findByFechaBetween(LocalDate desde, LocalDate hasta);
}
