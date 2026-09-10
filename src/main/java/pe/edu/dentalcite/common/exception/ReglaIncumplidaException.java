package pe.edu.dentalcite.common.exception;

/**
 * La peticion se entiende y esta bien formada, pero incumple una regla de
 * negocio que no depende del estado de otros recursos: 422.
 *
 * <p>Es la distincion que pide HU-09. Un 400 diria «lo has escrito mal» y un 409
 * diria «choca con lo que hay»; reservar a menos de dos horas (RN-05) no es
 * ninguna de las dos cosas: la peticion es correcta y no choca con nadie, pero la
 * regla no la admite. La cuota de RN-07 si es un 409, porque depende de cuantas
 * citas activas tenga ya el paciente.
 */
public class ReglaIncumplidaException extends RuntimeException {
    public ReglaIncumplidaException(String message) {
        super(message);
    }
}
