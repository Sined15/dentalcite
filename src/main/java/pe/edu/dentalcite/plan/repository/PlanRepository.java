package pe.edu.dentalcite.plan.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.dentalcite.plan.domain.Plan;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    /**
     * Los planes de un paciente, del más reciente al más antiguo, con todo lo que
     * hace falta para pintarlos.
     *
     * <p>El {@code @EntityGraph} incluye las sesiones a propósito: son una
     * colección, así que Hibernate no puede resolverlas con un JOIN sin romper la
     * paginación —por eso esto devuelve una {@code List} y no una {@code Page}—.
     * Un plan tiene unas pocas sesiones y un paciente unos pocos planes, de modo
     * que aquí no hay nada que paginar; la alternativa, dejarlas perezosas,
     * costaría una consulta por plan al pintar la ficha.
     */
    @EntityGraph(attributePaths = {"tratamiento", "odontologo", "sesiones"})
    List<Plan> findByFichaIdOrderByCreadoEnDesc(UUID fichaId);

    /** Un plan con sus asociaciones ya resueltas, para responder tras crearlo o suspenderlo. */
    @EntityGraph(attributePaths = {"ficha", "tratamiento", "odontologo", "sesiones"})
    Optional<Plan> findConDetalleById(UUID id);
}
