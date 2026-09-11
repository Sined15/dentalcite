package pe.edu.dentalcite.plan.service;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * Reconoce el choque contra el índice único parcial de RN-13 (HU-17).
 *
 * <p>Hermano menor de {@code cita.service.ConflictoDeSolape} y por el mismo
 * motivo: el nombre de la restricción es una cadena mágica atada a una
 * migración, y tenerla en un solo sitio con nombre es lo que evita que cambiarla
 * en la base deje el servicio devolviendo el error equivocado en silencio.
 *
 * <p>Aquí hay una restricción y no dos, así que no hace falta clasificar: solo
 * distinguir «este plan ya existe» de cualquier otro fallo de integridad, que no
 * hay que disfrazar de conflicto de planificación. El 409 saldría igual sin esta
 * clase —{@code GlobalExceptionHandler} ya mapea así todo
 * {@code DataIntegrityViolationException}—, pero con un mensaje que no diría qué
 * hacer.
 *
 * <p>Se mira el mensaje además del {@code ConstraintViolationException} de
 * Hibernate por lo aprendido en HU-10: Hibernate no siempre rellena el nombre de
 * la restricción, y el texto de PostgreSQL sí lo trae.
 */
public final class ConflictoDePlanActivo {

    /** Nombre declarado en {@code V19__planes_de_tratamiento.sql}. */
    static final String PLAN_ACTIVO_DUPLICADO = "ux_plan_activo_por_tratamiento";

    private ConflictoDePlanActivo() {
    }

    public static boolean esPlanActivoDuplicado(DataIntegrityViolationException ex) {
        if (ex == null) {
            return false;
        }
        for (Throwable causa = ex; causa != null && causa != causa.getCause(); causa = causa.getCause()) {
            if (causa instanceof org.hibernate.exception.ConstraintViolationException cve
                    && contiene(cve.getConstraintName())) {
                return true;
            }
            if (contiene(causa.getMessage())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contiene(String texto) {
        return texto != null
                && texto.toLowerCase(Locale.ROOT).contains(PLAN_ACTIVO_DUPLICADO);
    }
}
