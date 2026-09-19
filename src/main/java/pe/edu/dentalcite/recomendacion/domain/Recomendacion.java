package pe.edu.dentalcite.recomendacion.domain;

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

import java.util.UUID;

/**
 * Un cuidado del catálogo cerrado que el odontólogo indica al cerrar una sesión.
 *
 * <p>La tabla existe desde la primera migración, sembrada con seis cuidados; lo que
 * no existía hasta ahora era quien la leyera. Es <strong>cerrado</strong>: no se
 * escriben recomendaciones a mano, se eligen de aquí, y lo que el odontólogo quiera
 * añadir con sus palabras va en la observación de la sesión.
 *
 * <p>Sin mantenimiento por interfaz, como los consultorios: se retira una marcándola
 * inactiva, nunca borrándola, porque las sesiones ya cerradas siguen apuntando a
 * ella.
 */
@Entity
@Table(name = "recomendaciones")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Recomendacion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 255)
    private String descripcion;

    @Column(nullable = false)
    @Builder.Default
    private Boolean activa = true;
}
