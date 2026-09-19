package pe.edu.dentalcite.plan.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.plan.domain.Plan;

import java.util.Collection;
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
     * costaría una consulta por plan al pintar la ficha. La cita de cada sesión va
     * en el mismo grafo por la misma razón: sin ella serían una consulta por
     * sesión ocupada.
     */
    @EntityGraph(attributePaths = {"tratamiento", "odontologo", "sesiones", "sesiones.cita",
            "sesiones.recomendaciones"})
    List<Plan> findByFichaIdOrderByCreadoEnDesc(UUID fichaId);

    /** Un plan con sus asociaciones ya resueltas, para responder tras crearlo o suspenderlo. */
    @EntityGraph(attributePaths = {"ficha", "tratamiento", "odontologo", "sesiones", "sesiones.cita",
            "sesiones.recomendaciones"})
    Optional<Plan> findConDetalleById(UUID id);

    /**
     * Los planes que un odontólogo sigue, solo sus identificadores y ya paginados.
     *
     * <p>Sigue los que firmó él y todos los de los pacientes que tienen alguna cita
     * suya sin cancelar, los firme quien los firme: si el paciente ha pedido hora
     * con él, lo que otro compañero le tenga planificado es parte de lo que tiene
     * que saber antes de sentarlo en el sillón. La cita cancelada no cuenta por lo
     * mismo que en {@code CitaRepository.atendioAPorFichaDelOdontologo}, que es la
     * regla con la que después se abre cada plan: aquí no puede aparecer ninguno
     * que al pulsarlo respondiera 403.
     *
     * <p>Van solo los identificadores porque la paginación tiene que hacerse en
     * SQL, y con la colección de sesiones en el grafo Hibernate la haría en
     * memoria. El detalle lo carga {@link #findConDetalleByIdIn} para la página ya
     * cortada: dos consultas por página, sea cual sea su tamaño.
     *
     * <p>El orden va escrito en la consulta y no sale del {@code Pageable}: primero
     * los que siguen en curso y, dentro de cada grupo, el más reciente arriba.
     */
    @Query(value = """
            SELECT p.id FROM Plan p
            WHERE (p.odontologo.id = :odontologoId
                   OR EXISTS (SELECT 1 FROM Cita c
                              WHERE c.odontologo.id = :odontologoId
                                AND c.ficha = p.ficha
                                AND c.estado <> 'CANCELADA'))
              AND (:soloActivos = false OR p.activo = true)
            ORDER BY p.activo DESC, p.creadoEn DESC, p.id
            """,
            countQuery = """
            SELECT COUNT(p) FROM Plan p
            WHERE (p.odontologo.id = :odontologoId
                   OR EXISTS (SELECT 1 FROM Cita c
                              WHERE c.odontologo.id = :odontologoId
                                AND c.ficha = p.ficha
                                AND c.estado <> 'CANCELADA'))
              AND (:soloActivos = false OR p.activo = true)
            """)
    Page<UUID> idsDeSeguimiento(@Param("odontologoId") UUID odontologoId,
            @Param("soloActivos") boolean soloActivos, Pageable pageable);

    /**
     * Varios planes con todo lo que hace falta para pintarlos, paciente incluido.
     * No garantiza ningún orden: quien lo llama lo repone con el de los ids.
     */
    @EntityGraph(attributePaths = {"ficha", "tratamiento", "odontologo", "sesiones", "sesiones.cita",
            "sesiones.recomendaciones"})
    List<Plan> findConDetalleByIdIn(Collection<UUID> ids);

    /**
     * El plan activo de un paciente para un tratamiento, con su fila bloqueada
     * para escribir en él. Como mucho hay uno, porque la base no admite dos
     * activos del mismo tratamiento para la misma ficha.
     *
     * <p>El bloqueo es la razón de ser de este método, no un detalle: ver
     * {@code EnlaceDeSesiones}. No lleva grafo de entidades porque PostgreSQL no
     * admite {@code FOR UPDATE} sobre el lado opcional de un JOIN externo. Las
     * sesiones se cargan después, con la fila ya bloqueada, y por eso ven lo que
     * confirmó quien tenía el bloqueo antes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT p FROM Plan p
            WHERE p.ficha.id = :fichaId
              AND p.tratamiento.id = :tratamientoId
              AND p.activo = true
            """)
    Optional<Plan> findActivoParaEnlazar(@Param("fichaId") UUID fichaId,
            @Param("tratamientoId") UUID tratamientoId);

    /**
     * Un plan con su fila bloqueada para cerrar una de sus sesiones.
     *
     * <p>El bloqueo es lo que hace que dos cierres simultáneos de la misma sesión no
     * se pisen: el segundo espera y, al entrar, la ve ya cerrada y recibe su
     * conflicto. Y como es la misma fila que toma {@code EnlaceDeSesiones}, un
     * cierre de cita y un cierre de sesión tampoco se solapan.
     *
     * <p>Sin grafo de entidades por lo mismo que {@link #findActivoParaEnlazar}:
     * PostgreSQL no admite {@code FOR UPDATE} sobre el lado opcional de un JOIN
     * externo. Las sesiones se cargan después, con la fila ya bloqueada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Plan p WHERE p.id = :id")
    Optional<Plan> findParaCerrarSesion(@Param("id") UUID id);
}
