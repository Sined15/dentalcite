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

    @Column(name = "estado_anterior", length = 20)
    private String estadoAnterior;

    @Column(name = "estado_nuevo", nullable = false, length = 20)
    private String estadoNuevo;

    @Column(length = 255)
    private String motivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @CreationTimestamp
    @Column(name = "ocurrido_en", updatable = false)
    private OffsetDateTime ocurridoEn;
}
