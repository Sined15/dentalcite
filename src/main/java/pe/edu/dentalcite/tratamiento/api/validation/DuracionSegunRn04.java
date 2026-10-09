package pe.edu.dentalcite.tratamiento.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoRequestDTO;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DuracionSegunRn04.Validador.class)
public @interface DuracionSegunRn04 {

    String message() default "La duración debe ser un múltiplo de 15 minutos";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<DuracionSegunRn04, TratamientoRequestDTO> {

        private static final int PASO = 15;

        @Override
        public boolean isValid(TratamientoRequestDTO request, ConstraintValidatorContext contexto) {
            if (request == null || request.getDuracionMinutos() == null) {
                return true;
            }
            if (request.getDuracionMinutos() % PASO == 0) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("duracionMinutos")
                    .addConstraintViolation();
            return false;
        }
    }
}
