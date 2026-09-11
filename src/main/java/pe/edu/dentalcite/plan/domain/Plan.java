package pe.edu.dentalcite.plan.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Plan de tratamiento: en cuántas sesiones se dividirá un tratamiento largo
 * (HU-17 · RF-23).
 *
 * <p><strong>No guarda el avance.</strong> RN-14 lo dice sin margen: «el avance
 * de un plan se deriva siempre de las citas atendidas enlazadas a él; nunca se
 * persiste ni se edita a mano». Aquí solo están las sesiones <em>previstas</em>,
 * que es el compromiso que se le explica al paciente; cuántas se han cumplido lo
 * calculará HU-18 recorriendo {@link PlanSesion}.
 *
 * <p><strong>No se borra.</strong> RN-12: «en pacientes y planes la baja lógica
 * es una marca de actividad». Suspender baja {@link #activo} y conserva la fila
 * con su motivo, y es además lo que devuelve ese tratamiento al conjunto de los
 * planificables, porque el índice único de RN-13 es parcial sobre esa marca.
 */
@Entity
@Table(name = "planes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** De quién es el plan. Es la ficha y no la cuenta: RF-23 planifica sobre la historia clínica. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ficha_id", nullable = false)
    private Ficha ficha;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tratamiento_id", nullable = false)
    private Tratamiento tratamiento;

    /** Quién lo planificó. RF-26 lo necesitará para decidir quién puede consultarlo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "odontologo_id", nullable = false)
    private Odontologo odontologo;

    @Column(name = "sesiones_previstas", nullable = false)
    private Integer sesionesPrevistas;

    /** RN-12: la baja lógica del plan. Nace activo. */
    @Column(nullable = false)
    @Builder.Default
    private Boolean activo = true;

    /**
     * Por qué se suspendió. La base exige que vayan juntos: un plan activo no
     * puede tener motivo y uno suspendido no puede carecer de él.
     */
    @Column(name = "motivo_suspension", length = 255)
    private String motivoSuspension;

    /**
     * Las sesiones, numeradas desde 1. Se crean todas al nacer el plan —«quedará
     * ACTIVO con sus sesiones numeradas y todas pendientes»—, no según se van
     * cumpliendo: el paciente tiene que saber desde el principio a qué se
     * compromete, que es la razón de ser de la historia.
     *
     * <p>{@code CascadeType.ALL} porque una sesión no existe fuera de su plan, y
     * {@code orphanRemoval} por lo mismo.
     */
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @OrderBy("numero ASC")
    @Builder.Default
    private List<PlanSesion> sesiones = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    /**
     * Crea las sesiones previstas, todas pendientes, y las engancha a este plan.
     *
     * <p>Vive en la entidad y no en el servicio porque numerar las sesiones es
     * una invariante del plan, no un paso del caso de uso: un plan sin ellas
     * estaría a medio construir, y la regla de cobertura del {@code pom.xml}
     * alcanza igual a {@code *.domain}.
     */
    public void generarSesiones() {
        sesiones.clear();
        for (int numero = 1; numero <= sesionesPrevistas; numero++) {
            sesiones.add(PlanSesion.builder()
                    .plan(this)
                    .numero(numero)
                    .estado(PlanSesion.ESTADO_PENDIENTE)
                    .build());
        }
    }

    /**
     * RN-12: suspender no borra nada. Baja la marca de actividad y deja dicho por
     * qué, que es lo que la base exige que vaya junto.
     */
    public void suspender(String motivo) {
        this.activo = false;
        this.motivoSuspension = motivo;
    }
}
