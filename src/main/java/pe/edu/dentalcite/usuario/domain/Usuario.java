package pe.edu.dentalcite.usuario.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import pe.edu.dentalcite.ficha.domain.Ficha;
import java.util.UUID;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false)
    private String contrasenaHash;

    @Column(nullable = false)
    private String rol;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @Builder.Default
    @Column(name = "requiere_cambio_password", nullable = false)
    private Boolean requiereCambioPassword = false;

    @Builder.Default
    @Column(name = "tokens_validos_desde", nullable = false)
    private OffsetDateTime tokensValidosDesde = OffsetDateTime.now();

    // RNF-05: respaldo persistente del bloqueo por fuerza bruta (ver AuthService),
    // usado cuando la caché rápida en Redis no está disponible.
    @Builder.Default
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    @Column(name = "bloqueado_hasta")
    private OffsetDateTime bloqueadoHasta;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ficha_id", unique = true)
    private Ficha ficha;

    /**
     * Invalida todos los tokens emitidos hasta ahora: logout, cambio de rol,
     * desactivacion y cambio de contrasena (HU-03, HU-04).
     *
     * <p>El claim {@code iat} de un JWT solo distingue segundos, asi que la marca
     * se lleva al inicio del segundo siguiente: todo token emitido hasta el final
     * de este segundo queda invalidado. Con {@code now()} truncado hacia abajo, el
     * token emitido en el mismo segundo del logout seguia siendo aceptado.
     *
     * <p>Si la marca ya apunta a ese instante --dos revocaciones dentro del mismo
     * segundo-- avanza un segundo mas. Sin ese avance no superaria el {@code iat}
     * del token emitido entre ambas, y la segunda revocacion no surtiria efecto.
     */
    public void revocarTokensVigentes() {
        OffsetDateTime siguienteSegundo = OffsetDateTime.now()
                .truncatedTo(ChronoUnit.SECONDS)
                .plusSeconds(1);
        tokensValidosDesde = (tokensValidosDesde == null || siguienteSegundo.isAfter(tokensValidosDesde))
                ? siguienteSegundo
                : tokensValidosDesde.plusSeconds(1);
    }

    /**
     * La marca se guarda con precision de segundo, la misma del claim {@code iat}
     * contra el que se compara.
     */
    @PrePersist
    @PreUpdate
    void normalizarVigenciaDeTokens() {
        if (tokensValidosDesde != null) {
            tokensValidosDesde = tokensValidosDesde.truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
