package pe.edu.dentalcite.horario.api;

import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

import org.springframework.web.bind.annotation.*;
import pe.edu.dentalcite.horario.api.dto.HorarioRequest;
import pe.edu.dentalcite.horario.api.dto.HorarioResponseDTO;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.service.HorarioService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Agenda · Horarios", description = "M4 · Horario de atencion semanal de cada odontologo (HU-07, RF-11)")
@RestController
@RequestMapping("/api/v1/odontologos/{odontologoId}/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private final HorarioService horarioService;

    @Operation(summary = "Consultar el horario de un odontologo",
            description = "HU-07 · RF-11 · ODONTOLOGO sobre el suyo, ADMINISTRADOR sobre cualquiera.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tramos declarados"),
            @ApiResponse(responseCode = "403", description = "Es el horario de otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe un odontologo con ese identificador")
    })
    @GetMapping
    public List<HorarioResponseDTO> listarHorarios(@PathVariable UUID odontologoId) {
        return horarioService.listarHorariosPorOdontologo(odontologoId);
    }

    @Operation(summary = "Declarar un tramo de horario",
            description = "HU-07 · RF-11 · ODONTOLOGO sobre el suyo, ADMINISTRADOR sobre cualquiera. Dia de la semana de 1 (lunes) a 7 (domingo), con hora de inicio anterior a la de fin (RN-03).")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Tramo declarado"),
            @ApiResponse(responseCode = "400", description = "La hora de inicio no es anterior a la de fin"),
            @ApiResponse(responseCode = "403", description = "Es el horario de otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe un odontologo con ese identificador"),
            @ApiResponse(responseCode = "409", description = "El tramo se solapa con otro ya declarado")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public HorarioResponseDTO crearHorario(@PathVariable UUID odontologoId, @RequestBody @Valid HorarioRequest request) {
        HorarioAtencion horario = HorarioAtencion.builder()
                .diaSemana(request.getDiaSemana())
                .horaInicio(request.getHoraInicio())
                .horaFin(request.getHoraFin())
                .build();
        return horarioService.crearHorario(horario, odontologoId);
    }

    @Operation(summary = "Eliminar un tramo de horario",
            description = "HU-07 · RF-11 · ODONTOLOGO sobre el suyo, ADMINISTRADOR sobre cualquiera.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Tramo eliminado"),
            @ApiResponse(responseCode = "403", description = "Es el horario de otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "El tramo no existe o no pertenece a ese odontologo")
    })
    @DeleteMapping("/{horarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarHorario(@PathVariable UUID odontologoId, @PathVariable UUID horarioId) {
        horarioService.eliminarHorario(odontologoId, horarioId);
    }

    @Operation(summary = "Reemplazar un tramo de horario",
            description = "HU-07 · RF-11 · ODONTOLOGO sobre el suyo, ADMINISTRADOR sobre cualquiera.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tramo actualizado"),
            @ApiResponse(responseCode = "400", description = "La hora de inicio no es anterior a la de fin"),
            @ApiResponse(responseCode = "403", description = "Es el horario de otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "El tramo no existe o no pertenece a ese odontologo"),
            @ApiResponse(responseCode = "409", description = "El tramo se solapa con otro ya declarado")
    })
    @PutMapping("/{horarioId}")
    public HorarioResponseDTO actualizarHorario(@PathVariable UUID odontologoId,
                                                @PathVariable UUID horarioId,
                                                @RequestBody @Valid HorarioRequest request) {
        HorarioAtencion detalles = HorarioAtencion.builder()
                .diaSemana(request.getDiaSemana())
                .horaInicio(request.getHoraInicio())
                .horaFin(request.getHoraFin())
                .build();
        return horarioService.actualizarHorario(odontologoId, horarioId, detalles);
    }
}
