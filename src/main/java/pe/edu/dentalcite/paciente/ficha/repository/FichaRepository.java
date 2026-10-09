package pe.edu.dentalcite.ficha.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pe.edu.dentalcite.ficha.domain.Ficha;

import java.util.Optional;
import java.util.UUID;

public interface FichaRepository extends JpaRepository<Ficha, UUID> {
    
    Optional<Ficha> findByTipoDocumentoAndDocumento(String tipoDocumento, String documento);
    boolean existsByTipoDocumentoAndDocumento(String tipoDocumento, String documento);

    @Query(value = "SELECT nextval('sq_historia_clinica')", nativeQuery = true)
    Long getNextHistoriaClinica();

    @Query("""
            SELECT f FROM Ficha f
            WHERE (:termino IS NULL
                   OR LOWER(f.apellidos) LIKE :prefijo
                   OR f.documento = :termino
                   OR UPPER(f.numeroHistoria) = UPPER(:termino))
              AND NOT EXISTS (SELECT 1 FROM Odontologo o WHERE o.ficha = f)
            """)
    Page<Ficha> buscar(@Param("termino") String termino, @Param("prefijo") String prefijo,
            Pageable pageable);
}
