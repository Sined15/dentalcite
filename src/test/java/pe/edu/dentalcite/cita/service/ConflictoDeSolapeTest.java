package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * De qué restricción viene el choque (HU-10).
 *
 * <p>No es una prueba de adorno: la primera versión solo leía
 * {@code ConstraintViolationException.getConstraintName()} de Hibernate y no
 * reconocía ninguno de los dos solapes, así que el reintento de RF-16 nunca se
 * disparaba y de cuatro reservas simultáneas solo prosperaba una. El caso del
 * mensaje crudo de PostgreSQL es exactamente el que fallaba.
 */
class ConflictoDeSolapeTest {

    /** Lo que devuelve PostgreSQL al violar una restricción de exclusión (23P01). */
    private DataIntegrityViolationException comoLoManda(String restriccion) {
        return new DataIntegrityViolationException(
                "could not execute statement [ERROR: conflicting key value violates exclusion "
                        + "constraint \"" + restriccion + "\"]",
                new SQLException("conflicting key value violates exclusion constraint \""
                        + restriccion + "\"", "23P01"));
    }

    @Test
    void reconoceElSolapeDeConsultorioEnElMensajeDePostgres() {
        DataIntegrityViolationException ex = comoLoManda("citas_sin_solape_consultorio");

        assertTrue(ConflictoDeSolape.esSolapeDeConsultorio(ex));
        assertFalse(ConflictoDeSolape.esSolapeDeOdontologo(ex));
    }

    @Test
    void reconoceElSolapeDeOdontologoEnElMensajeDePostgres() {
        DataIntegrityViolationException ex = comoLoManda("citas_sin_solape_odontologo");

        assertTrue(ConflictoDeSolape.esSolapeDeOdontologo(ex));
        assertFalse(ConflictoDeSolape.esSolapeDeConsultorio(ex));
    }

    @Test
    void reconoceElNombreCuandoHibernateSiLoExpone() {
        // La via preferida, por si una version futura si envuelve el 23P01.
        DataIntegrityViolationException ex = new DataIntegrityViolationException("choque",
                new org.hibernate.exception.ConstraintViolationException(
                        "sin detalle", new SQLException("23P01"), "citas_sin_solape_consultorio"));

        assertTrue(ConflictoDeSolape.esSolapeDeConsultorio(ex));
    }

    @Test
    void otroFalloDeIntegridadNoSeDisfrazaDeSolape() {
        // Un codigo de cita duplicado no es un conflicto de agenda: reintentar con
        // otro consultorio no arreglaria nada y el error debe propagarse.
        DataIntegrityViolationException ex = new DataIntegrityViolationException(
                "duplicate key value violates unique constraint \"citas_codigo_key\"");

        assertNull(ConflictoDeSolape.restriccionVioladaEn(ex));
        assertFalse(ConflictoDeSolape.esSolapeDeOdontologo(ex));
        assertFalse(ConflictoDeSolape.esSolapeDeConsultorio(ex));
    }

    @Test
    void aguantaUnaExcepcionSinMensajeNiCausa() {
        assertNull(ConflictoDeSolape.restriccionVioladaEn(new DataIntegrityViolationException(null)));
        assertNull(ConflictoDeSolape.restriccionVioladaEn(null));
    }

    @Test
    void encuentraElNombreAunqueEsteEnUnaCausaProfunda() {
        DataIntegrityViolationException ex = new DataIntegrityViolationException("fallo al guardar",
                new RuntimeException("envoltorio",
                        new SQLException("conflicting key value violates exclusion constraint "
                                + "\"citas_sin_solape_odontologo\"")));

        assertTrue(ConflictoDeSolape.esSolapeDeOdontologo(ex));
    }
}
