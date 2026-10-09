package pe.edu.dentalcite.especialidad.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadRequestDTO;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;
import pe.edu.dentalcite.especialidad.service.EspecialidadService;

@RestController
@RequestMapping("/api/v1/especialidades")
@RequiredArgsConstructor
public class EspecialidadController {

    private final EspecialidadService especialidadService;

    @GetMapping
    public ResponseEntity<Page<EspecialidadResponseDTO>> listarEspecialidades(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(especialidadService.listarEspecialidades(pageable));
    }

    @PostMapping
    public ResponseEntity<EspecialidadResponseDTO> crearEspecialidad(
            @Valid @RequestBody EspecialidadRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especialidadService.crearEspecialidad(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EspecialidadResponseDTO> actualizarEspecialidad(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody EspecialidadRequestDTO request) {
        return ResponseEntity.ok(especialidadService.actualizarEspecialidad(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> darDeBajaEspecialidad(@PathVariable java.util.UUID id) {
        especialidadService.darDeBajaEspecialidad(id);
        return ResponseEntity.noContent().build();
    }
}
