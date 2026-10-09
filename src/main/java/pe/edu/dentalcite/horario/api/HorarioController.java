package pe.edu.dentalcite.horario.api;

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

@RestController
@RequestMapping("/api/v1/odontologos/{odontologoId}/horarios")
@RequiredArgsConstructor
public class HorarioController {

    private final HorarioService horarioService;

    @GetMapping
    public List<HorarioResponseDTO> listarHorarios(@PathVariable UUID odontologoId) {
        return horarioService.listarHorariosPorOdontologo(odontologoId);
    }

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

    @DeleteMapping("/{horarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void eliminarHorario(@PathVariable UUID odontologoId, @PathVariable UUID horarioId) {
        horarioService.eliminarHorario(odontologoId, horarioId);
    }

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
