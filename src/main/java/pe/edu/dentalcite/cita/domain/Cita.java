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
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "citas")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cita {

    public static final String ESTADO_CONFIRMADA = "CONFIRMADA";
    public static final String ESTADO_CANCELADA = "CANCELADA";
    public static final String ESTADO_ATENDIDA = "ATENDIDA";
    public static final String ESTADO_NO_ASISTIO = "NO_ASISTIO";

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
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creado_por_usuario_id")
    private Usuario creadoPor;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;
}
