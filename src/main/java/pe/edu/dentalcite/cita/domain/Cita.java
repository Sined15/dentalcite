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
 * Cita de la agenda. En el Sprint 1 solo se lee: las reglas RN-12 (no dar de baja
 * un odontólogo o tratamiento con citas activas) y RN-03 (no aplicar un bloqueo
 * que alcance citas activas) la consultan. La reserva y la máquina de estados
 * completas son del Sprint 2 (HU-09 a HU-11).
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
