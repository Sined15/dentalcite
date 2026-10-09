package pe.edu.dentalcite.common.api;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Set;

public final class Ordenacion {

    private Ordenacion() {
    }

    public static Pageable cribar(Pageable pedida, Set<String> admitidas, Sort porDefecto) {
        List<Sort.Order> pedidas = pedida.getSort().toList();
        List<Sort.Order> validas = pedidas.stream()
                .filter(orden -> admitidas.contains(orden.getProperty()))
                .toList();
        if (validas.size() == pedidas.size()) {
            return pedida;
        }

        Sort orden = validas.isEmpty() ? porDefecto : Sort.by(validas);
        return pedida.isPaged()
                ? PageRequest.of(pedida.getPageNumber(), pedida.getPageSize(), orden)
                : Pageable.unpaged(orden);
    }

    public static Pageable sinOrden(Pageable pedida) {
        return cribar(pedida, Set.of(), Sort.unsorted());
    }
}
