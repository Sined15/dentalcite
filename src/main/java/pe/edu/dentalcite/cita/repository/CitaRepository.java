package pe.edu.dentalcite.cita.repository;

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
}
