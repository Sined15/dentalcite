package pe.edu.dentalcite.odontologo.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.dentalcite.odontologo.api.dto.OdontologoRequestDTO;
import pe.edu.dentalcite.odontologo.api.dto.OdontologoResponseDTO;
import pe.edu.dentalcite.odontologo.service.OdontologoService;

@RestController
@RequestMapping("/api/v1/odontologos")
@RequiredArgsConstructor
public class OdontologoController {

    private final OdontologoService odontologoService;

    @GetMapping
    public ResponseEntity<Page<OdontologoResponseDTO>> listarOdontologos(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(odontologoService.listarOdontologos(pageable));
    }

    @PostMapping
    public ResponseEntity<OdontologoResponseDTO> registrarOdontologo(
            @Valid @RequestBody OdontologoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(odontologoService.registrarOdontologo(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<OdontologoResponseDTO> actualizarOdontologo(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody OdontologoRequestDTO request) {
        return ResponseEntity.ok(odontologoService.actualizarOdontologo(id, request));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<Void> darDeBajaOdontologo(@PathVariable java.util.UUID id) {
        odontologoService.darDeBajaOdontologo(id);
        return ResponseEntity.noContent().build();
    }
}
