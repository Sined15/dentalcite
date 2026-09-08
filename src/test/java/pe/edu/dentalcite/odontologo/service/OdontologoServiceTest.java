package pe.edu.dentalcite.odontologo.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.api.dto.OdontologoRequestDTO;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OdontologoServiceTest {

    @Mock
    private OdontologoRepository odontologoRepository;

    @Mock
    private FichaRepository fichaRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private pe.edu.dentalcite.usuario.repository.UsuarioRepository usuarioRepository;

    @InjectMocks
    private OdontologoService odontologoService;

    private Especialidad especialidad;
    private final UUID especialidadId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        especialidad = Especialidad.builder()
                .id(especialidadId)
                .nombre("ORTODONCIA")
                .activo(true)
                .build();
    }

    @Test
    void registrarOdontologo_conFichaYaAsignada_arrojaIllegalStateException() {
        UUID fichaId = UUID.randomUUID();
        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("12345");
        req.setFichaId(fichaId);

        when(odontologoRepository.existsByCop("12345")).thenReturn(false);
        when(odontologoRepository.existsByFichaId(fichaId)).thenReturn(true); // Ficha ya usada

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> odontologoService.registrarOdontologo(req));
        assertTrue(ex.getMessage().contains("ya está asignada a otro odontólogo"));
    }

    @Test
    void registrarOdontologo_exitoso_creaOdontologo() {
        UUID fichaId = UUID.randomUUID();
        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("54321");
        req.setNombres("Juan");
        req.setApellidos("Pérez");
        req.setFichaId(fichaId);
        req.setEspecialidadesIds(Set.of(especialidadId));

        Ficha ficha = new Ficha();
        ficha.setId(fichaId);

        when(odontologoRepository.existsByCop("54321")).thenReturn(false);
        when(odontologoRepository.existsByFichaId(fichaId)).thenReturn(false);
        when(fichaRepository.findById(fichaId)).thenReturn(Optional.of(ficha));
        when(especialidadRepository.findAllById(any())).thenReturn(List.of(especialidad));
        // RN-11: la ficha debe pertenecer a una cuenta de rol ODONTOLOGO.
        when(usuarioRepository.findByFichaId(fichaId)).thenReturn(Optional.of(
                pe.edu.dentalcite.usuario.domain.Usuario.builder().rol("ODONTOLOGO").ficha(ficha).build()));
        when(odontologoRepository.save(any(Odontologo.class))).thenAnswer(i -> i.getArgument(0));

        var response = odontologoService.registrarOdontologo(req);

        assertEquals("54321", response.getCop());
        assertEquals(fichaId, response.getFichaId());
        assertEquals(1, response.getEspecialidades().size());
        verify(odontologoRepository).save(any(Odontologo.class));
    }

    @Test
    void registrarOdontologo_conFichaDeCuentaNoOdontologo_arrojaIllegalStateException() {
        // RN-11 / HU-06: el odontólogo se vincula a una cuenta *de rol ODONTOLOGO*.
        UUID fichaId = UUID.randomUUID();
        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("99999");
        req.setNombres("Juan");
        req.setApellidos("Pérez");
        req.setFichaId(fichaId);
        req.setEspecialidadesIds(Set.of(especialidadId));

        Ficha ficha = new Ficha();
        ficha.setId(fichaId);

        when(odontologoRepository.existsByCop("99999")).thenReturn(false);
        when(odontologoRepository.existsByFichaId(fichaId)).thenReturn(false);
        when(fichaRepository.findById(fichaId)).thenReturn(Optional.of(ficha));
        when(usuarioRepository.findByFichaId(fichaId)).thenReturn(Optional.of(
                pe.edu.dentalcite.usuario.domain.Usuario.builder().rol("PACIENTE").ficha(ficha).build()));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> odontologoService.registrarOdontologo(req));
        assertTrue(ex.getMessage().contains("rol ODONTOLOGO"));
        verify(odontologoRepository, never()).save(any());
    }

    @Test
    void registrarOdontologo_conFichaSinCuenta_arrojaIllegalStateException() {
        UUID fichaId = UUID.randomUUID();
        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop("88888");
        req.setNombres("Juan");
        req.setApellidos("Pérez");
        req.setFichaId(fichaId);
        req.setEspecialidadesIds(Set.of(especialidadId));

        Ficha ficha = new Ficha();
        ficha.setId(fichaId);

        when(odontologoRepository.existsByCop("88888")).thenReturn(false);
        when(odontologoRepository.existsByFichaId(fichaId)).thenReturn(false);
        when(fichaRepository.findById(fichaId)).thenReturn(Optional.of(ficha));
        when(usuarioRepository.findByFichaId(fichaId)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> odontologoService.registrarOdontologo(req));
        verify(odontologoRepository, never()).save(any());
    }

    /** Odontólogo ya registrado, con una especialidad y su ficha vinculada. */
    private Odontologo odontologoExistente(UUID id, UUID fichaId) {
        Ficha ficha = new Ficha();
        ficha.setId(fichaId);
        return Odontologo.builder()
                .id(id)
                .cop("COP-VIEJO")
                .nombres("Juan")
                .apellidos("Pérez")
                .ficha(ficha)
                .activo(true)
                .especialidades(new java.util.HashSet<>(Set.of(especialidad)))
                .build();
    }

    private OdontologoRequestDTO peticionDeReemplazo(UUID fichaId, String cop, Set<UUID> especialidades) {
        OdontologoRequestDTO req = new OdontologoRequestDTO();
        req.setCop(cop);
        req.setNombres("Juan Carlos");
        req.setApellidos("Pérez Ramos");
        req.setFichaId(fichaId);
        req.setEspecialidadesIds(especialidades);
        return req;
    }

    @Test
    void actualizarOdontologo_conDatosValidos_reemplazaColegiaturaNombreYEspecialidades() {
        // Tabla 10 concede U sobre el catálogo clínico; sin esta operación no había
        // forma de corregir el COP ni de cambiar las especialidades tras el alta.
        UUID id = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();
        UUID otraEspecialidadId = UUID.randomUUID();
        Especialidad otra = Especialidad.builder().id(otraEspecialidadId).nombre("ENDODONCIA").activo(true).build();

        Odontologo odontologo = odontologoExistente(id, fichaId);
        when(odontologoRepository.findById(id)).thenReturn(Optional.of(odontologo));
        when(odontologoRepository.existsByCop("COP-NUEVO")).thenReturn(false);
        when(especialidadRepository.findAllById(any())).thenReturn(List.of(otra));
        when(odontologoRepository.save(any(Odontologo.class))).thenAnswer(i -> i.getArgument(0));

        var res = odontologoService.actualizarOdontologo(id,
                peticionDeReemplazo(fichaId, "COP-NUEVO", Set.of(otraEspecialidadId)));

        assertEquals("COP-NUEVO", res.getCop());
        assertEquals("Juan Carlos", res.getNombres());
        assertEquals("Pérez Ramos", res.getApellidos());
        assertEquals(1, res.getEspecialidades().size());
        assertEquals("ENDODONCIA", res.getEspecialidades().iterator().next().getNombre());
    }

    @Test
    void actualizarOdontologo_noTocaLaMarcaDeActividad() {
        // La baja tiene su propia operación, que además comprueba RN-12.
        UUID id = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();
        Odontologo odontologo = odontologoExistente(id, fichaId);
        odontologo.setActivo(false);

        when(odontologoRepository.findById(id)).thenReturn(Optional.of(odontologo));
        when(especialidadRepository.findAllById(any())).thenReturn(List.of(especialidad));
        when(odontologoRepository.save(any(Odontologo.class))).thenAnswer(i -> i.getArgument(0));

        var res = odontologoService.actualizarOdontologo(id,
                peticionDeReemplazo(fichaId, "COP-VIEJO", Set.of(especialidadId)));

        assertFalse(res.getActivo());
    }

    @Test
    void actualizarOdontologo_conCopDeOtroOdontologo_arrojaIllegalStateException() {
        UUID id = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();

        when(odontologoRepository.findById(id)).thenReturn(Optional.of(odontologoExistente(id, fichaId)));
        when(odontologoRepository.existsByCop("COP-AJENO")).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> odontologoService.actualizarOdontologo(id,
                        peticionDeReemplazo(fichaId, "COP-AJENO", Set.of(especialidadId))));
        assertTrue(ex.getMessage().contains("COP"));
        verify(odontologoRepository, never()).save(any());
    }

    @Test
    void actualizarOdontologo_conOtraFicha_arrojaIllegalStateException() {
        // RN-11: repuntar la ficha trasladaría la agenda del profesional y las
        // operaciones marcadas «propio» a otra persona.
        UUID id = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();

        when(odontologoRepository.findById(id)).thenReturn(Optional.of(odontologoExistente(id, fichaId)));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> odontologoService.actualizarOdontologo(id,
                        peticionDeReemplazo(UUID.randomUUID(), "COP-VIEJO", Set.of(especialidadId))));
        assertTrue(ex.getMessage().contains("RN-11"));
        verify(odontologoRepository, never()).save(any());
    }

    @Test
    void actualizarOdontologo_conEspecialidadInexistente_arrojaIllegalArgumentException() {
        UUID id = UUID.randomUUID();
        UUID fichaId = UUID.randomUUID();

        when(odontologoRepository.findById(id)).thenReturn(Optional.of(odontologoExistente(id, fichaId)));
        when(especialidadRepository.findAllById(any())).thenReturn(List.of()); // ninguna existe

        assertThrows(IllegalArgumentException.class,
                () -> odontologoService.actualizarOdontologo(id,
                        peticionDeReemplazo(fichaId, "COP-VIEJO", Set.of(UUID.randomUUID()))));
        verify(odontologoRepository, never()).save(any());
    }

    @Test
    void actualizarOdontologo_conIdInexistente_arrojaResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(odontologoRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(pe.edu.dentalcite.common.exception.ResourceNotFoundException.class,
                () -> odontologoService.actualizarOdontologo(id,
                        peticionDeReemplazo(UUID.randomUUID(), "COP-X", Set.of(especialidadId))));
    }

    @Test
    void darDeBajaOdontologo_conCitasActivas_arrojaExcepcion() {
        UUID odontologoId = UUID.randomUUID();
        Odontologo odontologo = new Odontologo();
        odontologo.setId(odontologoId);
        odontologo.setActivo(true);

        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(odontologoRepository.hasCitasActivas(odontologoId)).thenReturn(true);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> odontologoService.darDeBajaOdontologo(odontologoId));
        assertTrue(ex.getMessage().contains("citas activas"));
    }

    @Test
    void darDeBajaOdontologo_sinCitasActivas_desactivaOdontologo() {
        UUID odontologoId = UUID.randomUUID();
        Odontologo odontologo = new Odontologo();
        odontologo.setId(odontologoId);
        odontologo.setActivo(true);

        when(odontologoRepository.findById(odontologoId)).thenReturn(Optional.of(odontologo));
        when(odontologoRepository.hasCitasActivas(odontologoId)).thenReturn(false);

        odontologoService.darDeBajaOdontologo(odontologoId);

        assertFalse(odontologo.getActivo());
        verify(odontologoRepository).findById(odontologoId);
        verify(odontologoRepository).hasCitasActivas(odontologoId);
    }
}
