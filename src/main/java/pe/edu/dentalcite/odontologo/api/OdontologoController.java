package pe.edu.dentalcite.odontologo.api;

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
import pe.edu.dentalcite.odontologo.api.dto.OdontologoRequestDTO;
import pe.edu.dentalcite.odontologo.api.dto.OdontologoResponseDTO;
import pe.edu.dentalcite.odontologo.service.OdontologoService;

@Tag(name = "Catalogo · Odontologos", description = "M3 · Registro de odontologos, su colegiatura y sus especialidades (HU-06)")
@RestController
@RequestMapping("/api/v1/odontologos")
@RequiredArgsConstructor
public class OdontologoController {

    private final OdontologoService odontologoService;

    @Operation(summary = "Listar odontologos",
            description = "HU-06 · Todos los roles autenticados. Cada odontologo llega con sus especialidades asociadas, que son las que RN-08 exigira al asignarlo a un tratamiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de odontologos"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado")
    })
    @GetMapping
    public ResponseEntity<Page<OdontologoResponseDTO>> listarOdontologos(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(odontologoService.listarOdontologos(pageable));
    }

    @Operation(summary = "Registrar un odontologo",
            description = "HU-06 · RF-10 · ADMINISTRADOR. Exige colegiatura y al menos una especialidad. La ficha indicada debe pertenecer a una cuenta de rol ODONTOLOGO, y ni la cuenta ni el odontologo admiten un segundo enlace (RN-11).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Odontologo registrado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos, o alguna especialidad no existe"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "La ficha indicada no existe"),
            @ApiResponse(responseCode = "409", description = "La colegiatura ya existe, o la ficha no tiene cuenta de rol ODONTOLOGO o ya esta asignada (RN-11)")
    })
    @PostMapping
    public ResponseEntity<OdontologoResponseDTO> registrarOdontologo(
            @Valid @RequestBody OdontologoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(odontologoService.registrarOdontologo(request));
    }

    @Operation(summary = "Reemplazar un odontologo",
            description = "HU-06 · RF-10 · ADMINISTRADOR. Corresponde a la U de la Tabla 10 sobre catalogo clinico: permite corregir la colegiatura y el nombre, y cambiar las especialidades que RN-08 exigira al asignarlo a un tratamiento. No modifica la marca de actividad —la baja tiene su propia operacion— ni el vinculo con la ficha, que RN-11 fija al registrarlo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Odontologo actualizado"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos, o alguna especialidad no existe"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe un odontologo con ese identificador"),
            @ApiResponse(responseCode = "409", description = "La colegiatura ya esta en uso, o se intento cambiar la ficha vinculada (RN-11)")
    })
    @PutMapping("/{id}")
    public ResponseEntity<OdontologoResponseDTO> actualizarOdontologo(
            @PathVariable java.util.UUID id,
            @Valid @RequestBody OdontologoRequestDTO request) {
        return ResponseEntity.ok(odontologoService.actualizarOdontologo(id, request));
    }

    @Operation(summary = "Dar de baja un odontologo",
            description = "HU-06 · RN-12 · ADMINISTRADOR. Baja logica. Se rechaza mientras exista una cita CONFIRMADA cuya hora de fin no haya pasado (RN-09).")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Odontologo dado de baja"),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe un odontologo con ese identificador"),
            @ApiResponse(responseCode = "409", description = "El odontologo tiene citas activas (RN-12)")
    })
    @PatchMapping("/{id}")
    public ResponseEntity<Void> darDeBajaOdontologo(@PathVariable java.util.UUID id) {
        odontologoService.darDeBajaOdontologo(id);
        return ResponseEntity.noContent().build();
    }
}
