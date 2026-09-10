package pe.edu.dentalcite.consultorio.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;

import java.util.List;
import java.util.UUID;

public interface ConsultorioRepository extends JpaRepository<Consultorio, UUID> {

    /**
     * Consultorios en servicio. RF-14 solo puede asignar uno de estos, así que el
     * motor de disponibilidad parte de ellos para decidir si una franja tiene
     * dónde alojarse (RN-02).
     */
    List<Consultorio> findByInoperativoFalse();
}
