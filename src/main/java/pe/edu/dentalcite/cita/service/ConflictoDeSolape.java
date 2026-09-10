package pe.edu.dentalcite.cita.service;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * Traduce el fallo de una restricción de exclusión (HU-10) a algo que el servicio
 * pueda decidir.
 *
 * <p>Los dos solapes se tratan distinto y por eso hay que distinguirlos: si choca
 * el <strong>consultorio</strong> queda reintentar con otro (RF-16), mientras que
 * si choca el <strong>odontólogo</strong> no hay nada que reintentar, porque nadie
 * va a liberar esa agenda.
 *
 * <p><strong>Por qué se mira el mensaje.</strong> Lo natural sería leer
 * {@code ConstraintViolationException.getConstraintName()} de Hibernate, y se
 * intenta primero. Pero Hibernate solo envuelve así algunas violaciones —las de
 * clave única, con SQLSTATE 23505—; la de exclusión, que es 23P01, llega como un
 * {@code DataIntegrityViolationException} cuyo nombre de restricción solo aparece
 * en el texto que devuelve PostgreSQL:
 *
 * <pre>conflicting key value violates exclusion constraint "citas_sin_solape_consultorio"</pre>
 *
 * <p>Se comprobó midiendo: sin esta segunda vía, las tres reservas que deberían
 * reintentar con otro consultorio salían por el 409 genérico y solo prosperaba
 * una de cuatro. Leer el {@code PSQLException} directamente sería más limpio, pero
 * el driver está declarado con ámbito {@code runtime} y no se puede compilar
 * contra él.
 */
public final class ConflictoDeSolape {

    /** Nombres declarados en {@code V13__exclusion_de_citas.sql}. */
    static final String SOLAPE_ODONTOLOGO = "citas_sin_solape_odontologo";
    static final String SOLAPE_CONSULTORIO = "citas_sin_solape_consultorio";

    /** SQLSTATE de PostgreSQL para «deadlock detected». */
    private static final String SQLSTATE_INTERBLOQUEO = "40P01";

    private ConflictoDeSolape() {
    }

    public static boolean esSolapeDeOdontologo(DataIntegrityViolationException ex) {
        return SOLAPE_ODONTOLOGO.equals(restriccionVioladaEn(ex));
    }

    public static boolean esSolapeDeConsultorio(DataIntegrityViolationException ex) {
        return SOLAPE_CONSULTORIO.equals(restriccionVioladaEn(ex));
    }

    /**
     * @return {@link #SOLAPE_ODONTOLOGO}, {@link #SOLAPE_CONSULTORIO} o
     *         {@code null} si el fallo de integridad es otro —un código duplicado,
     *         por ejemplo—, en cuyo caso no hay que disfrazarlo de conflicto de
     *         agenda.
     */
    static String restriccionVioladaEn(DataIntegrityViolationException ex) {
        if (ex == null) {
            return null;
        }
        for (Throwable causa = ex; causa != null && causa != causa.getCause(); causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException cve) {
                String nombre = normalizar(cve.getConstraintName());
                if (nombre != null) {
                    return nombre;
                }
            }
            String nombre = normalizar(causa.getMessage());
            if (nombre != null) {
                return nombre;
            }
        }
        return null;
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String minusculas = texto.toLowerCase(Locale.ROOT);
        if (minusculas.contains(SOLAPE_ODONTOLOGO)) {
            return SOLAPE_ODONTOLOGO;
        }
        if (minusculas.contains(SOLAPE_CONSULTORIO)) {
            return SOLAPE_CONSULTORIO;
        }
        return null;
    }

    /**
     * ¿La transacción murió porque PostgreSQL la eligió víctima de un
     * interbloqueo?
     *
     * <p>Dos INSERT simultáneos pueden esperarse mutuamente mientras el motor
     * comprueba las restricciones de exclusión, cada uno sobre la fila aún sin
     * confirmar del otro; PostgreSQL rompe el ciclo abortando a uno. No es un
     * conflicto de agenda —no dice siquiera qué restricción lo provocó—, sino una
     * carrera perdida: quien la pierde tiene que volver a intentarlo, no recibir
     * un 500 (criterio 2 de HU-10).
     *
     * <p>Se reconoce por el SQLSTATE y no por el tipo de la excepción de Spring
     * porque el traductor no siempre la envuelve igual según de dónde salga —del
     * {@code flush} de Hibernate o del commit—, y el estado sí es estable.
     */
    public static boolean esInterbloqueo(Throwable ex) {
        for (Throwable causa = ex; causa != null && causa != causa.getCause(); causa = causa.getCause()) {
            if (causa instanceof java.sql.SQLException sql
                    && SQLSTATE_INTERBLOQUEO.equals(sql.getSQLState())) {
                return true;
            }
            String mensaje = causa.getMessage();
            if (mensaje != null && mensaje.toLowerCase(Locale.ROOT).contains("deadlock detected")) {
                return true;
            }
        }
        return false;
    }
}
