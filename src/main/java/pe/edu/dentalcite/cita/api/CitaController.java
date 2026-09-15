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
import pe.edu.dentalcite.cita.api.dto.ResultadoRequestDTO;
import pe.edu.dentalcite.cita.service.CancelacionService;
import pe.edu.dentalcite.cita.service.CierreDeCita;
import pe.edu.dentalcite.cita.service.CitaConsultaService;
import pe.edu.dentalcite.cita.service.CitaService;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Tag(name = "Citas", description = "M5 · Reserva desde el portal (HU-09, RF-15) y desde recepcion (HU-14, RF-17), agenda de la clinica (HU-11, RF-18), citas del paciente (HU-15, RF-18, RF-19) y cierre de la cita (HU-16, RF-22)")
@RestController
@RequestMapping("/api/v1/citas")
@RequiredArgsConstructor
public class CitaController {

    private final CitaService citaService;
    private final CitaConsultaService citaConsultaService;
    private final CancelacionService cancelacionService;
    private final CierreDeCita cierreDeCita;

    @Operation(summary = "Reservar una cita",
            description = "HU-09 · RF-15 · PACIENTE, para si mismo: su ficha sale del token y `pacienteId` esta"
                    + " prohibido. HU-14 · RF-17 · RECEPCIONISTA y ADMINISTRADOR, en nombre de cualquier paciente"
                    + " registrado: para ellos `pacienteId` es obligatorio, y funciona igual con un paciente que no"
                    + " tiene cuenta de acceso. La franja se indica con la fecha y la hora locales de la clinica que"
                    + " devolvio la consulta de disponibilidad. La cita nace CONFIRMADA con un codigo unico (RN-09),"
                    + " con un consultorio asignado automaticamente (RF-14) y dejando registrado quien la creo. Se"
                    + " comprueba la ventana de reserva (RN-05), la cuota de citas activas del paciente (RN-07) y"
                    + " que la franja siga ofreciendose.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cita creada, con su codigo unico"),
            @ApiResponse(responseCode = "400", description = "Cuerpo incompleto o mal formado, o reserva de recepcion sin `pacienteId`"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede reservar, un PACIENTE envio `pacienteId`, o la cuenta no tiene ficha (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "El paciente, el tratamiento o el odontologo no existen o estan de baja"),
            @ApiResponse(responseCode = "409", description = "Cuota de citas activas agotada (RN-07), o la franja ya no esta disponible"),
            @ApiResponse(responseCode = "422", description = "La franja incumple la ventana de reserva: menos de dos horas o mas de noventa dias (RN-05)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CitaResponseDTO reservar(@Valid @RequestBody CitaRequestDTO peticion) {
        return citaService.reservar(peticion);
    }

    @Operation(summary = "Consultar la agenda",
            description = "HU-11 · RF-18 · RECEPCIONISTA y ADMINISTRADOR ven la clinica entera; el ODONTOLOGO ve"
                    + " **las suyas**, que es la tercera audiencia del requisito. Devuelve las citas cuyo inicio cae en el"
                    + " rango de dias indicado, ambos extremos incluidos, con su paciente, su hora y su consultorio,"
                    + " ordenadas por hora. Los filtros de odontologo y de estado son opcionales; omitirlos equivale"
                    + " a «todos». Las horas son locales de la clinica y la respuesta declara su zona. La consulta"
                    + " del propio paciente sobre sus citas es GET /api/v1/citas/mias.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de citas, ordenadas por hora"),
            @ApiResponse(responseCode = "400", description = "Rango invertido, fechas ausentes o estado desconocido"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede consultar la agenda, o la cuenta de odontologo no tiene ficha (RNF-04)")
    })
    @GetMapping
    public Page<CitaResumenDTO> consultarAgenda(
            @Parameter(description = "Primer dia del rango, inclusive (AAAA-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Ultimo dia del rango, inclusive (AAAA-MM-DD)")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "Odontologo concreto. Omitirlo equivale a «todos». Al ODONTOLOGO se le"
                    + " ignora: su ambito sale del token, no del filtro, de modo que siempre recibe las suyas")
            @RequestParam(required = false) UUID odontologoId,
            @Parameter(description = "CONFIRMADA, ATENDIDA, NO_ASISTIO o CANCELADA (RN-09). Omitirlo equivale a «todos»")
            @RequestParam(required = false) String estado,
            // «Ordenadas por hora» es el criterio de aceptacion, asi que el orden
            // es el valor por defecto y no algo que el cliente deba recordar pedir.
            @PageableDefault(size = 20, sort = "inicio", direction = Sort.Direction.ASC) Pageable pageable) {
        return citaConsultaService.consultar(desde, hasta, odontologoId, estado, pageable);
    }

    @Operation(summary = "Consultar mis citas",
            description = "HU-15 · RF-18 · PACIENTE. Sus propias citas, futuras y pasadas, con su estado. La ficha"
                    + " sale del token: no hay forma de pedir las de otro. El rango de dias es **opcional** aqui,"
                    + " al reves que en la agenda de la clinica, porque el criterio pide ver las futuras y las"
                    + " pasadas; omitirlo equivale a «todas». Cada fila trae `cancelablePorPaciente`, que dice si"
                    + " la ventana de RN-06 permite cancelarla ahora mismo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de citas propias, de la mas reciente a la mas antigua"),
            @ApiResponse(responseCode = "400", description = "Rango invertido o estado desconocido"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no es PACIENTE, o la cuenta no tiene ficha (RNF-04)")
    })
    @GetMapping("/mias")
    public Page<CitaResumenDTO> misCitas(
            @Parameter(description = "Primer dia del rango, inclusive (AAAA-MM-DD). Omitirlo equivale a «sin limite»")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Ultimo dia del rango, inclusive (AAAA-MM-DD). Omitirlo equivale a «sin limite»")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @Parameter(description = "CONFIRMADA, ATENDIDA, NO_ASISTIO o CANCELADA (RN-09). Omitirlo equivale a «todos»")
            @RequestParam(required = false) String estado,
            // De la mas reciente a la mas antigua: quien abre sus citas viene a
            // ver la siguiente, no la de hace dos anos.
            @PageableDefault(size = 20, sort = "inicio", direction = Sort.Direction.DESC) Pageable pageable) {
        return citaConsultaService.mias(desde, hasta, estado, pageable);
    }

    @Operation(summary = "Cancelar una cita",
            description = "HU-11 · RF-20 · RECEPCIONISTA y ADMINISTRADOR, **sin ventana**: se puede cancelar"
                    + " cualquier cita confirmada, incluso dentro de las veinticuatro horas previas a su inicio."
                    + " HU-15 · RF-19 · PACIENTE, **solo la suya y dentro de la ventana de RN-06**: hasta"
                    + " veinticuatro horas antes del inicio, salvo que la reservara el mismo con menos antelacion,"
                    + " en cuyo caso puede deshacerla mientras se conserve el minimo de RN-05. La cita pasa a"
                    + " CANCELADA conservando su fila intacta (RN-12), la transicion queda en la bitacora con fecha,"
                    + " responsable y motivo (RF-21), y la franja vuelve a ofrecerse de inmediato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Cita cancelada"),
            @ApiResponse(responseCode = "400", description = "Cancelacion sin motivo"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "La cita no es suya, o no existe y quien pregunta es"
                    + " un PACIENTE: los dos casos responden igual para no revelar cuales existen (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cita con ese identificador (solo para recepcion y administracion)"),
            @ApiResponse(responseCode = "409", description = "La cita ya no esta CONFIRMADA: RN-09 no admite retornos"),
            @ApiResponse(responseCode = "422", description = "El paciente esta fuera de la ventana de RN-06: debe contactar con recepcion")
    })
    @PatchMapping("/{id}/cancelar")
    public CitaResponseDTO cancelar(@PathVariable UUID id,
            @Valid @RequestBody CancelacionRequestDTO peticion) {
        return cancelacionService.cancelar(id, peticion.getMotivo());
    }

    @Operation(summary = "Consultar las citas pendientes de cierre",
            description = "HU-16 · RF-22 · RECEPCIONISTA, ADMINISTRADOR y ODONTOLOGO. Las citas CONFIRMADA cuya"
                    + " hora de fin ya paso y siguen sin resultado (RN-09), de la mas antigua a la mas reciente:"
                    + " es una cola de trabajo, y lo que lleva mas tiempo sin cerrar es lo primero que hay que"
                    + " cerrar. El ODONTOLOGO ve solo las suyas; recepcion y administracion, las de toda la"
                    + " clinica. No hace falta filtrar por «sin resultado» aparte: registrar el resultado saca la"
                    + " cita de CONFIRMADA.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de citas pendientes de cierre"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede cerrar citas, o la cuenta de odontologo no tiene ficha (RNF-04)")
    })
    @GetMapping("/pendientes-cierre")
    public Page<CitaResumenDTO> pendientesDeCierre(
            @PageableDefault(size = 20) Pageable pageable) {
        return citaConsultaService.pendientesDeCierre(pageable);
    }

    @Operation(summary = "Registrar el resultado de una cita",
            description = "HU-16 · RF-22 · RECEPCIONISTA, ADMINISTRADOR y ODONTOLOGO (la propia). La cita pasa a"
                    + " ATENDIDA o NO_ASISTIO, sin retorno (RN-09), y la transicion queda en la bitacora con su"
                    + " responsable y su marca temporal (RF-21). Solo se puede registrar cuando la hora de fin ya"
                    + " ha pasado: las restricciones de exclusion de la base son parciales sobre CONFIRMADA, asi"
                    + " que cerrar una cita que aun no ha terminado liberaria una franja que todavia se va a"
                    + " ocupar. El PACIENTE no puede invocarla ni sobre la suya."
                    + " Si la cita queda ATENDIDA y su paciente tiene un plan activo del mismo tratamiento con"
                    + " sesiones pendientes, la cita ocupa la primera y la respuesta lo indica en"
                    + " `sesionEnlazada`; en cualquier otro caso ese campo no aparece.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Resultado registrado"),
            @ApiResponse(responseCode = "400", description = "Resultado ausente o distinto de ATENDIDA y NO_ASISTIO"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede cerrar citas, o la cita es de otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una cita con ese identificador"),
            @ApiResponse(responseCode = "409", description = "La cita ya esta en un estado final (RN-09), o todavia no ha terminado")
    })
    @PatchMapping("/{id}/resultado")
    public CitaResponseDTO registrarResultado(@PathVariable UUID id,
            @Valid @RequestBody ResultadoRequestDTO peticion) {
        return cierreDeCita.registrarResultado(id, peticion.getResultado());
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
