package pe.edu.dentalcite.usuario.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByCorreo(String correo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM Usuario u WHERE u.correo = :correo")
    Optional<Usuario> findParaLoginByCorreo(@Param("correo") String correo);
    boolean existsByCorreo(String correo);
    boolean existsByFichaId(UUID fichaId);
    Optional<Usuario> findByFichaId(UUID fichaId);

    @Query("SELECT u.tokensValidosDesde FROM Usuario u WHERE u.id = :id")
    Optional<java.time.OffsetDateTime> findTokensValidosDesdeById(@Param("id") UUID id);

    @Query("SELECT u.ficha.id FROM Usuario u WHERE u.ficha.id IN :fichaIds")
    java.util.Set<UUID> fichasConCuenta(@Param("fichaIds") java.util.Collection<UUID> fichaIds);
}
