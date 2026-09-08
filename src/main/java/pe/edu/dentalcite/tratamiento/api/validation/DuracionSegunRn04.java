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

/**
 * RN-04: la duración de un tratamiento es múltiplo de 15 minutos.
 *
 * <p>Es una restricción de clase, no de campo, solo para poder señalar el campo
 * culpable: un {@code @AssertTrue} sobre un getter hace que el 400 llegue con la
 * clave del getter ({@code duracionMultiploDeQuince}), que no corresponde a
 * ningún campo del recurso y que un formulario no puede asociar a su control.
 * Aquí {@code addPropertyNode} devuelve la clave real, {@code duracionMinutos}.
 *
 * <p>Los límites 15 y 240 los declaran {@code @Min} y {@code @Max} en el DTO,
 * que además los publican en el contrato OpenAPI.
 */
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
                // La ausencia la reporta @NotNull; duplicarla daría dos mensajes.
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
