package pe.edu.dentalcite.disponibilidad.api;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.disponibilidad.api.dto.DisponibilidadResponseDTO;
import pe.edu.dentalcite.disponibilidad.service.DisponibilidadService;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    @GetMapping
    public DisponibilidadResponseDTO consultarDisponibilidad(
            @RequestParam UUID tratamientoId,
            @RequestParam(required = false) UUID odontologoId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidadService.consultar(tratamientoId, odontologoId, desde, hasta);
    }
}
