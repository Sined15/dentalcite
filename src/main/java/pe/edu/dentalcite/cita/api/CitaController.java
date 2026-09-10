package pe.edu.dentalcite.cita.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import pe.edu.dentalcite.cita.service.CancelacionService;
import pe.edu.dentalcite.cita.service.CitaConsultaService;
import pe.edu.dentalcite.cita.service.CitaService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Citas", description = "M5 · Reserva desde el portal (HU-09, RF-15) y agenda de recepcion (HU-11, RF-18)")
@RestController
@RequestMapping("/api/v1/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;
    private final CitaConsultaService citaConsultaService;
    private final CancelacionService cancelacionService;

    @Operation(summary = "Reservar una cita",
            description = "HU-09 · RF-15 · PACIENTE, para si mismo: el paciente sale del token y no del cuerpo,"
                    + " asi que no se puede reservar en nombre de otro (eso es HU-14). La franja se indica con la"
                    + " fecha y la hora locales de la clinica que devolvio la consulta de disponibilidad. La cita"
                    + " nace CONFIRMADA con un codigo unico (RN-09) y con un consultorio asignado automaticamente"
                    + " (RF-14). Se comprueba la ventana de reserva (RN-05), la cuota de citas activas (RN-07) y"
                    + " que la franja siga ofreciendose.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cita creada, con su codigo unico"),
            @ApiResponse(responseCode = "400", description = "Cuerpo incompleto o mal formado"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no es PACIENTE, o la cuenta no tiene ficha (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "El tratamiento o el odontologo no existen o estan de baja"),
            @ApiResponse(responseCode = "409", description = "Cuota de citas activas agotada (RN-07), o la franja ya no esta disponible"),
            @ApiResponse(responseCode = "422", description = "La franja incumple la ventana de reserva: menos de dos horas o mas de noventa dias (RN-05)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponseDTO reservar(@Valid @RequestBody CitaRequestDTO peticion) {
        return citaService.reservar(peticion);
    }

    @Operation(summary = "Consultar la agenda",
            description = "HU-11 · RF-18 · RECEPCIONISTA y ADMINISTRADOR. Devuelve las citas cuyo inicio cae en el"
                    + " rango de dias indicado, ambos extremos incluidos, con su paciente, su hora y su consultorio,"
                    + " ordenadas por hora. Los filtros de odontologo y de estado son opcionales; omitirlos equivale"
                    + " a «todos». Las horas son locales de la clinica y la respuesta declara su zona. La consulta"
                    + " del propio paciente sobre sus citas es HU-15.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de citas, ordenadas por hora"),
            @ApiResponse(responseCode = "400", description = "Rango invertido, fechas ausentes o estado desconocido"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede consultar la agenda de la clinica (RNF-04)")
    })
    @GetMapping
    public Page<CitaResumenDTO> consultarAgenda(
            @Parameter(description = "Primer dia del rango, inclusive (AAAA-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Ultimo dia del rango, inclusive (AAAA-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Odontologo concreto. Omitirlo equivale a «todos»")
            @RequestParam(required = false) UUID odontologoId,
            @Parameter(description = "CONFIRMADA, ATENDIDA, NO_ASISTIO o CANCELADA (RN-09). Omitirlo equivale a «todos»")
            @RequestParam(required = false) String estado,
            // «Ordenadas por hora» es el criterio de aceptacion, asi que el orden
            // es el valor por defecto y no algo que el cliente deba recordar pedir.
            @PageableDefault(size = 20, sort = "inicio", direction = Sort.Direction.ASC) Pageable pageable) {
        return citaConsultaService.consultar(desde, hasta, odontologoId, estado, pageable);
    }

    @Operation(summary = "Cancelar una cita",
            description = "HU-11 · RF-20 · RECEPCIONISTA y ADMINISTRADOR, **sin ventana**: se puede cancelar"
                    + " cualquier cita confirmada, incluso dentro de las veinticuatro horas previas a su inicio. La"
                    + " ventana de RN-06 solo limita al paciente cancelando la suya, que es HU-15. La cita pasa a"
                    + " CANCELADA conservando su fila intacta (RN-12), la transicion queda en la bitacora con fecha,"
                    + " responsable y motivo (RF-21), y la franja vuelve a ofrecerse de inmediato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita cancelada"),
            @ApiResponse(responseCode = "400", description = "Cancelacion sin motivo"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede cancelar citas de terceros (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cita con ese identificador"),
            @ApiResponse(responseCode = "409", description = "La cita ya no esta CONFIRMADA: RN-09 no admite retornos")
    })
    @PatchMapping("/{id}/cancelar")
    public CitaResponseDTO cancelar(@PathVariable UUID id,
            @Valid @RequestBody CancelacionRequestDTO peticion) {
        return cancelacionService.cancelar(id, peticion.getMotivo());
    }

    @Operation(summary = "Consultar la bitacora de una cita",
            description = "HU-11 · RF-21 · RECEPCIONISTA y ADMINISTRADOR. Las transiciones de la cita en orden"
                    + " cronologico, cada una con su fecha, su responsable y su motivo. Como RN-12 impide borrar"
                    + " nada, esta es la unica forma de saber que le paso a una cita y por orden de quien.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Transiciones de la cita, de la mas antigua a la mas reciente"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede consultar la bitacora (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cita con ese identificador")
    })
    @GetMapping("/{id}/historial")
    public List<CitaHistorialDTO> historial(@PathVariable UUID id) {
        return citaConsultaService.historial(id);
    }
}
