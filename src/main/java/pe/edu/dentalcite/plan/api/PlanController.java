package pe.edu.dentalcite.plan.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.plan.api.dto.CierreSesionRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanResponseDTO;
import pe.edu.dentalcite.plan.api.dto.SuspensionRequestDTO;
import pe.edu.dentalcite.plan.service.PlanService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Planes de tratamiento",
        description = "M6 · Creacion, suspension, cierre de sesiones y avance del plan de tratamiento"
                + " (HU-17 a HU-20)")
@RestController
@RequestMapping("/api/v1/planes")
@RequiredArgsConstructor
public class PlanController {

    private final PlanService planService;

    @Operation(summary = "Crear un plan de tratamiento",
            description = "HU-17 · RF-23 · ODONTOLOGO y ADMINISTRADOR. El plan nace ACTIVO con sus sesiones"
                    + " numeradas desde 1 y todas PENDIENTE (RN-15). Un paciente no puede tener dos planes"
                    + " activos del mismo tratamiento (RN-13): el segundo recibe 409, y lo garantiza un indice"
                    + " unico parcial en la base, no una comprobacion previa. El ODONTOLOGO planifica en su"
                    + " propio nombre y no manda `odontologoId`; el ADMINISTRADOR esta obligado a indicarlo."
                    + " El ODONTOLOGO solo planifica para pacientes a los que ha atendido (RNF-06), el mismo"
                    + " vinculo que le permite abrir la ficha en HU-13; recepcion y administracion no tienen"
                    + " esa restriccion. El avance NO viene en la respuesta: RN-14 lo deriva de las citas"
                    + " atendidas enlazadas, que es HU-18.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Plan creado, con sus sesiones pendientes"),
            @ApiResponse(responseCode = "400", description = "Cuerpo incompleto, sesiones previstas menores que 1, o alta de administrador sin `odontologoId`"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede planificar, un odontologo intento planificar en nombre de otro, o no ha atendido a este paciente (RNF-04, RNF-06)"),
            @ApiResponse(responseCode = "404", description = "El paciente, el tratamiento o el odontologo no existen o estan de baja"),
            @ApiResponse(responseCode = "409", description = "El paciente ya tiene un plan activo de ese tratamiento (RN-13)")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanResponseDTO crear(@Valid @RequestBody PlanRequestDTO peticion) {
        return planService.crear(peticion);
    }

    @Operation(summary = "Consultar los planes de un paciente",
            description = "HU-17 · ODONTOLOGO y ADMINISTRADOR. Los planes de un paciente, del mas reciente al"
                    + " mas antiguo, activos y suspendidos. Existe porque sin ella la suspension de RF-23 no"
                    + " tendria como alcanzarse desde la interfaz: hay que ver el plan en curso para poder"
                    + " suspenderlo."
                    + " Un plan lleva el nombre del paciente, asi que este listado es una lectura de la"
                    + " historia clinica y cae bajo RNF-06: el ODONTOLOGO solo ve los planes de pacientes a"
                    + " los que ha atendido."
                    + " Cada sesion trae la cita atendida que la ocupa, o `null` si sigue pendiente, y lo"
                    + " indicado al cerrarla. Desde HU-20 cada plan trae `avance` —sesiones completadas y"
                    + " pendientes—, calculado al responder sobre las citas atendidas enlazadas y nunca"
                    + " persistido (RN-14). El PACIENTE solo alcanza su propia ficha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planes del paciente, con su avance y la cita que ocupa cada sesion"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede consultar planes, o no ha atendido a este paciente (RNF-04, RNF-06)")
    })
    @GetMapping
    public List<PlanResponseDTO> deFicha(
            @Parameter(description = "Ficha del paciente cuyos planes se consultan")
            @RequestParam UUID pacienteId) {
        return planService.deFicha(pacienteId);
    }

    @Operation(summary = "Seguimiento de los planes de mis pacientes",
            description = "HU-20 · RF-26 · ODONTOLOGO. Los planes que firmo y todos los de los pacientes que"
                    + " tienen alguna cita suya sin cancelar, los firme quien los firme, con su avance. Primero"
                    + " los que siguen en curso y, dentro de cada grupo, el mas reciente arriba; el orden lo"
                    + " fija el servidor y el parametro `sort` se ignora. Por defecto solo los activos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de planes con su avance"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no es ODONTOLOGO, o la cuenta no esta vinculada a un registro de odontologo (RNF-04)")
    })
    @GetMapping("/seguimiento")
    public Page<PlanResponseDTO> seguimiento(
            @Parameter(description = "Falso para incluir tambien los planes suspendidos")
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @PageableDefault(size = 20) Pageable pageable) {
        return planService.seguimiento(soloActivos, pageable);
    }

    @Operation(summary = "Consultar el avance de un plan",
            description = "HU-20 · RF-26 · PACIENTE (los propios), ODONTOLOGO (el que lo firmo, o con el paciente"
                    + " vinculado por una cita sin cancelar) y ADMINISTRADOR. El plan con su avance y todas sus"
                    + " sesiones en orden: fecha de atencion de la cita que la ocupa, si la tiene, y lo indicado"
                    + " al cerrarla, si se cerro. Una sesion ATENDIDA es una sesion atendida pendiente de cierre."
                    + " Un plan inexistente da 404 solo al ADMINISTRADOR; al PACIENTE y al ODONTOLOGO les da 403,"
                    + " igual que el plan ajeno, para que la respuesta no revele que planes existen (RNF-06).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan con su avance y sus sesiones"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El plan es de otro paciente, el odontologo no esta vinculado a el, o no existe y quien pregunta no es administrador (RNF-04, RNF-06)"),
            @ApiResponse(responseCode = "404", description = "Solo al ADMINISTRADOR: no existe un plan con ese identificador")
    })
    @GetMapping("/{id}")
    public PlanResponseDTO obtener(@PathVariable UUID id) {
        return planService.obtener(id);
    }

    @Operation(summary = "Suspender un plan de tratamiento",
            description = "HU-17 · RF-23 · ADMINISTRADOR y el ODONTOLOGO que lo creo. El plan deja de estar"
                    + " activo y su registro se conserva intacto con el motivo (RN-12): nada se borra. Como el"
                    + " indice unico de RN-13 es parcial sobre la marca de actividad, ese tratamiento vuelve a"
                    + " poder planificarse en el acto, sin ningun trabajo extra.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan suspendido"),
            @ApiResponse(responseCode = "400", description = "Suspension sin motivo"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede suspender planes, o el plan lo creo otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe un plan con ese identificador"),
            @ApiResponse(responseCode = "409", description = "El plan ya estaba suspendido")
    })
    @PatchMapping("/{id}/suspender")
    public PlanResponseDTO suspender(@PathVariable UUID id,
            @Valid @RequestBody SuspensionRequestDTO peticion) {
        return planService.suspender(id, peticion.getMotivo());
    }

    @Operation(summary = "Cerrar una sesion con sus recomendaciones",
            description = "HU-19 · RF-27 · ADMINISTRADOR y el ODONTOLOGO que planifico. Registra lo que el"
                    + " paciente debe hacer hasta la siguiente sesion: una o mas recomendaciones del catalogo"
                    + " cerrado, la fecha sugerida del proximo control y, si hace falta, una observacion de"
                    + " cuidado de hasta 300 caracteres, que no es una indicacion clinica. La sesion pasa a"
                    + " CERRADA (RN-15) y lo registrado queda visible para el paciente en sus planes. Solo se"
                    + " cierra una sesion que ya tiene una cita atendida: la pendiente y la ya cerrada reciben"
                    + " 409. El orden de las comprobaciones es contrato: 404, 403 antes de mirar el estado —para"
                    + " no decir de rebote en que estado esta la sesion de un plan ajeno—, el 409 del estado y,"
                    + " al final, el 400 de lo que trae el cuerpo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Plan con la sesion ya cerrada"),
            @ApiResponse(responseCode = "400", description = "Sin recomendaciones, sin fecha de proximo control, con una fecha anterior a hoy, con una observacion de mas de 300 caracteres o con una recomendacion que no esta en el catalogo vigente"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no cierra sesiones, o el plan lo creo otro odontologo (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe el plan, o el plan no tiene esa sesion"),
            @ApiResponse(responseCode = "409", description = "La sesion no tiene una cita atendida, o ya estaba cerrada (RN-15)")
    })
    @PostMapping("/{id}/sesiones/{numero}/cierre")
    public PlanResponseDTO cerrarSesion(@PathVariable UUID id,
            @Parameter(description = "Numero de la sesion dentro del plan, desde 1")
            @PathVariable int numero,
            @Valid @RequestBody CierreSesionRequestDTO peticion) {
        return planService.cerrarSesion(id, numero, peticion);
    }
}
