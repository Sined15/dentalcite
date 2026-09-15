package pe.edu.dentalcite.plan.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
import pe.edu.dentalcite.plan.api.dto.PlanRequestDTO;
import pe.edu.dentalcite.plan.api.dto.PlanResponseDTO;
import pe.edu.dentalcite.plan.api.dto.SuspensionRequestDTO;
import pe.edu.dentalcite.plan.service.PlanService;

import java.util.List;
import java.util.UUID;

@Tag(name = "Planes de tratamiento",
        description = "M6 · Creacion y suspension del plan de tratamiento (HU-17, RF-23)")
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
                    + " Cada sesion trae la cita atendida que la ocupa, o `null` si sigue pendiente. **No trae"
                    + " el avance como cifra**: se cuenta sobre las sesiones, y la consulta con avance, linea de"
                    + " tiempo y recomendaciones llegara con su propio contrato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Planes del paciente, con la cita que ocupa cada sesion"),
            @ApiResponse(responseCode = "401", description = "Sin token o con token revocado"),
            @ApiResponse(responseCode = "403", description = "El rol no puede consultar planes, o no ha atendido a este paciente (RNF-04, RNF-06)")
    })
    @GetMapping
    public List<PlanResponseDTO> deFicha(
            @Parameter(description = "Ficha del paciente cuyos planes se consultan")
            @RequestParam UUID pacienteId) {
        return planService.deFicha(pacienteId);
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
}
