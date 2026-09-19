package pe.edu.dentalcite.recomendacion.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.recomendacion.api.dto.RecomendacionDTO;
import pe.edu.dentalcite.recomendacion.domain.Recomendacion;
import pe.edu.dentalcite.recomendacion.repository.RecomendacionRepository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * El catálogo cerrado: qué se ofrece para elegir y qué se acepta de vuelta.
 */
@ExtendWith(MockitoExtension.class)
class RecomendacionServiceTest {

    @Mock private RecomendacionRepository recomendacionRepository;

    private RecomendacionService servicio;

    private Recomendacion dieta;
    private Recomendacion hielo;

    @BeforeEach
    void setUp() {
        servicio = new RecomendacionService(recomendacionRepository);
        dieta = cuidado("Mantener dieta blanda");
        hielo = cuidado("Aplicar hielo en la mejilla");
    }

    private static Recomendacion cuidado(String descripcion) {
        return Recomendacion.builder().id(UUID.randomUUID()).descripcion(descripcion)
                .activa(true).build();
    }

    @Test
    void catalogo_devuelveLasVigentesConSuDescripcion() {
        when(recomendacionRepository.findByActivaTrueOrderByDescripcionAsc())
                .thenReturn(List.of(hielo, dieta));

        List<RecomendacionDTO> catalogo = servicio.catalogo();

        assertEquals(2, catalogo.size());
        assertEquals(hielo.getId(), catalogo.get(0).getId());
        assertEquals("Aplicar hielo en la mejilla", catalogo.get(0).getDescripcion());
        assertEquals("Mantener dieta blanda", catalogo.get(1).getDescripcion());
    }

    @Test
    void resolverVigentes_conTodasEnElCatalogo_lasDevuelve() {
        when(recomendacionRepository.findByIdInAndActivaTrue(anyList()))
                .thenReturn(List.of(dieta, hielo));

        Set<Recomendacion> resueltas = servicio
                .resolverVigentes(List.of(dieta.getId(), hielo.getId()));

        assertEquals(Set.of(dieta, hielo), resueltas);
    }

    @Test
    void resolverVigentes_conUnaRepetida_noLaCuentaDosVeces() {
        // Repetir un identificador no es un error, pero tampoco son dos cuidados:
        // sin quitar el duplicado, el recuento no cuadraria y daria un 400 falso.
        when(recomendacionRepository.findByIdInAndActivaTrue(List.of(dieta.getId())))
                .thenReturn(List.of(dieta));

        assertEquals(Set.of(dieta),
                servicio.resolverVigentes(List.of(dieta.getId(), dieta.getId())));
    }

    @Test
    void resolverVigentes_conAlgunaQueNoExiste_lanzaIllegalArgument() {
        when(recomendacionRepository.findByIdInAndActivaTrue(anyList()))
                .thenReturn(List.of(dieta));

        assertThrows(IllegalArgumentException.class,
                () -> servicio.resolverVigentes(List.of(dieta.getId(), UUID.randomUUID())));
    }

    @Test
    void resolverVigentes_conUnaRetiradaDelCatalogo_lanzaIllegalArgument() {
        // Una recomendacion inactiva no la devuelve la consulta, asi que llega aqui
        // como lo que es: algo que hoy no se puede indicar.
        when(recomendacionRepository.findByIdInAndActivaTrue(anyList())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> servicio.resolverVigentes(List.of(UUID.randomUUID())));
    }
}
