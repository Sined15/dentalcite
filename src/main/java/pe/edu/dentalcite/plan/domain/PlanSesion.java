package pe.edu.dentalcite.plan.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una sesión del plan (HU-17 · RF-23 · RN-15).
 *
 * <p>RN-15 le da tres estados: <strong>pendiente</strong> sin cita enlazada,
 * <strong>atendida</strong> cuando la cita enlazada lo alcanza y
 * <strong>cerrada</strong> al registrarse su recomendación. HU-17 solo escribe
 * el primero; los otros dos llegan con HU-18 y HU-19, y las constantes están ya
 * porque los estados son del modelo y no de la historia que los estrena.
 *
 * <p>Es una fila y no un contador en {@link Plan} porque RN-16 exige que «una
 * sesión admita como máximo una cita enlazada, y una cita como máximo una
 * sesión»: eso es una relación entre entidades, y un número no la sostiene.
 */
@Entity
@Table(name = "plan_sesiones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanSesion {

    /** RN-15: sin cita enlazada. Es como nacen todas (HU-17). */
    public static final String ESTADO_PENDIENTE = "PENDIENTE";

    /** RN-15: la cita enlazada llegó a ATENDIDA. Lo alcanza HU-18. */
    public static final String ESTADO_ATENDIDA = "ATENDIDA";

    /** RN-15: se registró su recomendación. Lo alcanza HU-19. */
    public static final String ESTADO_CERRADA = "CERRADA";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    /**
     * Desde 1 y sin huecos. El orden importa: HU-18 enlazará cada cita atendida
     * a «la primera sesión pendiente en orden» (RN-15).
     */
    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = ESTADO_PENDIENTE;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;
}
