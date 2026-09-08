package pe.edu.dentalcite.horario.service;

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
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.horario.api.dto.HorarioResponseDTO;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class HorarioServiceTest {

    @Mock
    private HorarioAtencionRepository horarioRepository;

    @Mock
    private OdontologoRepository odontologoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private HorarioService horarioService;

    private UUID odontologoId;
    private UUID usuarioId;
    private Odontologo odontologo;
    private Usuario usuario;
    private HorarioAtencion horario;

    @BeforeEach
    void setUp() {
        odontologoId = UUID.randomUUID();
        usuarioId = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();

        Ficha ficha = Ficha.builder().id(fichaId).build();

        odontologo = Odontologo.builder()
                .id(odontologoId)
                .ficha(ficha)
                .build();

        usuario = Usuario.builder()
                .id(usuarioId)
                .correo("odontologo@test.com")
                .ficha(ficha)
                .build();

        horario = HorarioAtencion.builder()
                .id(UUID.randomUUID())
                .diaSemana(1)
                .horaInicio(LocalTime.of(9, 0))
                .horaFin(LocalTime.of(13, 0))
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
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void crearHorario_comoOdontologoPropio_exito() {
        // Arrange
        mockSecurityContext(usuarioId.toString(), "SCOPE_ODONTOLOGO");
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(horarioRepository.save(any(HorarioAtencion.class))).thenReturn(horario);

        // Act
        HorarioResponseDTO result = horarioService.crearHorario(horario, odontologoId);

        // Assert
        assertNotNull(result);
        assertEquals(odontologoId, result.getOdontologoId());
        verify(horarioRepository).save(horario);
    }

    @Test
    void crearHorario_comoAdmin_exito() {
        // Arrange
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(horarioRepository.save(any(HorarioAtencion.class))).thenReturn(horario);

        // Act
        HorarioResponseDTO result = horarioService.crearHorario(horario, odontologoId);

        // Assert
        assertNotNull(result);
        verify(usuarioRepository, never()).findById(any(UUID.class));
        verify(horarioRepository).save(horario);
    }

    @Test
    void crearHorario_comoOtroOdontologo_falla() {
        // Arrange
        UUID otroUsuarioId = UUID.randomUUID();
        mockSecurityContext(otroUsuarioId.toString(), "SCOPE_ODONTOLOGO");
        Usuario otroUsuario = Usuario.builder()
                .id(otroUsuarioId)
                .correo("otro@test.com")
                .ficha(Ficha.builder().id(UUID.randomUUID()).build())
                .build();

        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(usuarioRepository.findById(otroUsuarioId)).thenReturn(Optional.of(otroUsuario));

        // Act & Assert
        assertThrows(ResponseStatusException.class, () -> {
            horarioService.crearHorario(horario, odontologoId);
        });
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void crearHorario_conSolapamiento_falla() {
        // Arrange
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        
        // Simular que ya existe un horario solapado
        when(horarioRepository.findOverlappingHorarios(eq(odontologoId), eq(horario.getDiaSemana()), eq(horario.getHoraInicio()), eq(horario.getHoraFin()), isNull()))
                .thenReturn(List.of(HorarioAtencion.builder().build()));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            horarioService.crearHorario(horario, odontologoId);
        });
        assertEquals(409, exception.getStatusCode().value());
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void actualizarHorario_conSolapamiento_falla() {
        // Arrange
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        UUID horarioId = horario.getId();
        when(horarioRepository.findById(horarioId)).thenReturn(Optional.of(horario));
        
        HorarioAtencion detalles = HorarioAtencion.builder()
                .diaSemana(1)
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(14, 0))
                .build();

        // Simular que ya existe otro horario solapado (distinto al que actualizamos)
        when(horarioRepository.findOverlappingHorarios(eq(odontologoId), eq(detalles.getDiaSemana()), eq(detalles.getHoraInicio()), eq(detalles.getHoraFin()), eq(horarioId)))
                .thenReturn(List.of(HorarioAtencion.builder().build()));

        // Act & Assert
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> {
            horarioService.actualizarHorario(odontologoId, horarioId, detalles);
        });
        assertEquals(409, exception.getStatusCode().value());
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void listarHorariosPorOdontologo_comoOdontologoPropio_devuelveSusHorarios() {
        mockSecurityContext(usuarioId.toString(), "SCOPE_ODONTOLOGO");
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
        when(horarioRepository.findByOdontologoId(odontologoId)).thenReturn(List.of(horario));

        List<HorarioResponseDTO> result = horarioService.listarHorariosPorOdontologo(odontologoId);

        assertEquals(1, result.size());
        assertEquals(horario.getId(), result.get(0).getId());
        assertEquals(odontologoId, result.get(0).getOdontologoId());
    }

    @Test
    void listarHorariosPorOdontologo_comoOtroOdontologo_falla() {
        // HU-07 / RNF-04: la agenda declarada por un colega no es consultable.
        UUID otroUsuarioId = UUID.randomUUID();
        mockSecurityContext(otroUsuarioId.toString(), "SCOPE_ODONTOLOGO");
        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(usuarioRepository.findById(otroUsuarioId)).thenReturn(Optional.of(
                Usuario.builder()
                        .id(otroUsuarioId)
                        .ficha(Ficha.builder().id(UUID.randomUUID()).build())
                        .build()));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> horarioService.listarHorariosPorOdontologo(odontologoId));
        assertEquals(403, ex.getStatusCode().value());
        verify(horarioRepository, never()).findByOdontologoId(any());
    }

    @Test
    void listarHorariosPorOdontologo_conOdontologoInexistente_lanzaNotFound() {
        UUID inexistente = UUID.randomUUID();
        when(odontologoRepository.findById(inexistente)).thenReturn(Optional.empty());

        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class,
                () -> horarioService.listarHorariosPorOdontologo(inexistente));
    }

    @Test
    void eliminarHorario_comoAdmin_loBorra() {
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        UUID horarioId = horario.getId();
        when(horarioRepository.findById(horarioId)).thenReturn(Optional.of(horario));

        horarioService.eliminarHorario(odontologoId, horarioId);

        verify(horarioRepository).deleteById(horarioId);
    }

    @Test
    void eliminarHorario_conHorarioInexistente_lanzaNotFound() {
        UUID horarioId = UUID.randomUUID();
        when(horarioRepository.findById(horarioId)).thenReturn(Optional.empty());

        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class,
                () -> horarioService.eliminarHorario(odontologoId, horarioId));
        verify(horarioRepository, never()).deleteById(any());
    }

    @Test
    void crearHorario_conHoraInicioPosteriorAFin_falla() {
        HorarioAtencion invalido = HorarioAtencion.builder()
                .diaSemana(1)
                .horaInicio(LocalTime.of(14, 0))
                .horaFin(LocalTime.of(10, 0))
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> horarioService.crearHorario(invalido, odontologoId));
        verify(horarioRepository, never()).save(any());
    }

    @Test
    void actualizarHorario_comoAdmin_actualizaElTramo() {
        mockSecurityContext(UUID.randomUUID().toString(), "SCOPE_ADMINISTRADOR");
        UUID horarioId = horario.getId();
        when(horarioRepository.findById(horarioId)).thenReturn(Optional.of(horario));
        when(horarioRepository.findOverlappingHorarios(any(), any(), any(), any(), any()))
                .thenReturn(List.of());
        when(horarioRepository.save(any(HorarioAtencion.class))).thenAnswer(i -> i.getArgument(0));

        HorarioAtencion detalles = HorarioAtencion.builder()
                .diaSemana(2)
                .horaInicio(LocalTime.of(15, 0))
                .horaFin(LocalTime.of(19, 0))
                .build();

        HorarioResponseDTO result = horarioService.actualizarHorario(odontologoId, horarioId, detalles);

        assertEquals(2, result.getDiaSemana());
        assertEquals(LocalTime.of(15, 0), result.getHoraInicio());
        assertEquals(LocalTime.of(19, 0), result.getHoraFin());
    }

    @Test
    void eliminarHorario_conOdontologoIdDeOtroOdontologo_lanzaNotFound() {
        // Arrange: el horario existe pero pertenece a un odontólogo distinto al de la URL
        UUID horarioId = horario.getId();
        UUID otroOdontologoId = UUID.randomUUID();
        when(horarioRepository.findById(horarioId)).thenReturn(Optional.of(horario));

        // Act & Assert
        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class, () -> {
            horarioService.eliminarHorario(otroOdontologoId, horarioId);
        });
        verify(horarioRepository, never()).deleteById(any());
    }

    @Test
    void actualizarHorario_conOdontologoIdDeOtroOdontologo_lanzaNotFound() {
        // Arrange: el horario existe pero pertenece a un odontólogo distinto al de la URL
        UUID horarioId = horario.getId();
        UUID otroOdontologoId = UUID.randomUUID();
        when(horarioRepository.findById(horarioId)).thenReturn(Optional.of(horario));

        HorarioAtencion detalles = HorarioAtencion.builder()
                .diaSemana(1)
                .horaInicio(LocalTime.of(10, 0))
                .horaFin(LocalTime.of(14, 0))
                .build();

        // Act & Assert
        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class, () -> {
            horarioService.actualizarHorario(otroOdontologoId, horarioId, detalles);
        });
        verify(horarioRepository, never()).save(any());
    }
}
