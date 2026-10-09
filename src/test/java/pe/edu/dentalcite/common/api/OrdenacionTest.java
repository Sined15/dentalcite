package pe.edu.dentalcite.common.api;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdenacionTest {

    private static final Set<String> ADMITIDAS = Set.of("inicio", "estado");
    private static final Sort POR_DEFECTO = Sort.by(Sort.Direction.DESC, "inicio");

    @Test
    void cribar_sinOrdenPedido_devuelveLaMismaPagina() {
        Pageable pedida = PageRequest.of(2, 10);

        assertSame(pedida, Ordenacion.cribar(pedida, ADMITIDAS, POR_DEFECTO));
    }

    @Test
    void cribar_soloConPropiedadesAdmitidas_devuelveLaMismaPagina() {
        Pageable pedida = PageRequest.of(0, 20, Sort.by("estado"));

        assertSame(pedida, Ordenacion.cribar(pedida, ADMITIDAS, POR_DEFECTO));
    }

    @Test
    void cribar_conUnaPropiedadInventada_laDescartaYConservaLasDemas() {
        Pageable pedida = PageRequest.of(1, 20, Sort.by("noExiste").and(Sort.by("estado")));

        Pageable cribada = Ordenacion.cribar(pedida, ADMITIDAS, POR_DEFECTO);

        assertEquals(PageRequest.of(1, 20, Sort.by("estado")), cribada);
    }

    @Test
    void cribar_siNoQuedaNinguna_aplicaElOrdenPorDefecto() {
        Pageable pedida = PageRequest.of(0, 20, Sort.by("noExiste"));

        assertEquals(PageRequest.of(0, 20, POR_DEFECTO), Ordenacion.cribar(pedida, ADMITIDAS, POR_DEFECTO));
    }

    @Test
    void cribar_unaPaginaSinLimites_sigueSinLimites() {
        Pageable pedida = Pageable.unpaged(Sort.by("noExiste"));

        Pageable cribada = Ordenacion.cribar(pedida, ADMITIDAS, POR_DEFECTO);

        assertTrue(cribada.isUnpaged());
        assertEquals(POR_DEFECTO, cribada.getSort());
    }

    @Test
    void sinOrden_descartaCualquierOrden() {
        Pageable cribada = Ordenacion.sinOrden(PageRequest.of(3, 15, Sort.by("inicio")));

        assertEquals(PageRequest.of(3, 15), cribada);
        assertFalse(cribada.getSort().isSorted());
    }
}
