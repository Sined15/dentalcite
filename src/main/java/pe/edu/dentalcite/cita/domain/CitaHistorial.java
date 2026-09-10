package pe.edu.dentalcite.cita.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Una transición de la máquina de estados de una cita (RF-21).
 *
 * <p>Es la bitácora que RN-12 hace necesaria: como ninguna cita se borra, la
 * pregunta operativa deja de ser «¿existe?» y pasa a ser «¿qué le pasó, cuándo y
 * por orden de quién?». {@code Cita.motivoCancelacion} guarda el último motivo
 * para poder mostrarlo sin una consulta más, pero no responde a eso.
 *
 * <p>El alta se registra con {@code estadoAnterior} nulo: la cita nace
 * CONFIRMADA (RN-09) y esa es su primera transición.
 */
@Entity
@Table(name = "citas_historial")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitaHistorial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id", nullable = false)
    private Cita cita;

    /** Nulo en el alta. */
    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private String estadoNuevo;

    @Column(length = 255)
    private String motivo;

    /** Nulo cuando la transición no la origina una persona identificada. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "ocurrido_en", updatable = false)
    private OffsetDateTime ocurridoEn;
}
