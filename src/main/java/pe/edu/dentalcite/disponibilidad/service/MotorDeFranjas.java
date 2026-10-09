package pe.edu.dentalcite.disponibilidad.service;

import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.odontologo.domain.Odontologo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MotorDeFranjas {

    public static final int PASO_MINUTOS = 15;

    private MotorDeFranjas() {
    }

    public static Map<UUID, Map<LocalDate, List<LocalTime>>> calcular(AgendaDelRango agenda) {
        Map<UUID, Map<LocalDate, List<LocalTime>>> resultado = new LinkedHashMap<>();

        for (Odontologo odontologo : agenda.odontologos()) {
            List<HorarioAtencion> horarios =
                    agenda.horariosPorOdontologo().getOrDefault(odontologo.getId(), List.of());
            if (horarios.isEmpty()) {
                continue;
            }

            Map<LocalDate, List<LocalTime>> dias = new LinkedHashMap<>();
            for (LocalDate fecha = agenda.desde(); !fecha.isAfter(agenda.hasta()); fecha = fecha.plusDays(1)) {
                if (agenda.feriados().contains(fecha)) {
                    continue;
                }
                List<LocalTime> inicios = iniciosDelDia(agenda, odontologo.getId(), horarios, fecha);
                if (!inicios.isEmpty()) {
                    dias.put(fecha, inicios);
                }
            }

            if (!dias.isEmpty()) {
                resultado.put(odontologo.getId(), dias);
            }
        }
        return resultado;
    }

    private static List<LocalTime> iniciosDelDia(AgendaDelRango agenda, UUID odontologoId,
            List<HorarioAtencion> horarios, LocalDate fecha) {
        Integer diaSemana = fecha.getDayOfWeek().getValue();
        List<LocalTime> inicios = new ArrayList<>();

        for (HorarioAtencion tramo : horarios) {
            if (!diaSemana.equals(tramo.getDiaSemana())) {
                continue;
            }
            LocalDateTime finTramo = LocalDateTime.of(fecha, tramo.getHoraFin());
            for (LocalDateTime candidato = LocalDateTime.of(fecha, tramo.getHoraInicio());
                    !candidato.plusMinutes(agenda.duracionMinutos()).isAfter(finTramo);
                    candidato = candidato.plusMinutes(PASO_MINUTOS)) {

                OffsetDateTime inicio = candidato.atZone(agenda.zona()).toOffsetDateTime();
                OffsetDateTime fin = inicio.plusMinutes(agenda.duracionMinutos());

                if (esOfrecible(agenda, odontologoId, inicio, fin)) {
                    inicios.add(candidato.toLocalTime());
                }
            }
        }

        inicios.sort(LocalTime::compareTo);
        return inicios;
    }

    private static boolean esOfrecible(AgendaDelRango agenda, UUID odontologoId,
            OffsetDateTime inicio, OffsetDateTime fin) {
        if (!agenda.reglas().dentroDeVentana(inicio, agenda.ahora())) {
            return false;
        }
        if (Intervalo.algunoSolapa(agenda.citasPorOdontologo().get(odontologoId), inicio, fin)) {
            return false;
        }
        if (Intervalo.algunoSolapa(agenda.bloqueosPorOdontologo().get(odontologoId), inicio, fin)) {
            return false;
        }
        return hayConsultorioLibre(agenda, inicio, fin);
    }

    private static boolean hayConsultorioLibre(AgendaDelRango agenda, OffsetDateTime inicio, OffsetDateTime fin) {
        return ConsultoriosLibres.hayAlguno(agenda.consultoriosOperativos(),
                agenda.citasPorConsultorio(), agenda.bloqueosPorConsultorio(), inicio, fin);
    }
}
