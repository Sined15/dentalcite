package pe.edu.dentalcite.bloqueo.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoRequest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Un bloqueo necesita un ámbito: el odontólogo, el consultorio, o ambos.
 *
 * <p>La violación se reporta sobre los dos campos, no sobre uno elegido al azar:
 * cualquiera de ellos resuelve el error, así que un formulario debe poder marcar
 * los dos controles implicados.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AmbitoDeBloqueo.Validador.class)
public @interface AmbitoDeBloqueo {

    String message() default "Debe especificar un odontólogo o un consultorio para el bloqueo";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<AmbitoDeBloqueo, BloqueoRequest> {

        @Override
        public boolean isValid(BloqueoRequest request, ConstraintValidatorContext contexto) {
            if (request == null
                    || request.getOdontologoId() != null
                    || request.getConsultorioId() != null) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            String mensaje = contexto.getDefaultConstraintMessageTemplate();
            for (String campo : new String[] { "odontologoId", "consultorioId" }) {
                contexto.buildConstraintViolationWithTemplate(mensaje)
                        .addPropertyNode(campo)
                        .addConstraintViolation();
            }
            return false;
        }
    }
}
