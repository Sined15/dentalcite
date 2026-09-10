package pe.edu.dentalcite.cita.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.cita.domain.Cita;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface CitaRepository extends JpaRepository<Cita, UUID> {

    /**
     * Citas activas de un odontólogo que solapan el rango dado. RN-09: activa es
     * la confirmada cuya hora de fin no ha pasado. El solapamiento es la condición
     * estándar de intervalos semiabiertos: {@code inicio < rangoFin && fin > rangoInicio}.
     */
    @Query("""
            SELECT c FROM Cita c
            WHERE c.odontologo.id = :odontologoId
              AND c.estado = 'CONFIRMADA'
              AND c.fin > CURRENT_TIMESTAMP
              AND c.inicio < :fin
              AND c.fin > :inicio
            ORDER BY c.inicio
            """)
    List<Cita> findActivasDeOdontologoEnRango(@Param("odontologoId") UUID odontologoId,
            @Param("inicio") OffsetDateTime inicio, @Param("fin") OffsetDateTime fin);

    /** Igual que la anterior, para el consultorio (RN-02). */
    @Query("""
            SELECT c FROM Cita c
            WHERE c.consultorio.id = :consultorioId
              AND c.estado = 'CONFIRMADA'
              AND c.fin > CURRENT_TIMESTAMP
              AND c.inicio < :fin
              AND c.fin > :inicio
            ORDER BY c.inicio
            """)
    List<Cita> findActivasDeConsultorioEnRango(@Param("consultorioId") UUID consultorioId,
            @Param("inicio") OffsetDateTime inicio, @Param("fin") OffsetDateTime fin);

    /**
     * Todas las citas activas que solapan el rango, con su odontólogo y su
     * consultorio ya resueltos. El motor de disponibilidad (HU-08) la emite una
     * sola vez y la usa dos veces: la ocupación del odontólogo (RN-03) y la del
     * pool de consultorios, que RN-02 comparte entre todos, salen de la misma
     * lista. Pedirlas por separado duplicaría el trabajo sin añadir nada.
     */
    @Query("""
            SELECT c FROM Cita c
            JOIN FETCH c.odontologo
            JOIN FETCH c.consultorio
            WHERE c.estado = 'CONFIRMADA'
              AND c.fin > CURRENT_TIMESTAMP
              AND c.inicio < :fin
              AND c.fin > :inicio
            """)
    List<Cita> findActivasEnRango(@Param("inicio") OffsetDateTime inicio, @Param("fin") OffsetDateTime fin);

    /**
     * RN-07: cuantas citas activas tiene ya el paciente. Activa es la confirmada
     * cuya hora de fin no ha pasado (RN-09), de modo que una cita ya vencida deja
     * de consumir cuota sola, sin ningun proceso que la cierre.
     */
    @Query("""
            SELECT COUNT(c) FROM Cita c
            WHERE c.ficha.id = :fichaId
              AND c.estado = 'CONFIRMADA'
              AND c.fin > CURRENT_TIMESTAMP
            """)
    long countActivasDeFicha(@Param("fichaId") UUID fichaId);

    /**
     * RF-18: las citas cuyo inicio cae en el rango, filtrables por odontólogo y
     * por estado. Los dos filtros son opcionales —{@code null} significa «todos»—
     * con el patrón {@code :param IS NULL OR …}, que deja la consulta en una sola
     * pieza en vez de repartirla entre cuatro métodos o un Specification.
     *
     * <p>El {@code @EntityGraph} evita el N+1 de pintar paciente, odontólogo,
     * tratamiento y consultorio de cada fila. Las cuatro asociaciones son
     * {@code @ManyToOne}, así que Hibernate las resuelve con JOIN sin romper la
     * paginación; un {@code JOIN FETCH} sobre una colección sí la habría roto,
     * paginando en memoria.
     *
     * <p>El orden lo impone el {@link org.springframework.data.domain.Pageable},
     * que el controlador fija por defecto en {@code inicio}: «ordenadas por hora»
     * es el criterio de aceptación.
     */
    @EntityGraph(attributePaths = {"ficha", "odontologo", "tratamiento", "consultorio"})
    @Query("""
            SELECT c FROM Cita c
            WHERE c.inicio >= :desde
              AND c.inicio < :hasta
              AND (:odontologoId IS NULL OR c.odontologo.id = :odontologoId)
              AND (:estado IS NULL OR c.estado = :estado)
            """)
    Page<Cita> buscar(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta,
            @Param("odontologoId") UUID odontologoId, @Param("estado") String estado,
            Pageable pageable);

    /**
     * RF-15: correlativo del codigo de la cita. Sale de la secuencia de la base
     * —no de un COUNT— por la misma razon que `numeroHistoria`: dos reservas
     * simultaneas obtendrian el mismo numero.
     */
    @Query(value = "SELECT nextval('sq_codigo_cita')", nativeQuery = true)
    Long getNextCodigoCita();
}
