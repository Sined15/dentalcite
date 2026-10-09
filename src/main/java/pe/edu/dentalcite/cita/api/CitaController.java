package pe.edu.dentalcite.cita.api;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.cita.api.dto.CancelacionRequestDTO;
import pe.edu.dentalcite.cita.api.dto.CitaHistorialDTO;
import pe.edu.dentalcite.cita.api.dto.CitaRequestDTO;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.api.dto.CitaResumenDTO;
import pe.edu.dentalcite.cita.api.dto.ResultadoRequestDTO;
import pe.edu.dentalcite.cita.service.CancelacionService;
import pe.edu.dentalcite.cita.service.CierreDeCita;
import pe.edu.dentalcite.cita.service.CitaConsultaService;
import pe.edu.dentalcite.cita.service.CitaService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;
    private final CitaConsultaService citaConsultaService;
    private final CancelacionService cancelacionService;
    private final CierreDeCita cierreDeCita;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponseDTO reservar(@Valid @RequestBody CitaRequestDTO peticion) {
        return citaService.reservar(peticion);
    }

    @GetMapping
    public Page<CitaResumenDTO> consultarAgenda(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) UUID odontologoId,
            @RequestParam(required = false) String estado,
            // «Ordenadas por hora» es el criterio de aceptacion, asi que el orden
            // es el valor por defecto y no algo que el cliente deba recordar pedir.
            @PageableDefault(size = 20, sort = "inicio", direction = Sort.Direction.ASC) Pageable pageable) {
        return citaConsultaService.consultar(desde, hasta, odontologoId, estado, pageable);
    }

    @GetMapping("/mias")
    public Page<CitaResumenDTO> misCitas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String estado,
            // De la mas reciente a la mas antigua: quien abre sus citas viene a
            // ver la siguiente, no la de hace dos anos.
            @PageableDefault(size = 20, sort = "inicio", direction = Sort.Direction.DESC) Pageable pageable) {
        return citaConsultaService.mias(desde, hasta, estado, pageable);
    }

    @PatchMapping("/{id}/cancelar")
    public CitaResponseDTO cancelar(@PathVariable UUID id,
            @Valid @RequestBody CancelacionRequestDTO peticion) {
        return cancelacionService.cancelar(id, peticion.getMotivo());
    }

    @GetMapping("/pendientes-cierre")
    public Page<CitaResumenDTO> pendientesDeCierre(
            @PageableDefault(size = 20) Pageable pageable) {
        return citaConsultaService.pendientesDeCierre(pageable);
    }

    @PatchMapping("/{id}/resultado")
    public CitaResponseDTO registrarResultado(@PathVariable UUID id,
            @Valid @RequestBody ResultadoRequestDTO peticion) {
        return cierreDeCita.registrarResultado(id, peticion.getResultado());
    }

    @GetMapping("/{id}/historial")
    public List<CitaHistorialDTO> historial(@PathVariable UUID id) {
        return citaConsultaService.historial(id);
    }
}
