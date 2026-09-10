package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.usuario.domain.Usuario;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RN-06, una prueba por caso. Sin Spring y sin base: la regla es aritmética de
 * fechas más «quién la reservó», y los cuatro criterios de HU-15 que hablan de
 * ventana se pueden fijar aquí enteros.
 */
class VentanaDeCancelacionTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private VentanaDeCancelacion ventana;
    private OffsetDateTime ahora;
    private Ficha fichaDelPaciente;
    private Usuario cuentaDelPaciente;
    private Usuario recepcionista;

    @BeforeEach
    void setUp() {
        ventana = new VentanaDeCancelacion(24, new ReglasDeReserva(2, 90, 3));
        ahora = OffsetDateTime.now(LIMA);

        fichaDelPaciente = Ficha.builder().id(UUID.randomUUID()).documento("12345678")
                .nombres("Ana").apellidos("Torres").numeroHistoria("HC-00001").build();
        cuentaDelPaciente = Usuario.builder().id(UUID.randomUUID()).rol("PACIENTE")
                .correo("ana@demo.com").ficha(fichaDelPaciente).build();
        // La cuenta del mostrador no tiene ficha, como la del entorno de demostración.
        recepcionista = Usuario.builder().id(UUID.randomUUID()).rol("RECEPCIONISTA")
                .correo("recepcion@dentalcite.com").build();
    }

    /**
     * @param empiezaEnHoras dentro de cuánto empieza
     * @param reservadaHaceHoras hace cuánto se reservó
     */
    private Cita cita(String estado, long empiezaEnHoras, long reservadaHaceHoras, Usuario autor) {
        OffsetDateTime inicio = ahora.plusHours(empiezaEnHoras);
        return Cita.builder()
                .id(UUID.randomUUID()).codigo("CIT-000001")
                .ficha(fichaDelPaciente)
                .inicio(inicio).fin(inicio.plusMinutes(30))
                .estado(estado)
                .creadoEn(ahora.minusHours(reservadaHaceHoras))
                .creadoPor(autor)
                .build();
    }

    // ------------------------------------------------------------------
    // La ventana de las veinticuatro horas
    // ------------------------------------------------------------------

    @Test
    void puedeCancelar_masDeVeinticuatroHorasAntes_esCierto() {
        // Criterio 2: «una cita mía que empieza en más de veinticuatro horas».
        assertTrue(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 48, 72, cuentaDelPaciente), ahora));
    }

    @Test
    void puedeCancelar_justoEnElLimite_esCierto() {
        // A veinticuatro horas exactas todavía cuenta: RN-06 dice «hasta».
        assertTrue(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 24, 72, cuentaDelPaciente), ahora));
    }

    @Test
    void puedeCancelar_aDoceHorasYReservadaLaSemanaPasada_esFalso() {
        // Criterio 3, el 422: tuvo una semana para cancelarla con margen.
        assertFalse(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 12, 24 * 7, cuentaDelPaciente), ahora));
    }

    // ------------------------------------------------------------------
    // La excepción de RN-06: lo que uno reserva, uno lo deshace
    // ------------------------------------------------------------------

    @Test
    void puedeCancelar_reservadaPorElHaceUnaHoraYEmpiezaEnTres_esCierto() {
        // Criterio 4: «porque el autoservicio no puede permitir reservar lo que no
        // permite deshacer».
        assertTrue(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 3, 1, cuentaDelPaciente), ahora));
    }

    @Test
    void puedeCancelar_reservadaPorElPeroYaAMenosDeDosHoras_esFalso() {
        // «Mientras se conserve el mínimo de RN-05»: pasado ese punto, ni suya.
        assertFalse(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 1, 1, cuentaDelPaciente), ahora));
    }

    @Test
    void puedeCancelar_reservadaPorRecepcionHaceUnaHora_esFalso() {
        // La excepción es para «la cita que él mismo reservó». Si la pidió el
        // mostrador, el mostrador la deshace.
        assertFalse(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 3, 1, recepcionista), ahora));
    }

    @Test
    void puedeCancelar_sinAutorRegistrado_esFalso() {
        // Las citas anteriores a HU-14 no tienen autor: no se les puede atribuir
        // la excepción, así que les vale solo la ventana de veinticuatro horas.
        assertFalse(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 3, 1, null), ahora));
    }

    @Test
    void puedeCancelar_reservadaPorOtroPacienteHaceUnaHora_esFalso() {
        // Un tercero con ficha propia tampoco activa la excepción.
        Usuario otro = Usuario.builder().id(UUID.randomUUID()).rol("PACIENTE")
                .ficha(Ficha.builder().id(UUID.randomUUID()).numeroHistoria("HC-00002").build())
                .build();

        assertFalse(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, 3, 1, otro), ahora));
    }

    // ------------------------------------------------------------------
    // RN-09 manda sobre la ventana
    // ------------------------------------------------------------------

    @Test
    void puedeCancelar_citaYaCancelada_esFalso() {
        assertFalse(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CANCELADA, 48, 72, cuentaDelPaciente), ahora));
    }

    @Test
    void puedeCancelar_citaYaPasada_esFalso() {
        // El inicio quedó atrás: ni la ventana ni la excepción alcanzan.
        assertFalse(ventana.puedeCancelarElPaciente(
                cita(Cita.ESTADO_CONFIRMADA, -3, 48, cuentaDelPaciente), ahora));
    }

    // ------------------------------------------------------------------
    // RN-17: el umbral es configuración
    // ------------------------------------------------------------------

    @Test
    void puedeCancelar_conLaVentanaAjustadaADoceHoras_cambiaElVeredicto() {
        // La misma cita que con veinticuatro horas se rechaza, con doce se acepta:
        // es la palanca que RN-17 exige poder mover sin recompilar.
        Cita aDieciocho = cita(Cita.ESTADO_CONFIRMADA, 18, 24 * 7, cuentaDelPaciente);

        assertFalse(ventana.puedeCancelarElPaciente(aDieciocho, ahora));
        assertTrue(new VentanaDeCancelacion(12, new ReglasDeReserva(2, 90, 3))
                .puedeCancelarElPaciente(aDieciocho, ahora));
    }

    @Test
    void motivoDelRechazo_diceLaAntelacionYAdondeAcudir() {
        String mensaje = ventana.motivoDelRechazo();

        assertTrue(mensaje.contains("24"), mensaje);
        assertTrue(mensaje.toLowerCase().contains("recepción"), mensaje);
    }
}
