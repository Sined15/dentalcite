package pe.edu.dentalcite.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Lo que un 403 puede llevar en el cuerpo: nada, o el {@code {message}} de la
 * API con un texto que no describe el recurso.
 *
 * <p>Se busca por forma y no solo por los valores sembrados: un identificador,
 * un código de cita, un número de historia o una fecha en el mensaje delatan el
 * recurso aunque no sean los de la prueba. Los valores concretos —nombres,
 * documentos— se pasan además, porque esos no tienen forma reconocible.
 *
 * <p>El 403 que corta la regla de ruta sale vacío; el que decide un servicio con
 * el recurso en la mano sale con mensaje. Los dos caben aquí.
 */
final class CuerpoDeRechazo {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final List<Pattern> DELATORES = List.of(
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"),
            Pattern.compile("CIT-\\d+"),
            Pattern.compile("HC-\\d+"),
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}"),
            // Los estados de la cita y de la sesión dicen por dónde va el recurso.
            Pattern.compile("CONFIRMADA|ATENDIDA|NO_ASISTIO|CANCELADA|CERRADA|PENDIENTE|SUSPENDID"),
            // Decir de quién es el recurso ya es un dato de él.
            Pattern.compile("otro odont|otro paciente", Pattern.CASE_INSENSITIVE));

    static void comprobar(String cuerpo, String... datosDelRecurso) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return;
        }
        JsonNode arbol;
        try {
            arbol = JSON.readTree(cuerpo);
        } catch (Exception e) {
            fail("El cuerpo del 403 no es JSON: " + cuerpo);
            return;
        }
        assertTrue(arbol.isObject(), "El cuerpo del 403 no es un objeto: " + cuerpo);
        assertEquals(1, arbol.size(), "El cuerpo del 403 lleva más que el mensaje: " + cuerpo);
        assertTrue(arbol.path("message").isTextual(), "El cuerpo del 403 no es {message}: " + cuerpo);

        String mensaje = arbol.path("message").asText();
        for (Pattern delator : DELATORES) {
            assertFalse(delator.matcher(mensaje).find(),
                    "El 403 describe el recurso (" + delator + "): " + mensaje);
        }
        for (String dato : datosDelRecurso) {
            assertFalse(mensaje.toLowerCase().contains(dato.toLowerCase()),
                    "El 403 contiene «" + dato + "»: " + mensaje);
        }
    }

    private CuerpoDeRechazo() {
    }
}
