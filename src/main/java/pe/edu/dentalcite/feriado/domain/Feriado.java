package pe.edu.dentalcite.feriado.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Feriado nacional. La tabla existe y se siembra desde {@code V1__init.sql}, pero
 * hasta HU-08 nadie la leía: RN-03 exige que la cita quede «fuera de sus bloqueos
 * y de los generales, feriados incluidos», y el motor de disponibilidad es el
 * primero que necesita consultarla.
 *
 * <p>Como {@code consultorio} o {@code ficha}, el dominio solo tiene entidad y
 * repositorio: son datos semilla sin pantalla de mantenimiento (F-12).
 */
@Entity
@Table(name = "feriados")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Feriado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(nullable = false, length = 150)
    private String descripcion;
}
