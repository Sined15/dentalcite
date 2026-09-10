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

    /**
     * Cuáles de estas fichas tienen cuenta. Una consulta para toda la página del
     * listado de pacientes (HU-13): preguntarlo ficha a ficha con
     * {@link #existsByFichaId} son veinte consultas por página, y RNF-02 mide
     * justo esa pantalla.
     */
    @Query("SELECT u.ficha.id FROM Usuario u WHERE u.ficha.id IN :fichaIds")
    java.util.Set<UUID> fichasConCuenta(@Param("fichaIds") java.util.Collection<UUID> fichaIds);
}
