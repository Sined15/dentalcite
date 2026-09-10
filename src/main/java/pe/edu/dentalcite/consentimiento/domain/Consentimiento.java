package pe.edu.dentalcite.consentimiento.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "consentimientos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Consentimiento {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * La cuenta que firmó el consentimiento, cuando la hay. Es nulo en el alta
     * presencial de HU-12: ese paciente no tiene cuenta, y exigirle una para
     * poder registrar su consentimiento invertiría RNF-06 —lo convertiría en un
     * requisito de las altas con credenciales en vez de en uno de todas—.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * La ficha del titular. La rellenan las dos vías de alta —el portal junto
     * con el usuario, el mostrador en solitario—, de modo que preguntar por el
     * consentimiento de una persona no depende de por dónde entró.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ficha_id")
    private Ficha ficha;

    @Builder.Default
    @Column(nullable = false)
    private OffsetDateTime fecha = OffsetDateTime.now();

    @Column(name = "version_texto", nullable = false)
    private String versionTexto;
}
