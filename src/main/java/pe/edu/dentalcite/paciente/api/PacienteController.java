package pe.edu.dentalcite.paciente.api;

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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.paciente.api.dto.PacienteDetalleDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteRequestDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteResponseDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteUpdateRequestDTO;
import pe.edu.dentalcite.paciente.service.PacienteService;

import java.util.UUID;

@Tag(name = "Pacientes", description = "M2 · Alta presencial del paciente sin cuenta (HU-12); busqueda y ficha con alergias y citas (HU-13)")
@RestController
@RequestMapping("/api/v1/pacientes")
@RequiredArgsConstructor
public class PacienteController {

    private final PacienteService pacienteService;

    @Operation(summary = "Registrar un paciente presencial",
            description = "HU-12 · RF-06 · RECEPCIONISTA y ADMINISTRADOR. Crea la ficha de quien se atiende en la clinica sin exigirle una cuenta: genera su numero de historia desde la secuencia y deja constancia del consentimiento informado con su fecha y la version del texto (RNF-06). El par tipo y numero de documento identifica al paciente, de modo que repetirlo es un conflicto y no un alta nueva (RN-10). Si esa persona se registra despues en el portal con el mismo documento, aquella ficha se le vincula en lugar de duplicarse.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Ficha creada, con su numero de historia y sin credenciales de acceso"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos, o no consta el consentimiento informado del titular (RNF-06)"),
            @ApiResponse(responseCode = "403", description = "El rol no es RECEPCIONISTA ni ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "409", description = "Ya existe un paciente con ese tipo y numero de documento (RN-10)")
    })
    @PostMapping
    public ResponseEntity<PacienteResponseDTO> registrarPaciente(@Valid @RequestBody PacienteRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pacienteService.registrar(request));
    }

    @Operation(summary = "Buscar pacientes",
            description = "HU-13 · RF-07 · RECEPCIONISTA, ADMINISTRADOR y ODONTOLOGO. Un unico termino que se compara con el numero de documento, el numero de historia y el apellido, porque quien atiende el mostrador teclea lo que le dicen sin clasificarlo antes; documento e historia por igualdad, apellido por prefijo. Sin termino devuelve todas las fichas. El ODONTOLOGO solo ve a las personas a las que ha atendido (RNF-06). Resultados paginados, ordenados por apellidos.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina de pacientes"),
            @ApiResponse(responseCode = "403", description = "El rol es PACIENTE, que no busca pacientes (RNF-04)")
    })
    @GetMapping
    public ResponseEntity<Page<PacienteResponseDTO>> buscarPacientes(
            @RequestParam(required = false) String q,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(pacienteService.buscar(q, pageable));
    }

    @Operation(summary = "Consultar la ficha de un paciente",
            description = "HU-13 · RF-08 · RECEPCIONISTA y ADMINISTRADOR sobre cualquiera; ODONTOLOGO solo sobre los pacientes a los que ha atendido; PACIENTE solo sobre la suya (RNF-06). Devuelve sus datos, sus alergias y sus citas pasadas y futuras, de la mas reciente a la mas antigua y en la hora local de la clinica.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ficha del paciente"),
            @ApiResponse(responseCode = "403", description = "No ha atendido a esa persona, o no es su propia ficha (RNF-06)"),
            @ApiResponse(responseCode = "404", description = "No existe una ficha con ese identificador")
    })
    @GetMapping("/{id}")
    public ResponseEntity<PacienteDetalleDTO> obtenerPaciente(@PathVariable UUID id) {
        return ResponseEntity.ok(pacienteService.obtener(id));
    }

    @Operation(summary = "Corregir los datos de la ficha",
            description = "HU-13 · RF-08 · RECEPCIONISTA y ADMINISTRADOR. Reemplazo total de lo editable: nombres, apellidos, telefono y alergias. El tipo y el numero de documento no se editan, porque RN-10 los declara la identidad del paciente y cambiarlos convertiria esta ficha en la de otra persona, con su historia clinica y sus citas dentro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ficha actualizada"),
            @ApiResponse(responseCode = "400", description = "Datos invalidos"),
            @ApiResponse(responseCode = "403", description = "El rol no es RECEPCIONISTA ni ADMINISTRADOR (RNF-04)"),
            @ApiResponse(responseCode = "404", description = "No existe una ficha con ese identificador")
    })
    @PutMapping("/{id}")
    public ResponseEntity<PacienteDetalleDTO> actualizarPaciente(
            @PathVariable UUID id,
            @Valid @RequestBody PacienteUpdateRequestDTO request) {
        return ResponseEntity.ok(pacienteService.actualizar(id, request));
    }
}
