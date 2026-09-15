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
    // RN-10: la identidad es el par (tipo, número), no el número suelto.
    Optional<Ficha> findByTipoDocumentoAndDocumento(String tipoDocumento, String documento);
    boolean existsByTipoDocumentoAndDocumento(String tipoDocumento, String documento);

    @Query(value = "SELECT nextval('sq_historia_clinica')", nativeQuery = true)
    Long getNextHistoriaClinica();

    /**
     * RF-07: «buscar pacientes por documento, apellidos o número de historia».
     *
     * <p>Un solo término y no tres campos, porque quien atiende el mostrador
     * teclea lo que el paciente le dice sin clasificarlo antes. Documento y
     * número de historia se comparan por igualdad —son identificadores, y un
     * documento «que contenga 4567» no es una búsqueda que nadie quiera— y el
     * apellido por prefijo insensible a mayúsculas, que es lo que el índice
     * {@code ix_fichas_apellidos} puede resolver (RNF-02); un {@code LIKE
     * '%…%'} recorrería la tabla entera.
     *
     * <p>Sin término devuelve todas: el listado se abre poblado, no en blanco.
     *
     * <p><strong>Menos las del odontólogo.</strong> Un odontólogo tiene ficha
     * para que su cuenta resuelva a su registro, no porque se atienda en la
     * clínica: la suya no tiene historia que consultar y en el padrón se lee como
     * un error. Con siete odontólogos y un puñado de pacientes, el listado era
     * sobre todo personal. Quien busca al odontólogo lo encuentra donde vive, que
     * es el registro de odontólogos y su cuenta de acceso.
     *
     * <p>El paréntesis alrededor del término **es la regla**: sin él, el
     * {@code OR} se lo comería y la exclusión solo aplicaría a la última rama.
     */
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
