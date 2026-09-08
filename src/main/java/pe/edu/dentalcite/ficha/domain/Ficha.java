package pe.edu.dentalcite.ficha.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "fichas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ficha {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // RN-10: el paciente se identifica por el par (tipo, número) de documento;
    // la unicidad vive en la restricción uq_ficha_documento de la tabla.
    @Column(name = "tipo_documento", nullable = false, length = 20)
    @Builder.Default
    private String tipoDocumento = "DNI";

    @Column(nullable = false)
    private String documento;

    @Column(length = 100)
    private String nombres;

    @Column(length = 100)
    private String apellidos;

    private String telefono;

    @Column(name = "numero_historia", nullable = false, unique = true)
    private String numeroHistoria;

    /**
     * {@code @Builder.Default} solo alimenta al builder: un {@code new Ficha()}
     * dejaría el tipo a null y violaría el NOT NULL de la columna. Este gancho
     * cubre ambas formas de construir la entidad.
     */
    @PrePersist
    public void aplicarTipoDocumentoPorDefecto() {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            tipoDocumento = "DNI";
        }
    }
}
