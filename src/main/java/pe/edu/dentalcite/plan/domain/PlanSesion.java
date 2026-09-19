package pe.edu.dentalcite.plan.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Una sesión del plan (HU-17 · RF-23 · RN-15).
 *
 * <p>RN-15 le da tres estados: <strong>pendiente</strong> sin cita enlazada,
 * <strong>atendida</strong> cuando la cita enlazada lo alcanza y
 * <strong>cerrada</strong> al registrarse su recomendación. Todas nacen
 * pendientes, y pasan a atendidas cuando una cita atendida las ocupa; el estado
 * cerrado llegará con el registro de recomendaciones, y la constante está ya
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

    /** RN-15: la ocupa una cita que ya fue atendida. */
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
     * Desde 1 y sin huecos. El orden importa: una cita atendida ocupa siempre la
     * primera sesión pendiente, nunca una cualquiera.
     */
    @Column(nullable = false)
    private Integer numero;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String estado = ESTADO_PENDIENTE;

    /**
     * La cita que ocupa esta sesión. Nula mientras está pendiente, y la base lo
     * exige en los dos sentidos: una sesión pendiente no tiene cita y una atendida
     * no puede quedarse sin ella.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cita_id", unique = true)
    private Cita cita;

    /**
     * Los cuidados que se le indicaron al paciente al terminar la sesión. Son filas
     * y no un texto libre porque se eligen de un catálogo cerrado, y una sesión
     * puede llevar varios.
     *
     * <p>Un {@code Set} y no una lista: las sesiones de {@link Plan} ya son una
     * bolsa, y dos bolsas en el mismo grafo de entidades Hibernate no las resuelve
     * de una sola consulta.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "plan_sesion_recomendaciones",
            joinColumns = @JoinColumn(name = "sesion_id"),
            inverseJoinColumns = @JoinColumn(name = "recomendacion_id"))
    @OrderBy("descripcion ASC")
    @Builder.Default
    private Set<Recomendacion> recomendaciones = new LinkedHashSet<>();

    /**
     * Cuándo se sugiere volver. Nula mientras la sesión no está cerrada, y la base
     * exige que esté en cuanto lo está.
     */
    @Column(name = "proximo_control")
    private LocalDate proximoControl;

    /** Un cuidado escrito a mano, si lo hubo. No es una indicación clínica. */
    @Column(length = 300)
    private String observacion;

    @CreationTimestamp
    @Column(name = "creado_en", updatable = false)
    private OffsetDateTime creadoEn;

    @UpdateTimestamp
    @Column(name = "actualizado_en")
    private OffsetDateTime actualizadoEn;

    /** Todavía sin cita: es la única que una cita atendida puede ocupar. */
    public boolean estaPendiente() {
        return ESTADO_PENDIENTE.equals(estado);
    }

    /**
     * Ocupa la sesión con una cita ya atendida.
     *
     * <p>Qué sesión ocupar lo decide {@link Plan}, que es quien conoce el orden;
     * aquí solo se impide pisar una que ya tiene cita, porque eso borraría en
     * silencio la consulta que la ocupaba.
     */
    public void enlazar(Cita citaAtendida) {
        if (!estaPendiente()) {
            throw new IllegalStateException("La sesion " + numero + " ya esta ocupada por otra cita.");
        }
        this.cita = citaAtendida;
        this.estado = ESTADO_ATENDIDA;
    }

    /** La ocupa una cita atendida y todavía no se ha dicho qué cuidados seguir. */
    public boolean estaAtendida() {
        return ESTADO_ATENDIDA.equals(estado);
    }

    /**
     * Falla si la sesión no está en condiciones de cerrarse, y con el motivo que
     * corresponda.
     *
     * <p>Es público y separado de {@link #cerrar} porque quien atiende la petición
     * tiene que dar ese veredicto <em>antes</em> de mirar el catálogo: así una
     * sesión que no se puede cerrar responde eso, y no que sus recomendaciones son
     * inválidas.
     */
    public void verificarQueSePuedeCerrar() {
        if (estaPendiente()) {
            throw new IllegalStateException(
                    "La sesion " + numero + " todavia no tiene una cita atendida.");
        }
        if (!estaAtendida()) {
            throw new IllegalStateException("La sesion " + numero + " ya se cerro.");
        }
    }

    /**
     * Registra lo que el odontólogo indica al terminar la sesión y la da por
     * cerrada.
     *
     * <p>Solo se cierra lo que se ha atendido, y los dos rechazos son distintos a
     * propósito: una sesión pendiente no tiene ninguna consulta de la que hablar, y
     * una ya cerrada tiene sus recomendaciones dadas —cerrarla otra vez las
     * sustituiría en silencio, que es lo mismo que {@link #enlazar} impide con la
     * cita—.
     */
    public void cerrar(Set<Recomendacion> indicadas, LocalDate proximoControl, String observacion) {
        verificarQueSePuedeCerrar();
        this.recomendaciones = new LinkedHashSet<>(indicadas);
        this.proximoControl = proximoControl;
        this.observacion = observacion;
        this.estado = ESTADO_CERRADA;
    }
}
