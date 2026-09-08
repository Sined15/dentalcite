package pe.edu.dentalcite.usuario.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.util.Optional;
import java.util.UUID;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {
    Optional<Usuario> findByCorreo(String correo);
    boolean existsByCorreo(String correo);
    boolean existsByFichaId(UUID fichaId);
    Optional<Usuario> findByFichaId(UUID fichaId);

    @Query("SELECT u.tokensValidosDesde FROM Usuario u WHERE u.id = :id")
    Optional<java.time.OffsetDateTime> findTokensValidosDesdeById(@Param("id") UUID id);
}
