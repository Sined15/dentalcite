package pe.edu.dentalcite.bloqueo.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;

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
}
