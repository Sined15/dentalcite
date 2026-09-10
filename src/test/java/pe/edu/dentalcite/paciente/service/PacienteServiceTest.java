package pe.edu.dentalcite.paciente.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.dentalcite.consentimiento.domain.Consentimiento;
import pe.edu.dentalcite.consentimiento.repository.ConsentimientoRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.paciente.api.dto.PacienteRequestDTO;
import pe.edu.dentalcite.paciente.api.dto.PacienteResponseDTO;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HU-12, RF-06: el alta presencial vista desde el servicio, sin Testcontainers.
 * Lo que se comprueba aquí es lo que no depende de HTTP ni del esquema: de dónde
 * sale el número de historia, qué se rechaza por RN-10 y qué queda escrito del
 * consentimiento (RNF-06).
 */
@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private FichaRepository fichaRepository;

    @Mock
    private ConsentimientoRepository consentimientoRepository;

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
}
