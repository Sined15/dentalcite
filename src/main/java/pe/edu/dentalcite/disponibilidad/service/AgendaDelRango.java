package pe.edu.dentalcite.disponibilidad.service;

import pe.edu.dentalcite.cita.service.ReglasDeReserva;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.odontologo.domain.Odontologo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AgendaDelRango(
        int duracionMinutos,
        LocalDate desde,
        LocalDate hasta,
        ZoneId zona,
        OffsetDateTime ahora,
        ReglasDeReserva reglas,
        List<Odontologo> odontologos,
        Map<UUID, List<HorarioAtencion>> horariosPorOdontologo,
        Map<UUID, List<Intervalo>> citasPorOdontologo,
        Map<UUID, List<Intervalo>> citasPorConsultorio,
        Map<UUID, List<Intervalo>> bloqueosPorOdontologo,
        Map<UUID, List<Intervalo>> bloqueosPorConsultorio,
        List<UUID> consultoriosOperativos,
        Set<LocalDate> feriados) {
}
