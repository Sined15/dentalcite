package pe.edu.dentalcite.publico.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.publico.api.dto.CalendarioPublicoDTO;
import pe.edu.dentalcite.publico.api.dto.EspecialidadDetalleDTO;
import pe.edu.dentalcite.publico.api.dto.EspecialidadPublicaDTO;
import pe.edu.dentalcite.publico.api.dto.OdontologoPublicoDTO;
import pe.edu.dentalcite.publico.api.dto.TratamientoPublicoDTO;
import pe.edu.dentalcite.publico.service.CalendarioPublicoService;
import pe.edu.dentalcite.publico.service.CatalogoPublicoService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/publico")
@RequiredArgsConstructor
public class PublicoController {

    private final CatalogoPublicoService catalogoPublicoService;
    private final CalendarioPublicoService calendarioPublicoService;

    @GetMapping("/calendario")
    public ResponseEntity<CalendarioPublicoDTO> calendario() {
        return ResponseEntity.ok(calendarioPublicoService.consultar());
    }

    @GetMapping("/tratamientos")
    public ResponseEntity<List<TratamientoPublicoDTO>> listarTratamientos() {
        return ResponseEntity.ok(catalogoPublicoService.listarTratamientos());
    }

    @GetMapping("/especialidades")
    public ResponseEntity<List<EspecialidadPublicaDTO>> listarEspecialidades() {
        return ResponseEntity.ok(catalogoPublicoService.listarEspecialidades());
    }

    @GetMapping("/especialidades/{id}")
    public ResponseEntity<EspecialidadDetalleDTO> detalleDeEspecialidad(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoPublicoService.detalleDeEspecialidad(id));
    }

    @GetMapping("/odontologos")
    public ResponseEntity<List<OdontologoPublicoDTO>> listarOdontologos() {
        return ResponseEntity.ok(catalogoPublicoService.listarOdontologos());
    }
}
