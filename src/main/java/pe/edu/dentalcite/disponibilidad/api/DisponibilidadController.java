package pe.edu.dentalcite.disponibilidad.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Agenda · Disponibilidad",
        description = "M4 · Motor de franjas realmente libres para un tratamiento (HU-08, RF-13, RF-14)")
@RestController
@RequestMapping("/api/v1/disponibilidad")
@RequiredArgsConstructor
public class DisponibilidadController {

    private final DisponibilidadService disponibilidadService;

    @Operation(summary = "Consultar franjas disponibles",
            description = "HU-08 · RF-13, RF-14 · Todos los roles autenticados. Recorre el horario declarado de cada"
                    + " odontologo en incrementos de quince minutos y propone todo bloque contiguo que admita la"
                    + " duracion del tratamiento (RN-04), descontando citas activas, bloqueos de odontologo y de"
                    + " consultorio, feriados (RN-03) y las franjas sin ningun consultorio libre (RN-02, RF-14)."
                    + " Sin odontologoId se proponen todos los que poseen la especialidad que el tratamiento exige"
                    + " (RN-08). El rango se limita a catorce dias por RNF-01. Las horas son locales de la clinica;"
                    + " la respuesta declara su zona.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Franjas disponibles, agrupadas por odontologo"),
            @ApiResponse(responseCode = "400", description = "Rango invertido, mayor que el maximo, o parametro ausente o mal formado"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "404", description = "El tratamiento no existe o esta dado de baja, o el odontologo no existe")
    })
    @GetMapping
    public DisponibilidadResponseDTO consultarDisponibilidad(
            @Parameter(description = "Tratamiento a agendar: fija la duracion (RN-04) y la especialidad exigida (RN-08)")
            @RequestParam UUID tratamientoId,
            @Parameter(description = "Odontologo concreto. Omitirlo equivale a «cualquier odontologo»")
            @RequestParam(required = false) UUID odontologoId,
            @Parameter(description = "Primer dia del rango, inclusive (AAAA-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Ultimo dia del rango, inclusive (AAAA-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return disponibilidadService.consultar(tratamientoId, odontologoId, desde, hasta);
    }
}
