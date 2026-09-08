package pe.edu.dentalcite.especialidad.api;

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
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadRequestDTO;
import pe.edu.dentalcite.especialidad.api.dto.EspecialidadResponseDTO;
import pe.edu.dentalcite.especialidad.service.EspecialidadService;

@Tag(name = "Catalogo · Especialidades", description = "M3 · Especialidades odontologicas (HU-06)")
@RestController
@RequestMapping("/api/v1/especialidades")
@RequiredArgsConstructor
public class EspecialidadController {

    private final EspecialidadService especialidadService;

    @Operation(summary = "Listar especialidades",
            description = "HU-06 · Todos los roles autenticados. La Tabla 10 concede lectura del catalogo clinico a los cuatro roles.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de especialidades"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado")
    })
    @GetMapping
    public ResponseEntity<Page<EspecialidadResponseDTO>> listarEspecialidades(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(especialidadService.listarEspecialidades(pageable));
    }

    @Operation(summary = "Crear una especialidad",
            description = "HU-06 · ADMINISTRADOR. La escritura sobre el catalogo clinico es exclusiva del administrador (RNF-04).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Especialidad creada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "409", description = "Ya existe una especialidad con ese nombre")
    })
    @PostMapping
    public ResponseEntity<EspecialidadResponseDTO> crearEspecialidad(
            @Valid @RequestBody EspecialidadRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(especialidadService.crearEspecialidad(request));
    }

    @Operation(summary = "Reemplazar una especialidad",
            description = "HU-06 · ADMINISTRADOR. Corresponde a la U de la Tabla 10 sobre catalogo clinico.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Especialidad actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una especialidad con ese identificador"),
            @ApiResponse(responseCode = "409", description = "Otra especialidad ya usa ese nombre")
    })
    @PutMapping("/{id}")
    public ResponseEntity<EspecialidadResponseDTO> actualizarEspecialidad(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody EspecialidadRequestDTO request) {
        return ResponseEntity.ok(especialidadService.actualizarEspecialidad(id, request));
    }

    @Operation(summary = "Dar de baja una especialidad",
            description = "HU-06 · ADMINISTRADOR. Baja logica. Se rechaza mientras queden tratamientos activos que la exijan, porque RN-08 obliga a que el odontologo posea la especialidad del tratamiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Especialidad dada de baja"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una especialidad con ese identificador"),
            @ApiResponse(responseCode = "409", description = "Quedan tratamientos activos que exigen esta especialidad")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<Void> darDeBajaEspecialidad(@PathVariable java.util.UUID id) {
        especialidadService.darDeBajaEspecialidad(id);
        return ResponseEntity.noContent().build();
    }
}
