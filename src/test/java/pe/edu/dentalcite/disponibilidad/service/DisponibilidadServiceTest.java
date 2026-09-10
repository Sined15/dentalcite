package pe.edu.dentalcite.disponibilidad.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.bloqueo.repository.BloqueoRepository;
import pe.edu.dentalcite.cita.service.ReglasDeReserva;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.disponibilidad.api.dto.DisponibilidadResponseDTO;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.feriado.repository.FeriadoRepository;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La orquestación de HU-08: validación del rango, resolución del tratamiento,
 * selección de odontólogos por especialidad (RN-08) y uso de la caché. El cálculo
 * en sí se prueba desnudo en {@link MotorDeFranjasTest}.
 */
@ExtendWith(MockitoExtension.class)
class DisponibilidadServiceTest {

    @Mock private TratamientoRepository tratamientoRepository;
    @Mock private OdontologoRepository odontologoRepository;
    @Mock private HorarioAtencionRepository horarioRepository;
    @Mock private CitaRepository citaRepository;
    @Mock private BloqueoRepository bloqueoRepository;
    @Mock private ConsultorioRepository consultorioRepository;
    @Mock private FeriadoRepository feriadoRepository;
    @Mock private FranjasCache franjasCache;

    private DisponibilidadService disponibilidadService;

    private UUID tratamientoId;
    private UUID especialidadId;
    private UUID odontologoId;
    private Especialidad especialidad;
    private Tratamiento tratamiento;
    private Odontologo odontologo;
    private LocalDate desde;
    private LocalDate hasta;

    @BeforeEach
    void setUp() {
        // RN-17: los umbrales llegan por configuracion; aqui se usan los de defecto.
        disponibilidadService = new DisponibilidadService(tratamientoRepository, odontologoRepository,
                horarioRepository, citaRepository, bloqueoRepository, consultorioRepository,
                feriadoRepository, franjasCache, new ReglasDeReserva(2, 90, 3), "America/Lima", 14);

        tratamientoId = UUID.randomUUID();
        especialidadId = UUID.randomUUID();
        odontologoId = UUID.randomUUID();

        especialidad = Especialidad.builder().id(especialidadId).nombre("ENDODONCIA").activo(true).build();
        tratamiento = Tratamiento.builder()
                .id(tratamientoId).codigo("T-001").nombre("Endodoncia")
                .duracionMinutos(30).especialidad(especialidad).activo(true)
                .build();
        odontologo = Odontologo.builder()
                .id(odontologoId).cop("COP-90001").nombres("Juan").apellidos("Pérez")
                .activo(true).especialidades(Set.of(especialidad))
                .build();

        // Un rango futuro y estable: el motor descarta lo que caiga fuera de la
        // ventana de RN-05, y aquí solo se comprueba la orquestación.
        desde = LocalDate.now().plusDays(3);
        hasta = desde.plusDays(6);
    }

    private void agendaVacia() {
        lenient().when(horarioRepository.findByOdontologoIdIn(anyCollection())).thenReturn(List.of());
        lenient().when(citaRepository.findActivasEnRango(any(), any())).thenReturn(List.of());
        lenient().when(bloqueoRepository.findQueSolapanRango(any(), any())).thenReturn(List.of());
        lenient().when(consultorioRepository.findByInoperativoFalse())
                .thenReturn(List.of(Consultorio.builder().id(UUID.randomUUID()).nombre("C-1").inoperativo(false).build()));
        lenient().when(feriadoRepository.findByFechaBetween(any(), any())).thenReturn(List.of());
    }

    private void tratamientoExiste() {
        when(tratamientoRepository.findWithEspecialidadById(tratamientoId)).thenReturn(Optional.of(tratamiento));
    }

    // ------------------------------------------------------------------
    // Validación del rango (400)
    // ------------------------------------------------------------------

    @Test
    void consultar_conRangoInvertido_lanzaIllegalArgument() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> disponibilidadService.consultar(tratamientoId, null, hasta, desde));

        assertTrue(ex.getMessage().contains("anterior o igual"));
        verifyNoInteractions(tratamientoRepository);
    }

    @Test
    void consultar_conRangoMayorQueElMaximo_lanzaIllegalArgument() {
        // RNF-01 fija el umbral sobre catorce días; quince ya no se sirven.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> disponibilidadService.consultar(tratamientoId, null, desde, desde.plusDays(14)));

        assertTrue(ex.getMessage().contains("14"));
    }

    @Test
    void consultar_conRangoDeExactamenteElMaximo_loAcepta() {
        tratamientoExiste();
        when(odontologoRepository.findActivosConEspecialidad(especialidadId)).thenReturn(List.of());

        assertEquals(0, disponibilidadService
                .consultar(tratamientoId, null, desde, desde.plusDays(13))
                .getOdontologos().size());
    }

    @Test
    void consultar_conUnSoloDia_loAcepta() {
        tratamientoExiste();
        when(odontologoRepository.findActivosConEspecialidad(especialidadId)).thenReturn(List.of());

        assertEquals(desde, disponibilidadService.consultar(tratamientoId, null, desde, desde).getHasta());
    }

    // ------------------------------------------------------------------
    // Tratamiento (404)
    // ------------------------------------------------------------------

    @Test
    void consultar_conTratamientoInexistente_lanzaResourceNotFound() {
        when(tratamientoRepository.findWithEspecialidadById(tratamientoId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> disponibilidadService.consultar(tratamientoId, null, desde, hasta));
    }

    @Test
    void consultar_conTratamientoDadoDeBaja_lanzaResourceNotFound() {
        // No se agenda contra un tratamiento retirado del catálogo (RN-12).
        tratamiento.setActivo(false);
        when(tratamientoRepository.findWithEspecialidadById(tratamientoId)).thenReturn(Optional.of(tratamiento));

        assertThrows(ResourceNotFoundException.class,
                () -> disponibilidadService.consultar(tratamientoId, null, desde, hasta));
    }

    // ------------------------------------------------------------------
    // RN-08 · selección de odontólogos
    // ------------------------------------------------------------------

    @Test
    void consultar_sinOdontologo_soloPideLosQuePoseenLaEspecialidad() {
        tratamientoExiste();
        agendaVacia();
        when(odontologoRepository.findActivosConEspecialidad(especialidadId)).thenReturn(List.of(odontologo));

        disponibilidadService.consultar(tratamientoId, null, desde, hasta);

        verify(odontologoRepository).findActivosConEspecialidad(especialidadId);
        verify(odontologoRepository, never()).findConEspecialidadesById(any());
    }

    @Test
    void consultar_conOdontologoQueNoPoseeLaEspecialidad_devuelveAgendaVacia() {
        // No es un error del cliente: es que ese odontólogo no atiende eso (RN-08).
        tratamientoExiste();
        Odontologo otro = Odontologo.builder()
                .id(odontologoId).cop("COP-90002").nombres("Ana").apellidos("Ruiz")
                .activo(true).especialidades(Set.of(Especialidad.builder().id(UUID.randomUUID()).build()))
                .build();
        when(odontologoRepository.findConEspecialidadesById(odontologoId)).thenReturn(Optional.of(otro));

        DisponibilidadResponseDTO respuesta =
                disponibilidadService.consultar(tratamientoId, odontologoId, desde, hasta);

        assertTrue(respuesta.getOdontologos().isEmpty());
        verifyNoInteractions(horarioRepository, citaRepository, bloqueoRepository);
    }

    @Test
    void consultar_conOdontologoDadoDeBaja_devuelveAgendaVacia() {
        tratamientoExiste();
        odontologo.setActivo(false);
        when(odontologoRepository.findConEspecialidadesById(odontologoId)).thenReturn(Optional.of(odontologo));

        assertTrue(disponibilidadService.consultar(tratamientoId, odontologoId, desde, hasta)
                .getOdontologos().isEmpty());
    }

    @Test
    void consultar_conOdontologoInexistente_lanzaResourceNotFound() {
        tratamientoExiste();
        when(odontologoRepository.findConEspecialidadesById(odontologoId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> disponibilidadService.consultar(tratamientoId, odontologoId, desde, hasta));
    }

    // ------------------------------------------------------------------
    // Forma de la respuesta y caché
    // ------------------------------------------------------------------

    @Test
    void consultar_conAgendaDeclarada_devuelveLasFranjasDelOdontologo() {
        tratamientoExiste();
        agendaVacia();
        when(odontologoRepository.findActivosConEspecialidad(especialidadId)).thenReturn(List.of(odontologo));
        // Un tramo cada día del rango, para no depender de qué día de la semana caiga.
        when(horarioRepository.findByOdontologoIdIn(anyCollection())).thenReturn(
                java.util.stream.IntStream.rangeClosed(1, 7)
                        .mapToObj(dia -> HorarioAtencion.builder()
                                .id(UUID.randomUUID()).odontologo(odontologo).diaSemana(dia)
                                .horaInicio(LocalTime.parse("09:00")).horaFin(LocalTime.parse("11:00"))
                                .build())
                        .toList());

        DisponibilidadResponseDTO respuesta =
                disponibilidadService.consultar(tratamientoId, null, desde, hasta);

        assertEquals(tratamientoId, respuesta.getTratamientoId());
        assertEquals(30, respuesta.getDuracionMinutos());
        assertEquals("America/Lima", respuesta.getZonaHoraria());
        assertEquals(1, respuesta.getOdontologos().size());
        assertEquals("Juan", respuesta.getOdontologos().get(0).getNombres());
        assertFalse(respuesta.getOdontologos().get(0).getDias().isEmpty());
        assertEquals(LocalTime.parse("09:00"),
                respuesta.getOdontologos().get(0).getDias().get(0).getInicios().get(0));
    }

    @Test
    void consultar_conRespuestaCacheada_noConsultaLaAgenda() {
        tratamientoExiste();
        DisponibilidadResponseDTO cacheada = DisponibilidadResponseDTO.builder()
                .tratamientoId(tratamientoId).duracionMinutos(30).odontologos(List.of()).build();
        when(franjasCache.clave(tratamientoId, null, desde, hasta)).thenReturn("clave");
        when(franjasCache.leer("clave")).thenReturn(cacheada);

        assertEquals(cacheada, disponibilidadService.consultar(tratamientoId, null, desde, hasta));

        verifyNoInteractions(odontologoRepository, horarioRepository, citaRepository,
                bloqueoRepository, consultorioRepository, feriadoRepository);
    }

    @Test
    void consultar_sinAciertoDeCache_guardaElResultado() {
        tratamientoExiste();
        agendaVacia();
        when(odontologoRepository.findActivosConEspecialidad(especialidadId)).thenReturn(List.of());
        when(franjasCache.clave(tratamientoId, null, desde, hasta)).thenReturn("clave");
        when(franjasCache.leer("clave")).thenReturn(null);

        DisponibilidadResponseDTO respuesta =
                disponibilidadService.consultar(tratamientoId, null, desde, hasta);

        verify(franjasCache).guardar("clave", respuesta);
    }
}
