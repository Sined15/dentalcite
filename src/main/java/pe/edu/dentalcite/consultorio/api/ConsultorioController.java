package pe.edu.dentalcite.consultorio.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.consultorio.api.dto.ConsultorioResponseDTO;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;

import java.util.List;

/**
 * Solo lectura, y a propósito: F-12 deja fuera del alcance el mantenimiento por
 * interfaz de consultorios, que se cargan como datos semilla. El listado existe
 * porque registrar un bloqueo de consultorio (HU-07) exige poder elegir uno.
 */
@Tag(name = "Agenda · Consultorios", description = "M4 · Consultorios de la clinica. Solo lectura: F-12 excluye su mantenimiento por interfaz")
@RestController
@RequestMapping("/api/v1/consultorios")
@RequiredArgsConstructor
public class ConsultorioController {

    private final ConsultorioRepository consultorioRepository;

    @Operation(summary = "Listar consultorios",
            description = "Todos los roles autenticados. Existe porque registrar un bloqueo de consultorio (HU-07) exige poder elegir uno. Los consultorios se cargan como datos semilla (F-12).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Consultorios de la clinica"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado")
    })
    @GetMapping
    public ResponseEntity<List<ConsultorioResponseDTO>> listarConsultorios() {
        return ResponseEntity.ok(consultorioRepository.findAll().stream()
                .map(c -> ConsultorioResponseDTO.builder()
                        .id(c.getId())
                        .nombre(c.getNombre())
                        .inoperativo(c.getInoperativo())
                        .build())
                .toList());
    }
}
