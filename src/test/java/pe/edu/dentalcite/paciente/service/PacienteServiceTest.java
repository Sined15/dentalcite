package pe.edu.dentalcite.paciente.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.cita.service.CitaConsultaService;
import pe.edu.dentalcite.common.exception.ResourceNotFoundException;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;
import pe.edu.dentalcite.consentimiento.repository.ConsentimientoRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.paciente.api.dto.PacienteDetalleDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteRequestDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteResponseDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteUpdateRequestDTO;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El servicio de pacientes sin Testcontainers: lo que no depende de HTTP ni del
 * esquema.
 *
 * <p>HU-12, RF-06 — de dónde sale el número de historia, qué se rechaza por
 * RN-10 y qué queda escrito del consentimiento (RNF-06).
 *
 * <p>HU-13, RF-07 y RF-08 — cómo se traduce el término de búsqueda, cuál de las
 * dos consultas se emite según quién pregunte, y qué reemplaza el PUT (y qué no).
 */
@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private FichaRepository fichaRepository;

    @Mock
    private ConsentimientoRepository consentimientoRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private CitaRepository citaRepository;

    @Mock
    private CitaConsultaService citaConsultaService;

    @Mock
    private PacienteAccessGuard accessGuard;

    @InjectMocks
    private PacienteService pacienteService;

    private PacienteRequestDTO solicitudValida() {
        PacienteRequestDTO request = new PacienteRequestDTO();
        request.setNombres("  Rosa  ");
        request.setApellidos("  Huaman  ");
        request.setTipoDocumento("DNI");
        request.setDocumento(" 71234567 ");
        request.setTelefono(" 987654321 ");
        request.setConsentimientoAceptado(true);
        request.setVersionConsentimiento("1.0");
        return request;
    }

    @Test
    void registrar_conDocumentoNuevo_creaFichaSinCuentaYConHistoriaDeLaSecuencia() {
        when(fichaRepository.existsByTipoDocumentoAndDocumento("DNI", "71234567")).thenReturn(false);
        when(fichaRepository.getNextHistoriaClinica()).thenReturn(42L);
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArgument(0));

        PacienteResponseDTO respuesta = pacienteService.registrar(solicitudValida());

        // El correlativo sale de la secuencia, no de un conteo (RN-10).
        assertEquals("HC-00042", respuesta.getNumeroHistoria());
        assertEquals("71234567", respuesta.getDocumento());
        assertEquals("Rosa", respuesta.getNombres());
        assertEquals("Huaman", respuesta.getApellidos());
        assertEquals("987654321", respuesta.getTelefono());
        // El criterio dice «sin credenciales de acceso».
        assertFalse(respuesta.isTieneCuenta());
    }

    @Test
    void registrar_conDocumentoYaRegistrado_arrojaIllegalStateException() {
        PacienteRequestDTO request = solicitudValida();
        when(fichaRepository.existsByTipoDocumentoAndDocumento("DNI", "71234567")).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> pacienteService.registrar(request));

        verify(fichaRepository, never()).save(any());
        verify(fichaRepository, never()).getNextHistoriaClinica();
        verify(consentimientoRepository, never()).save(any());
    }

    @Test
    void registrar_conTipoDocumentoEnBlanco_asumeDni() {
        PacienteRequestDTO request = solicitudValida();
        request.setTipoDocumento("   ");
        when(fichaRepository.existsByTipoDocumentoAndDocumento("DNI", "71234567")).thenReturn(false);
        when(fichaRepository.getNextHistoriaClinica()).thenReturn(1L);
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArgument(0));

        assertEquals("DNI", pacienteService.registrar(request).getTipoDocumento());
    }

    @Test
    void registrar_sinTelefono_dejaLaColumnaNulaEnVezDeVacia() {
        PacienteRequestDTO request = solicitudValida();
        request.setTelefono("   ");
        when(fichaRepository.existsByTipoDocumentoAndDocumento("DNI", "71234567")).thenReturn(false);
        when(fichaRepository.getNextHistoriaClinica()).thenReturn(1L);
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArgument(0));

        assertNull(pacienteService.registrar(request).getTelefono());
    }

    /**
     * RNF-06: «cada alta registrará el consentimiento informado con su fecha y la
     * versión del texto». Y cuelga de la ficha, porque este paciente no tiene
     * cuenta de la que colgar: hasta V16 la fila no cabía en la tabla.
     */
    @Test
    void registrar_dejaConstanciaDelConsentimientoColgandoDeLaFicha() {
        when(fichaRepository.existsByTipoDocumentoAndDocumento("DNI", "71234567")).thenReturn(false);
        when(fichaRepository.getNextHistoriaClinica()).thenReturn(7L);
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArgument(0));

        pacienteService.registrar(solicitudValida());

        ArgumentCaptor<Consentimiento> captor = ArgumentCaptor.forClass(Consentimiento.class);
        verify(consentimientoRepository).save(captor.capture());
        Consentimiento consentimiento = captor.getValue();

        assertEquals("1.0", consentimiento.getVersionTexto());
        assertNotNull(consentimiento.getFecha());
        assertNotNull(consentimiento.getFicha());
        assertEquals("HC-00007", consentimiento.getFicha().getNumeroHistoria());
        assertNull(consentimiento.getUsuario());
    }

    // ---------- HU-13 ----------

    private Ficha fichaDe(String nombres, String apellidos, String documento) {
        return Ficha.builder()
                .id(UUID.randomUUID())
                .tipoDocumento("DNI")
                .documento(documento)
                .nombres(nombres)
                .apellidos(apellidos)
                .numeroHistoria("HC-00001")
                .build();
    }

    /**
     * RF-07. El apellido se busca por prefijo en minusculas porque es lo unico
     * que el indice ix_fichas_apellidos puede resolver: montar el patron dentro
     * de la consulta dejaria a PostgreSQL recorriendo la tabla, que es justo lo
     * que RNF-02 mide.
     */
    @Test
    void buscar_comoRecepcion_pasaElTerminoYSuPrefijoEnMinusculas() {
        Ficha ficha = fichaDe("Rosa", "Huaman", "71234567");
        when(accessGuard.odontologoDelListado()).thenReturn(null);
        when(fichaRepository.buscar(eq("Huaman"), eq("huaman%"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(ficha)));
        when(usuarioRepository.fichasConCuenta(anyCollection())).thenReturn(Set.of());

        var pagina = pacienteService.buscar("Huaman", PageRequest.of(0, 20));

        assertEquals(1, pagina.getTotalElements());
        assertFalse(pagina.getContent().get(0).isTieneCuenta());
        verify(citaRepository, never()).buscarPacientesDeOdontologo(any(), any(), any(), any());
    }

    /** RNF-06: el odontologo busca solo entre las personas a las que ha atendido. */
    @Test
    void buscar_comoOdontologo_usaLaConsultaRestringidaASusPacientes() {
        UUID fichaDelOdontologo = UUID.randomUUID();
        when(accessGuard.odontologoDelListado()).thenReturn(fichaDelOdontologo);
        when(citaRepository.buscarPacientesDeOdontologo(any(), any(), eq(fichaDelOdontologo), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        pacienteService.buscar(null, PageRequest.of(0, 20));

        verify(fichaRepository, never()).buscar(any(), any(), any());
    }

    /** Sin termino el listado se abre poblado: el nulo significa «todas». */
    @Test
    void buscar_sinTermino_noFiltraNada() {
        when(accessGuard.odontologoDelListado()).thenReturn(null);
        when(fichaRepository.buscar(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        assertTrue(pacienteService.buscar("   ", PageRequest.of(0, 20)).isEmpty());
    }

    /**
     * Un sort inventado en la barra de direcciones tiene que ignorarse, no
     * reventar la consulta con un 500.
     */
    @Test
    void buscar_conOrdenNoPermitido_caeAlOrdenPorApellidos() {
        when(accessGuard.odontologoDelListado()).thenReturn(null);
        when(fichaRepository.buscar(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        pacienteService.buscar(null, PageRequest.of(0, 20, Sort.by("contrasenaHash")));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(fichaRepository).buscar(any(), any(), captor.capture());
        assertEquals(Sort.by(Sort.Order.asc("apellidos"), Sort.Order.asc("nombres")),
                captor.getValue().getSort());
    }

    /** RF-08: la ficha lleva sus alergias y sus citas, y pasa por el guardian. */
    @Test
    void obtener_devuelveAlergiasYCitasTrasComprobarElAcceso() {
        Ficha ficha = fichaDe("Rosa", "Huaman", "71234567");
        ficha.setAlergias("Penicilina");
        when(fichaRepository.findById(ficha.getId())).thenReturn(Optional.of(ficha));
        when(usuarioRepository.existsByFichaId(ficha.getId())).thenReturn(true);
        when(citaConsultaService.deFicha(ficha.getId())).thenReturn(List.of());

        PacienteDetalleDTO detalle = pacienteService.obtener(ficha.getId());

        assertEquals("Penicilina", detalle.getAlergias());
        assertTrue(detalle.isTieneCuenta());
        assertNotNull(detalle.getCitas());
        verify(accessGuard).verificarLectura(ficha.getId());
    }

    @Test
    void obtener_deUnaFichaInexistente_arrojaResourceNotFound() {
        UUID id = UUID.randomUUID();
        when(fichaRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> pacienteService.obtener(id));
        // El 404 va antes que el 403: no se pregunta el permiso sobre lo que no existe.
        verify(accessGuard, never()).verificarLectura(any());
    }

    /**
     * RN-10: el documento es la identidad del paciente. Que el PUT no lo toque no
     * es un olvido; cambiarlo convertiria esta ficha en la de otra persona.
     */
    @Test
    void actualizar_reemplazaContactoYAlergiasPeroNuncaElDocumento() {
        Ficha ficha = fichaDe("Rosa", "Huaman", "71234567");
        ficha.setTelefono("987654321");
        when(fichaRepository.findById(ficha.getId())).thenReturn(Optional.of(ficha));
        when(fichaRepository.save(any(Ficha.class))).thenAnswer(i -> i.getArgument(0));
        when(citaConsultaService.deFicha(ficha.getId())).thenReturn(List.of());

        PacienteUpdateRequestDTO request = new PacienteUpdateRequestDTO();
        request.setNombres(" Rosa Maria ");
        request.setApellidos(" Huaman Rios ");
        request.setTelefono("   ");
        request.setAlergias(" Penicilina ");

        PacienteDetalleDTO detalle = pacienteService.actualizar(ficha.getId(), request);

        assertEquals("Rosa Maria", detalle.getNombres());
        assertEquals("Huaman Rios", detalle.getApellidos());
        // Es un PUT: el telefono ausente borra el que habia, que es como se corrige.
        assertNull(detalle.getTelefono());
        assertEquals("Penicilina", detalle.getAlergias());
        assertEquals("71234567", detalle.getDocumento());
        assertEquals("DNI", detalle.getTipoDocumento());
    }
}
