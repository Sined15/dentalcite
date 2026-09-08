package pe.edu.dentalcite.bloqueo.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
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

@Tag(name = "Agenda · Bloqueos", description = "M4 · Bloqueos de agenda de un odontologo o de un consultorio (HU-07, RF-12)")
@RestController
@RequestMapping("/api/v1/bloqueos")
@RequiredArgsConstructor
public class BloqueoController {

    private final BloqueoService bloqueoService;

    @Operation(summary = "Listar bloqueos",
            description = "HU-07 · RF-12 · RECEPCIONISTA y ADMINISTRADOR ven todos; el ODONTOLOGO ve los suyos y los de consultorio, que afectan a toda la clinica (RNF-04).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloqueos visibles para el solicitante"),
            @ApiResponse(responseCode = "403", description = "El rol no puede gestionar la agenda (RNF-04)")
    })
    @GetMapping
    public List<BloqueoResponseDTO> listarBloqueos() {
        return bloqueoService.listarBloqueos();
    }

    @Operation(summary = "Registrar un bloqueo",
            description = "HU-07 · RF-12 · ODONTOLOGO sobre el suyo, RECEPCIONISTA y ADMINISTRADOR sobre cualquiera y sobre consultorios. Si el rango alcanza citas CONFIRMADAS aun no vencidas, el bloqueo no se aplica y la respuesta enumera esas citas para que se cancelen antes con motivo (RN-03).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Bloqueo registrado"),
            @ApiResponse(responseCode = "400", description = "Rango invalido, o no se indico odontologo ni consultorio"),
            @ApiResponse(responseCode = "403", description = "Es la agenda de otro odontologo, o el rol no puede bloquear consultorios (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "El odontologo o el consultorio indicado no existe"),
            @ApiResponse(responseCode = "409",
                    description = "El rango alcanza citas activas; la respuesta las lista (RN-03)",
                    content = @Content(schema = @Schema(implementation = BloqueoConflictoResponse.class)))
    })
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

    @Operation(summary = "Eliminar un bloqueo",
            description = "HU-07 · RF-12 · ODONTOLOGO sobre el suyo, RECEPCIONISTA y ADMINISTRADOR sobre cualquiera.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Bloqueo eliminado"),
            @ApiResponse(responseCode = "403", description = "Es la agenda de otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe un bloqueo con ese identificador")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarBloqueo(@PathVariable UUID id) {
        bloqueoService.eliminarBloqueo(id);
    }

    @Operation(summary = "Reemplazar un bloqueo",
            description = "HU-07 · RF-12 · ODONTOLOGO sobre el suyo, RECEPCIONISTA y ADMINISTRADOR sobre cualquiera. El nuevo rango se comprueba contra las citas activas igual que en el alta (RN-03).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bloqueo actualizado"),
            @ApiResponse(responseCode = "400", description = "Rango invalido, o no se indico odontologo ni consultorio"),
            @ApiResponse(responseCode = "403", description = "Es la agenda de otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "El bloqueo, el odontologo o el consultorio indicado no existe"),
            @ApiResponse(responseCode = "409",
                    description = "El nuevo rango alcanza citas activas; la respuesta las lista (RN-03)",
                    content = @Content(schema = @Schema(implementation = BloqueoConflictoResponse.class)))
    })
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
