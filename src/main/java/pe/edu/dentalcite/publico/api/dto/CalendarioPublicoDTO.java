package pe.edu.dentalcite.publico.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarioPublicoDTO {
    private List<Integer> diasDeAtencion;

    private List<LocalDate> feriados;
    private List<OdontologoDias> porOdontologo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OdontologoDias {
        private UUID odontologoId;
        private List<Integer> dias;
    }
}
