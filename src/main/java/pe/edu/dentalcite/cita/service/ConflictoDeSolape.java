package pe.edu.dentalcite.cita.service;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

public final class ConflictoDeSolape {

    static final String SOLAPE_ODONTOLOGO = "citas_sin_solape_odontologo";
    static final String SOLAPE_CONSULTORIO = "citas_sin_solape_consultorio";

    private static final String SQLSTATE_INTERBLOQUEO = "40P01";

    private ConflictoDeSolape() {
    }

    public static boolean esSolapeDeOdontologo(DataIntegrityViolationException ex) {
        return SOLAPE_ODONTOLOGO.equals(restriccionVioladaEn(ex));
    }

    public static boolean esSolapeDeConsultorio(DataIntegrityViolationException ex) {
        return SOLAPE_CONSULTORIO.equals(restriccionVioladaEn(ex));
    }

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
