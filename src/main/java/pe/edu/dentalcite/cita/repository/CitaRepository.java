package pe.edu.dentalcite.cita.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.ficha.domain.Ficha;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CitaRepository extends JpaRepository<Cita, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Cita c WHERE c.id = :id")
    Optional<Cita> findParaTransicion(@Param("id") UUID id);

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

    @Query("""
            SELECT COUNT(c) FROM Cita c
            WHERE c.ficha.id = :fichaId
              AND c.estado = 'CONFIRMADA'
              AND c.fin > CURRENT_TIMESTAMP
            """)
    long countActivasDeFicha(@Param("fichaId") UUID fichaId);

    @EntityGraph(attributePaths = {"ficha", "odontologo", "tratamiento", "consultorio",
            "creadoPor", "creadoPor.ficha"})
    @Query("""
            SELECT c FROM Cita c
            WHERE c.inicio >= COALESCE(:desde, c.inicio)
              AND c.inicio < COALESCE(:hasta, c.fin)
              AND (:fichaId IS NULL OR c.ficha.id = :fichaId)
              AND (:odontologoId IS NULL OR c.odontologo.id = :odontologoId)
              AND (:fichaDelOdontologoId IS NULL
                   OR c.odontologo.ficha.id = :fichaDelOdontologoId)
              AND (:estado IS NULL OR c.estado = :estado)
            """)
    Page<Cita> buscar(@Param("desde") OffsetDateTime desde, @Param("hasta") OffsetDateTime hasta,
            @Param("fichaId") UUID fichaId,
            @Param("odontologoId") UUID odontologoId,
            @Param("fichaDelOdontologoId") UUID fichaDelOdontologoId,
            @Param("estado") String estado,
            Pageable pageable);

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

    @EntityGraph(attributePaths = {"ficha", "odontologo", "tratamiento", "consultorio",
            "creadoPor", "creadoPor.ficha"})
    List<Cita> findByFichaIdOrderByInicioDesc(UUID fichaId);

    @Query("""
            SELECT COUNT(c) > 0 FROM Cita c
            WHERE c.odontologo.ficha.id = :fichaDelOdontologoId
              AND c.ficha.id = :fichaDelPacienteId
              AND c.estado <> 'CANCELADA'
            """)
    boolean atendioAPorFichaDelOdontologo(@Param("fichaDelOdontologoId") UUID fichaDelOdontologoId,
            @Param("fichaDelPacienteId") UUID fichaDelPacienteId);

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
              AND NOT EXISTS (SELECT 1 FROM Odontologo o WHERE o.ficha = f)
            """)
    Page<Ficha> buscarPacientesDeOdontologo(@Param("termino") String termino,
            @Param("prefijo") String prefijo,
            @Param("fichaDelOdontologoId") UUID fichaDelOdontologoId,
            Pageable pageable);

    @Query("""
            SELECT c FROM Cita c
            WHERE c.ficha.id = :fichaId
              AND c.tratamiento.id = :tratamientoId
              AND c.estado = 'ATENDIDA'
              AND c.inicio >= :desde
              AND c.inicio < :hasta
              AND NOT EXISTS (SELECT 1 FROM PlanSesion s WHERE s.cita = c)
            ORDER BY c.inicio ASC
            """)
    List<Cita> atendidasSinSesion(@Param("fichaId") UUID fichaId,
            @Param("tratamientoId") UUID tratamientoId,
            @Param("desde") OffsetDateTime desde,
            @Param("hasta") OffsetDateTime hasta);

    @Query(value = "SELECT nextval('sq_codigo_cita')", nativeQuery = true)
    Long getNextCodigoCita();
}
