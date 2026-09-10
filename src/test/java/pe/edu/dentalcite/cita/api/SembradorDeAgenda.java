package pe.edu.dentalcite.cita.api;

import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.horario.domain.HorarioAtencion;
import pe.edu.dentalcite.horario.repository.HorarioAtencionRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Siembra y retira el escenario de una prueba de exclusión mutua.
 *
 * <p>Las dos pruebas de HU-10 —la normal y la que corre sin Redis— necesitan
 * exactamente lo mismo: una especialidad, un tratamiento, odontólogos con
 * horario y un puñado de pacientes con ficha. Se comparte aquí para que no haya
 * dos copias que se desincronicen, que es justo lo que pasaría con cien líneas de
 * montaje duplicadas.
 *
 * <p>No es una prueba: no lleva {@code @Test} y por eso no la recoge Surefire.
 */
class SembradorDeAgenda {

    private final UsuarioRepository usuarioRepository;
    private final FichaRepository fichaRepository;
    private final OdontologoRepository odontologoRepository;
    private final TratamientoRepository tratamientoRepository;
    private final HorarioAtencionRepository horarioRepository;
    private final EspecialidadRepository especialidadRepository;
    private final ConsultorioRepository consultorioRepository;

    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> fichas = new ArrayList<>();
    private final List<UUID> odontologos = new ArrayList<>();
    private final List<UUID> horarios = new ArrayList<>();
    private final List<UUID> consultoriosInhabilitados = new ArrayList<>();

    private UUID especialidadId;
    private UUID tratamientoId;

    SembradorDeAgenda(UsuarioRepository usuarioRepository, FichaRepository fichaRepository,
            OdontologoRepository odontologoRepository, TratamientoRepository tratamientoRepository,
            HorarioAtencionRepository horarioRepository, EspecialidadRepository especialidadRepository,
            ConsultorioRepository consultorioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.fichaRepository = fichaRepository;
        this.odontologoRepository = odontologoRepository;
        this.tratamientoRepository = tratamientoRepository;
        this.horarioRepository = horarioRepository;
        this.especialidadRepository = especialidadRepository;
        this.consultorioRepository = consultorioRepository;
    }

    /** Un lunes futuro: lejos de las dos horas de RN-05 y dentro de los noventa días. */
    static LocalDate proximoLunes() {
        LocalDate dia = LocalDate.now().plusDays(14);
        while (dia.getDayOfWeek().getValue() != 1) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    UUID tratamientoId() {
        return tratamientoId;
    }

    /** Tratamiento de treinta minutos con su especialidad recién creada. */
    void sembrarCatalogo(String prefijo) {
        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID())
                .nombre(prefijo + "-" + UUID.randomUUID().toString().substring(0, 8))
                .activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId)
                .codigo("TX-" + UUID.randomUUID().toString().substring(0, 8))
                .nombre("Tratamiento de exclusion")
                .duracionMinutos(30)
                .especialidad(especialidad)
                .activo(true).build());
    }

    /** Odontólogo con la especialidad del catálogo y jornada los lunes. */
    UUID sembrarOdontologo() {
        Ficha ficha = nuevaFicha("Odontologo", "Exclusion");
        UUID id = UUID.randomUUID();
        Odontologo odontologo = odontologoRepository.save(Odontologo.builder()
                .id(id)
                .cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Odontologo").apellidos("Exclusion")
                .ficha(ficha).activo(true)
                .especialidades(Set.of(especialidadRepository.findById(especialidadId).orElseThrow()))
                .build());
        odontologos.add(id);

        horarios.add(horarioRepository.save(HorarioAtencion.builder()
                .odontologo(odontologo).diaSemana(1)
                .horaInicio(LocalTime.of(9, 0)).horaFin(LocalTime.of(13, 0))
                .build()).getId());
        return id;
    }

    /** @return los identificadores de usuario de los pacientes creados. */
    List<UUID> sembrarPacientes(int cuantos) {
        List<UUID> creados = new ArrayList<>(cuantos);
        for (int i = 0; i < cuantos; i++) {
            Ficha ficha = nuevaFicha("Paciente", "Exclusion " + i);
            UUID id = usuarioRepository.save(Usuario.builder()
                    .nombre("Paciente Exclusion " + i)
                    .correo("excl-" + UUID.randomUUID() + "@demo.com")
                    .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                    .rol("PACIENTE").activo(true).ficha(ficha).build()).getId();
            usuarios.add(id);
            creados.add(id);
        }
        return creados;
    }

    /**
     * Deja exactamente {@code cuantos} consultorios en servicio, marcando el resto
     * como inoperativos. Es como se monta el escenario de «tres consultorios
     * libres» sin depender de cuántos tenga la clínica.
     */
    void dejarSoloConsultoriosLibres(int cuantos) {
        List<Consultorio> operativos = consultorioRepository.findByInoperativoFalse();
        for (int i = cuantos; i < operativos.size(); i++) {
            Consultorio consultorio = operativos.get(i);
            consultorio.setInoperativo(true);
            consultorioRepository.save(consultorio);
            consultoriosInhabilitados.add(consultorio.getId());
        }
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        Ficha ficha = fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(System.nanoTime()).substring(0, 8))
                .nombres(nombres).apellidos(apellidos).build());
        fichas.add(ficha.getId());
        return ficha;
    }

    /** Deshace la siembra en el orden que respetan las claves ajenas. */
    void limpiar(java.util.function.Consumer<UUID> borrarCitasDe) {
        consultoriosInhabilitados.forEach(id -> consultorioRepository.findById(id).ifPresent(c -> {
            c.setInoperativo(false);
            consultorioRepository.save(c);
        }));
        fichas.forEach(borrarCitasDe);
        horarios.forEach(horarioRepository::deleteById);
        usuarios.forEach(usuarioRepository::deleteById);
        odontologos.forEach(odontologoRepository::deleteById);
        if (tratamientoId != null) {
            tratamientoRepository.deleteById(tratamientoId);
        }
        fichas.forEach(id -> {
            try {
                fichaRepository.deleteById(id);
            } catch (RuntimeException e) {
                // Alguna quedó referenciada; no es asunto de la limpieza.
            }
        });
        if (especialidadId != null) {
            especialidadRepository.deleteById(especialidadId);
        }
        consultoriosInhabilitados.clear();
        fichas.clear();
        horarios.clear();
        usuarios.clear();
        odontologos.clear();
    }
}
