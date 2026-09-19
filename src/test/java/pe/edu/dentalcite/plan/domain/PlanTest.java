package pe.edu.dentalcite.plan.domain;

import org.junit.jupiter.api.Test;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Las decisiones que el plan toma sobre sus propias sesiones: cuál es la
 * siguiente que se ocupa, cuándo no admite más y cuántas citas anteriores caben
 * al crearlo. Sin base de datos: son cuentas sobre la colección.
 */
class PlanTest {

    private static Plan planDe(int sesiones) {
        Plan plan = Plan.builder().sesionesPrevistas(sesiones).build();
        plan.generarSesiones();
        return plan;
    }

    private static Cita atendida(String codigo) {
        return Cita.builder().id(UUID.randomUUID()).codigo(codigo).estado(Cita.ESTADO_ATENDIDA).build();
    }

    @Test
    void generarSesiones_lasNumeraDesdeUnoYTodasPendientes() {
        Plan plan = planDe(3);

        assertEquals(List.of(1, 2, 3), plan.getSesiones().stream().map(PlanSesion::getNumero).toList());
        assertTrue(plan.getSesiones().stream().allMatch(PlanSesion::estaPendiente));
        assertTrue(plan.getSesiones().stream().allMatch(s -> s.getPlan() == plan));
    }

    @Test
    void primeraSesionPendiente_conLaPrimeraOcupada_devuelveLaSegunda() {
        Plan plan = planDe(3);
        plan.getSesiones().get(0).enlazar(atendida("CIT-1"));

        assertEquals(2, plan.primeraSesionPendiente().orElseThrow().getNumero());
    }

    @Test
    void primeraSesionPendiente_ordenaPorNumeroAunqueLaListaNoLoEste() {
        Plan plan = planDe(3);
        Collections.reverse(plan.getSesiones());

        assertEquals(1, plan.primeraSesionPendiente().orElseThrow().getNumero());
    }

    @Test
    void estaCompleto_conAlgunaSesionPendiente_esFalso() {
        Plan plan = planDe(2);
        plan.getSesiones().get(0).enlazar(atendida("CIT-1"));

        assertFalse(plan.estaCompleto());
    }

    @Test
    void estaCompleto_conTodasLasSesionesOcupadas_esVerdadero() {
        Plan plan = planDe(2);
        plan.getSesiones().get(0).enlazar(atendida("CIT-1"));
        plan.getSesiones().get(1).enlazar(atendida("CIT-2"));

        assertTrue(plan.estaCompleto());
        assertTrue(plan.primeraSesionPendiente().isEmpty());
    }

    @Test
    void enlazarRetroactivas_conMasCitasQueSesiones_lasOcupaEnOrdenYDejaLaUltimaPendiente() {
        Plan plan = planDe(3);
        List<Cita> citas = List.of(atendida("A"), atendida("B"), atendida("C"), atendida("D"));

        assertEquals(2, plan.enlazarRetroactivas(citas));

        assertSame(citas.get(0), plan.getSesiones().get(0).getCita());
        assertSame(citas.get(1), plan.getSesiones().get(1).getCita());
        assertTrue(plan.getSesiones().get(2).estaPendiente());
        assertNull(plan.getSesiones().get(2).getCita());
    }

    @Test
    void enlazarRetroactivas_conMenosCitasQueSesiones_lasOcupaTodas() {
        Plan plan = planDe(4);

        assertEquals(2, plan.enlazarRetroactivas(List.of(atendida("A"), atendida("B"))));
        assertEquals(3, plan.primeraSesionPendiente().orElseThrow().getNumero());
    }

    @Test
    void enlazarRetroactivas_conUnaSolaSesion_noOcupaNinguna() {
        Plan plan = planDe(1);

        assertEquals(0, plan.enlazarRetroactivas(List.of(atendida("A"))));
        assertFalse(plan.estaCompleto());
    }

    @Test
    void enlazarRetroactivas_sinCitas_noOcupaNinguna() {
        Plan plan = planDe(3);

        assertEquals(0, plan.enlazarRetroactivas(List.of()));
        assertTrue(plan.getSesiones().stream().allMatch(PlanSesion::estaPendiente));
    }

    @Test
    void enlazar_dejaLaSesionAtendidaConSuCita() {
        PlanSesion sesion = planDe(1).getSesiones().get(0);
        Cita cita = atendida("A");

        sesion.enlazar(cita);

        assertEquals(PlanSesion.ESTADO_ATENDIDA, sesion.getEstado());
        assertSame(cita, sesion.getCita());
        assertFalse(sesion.estaPendiente());
    }

    @Test
    void enlazar_sobreUnaSesionYaOcupada_lanzaIllegalStateSinPisarLaCita() {
        PlanSesion sesion = planDe(1).getSesiones().get(0);
        Cita primera = atendida("A");
        sesion.enlazar(primera);

        assertThrows(IllegalStateException.class, () -> sesion.enlazar(atendida("B")));
        assertSame(primera, sesion.getCita());
    }

    // ------------------------------------------------------------------
    // El cierre de la sesión
    // ------------------------------------------------------------------

    private static Recomendacion cuidado(String descripcion) {
        return Recomendacion.builder().id(UUID.randomUUID()).descripcion(descripcion).activa(true).build();
    }

    /** Una sesión con su cita atendida: la única que se puede cerrar. */
    private static PlanSesion sesionAtendida() {
        PlanSesion sesion = planDe(1).getSesiones().get(0);
        sesion.enlazar(atendida("CIT-1"));
        return sesion;
    }

    @Test
    void cerrar_unaSesionAtendida_laDejaCerradaConLoQueSeIndico() {
        PlanSesion sesion = sesionAtendida();
        Recomendacion dieta = cuidado("Dieta blanda");
        LocalDate control = LocalDate.of(2026, 10, 1);

        sesion.cerrar(Set.of(dieta), control, "Volver antes si hay dolor");

        assertEquals(PlanSesion.ESTADO_CERRADA, sesion.getEstado());
        assertEquals(Set.of(dieta), sesion.getRecomendaciones());
        assertEquals(control, sesion.getProximoControl());
        assertEquals("Volver antes si hay dolor", sesion.getObservacion());
        // La cita que la ocupaba sigue siendo la suya: cerrarla no la desenlaza.
        assertEquals("CIT-1", sesion.getCita().getCodigo());
    }

    @Test
    void cerrar_unaSesionPendiente_lanzaIllegalStateSinTocarla() {
        PlanSesion sesion = planDe(1).getSesiones().get(0);

        assertThrows(IllegalStateException.class,
                () -> sesion.cerrar(Set.of(cuidado("Dieta blanda")), LocalDate.now(), null));

        assertTrue(sesion.estaPendiente());
        assertNull(sesion.getProximoControl());
        assertTrue(sesion.getRecomendaciones().isEmpty());
    }

    @Test
    void cerrar_unaSesionYaCerrada_lanzaIllegalStateSinSustituirLoIndicado() {
        PlanSesion sesion = sesionAtendida();
        Recomendacion primera = cuidado("Dieta blanda");
        LocalDate control = LocalDate.of(2026, 10, 1);
        sesion.cerrar(Set.of(primera), control, "La primera");

        assertThrows(IllegalStateException.class,
                () -> sesion.cerrar(Set.of(cuidado("Hielo en la mejilla")),
                        LocalDate.of(2026, 11, 1), "La segunda"));

        assertEquals(Set.of(primera), sesion.getRecomendaciones());
        assertEquals(control, sesion.getProximoControl());
        assertEquals("La primera", sesion.getObservacion());
    }

    @Test
    void estaAtendida_soloLoEsLaQueTieneCitaYSigueSinCerrar() {
        assertFalse(planDe(1).getSesiones().get(0).estaAtendida());

        PlanSesion conCita = sesionAtendida();
        assertTrue(conCita.estaAtendida());

        conCita.cerrar(Set.of(cuidado("Dieta blanda")), LocalDate.now(), null);
        assertFalse(conCita.estaAtendida());
    }

    @Test
    void sesionesCompletadas_deSeisConDosAtendidas_sonDosYQuedanCuatroPendientes() {
        Plan plan = planDe(6);
        plan.getSesiones().get(0).enlazar(atendida("CIT-1"));
        plan.getSesiones().get(1).enlazar(atendida("CIT-2"));

        assertEquals(2, plan.sesionesCompletadas());
        assertEquals(4, plan.sesionesPendientes());
    }

    @Test
    void sesionesCompletadas_unaSesionCerradaSigueContandoComoCompletada() {
        Plan plan = planDe(3);
        PlanSesion primera = plan.getSesiones().get(0);
        primera.enlazar(atendida("CIT-1"));
        primera.cerrar(Set.of(cuidado("Dieta blanda")), LocalDate.of(2026, 10, 1), null);

        assertEquals(1, plan.sesionesCompletadas());
        assertEquals(2, plan.sesionesPendientes());
    }

    @Test
    void sesionesCompletadas_deUnPlanRecienCreado_esCero() {
        Plan plan = planDe(4);

        assertEquals(0, plan.sesionesCompletadas());
        assertEquals(4, plan.sesionesPendientes());
    }

    @Test
    void suspender_bajaLaMarcaDeActividadYGuardaElMotivo() {
        Plan plan = planDe(2);

        plan.suspender("El paciente se muda de ciudad");

        assertFalse(plan.getActivo());
        assertEquals("El paciente se muda de ciudad", plan.getMotivoSuspension());
    }
}
