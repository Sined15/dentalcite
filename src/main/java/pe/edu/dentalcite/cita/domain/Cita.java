package pe.edu.dentalcite.cita.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Cita de la agenda.
 *
 * <p>RN-09 define su máquina de estados: nace CONFIRMADA (HU-09) y transita a uno
 * de tres estados finales, sin retorno. Hoy están construidas la reserva y la
 * cancelación (HU-11); ATENDIDA y NO_ASISTIO llegan con HU-16. Cada transición se
 * registra en {@link CitaHistorial} (RF-21), y ninguna borra la fila: la baja de
 * la cita es su estado CANCELADA (RN-12).
 *
 * <p>Se llama <strong>activa</strong> a la confirmada cuya hora de fin no ha
 * pasado. Es la única que ocupa odontólogo y consultorio, la única que cuentan
 * RN-01, RN-02 y RN-07, y la única que ve el motor de disponibilidad.
 */
@Entity
@Table(name = "citas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cita {

    /** RN-09: la cita nace confirmada y transita a uno de tres estados finales. */
    public static final String ESTADO_CONFIRMADA = "CONFIRMADA";

    /**
     * Estado final tras una cancelación (HU-11, HU-15). Libera la franja en el
     * acto: las restricciones de exclusión de {@code V13} son parciales sobre
     * CONFIRMADA, así que dejan de aplicar en cuanto el estado cambia.
     */
    public static final String ESTADO_CANCELADA = "CANCELADA";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ficha_id", nullable = false)
    private Ficha ficha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "odontologo_id", nullable = false)
    private Odontologo odontologo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tratamiento_id", nullable = false)
    private Tratamiento tratamiento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "consultorio_id", nullable = false)
    private Consultorio consultorio;

    @Column(nullable = false)
    private OffsetDateTime inicio;

    @Column(nullable = false)
    private OffsetDateTime fin;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = ESTADO_CONFIRMADA;

    @Column(name = "motivo_cancelacion", length = 255)
    private String motivoCancelacion;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;
}
