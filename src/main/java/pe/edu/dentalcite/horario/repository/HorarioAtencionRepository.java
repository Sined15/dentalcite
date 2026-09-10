package pe.edu.dentalcite.horario.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;

import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface HorarioAtencionRepository extends JpaRepository<HorarioAtencion, UUID> {
    
    List<HorarioAtencion> findByOdontologoId(UUID odontologoId);
    
    @Query("SELECT h FROM HorarioAtencion h WHERE h.odontologo.id = :odontologoId AND h.diaSemana = :diaSemana AND " +
           "(h.horaInicio < :horaFin AND h.horaFin > :horaInicio) AND (:id IS NULL OR h.id != :id)")
    List<HorarioAtencion> findOverlappingHorarios(@Param("odontologoId") UUID odontologoId, 
                                                  @Param("diaSemana") Integer diaSemana, 
                                                  @Param("horaInicio") LocalTime horaInicio, 
                                                  @Param("horaFin") LocalTime horaFin,
                                                  @Param("id") UUID id);

    /**
     * Horario de varios odontólogos en una sola consulta. El motor de
     * disponibilidad (HU-08) recorre hasta catorce días de cinco agendas: pedirlo
     * odontólogo a odontólogo sería el N+1 que RNF-01 no tolera.
     */
    List<HorarioAtencion> findByOdontologoIdIn(Collection<UUID> odontologoIds);
}
