package pe.edu.dentalcite.cita.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.ficha.domain.Ficha;

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
     * RF-18: las citas cuyo inicio cae en el rango, filtrables por odontólogo,
     * por estado y por paciente. <strong>Los cinco filtros son opcionales</strong>
     * —{@code null} significa «todos»— con el patrón {@code :param IS NULL OR …},
     * que deja la consulta en una sola pieza en vez de repartirla entre varios
     * métodos o un Specification.
     *
     * <p>La sirven dos pantallas: la agenda de la clínica (HU-11), que siempre
     * acota el rango y nunca la ficha, y «mis citas» (HU-15), que es al revés
     * —fija la ficha y no acota el rango, porque el criterio pide ver «las
     * futuras y las pasadas»—. Tener una sola consulta es lo que hace que el
     * «ninguna de otro paciente» de HU-15 sea estructural: quien llama pasa la
     * ficha del token y no hay forma de que la consulta devuelva otra.
     *
     * <p>El {@code @EntityGraph} evita el N+1 de pintar paciente, odontólogo,
     * tratamiento y consultorio de cada fila. Todas las asociaciones son
     * {@code @ManyToOne} o {@code @OneToOne}, así que Hibernate las resuelve con
     * JOIN sin romper la paginación; un {@code JOIN FETCH} sobre una colección sí
     * la habría roto, paginando en memoria. {@code creadoPor.ficha} entra por
     * HU-15: sin él, decidir si cada fila la reservó el propio paciente costaría
     * dos consultas por cita.
     *
     * <p>Los dos extremos del rango se comparan con {@code COALESCE} y no con el
     * {@code :param IS NULL OR …} de los demás filtros, y no por gusto: un
     * parámetro que aparece suelto en {@code ? IS NULL} no tiene ningún tipo que
     * PostgreSQL pueda inferir, y con un instante a nulo la consulta falla antes
     * de ejecutarse con «could not determine data type of parameter». Dentro del
     * {@code COALESCE} el tipo lo da la columna. Los otros tres filtros no
     * padecen el problema porque su nulo sí llega tipado desde el controlador.
     *
     * <p>El sustituto del extremo superior es {@code c.fin}, que siempre es
     * mayor que {@code c.inicio} —la duración es un múltiplo positivo de quince
     * minutos (RN-04) y el {@code tstzrange} de {@code V13} no admitiría lo
     * contrario—, de modo que sin {@code hasta} la condición es siempre cierta.
     *
     * <p>El orden lo impone el {@link org.springframework.data.domain.Pageable},
     * que cada controlador fija por defecto: «ordenadas por hora» es el criterio
     * de aceptación de la agenda.
     */
    @EntityGraph(attributePaths = {"ficha", "odontologo", "tratamiento", "consultorio",
            "creadoPor", "creadoPor.ficha"})
    @Query("""
            SELECT c FROM Cita c
            WHERE c.inicio >= COALESCE(:desde, c.inicio)
              AND c.inicio < COALESCE(:hasta, c.fin)
              AND (:fichaId IS NULL OR c.ficha.id = :fichaId)
              AND (:odontologoId IS NULL OR c.odontologo.id = :odontologoId)
              AND (:estado IS NULL OR c.estado = :estado)
            """)
    Page<Cita> buscar(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta,
            @Param("fichaId") UUID fichaId,
            @Param("odontologoId") UUID odontologoId, @Param("estado") String estado,
            Pageable pageable);

    /**
     * RF-22, HU-16: las citas <strong>pendientes de cierre</strong>, es decir las
     * confirmadas cuya hora de fin ya pasó y que siguen sin resultado.
     *
     * <p>RN-09 las define exactamente así, y es la definición complementaria a la
     * de «activa» que usan las otras consultas: el mismo {@code CURRENT_TIMESTAMP}
     * separa unas de otras, con la desigualdad al revés. No hace falta comprobar
     * «sin resultado» aparte: registrar el resultado saca la cita de CONFIRMADA,
     * de modo que estar confirmada <em>es</em> seguir sin él.
     *
     * <p>El filtro por ficha del odontólogo es opcional —{@code null} significa
     * «todas»— y sirve al «(la propia)» del contrato: recepción y administración
     * ven la clínica entera, el odontólogo solo lo suyo. Se pregunta por su ficha
     * y no por su registro por lo mismo que en
     * {@link #atendioAPorFichaDelOdontologo}: de una cuenta a su registro de
     * odontólogo solo se llega por la ficha que RN-11 comparte entre ambos.
     *
     * <p>Se ordenan de la más antigua a la más reciente porque es una cola de
     * trabajo: lo que lleva más tiempo sin cerrar es lo que primero hay que
     * cerrar.
     */
    @EntityGraph(attributePaths = {"ficha", "odontologo", "tratamiento", "consultorio",
            "creadoPor", "creadoPor.ficha"})
    @Query("""
            SELECT c FROM Cita c
            WHERE c.estado = 'CONFIRMADA'
              AND c.fin < CURRENT_TIMESTAMP
              AND (:fichaDelOdontologoId IS NULL
                   OR c.odontologo.ficha.id = :fichaDelOdontologoId)
            ORDER BY c.inicio ASC
            """)
    Page<Cita> pendientesDeCierre(@Param("fichaDelOdontologoId") UUID fichaDelOdontologoId,
            Pageable pageable);

    /**
     * RF-08, HU-13: las citas de una ficha, pasadas y futuras, de la más reciente
     * a la más antigua.
     *
     * <p>Vive aquí y no en {@code FichaRepository} por la dirección de la
     * dependencia: {@code cita} ya conoce a {@code ficha}, mientras que meter una
     * consulta sobre {@code Cita} en el repositorio de fichas haría que el
     * dominio base del que cuelgan auth, usuario y odontólogo dependiese a su vez
     * de citas.
     *
     * <p>El {@code @EntityGraph} evita el N+1 al pintar la ficha, por el mismo
     * motivo que en {@link #buscar}. Incluye {@code ficha} aunque quien llama ya
     * la tenga, porque el mapeador que se reutiliza la lee de cada cita.
     */
    @EntityGraph(attributePaths = {"ficha", "odontologo", "tratamiento", "consultorio",
            "creadoPor", "creadoPor.ficha"})
    List<Cita> findByFichaIdOrderByInicioDesc(UUID fichaId);

    /**
     * HU-13, criterio 4: si este odontólogo ha tenido alguna cita con esta
     * persona. Es lo que decide el 403 de RNF-06.
     *
     * <p>Una cita CANCELADA no cuenta: nunca llegó a existir como consulta, y
     * dejar que abra la ficha —alergias incluidas— para siempre sería regalar el
     * acceso a cambio de una reserva que el paciente deshizo. Sí cuenta la
     * CONFIRMADA todavía futura, que es justo cuando el profesional necesita
     * consultarla para preparar la sesión.
     *
     * <p>Se pregunta por la <em>ficha</em> del odontólogo y no por su registro
     * porque quien llama parte de una cuenta autenticada, y de una cuenta a su
     * registro de odontólogo solo se llega por la ficha que RN-11 comparte entre
     * ambos. Resolverlo aquí ahorra la consulta intermedia.
     */
    @Query("""
            SELECT COUNT(c) > 0 FROM Cita c
            WHERE c.odontologo.ficha.id = :fichaDelOdontologoId
              AND c.ficha.id = :fichaDelPacienteId
              AND c.estado <> 'CANCELADA'
            """)
    boolean atendioAPorFichaDelOdontologo(@Param("fichaDelOdontologoId") UUID fichaDelOdontologoId,
            @Param("fichaDelPacienteId") UUID fichaDelPacienteId);

    /**
     * RF-07 restringido a «sus pacientes»: el mismo listado que
     * {@code FichaRepository.buscar}, acotado a las personas con las que este
     * odontólogo ha tenido cita. Devuelve {@code Ficha} y no {@code Cita} porque
     * lo que se lista son pacientes; el {@code EXISTS} evita el duplicado que
     * daría unir por citas.
     */
    @Query("""
            SELECT f FROM Ficha f
            WHERE (:termino IS NULL
                   OR LOWER(f.apellidos) LIKE :prefijo
                   OR f.documento = :termino
                   OR UPPER(f.numeroHistoria) = UPPER(:termino))
              AND EXISTS (SELECT 1 FROM Cita c
                          WHERE c.ficha = f
                            AND c.odontologo.ficha.id = :fichaDelOdontologoId
                            AND c.estado <> 'CANCELADA')
            """)
    Page<Ficha> buscarPacientesDeOdontologo(@Param("termino") String termino,
            @Param("prefijo") String prefijo,
            @Param("fichaDelOdontologoId") UUID fichaDelOdontologoId,
            Pageable pageable);

    /**
     * RF-15: correlativo del codigo de la cita. Sale de la secuencia de la base
     * —no de un COUNT— por la misma razon que `numeroHistoria`: dos reservas
     * simultaneas obtendrian el mismo numero.
     */
    @Query(value = "SELECT nextval('sq_codigo_cita')", nativeQuery = true)
    Long getNextCodigoCita();
}
