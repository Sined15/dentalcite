package pe.edu.dentalcite.paciente.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Corrección de la ficha (HU-13 · RF-08: «editar los datos de contacto»).
 *
 * <p>Es un {@code PUT}, así que reemplaza por completo lo editable: un
 * {@code telefono} ausente borra el que hubiera, que es como se corrige un
 * teléfono equivocado.
 *
 * <p>No lleva tipo ni número de documento a propósito. RN-10 los declara la
 * identidad del paciente: cambiarlos no sería corregir un dato de esta persona
 * sino convertir su ficha en la de otra, con su historia clínica y sus citas
 * dentro. Un documento mal tecleado se arregla dando de alta la ficha correcta.
 */
@Data
public class PacienteUpdateRequestDTO {

    @NotBlank(message = "Los nombres son obligatorios")
    private String nombres;

    @NotBlank(message = "Los apellidos son obligatorios")
    private String apellidos;

    private String telefono;

    /** RF-08. Se edita junto al contacto: es el único punto que las escribe. */
    private String alergias;
}
