package pe.edu.dentalcite.usuario.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import pe.edu.dentalcite.usuario.api.dto.UsuarioRequestDTO;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = DocumentoDeOdontologo.Validador.class)
public @interface DocumentoDeOdontologo {

    String message() default "Una cuenta de rol ODONTOLOGO necesita el documento de la persona para crear su ficha (RN-11)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validador implements ConstraintValidator<DocumentoDeOdontologo, UsuarioRequestDTO> {

        private static final String ROL_ODONTOLOGO = "ODONTOLOGO";

        @Override
        public boolean isValid(UsuarioRequestDTO request, ConstraintValidatorContext contexto) {
            if (request == null || !ROL_ODONTOLOGO.equals(request.getRol())) {
                return true;
            }
            String documento = request.getDocumento();
            if (documento != null && !documento.isBlank()) {
                return true;
            }
            contexto.disableDefaultConstraintViolation();
            contexto.buildConstraintViolationWithTemplate(contexto.getDefaultConstraintMessageTemplate())
                    .addPropertyNode("documento")
                    .addConstraintViolation();
            return false;
        }
    }
}
