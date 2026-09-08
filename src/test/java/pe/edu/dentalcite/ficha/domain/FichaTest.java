package pe.edu.dentalcite.ficha.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * RN-10: el paciente se identifica por su tipo y número de documento, y el tipo
 * nunca puede quedar nulo (la columna es NOT NULL).
 */
class FichaTest {

    @Test
    void aplicarTipoDocumentoPorDefecto_conConstructorVacio_asignaDni() {
        // `@Builder.Default` solo alimenta al builder: por esta vía el tipo llegaría
        // nulo a la base de datos si el gancho no lo cubriera.
        Ficha ficha = new Ficha();
        ficha.setDocumento("40123456");

        ficha.aplicarTipoDocumentoPorDefecto();

        assertEquals("DNI", ficha.getTipoDocumento());
    }

    @Test
    void aplicarTipoDocumentoPorDefecto_conTipoEnBlanco_asignaDni() {
        Ficha ficha = new Ficha();
        ficha.setTipoDocumento("   ");

        ficha.aplicarTipoDocumentoPorDefecto();

        assertEquals("DNI", ficha.getTipoDocumento());
    }

    @Test
    void aplicarTipoDocumentoPorDefecto_conTipoExplicito_loConserva() {
        Ficha ficha = Ficha.builder()
                .tipoDocumento("PASAPORTE")
                .documento("X1234567")
                .build();

        ficha.aplicarTipoDocumentoPorDefecto();

        assertEquals("PASAPORTE", ficha.getTipoDocumento());
    }

    @Test
    void builder_sinTipoDocumento_usaDniComoValorPorDefecto() {
        Ficha ficha = Ficha.builder().documento("40987654").build();

        assertEquals("DNI", ficha.getTipoDocumento());
    }
}
