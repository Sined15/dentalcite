package pe.edu.dentalcite.paciente.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.paciente.api.dto.PacienteDetalleDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteRequestDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteResponseDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteUpdateRequestDTO;
import pe.edu.dentalcite.paciente.service.PacienteService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;

    @PostMapping
    public ResponseEntity<PacienteResponseDTO> registrarPaciente(@Valid @RequestBody PacienteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.registrar(request));
    }

    @GetMapping
    public ResponseEntity<Page<PacienteResponseDTO>> buscarPacientes(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pacienteService.buscar(q, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PacienteDetalleDTO> obtenerPaciente(@PathVariable UUID id) {
        return ResponseEntity.ok(pacienteService.obtener(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PacienteDetalleDTO> actualizarPaciente(
            @PathVariable UUID id,
            @Valid @RequestBody PacienteUpdateRequestDTO request) {
        return ResponseEntity.ok(pacienteService.actualizar(id, request));
    }
}
