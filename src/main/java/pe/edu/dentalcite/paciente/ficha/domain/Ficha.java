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

    @Column(columnDefinition = "text")
    private String alergias;

    @PrePersist
    public void aplicarTipoDocumentoPorDefecto() {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            tipoDocumento = "DNI";
        }
    }
}
