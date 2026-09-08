package pe.edu.dentalcite.ficha.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import pe.edu.dentalcite.ficha.domain.Ficha;

import java.util.Optional;
import java.util.UUID;

public interface FichaRepository extends JpaRepository<Ficha, UUID> {
    // RN-10: la identidad es el par (tipo, número), no el número suelto.
    Optional<Ficha> findByTipoDocumentoAndDocumento(String tipoDocumento, String documento);
    boolean existsByTipoDocumentoAndDocumento(String tipoDocumento, String documento);

    @Query(value = "SELECT nextval('sq_historia_clinica')", nativeQuery = true)
    Long getNextHistoriaClinica();
}
