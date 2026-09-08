package pe.edu.dentalcite.bloqueo.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import pe.edu.dentalcite.bloqueo.api.dto.BloqueoResponseDTO;
import pe.edu.dentalcite.bloqueo.domain.Bloqueo;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.bloqueo.repository.BloqueoRepository;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BloqueoServiceTest {

    @Mock
    private BloqueoRepository bloqueoRepository;

    @Mock
    private OdontologoRepository odontologoRepository;

    @Mock
    private ConsultorioRepository consultorioRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private pe.edu.dentalcite.cita.repository.CitaRepository citaRepository;

    @InjectMocks
    private BloqueoService bloqueoService;

    private UUID odontologoId;
    private UUID consultorioId;
    private Odontologo odontologo;
    private Consultorio consultorio;
    private Bloqueo bloqueo;

    @BeforeEach
    void setUp() {
        odontologoId = UUID.randomUUID();
        consultorioId = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();

        Ficha ficha = Ficha.builder().id(fichaId).build();
        odontologo = Odontologo.builder().id(odontologoId).ficha(ficha).build();
        consultorio = Consultorio.builder().id(consultorioId).build();

        bloqueo = Bloqueo.builder()
                .id(UUID.randomUUID())
                .motivo("Vacaciones")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(5))
                .odontologo(odontologo)
                .build();
    }

    @AfterEach
    void limpiarContextoSeguridad() {
        // El guard de propiedad ahora falla cerrado sin autenticacion, asi que el
        // contexto no puede filtrarse de un test al siguiente.
        SecurityContextHolder.clearContext();
    }

    private void mockSecurityContext(String username, String role) {
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null,
                List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void crearBloqueo_soloConsultorio_comoOdontologo_falla() {
        // Arrange
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ODONTOLOGO");
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));
        Bloqueo nuevo = Bloqueo.builder()
                .motivo("Mantenimiento")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(2))
                .build();
        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            bloqueoService.crearBloqueo(nuevo, null, consultorioId);
        });
        assertEquals(403, exception.getStatusCode().value());
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void crearBloqueo_soloConsultorio_comoRecepcionista_exito() {
        // Arrange
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_RECEPCIONISTA");
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));
        when(bloqueoRepository.save(any(Bloqueo.class))).thenAnswer(i -> i.getArgument(0));
        Bloqueo nuevo = Bloqueo.builder()
                .motivo("Mantenimiento")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(2))
                .build();
        // Act
        BloqueoResponseDTO result = bloqueoService.crearBloqueo(nuevo, null, consultorioId);

        // Assert
        assertNotNull(result);
        assertNull(result.getOdontologoId());
        assertEquals(consultorioId, result.getConsultorioId());
        verify(bloqueoRepository).save(any());
    }

    /** Cuenta cuya ficha es la del odontólogo de {@code setUp}: acredita propiedad. */
    private UUID autenticarComoElOdontologoDuenio() {
        UUID usuarioId = UUID.randomUUID();
        mockSecurityContext(usuarioId.toString(), "SCOPE_ODONTOLOGO");
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(
                pe.edu.dentalcite.usuario.domain.Usuario.builder()
                        .id(usuarioId)
                        .rol("ODONTOLOGO")
                        .ficha(odontologo.getFicha())
                        .build()));
        return usuarioId;
    }

    @Test
    void crearBloqueo_conSuPropiaAgendaYAdemasUnConsultorio_comoOdontologo_falla() {
        // El agujero que cerró A4: adjuntar el identificador propio hacía que la
        // verificación saliera por la rama de propiedad y nadie mirase el
        // consultorio, de modo que cualquier odontólogo podía dejar un consultorio
        // inoperativo para toda la clínica (RNF-04, Tabla 10).
        autenticarComoElOdontologoDuenio();
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));

        Bloqueo nuevo = Bloqueo.builder()
                .motivo("Me llevo el consultorio")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(2))
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bloqueoService.crearBloqueo(nuevo, odontologoId, consultorioId));
        assertEquals(403, ex.getStatusCode().value());
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void crearBloqueo_soloDeSuPropiaAgenda_comoOdontologo_exito() {
        // Control positivo: la restricción del consultorio no puede llevarse por
        // delante lo que la Tabla 10 sí concede al odontólogo sobre su agenda.
        autenticarComoElOdontologoDuenio();
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(citaRepository.findActivasDeOdontologoEnRango(eq(odontologoId), any(), any()))
                .thenReturn(List.of());
        when(bloqueoRepository.save(any(Bloqueo.class))).thenAnswer(i -> i.getArgument(0));

        Bloqueo nuevo = Bloqueo.builder()
                .motivo("Congreso")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(2))
                .build();

        BloqueoResponseDTO result = bloqueoService.crearBloqueo(nuevo, odontologoId, null);

        assertEquals(odontologoId, result.getOdontologoId());
        assertNull(result.getConsultorioId());
    }

    @Test
    void eliminarBloqueo_queAlcanzaUnConsultorio_comoOdontologo_falla() {
        // Levantar el bloqueo de un consultorio lo devuelve al servicio para toda la
        // clínica: pide el mismo permiso que aplicarlo.
        autenticarComoElOdontologoDuenio();
        UUID bloqueoId = UUID.randomUUID();
        when(bloqueoRepository.findById(bloqueoId)).thenReturn(Optional.of(Bloqueo.builder()
                .id(bloqueoId)
                .odontologo(odontologo)
                .consultorio(consultorio)
                .motivo("Mixto")
                .build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bloqueoService.eliminarBloqueo(bloqueoId));
        assertEquals(403, ex.getStatusCode().value());
        verify(bloqueoRepository, never()).deleteById(any());
    }

    @Test
    void actualizarBloqueo_paraAnadirleUnConsultorio_comoOdontologo_falla() {
        autenticarComoElOdontologoDuenio();
        UUID bloqueoId = bloqueo.getId();
        when(bloqueoRepository.findById(bloqueoId)).thenReturn(Optional.of(bloqueo));
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(consultorioRepository.findById(consultorioId)).thenReturn(Optional.of(consultorio));

        Bloqueo detalles = Bloqueo.builder()
                .motivo("Ahora también el consultorio")
                .fechaInicio(OffsetDateTime.now().plusDays(10))
                .fechaFin(OffsetDateTime.now().plusDays(12))
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> bloqueoService.actualizarBloqueo(bloqueoId, detalles, odontologoId, consultorioId));
        assertEquals(403, ex.getStatusCode().value());
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void crearBloqueo_conCitasActivasEnElRango_lasListaYNoAplicaElBloqueo() {
        // HU-07 / RN-03: «el sistema me listará esas citas y no aplicará el bloqueo
        // hasta que se cancelen con motivo».
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));

        Ficha fichaPaciente = Ficha.builder()
                .id(UUID.randomUUID())
                .nombres("Ana")
                .apellidos("Quispe")
                .numeroHistoria("HC-00042")
                .build();

        Cita cita = Cita.builder()
                .id(UUID.randomUUID())
                .codigo("CT-0001")
                .ficha(fichaPaciente)
                .inicio(OffsetDateTime.now().plusDays(2))
                .fin(OffsetDateTime.now().plusDays(2).plusHours(1))
                .estado(Cita.ESTADO_CONFIRMADA)
                .build();

        when(citaRepository.findActivasDeOdontologoEnRango(eq(odontologoId), any(), any()))
                .thenReturn(List.of(cita));

        Bloqueo nuevo = Bloqueo.builder()
                .motivo("Congreso")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(5))
                .build();

        CitasActivasEnRangoException ex = assertThrows(CitasActivasEnRangoException.class,
                () -> bloqueoService.crearBloqueo(nuevo, odontologoId, null));

        assertEquals(1, ex.getCitasActivas().size());
        assertEquals("CT-0001", ex.getCitasActivas().get(0).getCodigo());
        assertEquals("Ana Quispe", ex.getCitasActivas().get(0).getPaciente());
        assertEquals("HC-00042", ex.getCitasActivas().get(0).getNumeroHistoria());
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void crearBloqueo_sinCitasActivasEnElRango_seAplica() {
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(citaRepository.findActivasDeOdontologoEnRango(eq(odontologoId), any(), any()))
                .thenReturn(List.of());
        when(bloqueoRepository.save(any(Bloqueo.class))).thenAnswer(i -> i.getArgument(0));

        Bloqueo nuevo = Bloqueo.builder()
                .motivo("Congreso")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(5))
                .build();

        BloqueoResponseDTO result = bloqueoService.crearBloqueo(nuevo, odontologoId, null);

        assertEquals(odontologoId, result.getOdontologoId());
        verify(bloqueoRepository).save(any(Bloqueo.class));
    }

    @Test
    void actualizarBloqueo_comoAdmin_actualizaMotivoYFechas() {
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        UUID bloqueoId = bloqueo.getId();
        when(bloqueoRepository.findById(bloqueoId)).thenReturn(Optional.of(bloqueo));
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(citaRepository.findActivasDeOdontologoEnRango(eq(odontologoId), any(), any()))
                .thenReturn(List.of());
        when(bloqueoRepository.save(any(Bloqueo.class))).thenAnswer(i -> i.getArgument(0));

        Bloqueo detalles = Bloqueo.builder()
                .motivo("Congreso internacional")
                .fechaInicio(OffsetDateTime.now().plusDays(10))
                .fechaFin(OffsetDateTime.now().plusDays(12))
                .build();

        BloqueoResponseDTO result = bloqueoService.actualizarBloqueo(bloqueoId, detalles, odontologoId, null);

        assertEquals("Congreso internacional", result.getMotivo());
        assertEquals(odontologoId, result.getOdontologoId());
        assertNull(result.getConsultorioId());
        verify(bloqueoRepository).save(bloqueo);
    }

    @Test
    void actualizarBloqueo_conCitasActivasEnElNuevoRango_noAplicaElCambio() {
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        UUID bloqueoId = bloqueo.getId();
        when(bloqueoRepository.findById(bloqueoId)).thenReturn(Optional.of(bloqueo));
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(citaRepository.findActivasDeOdontologoEnRango(eq(odontologoId), any(), any()))
                .thenReturn(List.of(citaConfirmada()));

        Bloqueo detalles = Bloqueo.builder()
                .motivo("Congreso internacional")
                .fechaInicio(OffsetDateTime.now().plusDays(10))
                .fechaFin(OffsetDateTime.now().plusDays(12))
                .build();

        assertThrows(CitasActivasEnRangoException.class,
                () -> bloqueoService.actualizarBloqueo(bloqueoId, detalles, odontologoId, null));
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void actualizarBloqueo_conFechasInvertidas_falla() {
        Bloqueo detalles = Bloqueo.builder()
                .motivo("Congreso")
                .fechaInicio(OffsetDateTime.now().plusDays(5))
                .fechaFin(OffsetDateTime.now().plusDays(1))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> bloqueoService.actualizarBloqueo(UUID.randomUUID(), detalles, odontologoId, null));
        verify(bloqueoRepository, never()).save(any());
    }

    @Test
    void actualizarBloqueo_conIdInexistente_lanzaNotFound() {
        UUID bloqueoId = UUID.randomUUID();
        when(bloqueoRepository.findById(bloqueoId)).thenReturn(Optional.empty());

        Bloqueo detalles = Bloqueo.builder()
                .motivo("Congreso")
                .fechaInicio(OffsetDateTime.now().plusDays(1))
                .fechaFin(OffsetDateTime.now().plusDays(2))
                .build();

        assertThrows(ResourceNotFoundException.class,
                () -> bloqueoService.actualizarBloqueo(bloqueoId, detalles, odontologoId, null));
    }

    @Test
    void eliminarBloqueo_comoAdmin_loBorra() {
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        UUID bloqueoId = bloqueo.getId();
        when(bloqueoRepository.findById(bloqueoId)).thenReturn(Optional.of(bloqueo));

        bloqueoService.eliminarBloqueo(bloqueoId);

        verify(bloqueoRepository).deleteById(bloqueoId);
    }

    @Test
    void eliminarBloqueo_conIdInexistente_lanzaNotFound() {
        UUID bloqueoId = UUID.randomUUID();
        when(bloqueoRepository.findById(bloqueoId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> bloqueoService.eliminarBloqueo(bloqueoId));
        verify(bloqueoRepository, never()).deleteById(any());
    }

    @Test
    void listarBloqueos_comoRecepcionista_devuelveTodos() {
        // Tabla 10: recepcion y administracion ven la agenda completa.
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_RECEPCIONISTA");
        Bloqueo ajeno = Bloqueo.builder()
                .id(UUID.randomUUID())
                .odontologo(otroOdontologo())
                .motivo("Ajeno")
                .build();
        when(bloqueoRepository.findAll()).thenReturn(List.of(bloqueo, ajeno));

        assertEquals(2, bloqueoService.listarBloqueos().size());
    }

    @Test
    void listarBloqueos_comoOdontologo_ocultaLosDeOtrosYConservaLosDeConsultorio() {
        // RNF-04: un odontologo no ve la agenda de sus colegas, pero si los bloqueos
        // de consultorio, que afectan a toda la clinica.
        UUID usuarioId = UUID.randomUUID();
        mockSecurityContext(usuarioId.toString(), "SCOPE_ODONTOLOGO");

        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(
                pe.edu.dentalcite.usuario.domain.Usuario.builder()
                        .id(usuarioId)
                        .rol("ODONTOLOGO")
                        .ficha(odontologo.getFicha())
                        .build()));

        Bloqueo ajeno = Bloqueo.builder()
                .id(UUID.randomUUID())
                .odontologo(otroOdontologo())
                .motivo("Ajeno")
                .build();
        Bloqueo deConsultorio = Bloqueo.builder()
                .id(UUID.randomUUID())
                .consultorio(consultorio)
                .motivo("Mantenimiento")
                .build();
        when(bloqueoRepository.findAll()).thenReturn(List.of(bloqueo, ajeno, deConsultorio));

        List<UUID> visibles = bloqueoService.listarBloqueos().stream()
                .map(BloqueoResponseDTO::getId)
                .toList();

        assertEquals(2, visibles.size());
        assertTrue(visibles.contains(bloqueo.getId()));
        assertTrue(visibles.contains(deConsultorio.getId()));
        assertFalse(visibles.contains(ajeno.getId()));
    }

    private Odontologo otroOdontologo() {
        return Odontologo.builder()
                .id(UUID.randomUUID())
                .ficha(Ficha.builder().id(UUID.randomUUID()).build())
                .build();
    }

    private Cita citaConfirmada() {
        return Cita.builder()
                .id(UUID.randomUUID())
                .codigo("CT-0002")
                .ficha(Ficha.builder().id(UUID.randomUUID()).nombres("Ana").apellidos("Quispe")
                        .numeroHistoria("HC-00043").build())
                .inicio(OffsetDateTime.now().plusDays(11))
                .fin(OffsetDateTime.now().plusDays(11).plusHours(1))
                .estado(Cita.ESTADO_CONFIRMADA)
                .build();
    }

    @Test
    void crearBloqueo_sinConsultorioNiOdontologo_falla() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            bloqueoService.crearBloqueo(bloqueo, null, null);
        });
        assertTrue(exception.getMessage().contains("Debe especificar"));
    }
}
