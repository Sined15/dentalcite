package pe.edu.dentalcite.publico.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.publico.api.dto.CalendarioPublicoDTO;
import pe.edu.dentalcite.publico.api.dto.EspecialidadDetalleDTO;
import pe.edu.dentalcite.publico.api.dto.EspecialidadPublicaDTO;
import pe.edu.dentalcite.publico.api.dto.OdontologoPublicoDTO;
import pe.edu.dentalcite.publico.api.dto.TratamientoPublicoDTO;
import pe.edu.dentalcite.publico.service.CalendarioPublicoService;
import pe.edu.dentalcite.publico.service.CatalogoPublicoService;

import java.util.List;
import java.util.UUID;

/**
 * Catálogo clínico abierto: lo único que la API responde sin sesión, junto con la
 * consulta de disponibilidad (RNF-04, revisión v4).
 *
 * <p>Son rutas propias y no las de {@code /api/v1/especialidades} abiertas,
 * porque la forma de la respuesta cambia: aquí no hay {@code fichaId}, ni estado,
 * ni descripción de la especialidad, ni filas dadas de baja. Un mismo camino que
 * devolviera una cosa u otra según quién pregunte no se podría publicar en el
 * contrato.
 *
 * <p><strong>Publico no quiere decir que no autentique.</strong> Si llega una
 * cabecera {@code Authorization} con un token que no vale, el filtro del servidor
 * de recursos la rechaza con 401 antes de llegar aqui, aunque la regla sea
 * {@code permitAll}. Esta documentado en cada operacion porque tiene consecuencia
 * en el cliente: su interceptor cierra la sesion ante un 401.
 */
@Tag(name = "Catalogo publico", description = "M3 · El catalogo clinico sin sesion (HU-06)")
@RestController
@RequestMapping("/api/v1/publico")
@RequiredArgsConstructor
public class PublicoController {

    private final CatalogoPublicoService catalogoPublicoService;
    private final CalendarioPublicoService calendarioPublicoService;

    @Operation(summary = "Dias en que la clinica atiende",
            description = "Publico, sin token. Los dias de la semana en que atiende alguien (1 = lunes,"
                    + " 7 = domingo), los feriados, y los dias de cada odontologo por separado. Lo consume el"
                    + " calendario del cliente para pintar cerrados los dias que no se pueden elegir: sin este"
                    + " dato ofrecia domingos y feriados que la consulta de disponibilidad descarta despues,"
                    + " sin explicar por que. Viaja entero porque el calendario se estrecha al elegir"
                    + " odontologo y una consulta por cambio seria un viaje por clic.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dias de atencion, feriados y el desglose por odontologo"),
            @ApiResponse(responseCode = "401", description = "Se envio una cabecera Authorization con un token invalido. Sin cabecera, la ruta responde 200")
    })
    @GetMapping("/calendario")
    public ResponseEntity<CalendarioPublicoDTO> calendario() {
        return ResponseEntity.ok(calendarioPublicoService.consultar());
    }

    @Operation(summary = "Tratamientos que ofrece la clinica",
            description = "HU-06 · Publico, sin token. Los tratamientos activos, que son los que anuncia la portada.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tratamientos activos"),
            @ApiResponse(responseCode = "401", description = "Se envio una cabecera Authorization con un token invalido. Sin cabecera, la ruta responde 200")
    })
    @GetMapping("/tratamientos")
    public ResponseEntity<List<TratamientoPublicoDTO>> listarTratamientos() {
        return ResponseEntity.ok(catalogoPublicoService.listarTratamientos());
    }

    @Operation(summary = "Especialidades de la clinica",
            description = "HU-06 · Publico, sin token. Imagen y nombre: ni descripcion, ni estado, ni mantenimiento.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Especialidades activas"),
            @ApiResponse(responseCode = "401", description = "Se envio una cabecera Authorization con un token invalido. Sin cabecera, la ruta responde 200")
    })
    @GetMapping("/especialidades")
    public ResponseEntity<List<EspecialidadPublicaDTO>> listarEspecialidades() {
        return ResponseEntity.ok(catalogoPublicoService.listarEspecialidades());
    }

    @Operation(summary = "Una especialidad con sus tratamientos y sus odontologos",
            description = "HU-06 · Publico, sin token. Es la pantalla desde la que el visitante decide pedir cita.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Detalle de la especialidad"),
            @ApiResponse(responseCode = "404", description = "No se ofrece ninguna especialidad con ese identificador"),
            @ApiResponse(responseCode = "401", description = "Se envio una cabecera Authorization con un token invalido. Sin cabecera, la ruta responde 200")
    })
    @GetMapping("/especialidades/{id}")
    public ResponseEntity<EspecialidadDetalleDTO> detalleDeEspecialidad(@PathVariable UUID id) {
        return ResponseEntity.ok(catalogoPublicoService.detalleDeEspecialidad(id));
    }

    @Operation(summary = "El equipo de odontologos",
            description = "HU-06 · Publico, sin token. Ficha con imagen, nombre, especialidad y COP (RF-10).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Odontologos activos"),
            @ApiResponse(responseCode = "401", description = "Se envio una cabecera Authorization con un token invalido. Sin cabecera, la ruta responde 200")
    })
    @GetMapping("/odontologos")
    public ResponseEntity<List<OdontologoPublicoDTO>> listarOdontologos() {
        return ResponseEntity.ok(catalogoPublicoService.listarOdontologos());
    }
}
