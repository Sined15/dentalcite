package pe.edu.dentalcite.consultorio.api;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pe.edu.dentalcite.consultorio.api.dto.ConsultorioResponseDTO;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;

import java.util.List;

@RestController
@RequestMapping("/api/v1/consultorios")
@RequiredArgsConstructor
public class ConsultorioController {

    private final ConsultorioRepository consultorioRepository;

    @GetMapping
    public ResponseEntity<List<ConsultorioResponseDTO>> listarConsultorios() {
        return ResponseEntity.ok(consultorioRepository.findAll().stream()
                .map(c -> ConsultorioResponseDTO.builder()
                        .id(c.getId())
                        .nombre(c.getNombre())
                        .inoperativo(c.getInoperativo())
                        .build())
                .toList());
    }
}
