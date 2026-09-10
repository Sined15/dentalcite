package pe.edu.dentalcite.paciente.api;

import pe.edu.dentalcite.cita.domain.Cita;
import pe.edu.dentalcite.cita.repository.CitaRepository;
import pe.edu.dentalcite.consultorio.domain.Consultorio;
import pe.edu.dentalcite.consultorio.repository.ConsultorioRepository;
import pe.edu.dentalcite.especialidad.domain.Especialidad;
import pe.edu.dentalcite.especialidad.repository.EspecialidadRepository;
import pe.edu.dentalcite.ficha.domain.Ficha;
import pe.edu.dentalcite.ficha.repository.FichaRepository;
import pe.edu.dentalcite.odontologo.domain.Odontologo;
import pe.edu.dentalcite.odontologo.repository.OdontologoRepository;
import pe.edu.dentalcite.tratamiento.domain.Tratamiento;
import pe.edu.dentalcite.tratamiento.repository.TratamientoRepository;
import pe.edu.dentalcite.usuario.domain.Usuario;
import pe.edu.dentalcite.usuario.repository.UsuarioRepository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Siembra el escenario que comparten las dos pruebas de HU-13: dos pacientes,
 * dos odontólogos y las citas que los relacionan.
 *
 * <p>La búsqueda y la ficha son dos criterios distintos y por eso son dos
 * clases de prueba, pero necesitan exactamente el mismo montaje. Se comparte
 * aquí por lo mismo que {@code SembradorDeAgenda} en HU-10: dos copias de cien
 * líneas de siembra se desincronizan.
 *
 * <p>Lo que el escenario tiene que poder distinguir:
 * <ul>
 *   <li>el odontólogo <em>propio</em>, con una cita ATENDIDA y otra CONFIRMADA
 *       futura con el paciente uno;</li>
 *   <li>el odontólogo <em>ajeno</em>, cuya única cita con ese paciente está
 *       CANCELADA —que es el caso límite del criterio 4—;</li>
 *   <li>un paciente con cuenta y otro sin ella, que es lo que `tieneCuenta`
 *       reporta en el listado.</li>
 * </ul>
 *
 * <p>No es una prueba ni un bean: no lleva {@code @Test}, asi que Surefire no la
 * recoge, y se construye a mano en cada prueba en vez de anotarla, para no
 * aparecer como componente en el contexto de todas las demas.
 */
class EscenarioDePacientes {

    private final FichaRepository fichaRepository;
    private final UsuarioRepository usuarioRepository;
    private final OdontologoRepository odontologoRepository;
    private final EspecialidadRepository especialidadRepository;
    private final TratamientoRepository tratamientoRepository;
    private final ConsultorioRepository consultorioRepository;
    private final CitaRepository citaRepository;

    private final List<UUID> citas = new ArrayList<>();
    private final List<UUID> usuarios = new ArrayList<>();
    private final List<UUID> odontologos = new ArrayList<>();
    private final List<UUID> fichas = new ArrayList<>();
    private UUID tratamientoId;
    private UUID especialidadId;

    /** El apellido lleva sufijo aleatorio: la base sobrevive de una prueba a otra. */
    String sufijo;

    Ficha pacienteConCuenta;
    Ficha pacienteSinCuenta;
    UUID usuarioDelPaciente;
    /** Cuenta del odontólogo que sí ha atendido al paciente con cuenta. */
    UUID usuarioOdontologoPropio;
    /** Cuenta del odontólogo cuya única cita con él está CANCELADA. */
    UUID usuarioOdontologoAjeno;

    EscenarioDePacientes(FichaRepository fichaRepository, UsuarioRepository usuarioRepository,
            OdontologoRepository odontologoRepository, EspecialidadRepository especialidadRepository,
            TratamientoRepository tratamientoRepository, ConsultorioRepository consultorioRepository,
            CitaRepository citaRepository) {
        this.fichaRepository = fichaRepository;
        this.usuarioRepository = usuarioRepository;
        this.odontologoRepository = odontologoRepository;
        this.especialidadRepository = especialidadRepository;
        this.tratamientoRepository = tratamientoRepository;
        this.consultorioRepository = consultorioRepository;
        this.citaRepository = citaRepository;
    }

    void sembrar() {
        sufijo = UUID.randomUUID().toString().substring(0, 6);

        Especialidad especialidad = especialidadRepository.save(Especialidad.builder()
                .id(UUID.randomUUID()).nombre("Esp-" + sufijo).activo(true).build());
        especialidadId = especialidad.getId();

        tratamientoId = UUID.randomUUID();
        tratamientoRepository.save(Tratamiento.builder()
                .id(tratamientoId).codigo("TX-" + sufijo).nombre("Tratamiento " + sufijo)
                .duracionMinutos(30).especialidad(especialidad).activo(true).build());

        pacienteConCuenta = nuevaFicha("Rosa", "Huaman" + sufijo);
        pacienteSinCuenta = nuevaFicha("Mario", "Quispe" + sufijo);

        usuarioDelPaciente = nuevaCuenta("PACIENTE", pacienteConCuenta);

        Odontologo propio = nuevoOdontologo("Propio", especialidad);
        Odontologo ajeno = nuevoOdontologo("Ajeno", especialidad);
        usuarioOdontologoPropio = nuevaCuenta("ODONTOLOGO", propio.getFicha());
        usuarioOdontologoAjeno = nuevaCuenta("ODONTOLOGO", ajeno.getFicha());

        Consultorio consultorio = consultorioRepository.findByInoperativoFalse().get(0);
        // Horas separadas y muy en el futuro: la exclusión de V13 rechaza el solape,
        // y estas citas se insertan sin pasar por el motor que lo evita.
        OffsetDateTime base = OffsetDateTime.now().plusDays(30).withMinute(0).withNano(0);

        nuevaCita(propio, pacienteConCuenta, consultorio, base.minusDays(60), "ATENDIDA");
        nuevaCita(propio, pacienteConCuenta, consultorio, base.plusHours(1), "CONFIRMADA");
        // El caso límite del criterio 4: existe la fila, pero no la consulta.
        nuevaCita(ajeno, pacienteConCuenta, consultorio, base.plusHours(3), "CANCELADA");
    }

    private Ficha nuevaFicha(String nombres, String apellidos) {
        Ficha ficha = fichaRepository.save(Ficha.builder()
                .numeroHistoria(String.format("HC-%05d", fichaRepository.getNextHistoriaClinica()))
                .tipoDocumento("DNI")
                .documento(String.valueOf(ThreadLocalRandom.current().nextLong(10_000_000L, 99_999_999L)))
                .nombres(nombres).apellidos(apellidos)
                .telefono("987000000")
                .alergias("Rosa".equals(nombres) ? "Penicilina" : null)
                .build());
        fichas.add(ficha.getId());
        return ficha;
    }

    private UUID nuevaCuenta(String rol, Ficha ficha) {
        UUID id = usuarioRepository.save(Usuario.builder()
                .nombre(rol + " " + sufijo)
                .correo(rol.toLowerCase() + "-" + UUID.randomUUID() + "@demo.com")
                .contrasenaHash("$2a$12$sinUsoEnEstaPrueba000000000000000000000000000000000000")
                .rol(rol).activo(true).ficha(ficha).build()).getId();
        usuarios.add(id);
        return id;
    }

    private Odontologo nuevoOdontologo(String apellido, Especialidad especialidad) {
        Ficha ficha = nuevaFicha("Doctor", apellido + sufijo);
        UUID id = UUID.randomUUID();
        odontologos.add(id);
        return odontologoRepository.save(Odontologo.builder()
                .id(id).cop("COP-" + UUID.randomUUID().toString().substring(0, 8))
                .nombres("Doctor").apellidos(apellido + sufijo)
                .ficha(ficha).activo(true)
                .especialidades(Set.of(especialidad))
                .build());
    }

    private void nuevaCita(Odontologo odontologo, Ficha ficha, Consultorio consultorio,
            OffsetDateTime inicio, String estado) {
        Cita cita = citaRepository.save(Cita.builder()
                .codigo(String.format("CIT-%06d", citaRepository.getNextCodigoCita()))
                .ficha(ficha).odontologo(odontologo).consultorio(consultorio)
                .tratamiento(tratamientoRepository.findById(tratamientoId).orElseThrow())
                .inicio(inicio).fin(inicio.plusMinutes(30))
                .estado(estado)
                .motivoCancelacion("CANCELADA".equals(estado) ? "El paciente no pudo asistir" : null)
                .build());
        citas.add(cita.getId());
    }

    /** Deshace la siembra en el orden que respetan las claves ajenas. */
    void limpiar() {
        citas.forEach(citaRepository::deleteById);
        usuarios.forEach(usuarioRepository::deleteById);
        odontologos.forEach(odontologoRepository::deleteById);
        if (tratamientoId != null) {
            tratamientoRepository.deleteById(tratamientoId);
        }
        fichas.forEach(fichaRepository::deleteById);
        if (especialidadId != null) {
            especialidadRepository.deleteById(especialidadId);
        }
        citas.clear();
        usuarios.clear();
        odontologos.clear();
        fichas.clear();
    }
}
