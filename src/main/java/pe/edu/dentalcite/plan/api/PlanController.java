package pe.edu.dentalcite.plan.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.plan.api.dto.CierreSesionRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanResponseDTO;
import pe.edu.dentalcite.plan.api.dto.SuspensionRequestDTO;
import pe.edu.dentalcite.plan.service.PlanService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/planes")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponseDTO crear(@Valid @RequestBody PlanRequestDTO peticion) {
        return planService.crear(peticion);
    }

    @GetMapping
    public List<PlanResponseDTO> deFicha(
            @RequestParam UUID pacienteId) {
        return planService.deFicha(pacienteId);
    }

    @GetMapping("/seguimiento")
    public Page<PlanResponseDTO> seguimiento(
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @PageableDefault(size = 20) Pageable pageable) {
        return planService.seguimiento(soloActivos, pageable);
    }

    @GetMapping("/{id}")
    public PlanResponseDTO obtener(@PathVariable UUID id) {
        return planService.obtener(id);
    }

    @PatchMapping("/{id}/suspender")
    public PlanResponseDTO suspender(@PathVariable UUID id,
            @Valid @RequestBody SuspensionRequestDTO peticion) {
        return planService.suspender(id, peticion.getMotivo());
    }

    @PostMapping("/{id}/sesiones/{numero}/cierre")
    public PlanResponseDTO cerrarSesion(@PathVariable UUID id,
            @PathVariable int numero,
            @Valid @RequestBody CierreSesionRequestDTO peticion) {
        return planService.cerrarSesion(id, numero, peticion);
    }
}
