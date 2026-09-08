package pe.edu.dentalcite.common.api;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoConflictoResponse;
import pe.edu.dentalcite.bloqueo.service.CitasActivasEnRangoException;
import pe.edu.dentalcite.common.api.dto.MessageResponse;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<MessageResponse> handleLockedException(LockedException ex) {
        return ResponseEntity.status(423).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<MessageResponse> handleBadCredentialsException(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<MessageResponse> handleDisabledException(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new MessageResponse(ex.getMessage()));
    }

    // RN-03 / HU-07: el 409 de un bloqueo bloqueado por citas activas necesita
    // llevar la lista de esas citas, no solo un mensaje.
    @ExceptionHandler(CitasActivasEnRangoException.class)
    public ResponseEntity<BloqueoConflictoResponse> handleCitasActivasEnRango(CitasActivasEnRangoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new BloqueoConflictoResponse(ex.getMessage(), ex.getCitasActivas()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<MessageResponse> handleIllegalStateException(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MessageResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new MessageResponse(ex.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<MessageResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MessageResponse(ex.getMessage()));
    }

    // Red de seguridad para condiciones de carrera en validaciones check-then-act
    // (p. ej. dos registros concurrentes con el mismo correo o código): la
    // restricción UNIQUE de la BD es la que realmente garantiza la unicidad, y
    // sin este handler la violación se filtraría como un 500 crudo.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<MessageResponse> handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new MessageResponse("El recurso ya existe o viola una restricción de integridad de datos."));
    }

    /**
     * Los servicios que necesitan fijar el estado a mano —el 409 de un horario
     * solapado (RN-03) y los 403 de {@code OdontologoOwnershipGuard} (RNF-04)—
     * lanzan {@link ResponseStatusException}. Sin este manejador no pasaban por
     * aqui: salian por el {@code /error} por defecto de Spring, con un cuerpo
     * {@code {timestamp, status, error, path}} que no lleva el motivo. El cliente
     * solo entiende {@code {message}}, asi que al usuario que solapaba un tramo
     * no se le mostraba «El horario se solapa con uno existente», sino la ruta y
     * la marca de tiempo de la peticion.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<MessageResponse> handleResponseStatusException(ResponseStatusException ex) {
        String motivo = ex.getReason() == null ? ex.getStatusCode().toString() : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(new MessageResponse(motivo));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errors);
    }
}
