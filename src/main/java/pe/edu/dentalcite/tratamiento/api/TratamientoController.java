package pe.edu.dentalcite.tratamiento.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoRequestDTO;
import pe.edu.dentalcite.tratamiento.api.dto.TratamientoResponseDTO;
import pe.edu.dentalcite.tratamiento.service.TratamientoService;

@RestController
@RequestMapping("/api/v1/tratamientos")
@RequiredArgsConstructor
public class TratamientoController {

    private final TratamientoService tratamientoService;

    @GetMapping
    public ResponseEntity<Page<TratamientoResponseDTO>> listarTratamientos(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tratamientoService.listarTratamientos(pageable));
    }

    @PostMapping
    public ResponseEntity<TratamientoResponseDTO> crearTratamiento(
            @Valid @RequestBody TratamientoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tratamientoService.crearTratamiento(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TratamientoResponseDTO> actualizarTratamiento(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody TratamientoRequestDTO request) {
        return ResponseEntity.ok(tratamientoService.actualizarTratamiento(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> darDeBajaTratamiento(@PathVariable java.util.UUID id) {
        tratamientoService.darDeBajaTratamiento(id);
        return ResponseEntity.noContent().build();
    }
}
