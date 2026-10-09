package pe.edu.dentalcite.bloqueo.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoConflictoResponse;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoRequest;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoResponseDTO;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;
import pe.edu.dentalcite.bloqueo.service.BloqueoService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bloqueos")
@RequiredArgsConstructor
public class BloqueoController {

    private final BloqueoService bloqueoService;

    @GetMapping
    public List<BloqueoResponseDTO> listarBloqueos() {
        return bloqueoService.listarBloqueos();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BloqueoResponseDTO crearBloqueo(@RequestBody @Valid BloqueoRequest request) {
        Bloqueo bloqueo = Bloqueo.builder()
                .motivo(request.getMotivo())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .build();
        return bloqueoService.crearBloqueo(bloqueo, request.getOdontologoId(), request.getConsultorioId());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBloqueo(@PathVariable UUID id) {
        bloqueoService.eliminarBloqueo(id);
    }

    @PutMapping("/{id}")
    public BloqueoResponseDTO actualizarBloqueo(@PathVariable UUID id, @RequestBody @Valid BloqueoRequest request) {
        Bloqueo detalles = Bloqueo.builder()
                .motivo(request.getMotivo())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .build();
        return bloqueoService.actualizarBloqueo(id, detalles, request.getOdontologoId(), request.getConsultorioId());
    }
}
