package pe.edu.dentalcite.publico.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.dentalcite.feriado.domain.Feriado;
import pe.edu.dentalcite.feriado.repository.FeriadoRepository;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.publico.api.dto.CalendarioPublicoDTO;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Los días en que la clínica abre, tal como los necesita un calendario.
 *
 * <p>Vive aparte de {@link CatalogoPublicoService} porque no es catálogo: aquel
 * publica lo que la clínica ofrece y este cuándo lo ofrece. Comparten el ser
 * accesibles sin sesión, y nada más.
 *
 * <p>Es de lectura y no expone nada sensible: qué días abre una clínica está en
 * su puerta. Lo que no sale de aquí son las horas ni las citas, que son otra
 * consulta y con otra autorización.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarioPublicoService {

    private final HorarioAtencionRepository horarioRepository;
    private final FeriadoRepository feriadoRepository;

    /**
     * Una sola consulta al horario y otra a los feriados, y el agrupado por
     * odontólogo se hace en memoria. Son unas decenas de tramos: pedir los días
     * de cada odontólogo por separado serían siete consultas para el mismo dato.
     */
    public CalendarioPublicoDTO consultar() {
        List<HorarioAtencion> tramos = horarioRepository.findDeOdontologosActivos();

        Map<UUID, TreeSet<Integer>> porOdontologo = tramos.stream().collect(Collectors.groupingBy(
                tramo -> tramo.getOdontologo().getId(),
                Collectors.mapping(HorarioAtencion::getDiaSemana,
                        Collectors.toCollection(TreeSet::new))));

        // El día en que no atiende nadie es el día en que la clínica cierra.
        TreeSet<Integer> deLaClinica = tramos.stream()
                .map(HorarioAtencion::getDiaSemana)
                .collect(Collectors.toCollection(TreeSet::new));

        return CalendarioPublicoDTO.builder()
                .diasDeAtencion(List.copyOf(deLaClinica))
                .feriados(feriadoRepository.findAllByOrderByFechaAsc().stream()
                        .map(Feriado::getFecha)
                        .toList())
                .porOdontologo(porOdontologo.entrySet().stream()
                        .map(entrada -> CalendarioPublicoDTO.OdontologoDias.builder()
                                .odontologoId(entrada.getKey())
                                .dias(List.copyOf(entrada.getValue()))
                                .build())
                        .sorted(Comparator.comparing(
                                dias -> dias.getOdontologoId().toString()))
                        .toList())
                .build();
    }
}
