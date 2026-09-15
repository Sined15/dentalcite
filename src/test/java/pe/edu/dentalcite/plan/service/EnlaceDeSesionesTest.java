package pe.edu.dentalcite.plan.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.plan.domain.Plan;
import pe.edu.dentalcite.plan.repository.PlanRepository;
import pe.edu.dentalcite.plan.service.EnlaceDeSesiones.SesionOcupada;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Qué plan se busca y qué se hace con él. Que el bloqueo sirva de algo bajo
 * concurrencia, que la ventana de días se aplique en SQL y que la base rechace
 * una cita repetida lo comprueba {@code EnlaceDeCitaConSesionIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
class EnlaceDeSesionesTest {

    @Mock private PlanRepository planRepository;
    @Mock private CitaRepository citaRepository;

    private EnlaceDeSesiones enlace;

    private Ficha ficha;
    private Tratamiento ortodoncia;

    @BeforeEach
    void setUp() {
        enlace = new EnlaceDeSesiones(planRepository, citaRepository);
        ficha = Ficha.builder().id(UUID.randomUUID()).numeroHistoria("HC-00001").build();
        ortodoncia = Tratamiento.builder().id(UUID.randomUUID()).nombre("Ortodoncia")
                .duracionMinutos(30).build();
    }

    private Plan planDe(int sesiones) {
        Plan plan = Plan.builder().id(UUID.randomUUID()).ficha(ficha).tratamiento(ortodoncia)
                .sesionesPrevistas(sesiones).activo(true).build();
        plan.generarSesiones();
        return plan;
    }

    private Cita atendida(Tratamiento tratamiento) {
        return Cita.builder().id(UUID.randomUUID())
                .codigo("CIT-" + UUID.randomUUID().toString().substring(0, 6))
                .ficha(ficha).tratamiento(tratamiento).estado(Cita.ESTADO_ATENDIDA).build();
    }

    // ------------------------------------------------------------------
    // Al registrar el resultado de una cita
    // ------------------------------------------------------------------

    @Test
    void enlazarCitaAtendida_conPlanActivo_ocupaLaPrimeraSesionPendiente() {
        Plan plan = planDe(3);
        plan.getSesiones().get(0).enlazar(atendida(ortodoncia));
        when(planRepository.findActivoParaEnlazar(ficha.getId(), ortodoncia.getId()))
                .thenReturn(Optional.of(plan));
        Cita nueva = atendida(ortodoncia);

        SesionOcupada ocupada = enlace.enlazarCitaAtendida(nueva).orElseThrow();

        assertEquals(2, ocupada.numero());
        assertEquals(plan.getId(), ocupada.planId());
        assertEquals("Ortodoncia", ocupada.tratamiento());
        assertSame(nueva, plan.getSesiones().get(1).getCita());
        assertTrue(plan.getSesiones().get(2).estaPendiente());
    }

    @Test
    void enlazarCitaAtendida_sinPlanActivo_noOcupaNada() {
        when(planRepository.findActivoParaEnlazar(ficha.getId(), ortodoncia.getId()))
                .thenReturn(Optional.empty());

        assertTrue(enlace.enlazarCitaAtendida(atendida(ortodoncia)).isEmpty());
    }

    @Test
    void enlazarCitaAtendida_buscaElPlanDelTratamientoDeLaCitaYNoOtro() {
        // La cita es de otro tratamiento: se pregunta por el plan de ese, y el de
        // ortodoncia ni se consulta.
        Tratamiento endodoncia = Tratamiento.builder().id(UUID.randomUUID()).nombre("Endodoncia")
                .duracionMinutos(30).build();
        when(planRepository.findActivoParaEnlazar(ficha.getId(), endodoncia.getId()))
                .thenReturn(Optional.empty());

        assertTrue(enlace.enlazarCitaAtendida(atendida(endodoncia)).isEmpty());
        verify(planRepository).findActivoParaEnlazar(ficha.getId(), endodoncia.getId());
    }

    @Test
    void enlazarCitaAtendida_conElPlanCompleto_noOcupaNada() {
        Plan plan = planDe(1);
        Cita unica = atendida(ortodoncia);
        plan.getSesiones().get(0).enlazar(unica);
        when(planRepository.findActivoParaEnlazar(ficha.getId(), ortodoncia.getId()))
                .thenReturn(Optional.of(plan));

        assertTrue(enlace.enlazarCitaAtendida(atendida(ortodoncia)).isEmpty());
        assertEquals(1, plan.getSesiones().size());
        assertSame(unica, plan.getSesiones().get(0).getCita());
    }

    // ------------------------------------------------------------------
    // Al crear el plan
    // ------------------------------------------------------------------

    @Test
    void enlazarRetroactivas_pideLasAtendidasDeLosUltimosNoventaDiasYDejaUnaPendiente() {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-09-15T10:00:00-05:00");
        Plan plan = planDe(3);
        when(citaRepository.atendidasSinSesion(ficha.getId(), ortodoncia.getId(),
                ahora.minusDays(EnlaceDeSesiones.DIAS_DE_HISTORIAL), ahora))
                .thenReturn(List.of(atendida(ortodoncia), atendida(ortodoncia), atendida(ortodoncia)));

        assertEquals(2, enlace.enlazarRetroactivas(plan, ahora));
        assertTrue(plan.getSesiones().get(2).estaPendiente());
        assertEquals(90, EnlaceDeSesiones.DIAS_DE_HISTORIAL);
    }

    @Test
    void enlazarRetroactivas_sinCitasAnteriores_noOcupaNada() {
        OffsetDateTime ahora = OffsetDateTime.parse("2026-09-15T10:00:00-05:00");
        Plan plan = planDe(3);
        when(citaRepository.atendidasSinSesion(ficha.getId(), ortodoncia.getId(),
                ahora.minusDays(EnlaceDeSesiones.DIAS_DE_HISTORIAL), ahora))
                .thenReturn(List.of());

        assertEquals(0, enlace.enlazarRetroactivas(plan, ahora));
        assertTrue(plan.getSesiones().stream().allMatch(s -> s.estaPendiente()));
    }
}
