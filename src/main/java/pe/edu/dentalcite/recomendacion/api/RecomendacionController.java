package pe.edu.dentalcite.recomendacion.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.recomendacion.api.dto.RecomendacionDTO;
import pe.edu.dentalcite.recomendacion.service.RecomendacionService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/recomendaciones")
@RequiredArgsConstructor
public class RecomendacionController {

    private final RecomendacionService recomendacionService;

    @GetMapping
    public List<RecomendacionDTO> listar() {
        return recomendacionService.catalogo();
    }
}
