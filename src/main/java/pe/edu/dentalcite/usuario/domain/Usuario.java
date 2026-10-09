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

    public void revocarTokensVigentes() {
        OffsetDateTime siguienteSegundo = OffsetDateTime.now()
                .truncatedTo(ChronoUnit.SECONDS)
                .plusSeconds(1);
        tokensValidosDesde = (tokensValidosDesde == null || siguienteSegundo.isAfter(tokensValidosDesde))
                ? siguienteSegundo
                : tokensValidosDesde.plusSeconds(1);
    }

    @PrePersist
    @PreUpdate
    void normalizarVigenciaDeTokens() {
        if (tokensValidosDesde != null) {
            tokensValidosDesde = tokensValidosDesde.truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
