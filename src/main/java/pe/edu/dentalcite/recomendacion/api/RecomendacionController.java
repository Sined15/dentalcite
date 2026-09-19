package pe.edu.dentalcite.recomendacion.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.recomendacion.api.dto.RecomendacionDTO;
import pe.edu.dentalcite.recomendacion.service.RecomendacionService;

import java.util.List;

/**
 * Solo lectura, como los consultorios: el catálogo es cerrado y se carga con los
 * datos semilla, así que no hay alta ni edición por interfaz.
 */
@Tag(name = "Plan de tratamiento · Recomendaciones",
        description = "M6 · Catalogo cerrado de cuidados (HU-19, RF-27). Solo lectura")
@RestController
@RequestMapping("/api/v1/recomendaciones")
@RequiredArgsConstructor
public class RecomendacionController {

    private final RecomendacionService recomendacionService;

    @Operation(summary = "Listar el catalogo de recomendaciones",
            description = "HU-19 · RF-27 · ODONTOLOGO y ADMINISTRADOR. Las recomendaciones vigentes, que son"
                    + " entre las que se elige al cerrar una sesion del plan. El catalogo es cerrado: no se"
                    + " escriben recomendaciones a mano, y lo que el odontologo quiera anadir con sus palabras"
                    + " va en la observacion de cuidado del cierre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Recomendaciones vigentes"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no cierra sesiones, asi que no elige de este catalogo (RNF-04)")
    })
    @GetMapping
    public List<RecomendacionDTO> listar() {
        return recomendacionService.catalogo();
    }
}
