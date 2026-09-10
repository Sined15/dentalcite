package pe.edu.dentalcite.cita.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.cita.api.dto.CitaRequestDTO;
import pe.edu.dentalcite.cita.api.dto.CitaResponseDTO;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.common.exception.ReglaIncumplidaException;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.disponibilidad.api.dto.AgendaOdontologoDTO;
import pe.edu.dentalcite.disponibilidad.api.dto.DiaDisponibleDTO;
import pe.edu.dentalcite.disponibilidad.api.dto.DisponibilidadResponseDTO;
import pe.edu.dentalcite.disponibilidad.service.DisponibilidadService;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Las reglas de HU-09, una por prueba. El cálculo de la franja no se ejercita
 * aquí: el servicio se lo delega al motor, y por eso el motor se puede simular.
 */
@ExtendWith(MockitoExtension.class)
class CitaServiceTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Mock private CitaRepository citaRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private TratamientoRepository tratamientoRepository;
    @Mock private OdontologoRepository odontologoRepository;
    @Mock private ConsultorioRepository consultorioRepository;
    @Mock private DisponibilidadService disponibilidadService;
    @Mock private BloqueoDeFranja bloqueoDeFranja;
    @Mock private RegistroDeCita registroDeCita;

    private CitaService citaService;

    private UUID usuarioId;
    private UUID tratamientoId;
    private UUID odontologoId;
    private UUID consultorioId;
    private Ficha ficha;
    private Usuario usuario;
    private Tratamiento tratamiento;
    private Odontologo odontologo;
    private Consultorio consultorio;

    /** Dentro de la ventana de RN-05 por los dos lados: mañana. */
    private LocalDate fecha;
    private final LocalTime hora = LocalTime.of(9, 15);

    @BeforeEach
    void setUp() {
        citaService = new CitaService(citaRepository, usuarioRepository, tratamientoRepository,
                odontologoRepository, consultorioRepository, disponibilidadService,
                bloqueoDeFranja, registroDeCita, new ReglasDeReserva(2, 90, 3), "America/Lima");

        usuarioId = UUID.randomUUID();
        tratamientoId = UUID.randomUUID();
        odontologoId = UUID.randomUUID();
        consultorioId = UUID.randomUUID();
        fecha = LocalDate.now(ZONA).plusDays(1);

        ficha = Ficha.builder().id(UUID.randomUUID()).documento("12345678")
                .nombres("Ana").apellidos("Torres").numeroHistoria("HC-00001").build();
        usuario = Usuario.builder().id(usuarioId).correo("paciente@demo.com").rol("PACIENTE")
                .activo(true).ficha(ficha).build();

        Especialidad especialidad = Especialidad.builder().id(UUID.randomUUID())
                .nombre("ENDODONCIA").activo(true).build();
        tratamiento = Tratamiento.builder().id(tratamientoId).codigo("T-001").nombre("Endodoncia")
                .duracionMinutos(30).especialidad(especialidad).activo(true).build();
        odontologo = Odontologo.builder().id(odontologoId).cop("COP-1").nombres("Luis")
                .apellidos("Pérez").activo(true).especialidades(Set.of(especialidad)).build();
        consultorio = Consultorio.builder().id(consultorioId).nombre("Consultorio 2")
                .inoperativo(false).build();

        autenticarComoPaciente();
    }

    @AfterEach
    void limpiarContexto() {
        // Sin esto el contexto de seguridad se filtra entre pruebas y las de 403
        // pasarían por la razón equivocada.
        SecurityContextHolder.clearContext();
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    private void autenticarComoPaciente() {
        autenticar(usuarioId.toString(), "SCOPE_PACIENTE");
    }

    private void autenticar(String nombre, String autoridad) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(nombre, "n/a",
                        List.of(new SimpleGrantedAuthority(autoridad))));
    }

    private CitaRequestDTO peticion() {
        return CitaRequestDTO.builder()
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(fecha).hora(hora).build();
    }

    /** El camino feliz completo: usuario, catálogo, franja ofrecida y consultorio. */
    private void todoEnOrden() {
        lenient().when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        lenient().when(tratamientoRepository.findWithEspecialidadById(tratamientoId))
                .thenReturn(Optional.of(tratamiento));
        lenient().when(odontologoRepository.findConEspecialidadesById(odontologoId))
                .thenReturn(Optional.of(odontologo));
        lenient().when(citaRepository.countActivasDeFicha(ficha.getId())).thenReturn(0L);
        lenient().when(disponibilidadService.consultar(tratamientoId, odontologoId, fecha, fecha))
                .thenReturn(ofertaCon(hora));
        lenient().when(disponibilidadService.consultoriosLibres(any(), any()))
                .thenReturn(List.of(consultorioId));
        lenient().when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));
        lenient().when(bloqueoDeFranja.tomar(any(), any())).thenReturn(bloqueoTomado());
        lenient().when(registroDeCita.crear(any(), any(), any(), any(), any(), any()))
                .thenAnswer(inv -> citaCreada(inv.getArgument(3)));
    }

    private BloqueoDeFranja.Adquisicion bloqueoTomado() {
        return new BloqueoDeFranja.Adquisicion(BloqueoDeFranja.Estado.TOMADO, "clave", "token");
    }

    /** Lo que devolveria el INSERT: la cita ya persistida con su codigo. */
    private Cita citaCreada(UUID consultorioId) {
        return Cita.builder()
                .id(UUID.randomUUID())
                .codigo("CIT-000123")
                .ficha(ficha)
                .odontologo(odontologo)
                .tratamiento(tratamiento)
                .consultorio(Consultorio.builder().id(consultorioId).nombre("Consultorio 2")
                        .inoperativo(false).build())
                .inicio(fecha.atTime(hora).atZone(ZONA).toOffsetDateTime())
                .fin(fecha.atTime(hora).plusMinutes(30).atZone(ZONA).toOffsetDateTime())
                .estado(Cita.ESTADO_CONFIRMADA)
                .build();
    }

    /** El error que lanza PostgreSQL cuando salta una de las dos exclusiones. */
    private DataIntegrityViolationException solapeDe(String restriccion) {
        return new DataIntegrityViolationException("choque",
                new org.hibernate.exception.ConstraintViolationException(
                        "conflicting key value violates exclusion constraint",
                        new java.sql.SQLException("23P01"), restriccion));
    }

    /** Lo que llega cuando PostgreSQL elige víctima de un interbloqueo (40P01). */
    private CannotAcquireLockException interbloqueo() {
        return new CannotAcquireLockException("interbloqueo",
                new java.sql.SQLException("ERROR: deadlock detected", "40P01"));
    }

    private DisponibilidadResponseDTO ofertaCon(LocalTime... horas) {
        return DisponibilidadResponseDTO.builder()
                .tratamientoId(tratamientoId).duracionMinutos(30)
                .desde(fecha).hasta(fecha).zonaHoraria("America/Lima")
                .odontologos(List.of(AgendaOdontologoDTO.builder()
                        .id(odontologoId).nombres("Luis").apellidos("Pérez")
                        .dias(List.of(DiaDisponibleDTO.builder()
                                .fecha(fecha).inicios(List.of(horas)).build()))
                        .build()))
                .build();
    }

    // ------------------------------------------------------------------
    // Camino feliz · RF-15, RN-09, RF-14
    // ------------------------------------------------------------------

    @Test
    void reservar_conFranjaDisponible_creaLaCitaConfirmadaConSuCodigo() {
        todoEnOrden();

        CitaResponseDTO respuesta = citaService.reservar(peticion());

        assertNotNull(respuesta.getId());
        assertEquals("CIT-000123", respuesta.getCodigo(), "el correlativo sale de la secuencia");
        assertEquals("CONFIRMADA", respuesta.getEstado(), "RN-09: toda cita nace confirmada");
        assertEquals(fecha, respuesta.getFecha());
        assertEquals(hora, respuesta.getHora());
        assertEquals(30, respuesta.getDuracionMinutos());
        assertEquals("America/Lima", respuesta.getZonaHoraria());
        assertEquals("Consultorio 2", respuesta.getConsultorio().getNombre(), "RF-14: consultorio asignado");
        assertEquals("Luis Pérez", respuesta.getOdontologo().getNombre());
    }

    @Test
    void reservar_conFranjaDisponible_guardaElIntervaloDelTratamiento() {
        todoEnOrden();

        citaService.reservar(peticion());

        ZonedDateTime inicioEsperado = fecha.atTime(hora).atZone(ZONA);
        // RN-04: la duración de la cita es la del tratamiento.
        verify(registroDeCita).crear(eq(ficha.getId()), eq(odontologoId), eq(tratamientoId),
                eq(consultorioId),
                eq(inicioEsperado.toOffsetDateTime()),
                eq(inicioEsperado.plusMinutes(30).toOffsetDateTime()));
    }

    @Test
    void reservar_tomaYLiberaElBloqueoDeLaFranja() {
        // HU-10: es lo que impide que dos pacientes elijan al mismo odontólogo a
        // la misma hora en el mismo instante.
        todoEnOrden();

        citaService.reservar(peticion());

        verify(bloqueoDeFranja).tomar(odontologoId, fecha.atTime(hora).atZone(ZONA).toOffsetDateTime());
        verify(bloqueoDeFranja).liberar(any());
    }

    // ------------------------------------------------------------------
    // RN-05 · ventana de reserva → 422
    // ------------------------------------------------------------------

    @Test
    void reservar_conFranjaAMenosDeDosHoras_lanzaReglaIncumplida() {
        todoEnOrden();
        CitaRequestDTO ahora = CitaRequestDTO.builder()
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(LocalDate.now(ZONA)).hora(LocalTime.now(ZONA).plusMinutes(30))
                .build();

        ReglaIncumplidaException ex = assertThrows(ReglaIncumplidaException.class,
                () -> citaService.reservar(ahora));

        assertTrue(ex.getMessage().contains("2 horas"));
        verify(registroDeCita, never()).crear(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservar_conFranjaAMasDeNoventaDias_lanzaReglaIncumplida() {
        todoEnOrden();
        CitaRequestDTO lejana = CitaRequestDTO.builder()
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(LocalDate.now(ZONA).plusDays(120)).hora(hora)
                .build();

        ReglaIncumplidaException ex = assertThrows(ReglaIncumplidaException.class,
                () -> citaService.reservar(lejana));

        assertTrue(ex.getMessage().contains("90 días"));
        verify(registroDeCita, never()).crear(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservar_conFranjaFueraDeVentana_niSiquieraConsultaLaDisponibilidad() {
        // El orden importa: si se comprobara primero la disponibilidad, la franja
        // saldría como «ya no disponible» (409) en vez de con el 422 que pide el
        // criterio de aceptación.
        todoEnOrden();
        CitaRequestDTO lejana = CitaRequestDTO.builder()
                .tratamientoId(tratamientoId).odontologoId(odontologoId)
                .fecha(LocalDate.now(ZONA).plusDays(120)).hora(hora)
                .build();

        assertThrows(ReglaIncumplidaException.class, () -> citaService.reservar(lejana));

        verify(disponibilidadService, never()).consultar(any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // RN-07 · cuota de citas activas → 409
    // ------------------------------------------------------------------

    @Test
    void reservar_conTresCitasActivas_lanzaIllegalState() {
        todoEnOrden();
        when(citaRepository.countActivasDeFicha(ficha.getId())).thenReturn(3L);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> citaService.reservar(peticion()));

        assertTrue(ex.getMessage().contains("máximo permitido"));
        verify(registroDeCita, never()).crear(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservar_conDosCitasActivas_todaviaCabeLaTercera() {
        // La cuota es «no más de tres»: con dos, la siguiente entra.
        todoEnOrden();
        when(citaRepository.countActivasDeFicha(ficha.getId())).thenReturn(2L);

        assertNotNull(citaService.reservar(peticion()).getCodigo());
    }

    @Test
    void reservar_soloCuentaLasActivas_porqueLaVencidaNoConsumeCuota() {
        // RN-09: la cuota la lleva la consulta, que ya filtra por `fin > now`. Aquí
        // se fija que el servicio use ese contador y no un `count` de todas.
        todoEnOrden();
        when(citaRepository.countActivasDeFicha(ficha.getId())).thenReturn(0L);

        citaService.reservar(peticion());

        verify(citaRepository).countActivasDeFicha(ficha.getId());
        verify(citaRepository, never()).count();
    }

    // ------------------------------------------------------------------
    // La franja debe seguir ofreciéndose → 409
    // ------------------------------------------------------------------

    @Test
    void reservar_conFranjaQueElMotorNoOfrece_lanzaIllegalState() {
        todoEnOrden();
        // El motor ofrece las 10:00, no las 09:15 que se piden.
        when(disponibilidadService.consultar(tratamientoId, odontologoId, fecha, fecha))
                .thenReturn(ofertaCon(LocalTime.of(10, 0)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> citaService.reservar(peticion()));

        assertTrue(ex.getMessage().contains("ya no está disponible"));
        verify(registroDeCita, never()).crear(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservar_sinConsultorioLibre_lanzaIllegalState() {
        todoEnOrden();
        when(disponibilidadService.consultoriosLibres(any(), any())).thenReturn(List.of());

        assertThrows(IllegalStateException.class, () -> citaService.reservar(peticion()));

        verify(registroDeCita, never()).crear(any(), any(), any(), any(), any(), any());
    }

    // ------------------------------------------------------------------
    // Catálogo → 404
    // ------------------------------------------------------------------

    @Test
    void reservar_conTratamientoInexistente_lanzaResourceNotFound() {
        todoEnOrden();
        when(tratamientoRepository.findWithEspecialidadById(tratamientoId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> citaService.reservar(peticion()));
    }

    @Test
    void reservar_conTratamientoDadoDeBaja_lanzaResourceNotFound() {
        todoEnOrden();
        tratamiento.setActivo(false);

        assertThrows(ResourceNotFoundException.class, () -> citaService.reservar(peticion()));
    }

    @Test
    void reservar_conOdontologoDadoDeBaja_lanzaResourceNotFound() {
        todoEnOrden();
        odontologo.setActivo(false);

        assertThrows(ResourceNotFoundException.class, () -> citaService.reservar(peticion()));
    }

    // ------------------------------------------------------------------
    // RNF-04 · quién puede reservar
    // ------------------------------------------------------------------

    @Test
    void reservar_comoRecepcionista_lanzaForbidden() {
        // Reservar en nombre de otro es HU-14; el servicio no se fía de que
        // SecurityConfig ya lo corte.
        autenticar(usuarioId.toString(), "SCOPE_RECEPCIONISTA");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> citaService.reservar(peticion()));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void reservar_conCuentaSinFicha_lanzaForbidden() {
        usuario.setFicha(null);
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> citaService.reservar(peticion()));

        assertEquals(HttpStatus.FORBIDDEN.value(), ex.getStatusCode().value());
    }

    @Test
    void reservar_sinAutenticacion_lanzaUnauthorized() {
        SecurityContextHolder.clearContext();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> citaService.reservar(peticion()));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getStatusCode().value());
    }

    @Test
    void reservar_conSubjectQueNoEsUuid_lanzaUnauthorized() {
        autenticar("no-es-un-uuid", "SCOPE_PACIENTE");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> citaService.reservar(peticion()));

        assertEquals(HttpStatus.UNAUTHORIZED.value(), ex.getStatusCode().value());
    }

    // ------------------------------------------------------------------
    // HU-10 · exclusión mutua
    // ------------------------------------------------------------------

    @Test
    void reservar_cuandoOtroTieneElBloqueoDeLaFranja_lanzaIllegalStateSinTocarLaBase() {
        // Es el 409 que reciben las noventa y nueve peticiones perdedoras.
        todoEnOrden();
        when(bloqueoDeFranja.tomar(any(), any()))
                .thenReturn(new BloqueoDeFranja.Adquisicion(BloqueoDeFranja.Estado.OCUPADO, "clave", null));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> citaService.reservar(peticion()));

        assertTrue(ex.getMessage().contains("Otro paciente está reservando"));
        verify(registroDeCita, never()).crear(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservar_conRedisCaido_reservaIgual() {
        // RNF-12: Redis no es la barrera única. Sin él la reserva sigue su curso y
        // la restricción de exclusión de la base es la que garantiza.
        todoEnOrden();
        when(bloqueoDeFranja.tomar(any(), any()))
                .thenReturn(new BloqueoDeFranja.Adquisicion(BloqueoDeFranja.Estado.SIN_REDIS, "clave", null));

        assertEquals("CIT-000123", citaService.reservar(peticion()).getCodigo());
    }

    @Test
    void reservar_siElConsultorioSeOcupaAlConfirmar_reintentaConOtro() {
        // RF-16: la franja sigue siendo del paciente; lo que falta es sala.
        todoEnOrden();
        UUID segundo = UUID.randomUUID();
        Consultorio otro = Consultorio.builder().id(segundo).nombre("Consultorio 3")
                .inoperativo(false).build();
        when(disponibilidadService.consultoriosLibres(any(), any()))
                .thenReturn(List.of(consultorioId, segundo));
        when(consultorioRepository.findById(segundo)).thenReturn(Optional.of(otro));
        when(registroDeCita.crear(any(), any(), any(), eq(consultorioId), any(), any()))
                .thenThrow(solapeDe(ConflictoDeSolape.SOLAPE_CONSULTORIO));

        CitaResponseDTO respuesta = citaService.reservar(peticion());

        assertEquals("CIT-000123", respuesta.getCodigo());
        verify(registroDeCita).crear(any(), any(), any(), eq(segundo), any(), any());
    }

    @Test
    void reservar_siTodosLosConsultoriosSeOcupan_lanzaIllegalState() {
        todoEnOrden();
        when(registroDeCita.crear(any(), any(), any(), any(), any(), any()))
                .thenThrow(solapeDe(ConflictoDeSolape.SOLAPE_CONSULTORIO));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> citaService.reservar(peticion()));

        assertTrue(ex.getMessage().contains("se ocuparon mientras confirmabas"));
    }

    @Test
    void reservar_siElInsertMuereEnInterbloqueo_reintentaElMismoConsultorio() {
        // HU-10: dos INSERT simultáneos pueden esperarse mutuamente mientras
        // PostgreSQL comprueba las restricciones de exclusión, y el motor aborta a
        // uno con «deadlock detected». Es una carrera perdida, no una petición
        // inválida: quien la pierde tiene que acabar con su 201 si aún hay sitio.
        todoEnOrden();
        when(registroDeCita.crear(any(), any(), any(), eq(consultorioId), any(), any()))
                .thenThrow(interbloqueo())
                .thenAnswer(inv -> citaCreada(inv.getArgument(3)));

        CitaResponseDTO respuesta = citaService.reservar(peticion());

        assertEquals("CIT-000123", respuesta.getCodigo());
        verify(registroDeCita, times(2)).crear(any(), any(), any(), eq(consultorioId), any(), any());
    }

    @Test
    void reservar_siElInterbloqueoSeRepite_pruebaElSiguienteConsultorio() {
        todoEnOrden();
        UUID segundo = UUID.randomUUID();
        Consultorio otro = Consultorio.builder().id(segundo).nombre("Consultorio 3")
                .inoperativo(false).build();
        when(disponibilidadService.consultoriosLibres(any(), any()))
                .thenReturn(List.of(consultorioId, segundo));
        when(consultorioRepository.findById(segundo)).thenReturn(Optional.of(otro));
        when(registroDeCita.crear(any(), any(), any(), eq(consultorioId), any(), any()))
                .thenThrow(interbloqueo());

        CitaResponseDTO respuesta = citaService.reservar(peticion());

        assertEquals("CIT-000123", respuesta.getCodigo());
        verify(registroDeCita, times(2)).crear(any(), any(), any(), eq(consultorioId), any(), any());
        verify(registroDeCita).crear(any(), any(), any(), eq(segundo), any(), any());
    }

    @Test
    void reservar_siElInterbloqueoAgotaLosConsultorios_lanzaIllegalState() {
        // El criterio 2 de HU-10 exige un 409 para quien no cabe, nunca un 500:
        // el interbloqueo no puede escaparse sin traducir.
        todoEnOrden();
        when(registroDeCita.crear(any(), any(), any(), any(), any(), any()))
                .thenThrow(interbloqueo());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> citaService.reservar(peticion()));

        assertTrue(ex.getMessage().contains("se ocuparon mientras confirmabas"));
    }

    @Test
    void reservar_siElOdontologoSeOcupaAlConfirmar_noReintenta() {
        // Reintentar con otro consultorio no cambiaría nada: la agenda del
        // odontólogo es la que está tomada.
        todoEnOrden();
        UUID segundo = UUID.randomUUID();
        when(disponibilidadService.consultoriosLibres(any(), any()))
                .thenReturn(List.of(consultorioId, segundo));
        when(registroDeCita.crear(any(), any(), any(), any(), any(), any()))
                .thenThrow(solapeDe(ConflictoDeSolape.SOLAPE_ODONTOLOGO));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> citaService.reservar(peticion()));

        assertTrue(ex.getMessage().contains("odontólogo acaba de ocuparse"));
        // Un solo intento: no se probó el segundo consultorio.
        verify(registroDeCita).crear(any(), any(), any(), any(), any(), any());
    }

    @Test
    void reservar_conUnFalloDeIntegridadDesconocido_loPropaga() {
        // No es un solape nuestro: no hay que disfrazarlo de conflicto de agenda.
        todoEnOrden();
        when(registroDeCita.crear(any(), any(), any(), any(), any(), any()))
                .thenThrow(new DataIntegrityViolationException("codigo duplicado"));

        assertThrows(DataIntegrityViolationException.class, () -> citaService.reservar(peticion()));
    }

    @Test
    void reservar_liberaElBloqueoAunqueLaReservaFalle() {
        // Sin el finally, una franja quedaria bloqueada hasta que caducase el TTL.
        todoEnOrden();
        when(registroDeCita.crear(any(), any(), any(), any(), any(), any()))
                .thenThrow(solapeDe(ConflictoDeSolape.SOLAPE_ODONTOLOGO));

        assertThrows(IllegalStateException.class, () -> citaService.reservar(peticion()));

        verify(bloqueoDeFranja).liberar(any());
    }
}
