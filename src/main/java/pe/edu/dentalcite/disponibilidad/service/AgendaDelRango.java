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

/**
 * Todo lo que el motor necesita para calcular, ya leído de la base de datos.
 *
 * <p>Existe para que {@link MotorDeFranjas} no tenga repositorios: el servicio
 * emite un número fijo de consultas —cinco, independientes del tamaño del rango y
 * del número de odontólogos— y el cálculo ocurre después, en memoria. Es lo que
 * sostiene el umbral de RNF-01 con la caché fría; una consulta por día habría
 * hecho crecer el coste con el rango consultado.
 *
 * @param duracionMinutos        duración del tratamiento (RN-04, múltiplo de quince)
 * @param ahora                  instante de la consulta, para la ventana de RN-05
 * @param reglas                 umbrales de RN-05, inyectados en vez de compilados (RN-17)
 * @param horariosPorOdontologo  tramos declarados en HU-07, indexados por odontólogo
 * @param citasPorOdontologo     ocupación del odontólogo (RN-03)
 * @param citasPorConsultorio    ocupación del pool que RN-02 comparte entre todos
 * @param consultoriosOperativos consultorios no inoperativos, candidatos de RF-14
 */
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
