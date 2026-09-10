package pe.edu.dentalcite.paciente.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.paciente.api.dto.PacienteRequestDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteResponseDTO;
import pe.edu.dentalcite.paciente.service.PacienteService;

@Tag(name = "Pacientes", description = "M2 · Alta presencial del paciente sin cuenta (HU-12)")
@RestController
@RequestMapping("/api/v1/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;

    @Operation(summary = "Registrar un paciente presencial",
            description = "HU-12 · RF-06 · RECEPCIONISTA y ADMINISTRADOR. Crea la ficha de quien se atiende en la clinica sin exigirle una cuenta: genera su numero de historia desde la secuencia y deja constancia del consentimiento informado con su fecha y la version del texto (RNF-06). El par tipo y numero de documento identifica al paciente, de modo que repetirlo es un conflicto y no un alta nueva (RN-10). Si esa persona se registra despues en el portal con el mismo documento, aquella ficha se le vincula en lugar de duplicarse.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ficha creada, con su numero de historia y sin credenciales de acceso"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos, o no consta el consentimiento informado del titular (RNF-06)"),
            @ApiResponse(responseCode = "403", description = "El rol no es RECEPCIONISTA ni ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "409", description = "Ya existe un paciente con ese tipo y numero de documento (RN-10)")
    })
    @PostMapping
    public ResponseEntity<PacienteResponseDTO> registrarPaciente(@Valid @RequestBody PacienteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.registrar(request));
    }
}
