package pe.edu.dentalcite.tratamiento.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

@Tag(name = "Catalogo · Tratamientos", description = "M3 · Tratamientos con su duracion y especialidad requerida (HU-06)")
@RestController
@RequestMapping("/api/v1/tratamientos")
@RequiredArgsConstructor
public class TratamientoController {

    private final TratamientoService tratamientoService;

    @Operation(summary = "Listar tratamientos",
            description = "HU-06 · Todos los roles autenticados. Cada tratamiento llega con su especialidad requerida.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de tratamientos"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado")
    })
    @GetMapping
    public ResponseEntity<Page<TratamientoResponseDTO>> listarTratamientos(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(tratamientoService.listarTratamientos(pageable));
    }

    @Operation(summary = "Crear un tratamiento",
            description = "HU-06 · RF-09 · ADMINISTRADOR. La duracion debe ser multiplo de quince minutos entre 15 y 240 (RN-04), regla aplicada tanto en el servicio como por una restriccion de integridad de la base.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tratamiento creado"),
            @ApiResponse(responseCode = "400", description = "Duracion que no cumple RN-04, o datos invalidos"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "La especialidad indicada no existe"),
            @ApiResponse(responseCode = "409", description = "Ya existe un tratamiento con ese codigo")
    })
    @PostMapping
    public ResponseEntity<TratamientoResponseDTO> crearTratamiento(
            @Valid @RequestBody TratamientoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tratamientoService.crearTratamiento(request));
    }

    @Operation(summary = "Reemplazar un tratamiento",
            description = "HU-06 · ADMINISTRADOR. Corresponde a la U de la Tabla 10. Revalida RN-04 igual que el alta. No modifica la marca de actividad: la baja tiene su propia operacion.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tratamiento actualizado"),
            @ApiResponse(responseCode = "400", description = "Duracion que no cumple RN-04, o datos invalidos"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe el tratamiento o la especialidad indicada"),
            @ApiResponse(responseCode = "409", description = "Otro tratamiento ya usa ese codigo")
    })
    @PutMapping("/{id}")
    public ResponseEntity<TratamientoResponseDTO> actualizarTratamiento(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody TratamientoRequestDTO request) {
        return ResponseEntity.ok(tratamientoService.actualizarTratamiento(id, request));
    }

    @Operation(summary = "Dar de baja un tratamiento",
            description = "HU-06 · RN-12 · ADMINISTRADOR. Baja logica. Se rechaza mientras exista una cita CONFIRMADA cuya hora de fin no haya pasado; una cita cancelada o ya vencida no lo impide (RN-09).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tratamiento dado de baja"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe un tratamiento con ese identificador"),
            @ApiResponse(responseCode = "409", description = "El tratamiento tiene citas activas (RN-12)")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<Void> darDeBajaTratamiento(@PathVariable java.util.UUID id) {
        tratamientoService.darDeBajaTratamiento(id);
        return ResponseEntity.noContent().build();
    }
}
