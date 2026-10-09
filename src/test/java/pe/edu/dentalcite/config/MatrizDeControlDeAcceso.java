package pe.edu.dentalcite.config;

import org.springframework.http.HttpMethod;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.http.HttpMethod.DELETE;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

/**
 * La matriz de control de acceso del plan de proyecto, escrita operación por
 * operación sobre la API tal como está expuesta.
 *
 * <p>Cada fila es un endpoint y los roles que lo alcanzan. Todo rol que no
 * figura en ella es una combinación no autorizada, y {@code ControlDeAccesoIntegrationTest}
 * la recorre entera. La misma prueba compara esta lista con los endpoints que
 * Spring tiene registrados, así que un endpoint nuevo sin su fila rompe la suite:
 * es la forma de que «toda operación expuesta» siga siéndolo mañana.
 *
 * <p>Las celdas de la matriz hablan de recursos y esta tabla de rutas. Donde la
 * API es más estricta que la celda —una ruta que un rol de la celda no alcanza—,
 * se dice en la fila: no es una omisión, es que esa operación concreta no se le
 * ofrece.
 *
 * <p>No es una prueba: no lleva {@code @Test} y Surefire no la recoge.
 */
final class MatrizDeControlDeAcceso {

    enum Rol {
        PACIENTE, RECEPCIONISTA, ODONTOLOGO, ADMINISTRADOR;

        String autoridad() {
            return "SCOPE_" + name();
        }
    }

    /**
     * Un endpoint y quién lo alcanza.
     *
     * @param publica si responde sin token. Lo que no es público exige sesión
     *                aunque todos los roles lo alcancen.
     */
    record Operacion(HttpMethod metodo, String ruta, Set<Rol> autorizados, boolean publica) {

        /** La ruta con sus variables sustituidas por valores que no existen. */
        String rutaConcreta() {
            return ruta.replace("{numero}", "1")
                    .replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }

        /** La ruta sin los nombres de sus variables, para compararla con la de Spring. */
        String patron() {
            return patron(ruta);
        }

        static String patron(String ruta) {
            return ruta.replaceAll("\\{[^}]+}", "{}");
        }

        boolean llevaCuerpo() {
            return metodo == POST || metodo == PUT || metodo == PATCH;
        }

        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    private static final Set<Rol> TODOS = EnumSet.allOf(Rol.class);
    private static final Set<Rol> ADMIN = EnumSet.of(Rol.ADMINISTRADOR);

    private static Operacion publica(HttpMethod metodo, String ruta) {
        return new Operacion(metodo, "/api/v1" + ruta, TODOS, true);
    }

    private static Operacion de(HttpMethod metodo, String ruta, Set<Rol> autorizados) {
        return new Operacion(metodo, "/api/v1" + ruta, autorizados, false);
    }

    private static Set<Rol> roles(Rol... roles) {
        return EnumSet.copyOf(List.of(roles));
    }

    static final List<Operacion> OPERACIONES = List.of(
            // Las dos operaciones con las que se deja de ser visitante, y el cierre
            // de la sesión, que cualquiera que la tenga puede cerrar.
            publica(POST, "/auth/registro"),
            publica(POST, "/auth/login"),
            de(POST, "/auth/logout", TODOS),

            // Cuentas de usuario y roles: solo administración. El perfil propio lo
            // leen todos y lo único que actualizan es la contraseña.
            de(GET, "/usuarios/me", TODOS),
            de(POST, "/usuarios/me/password", TODOS),
            de(GET, "/usuarios", ADMIN),
            de(GET, "/usuarios/{id}", ADMIN),
            de(POST, "/usuarios", ADMIN),
            de(PUT, "/usuarios/{id}", ADMIN),
            de(PATCH, "/usuarios/{id}", ADMIN),
            de(POST, "/usuarios/{id}/password-provisional", ADMIN),

            // Catálogo clínico: lo leen todos los roles y lo mantiene administración.
            // El visitante lo lee por las rutas públicas de más abajo.
            de(GET, "/especialidades", TODOS),
            de(POST, "/especialidades", ADMIN),
            de(PUT, "/especialidades/{id}", ADMIN),
            de(PATCH, "/especialidades/{id}", ADMIN),
            de(GET, "/tratamientos", TODOS),
            de(POST, "/tratamientos", ADMIN),
            de(PUT, "/tratamientos/{id}", ADMIN),
            de(PATCH, "/tratamientos/{id}", ADMIN),
            de(GET, "/odontologos", TODOS),
            de(POST, "/odontologos", ADMIN),
            de(PUT, "/odontologos/{id}", ADMIN),
            de(PATCH, "/odontologos/{id}", ADMIN),

            // Los consultorios se leen y no se mantienen por interfaz.
            de(GET, "/consultorios", TODOS),

            // Horario de atención: el odontólogo el suyo, administración cualquiera.
            // El visitante solo ve qué días se atiende, por /publico/calendario.
            de(GET, "/odontologos/{odontologoId}/horarios", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(POST, "/odontologos/{odontologoId}/horarios", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(PUT, "/odontologos/{odontologoId}/horarios/{horarioId}", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(DELETE, "/odontologos/{odontologoId}/horarios/{horarioId}", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),

            // Bloqueos de agenda y de consultorio.
            de(GET, "/bloqueos", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(POST, "/bloqueos", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(PUT, "/bloqueos/{id}", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(DELETE, "/bloqueos/{id}", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),

            // La consulta de disponibilidad no pide sesión, y con ella responde igual.
            publica(GET, "/disponibilidad"),

            // Citas. El odontólogo lee las suyas por la agenda y no reserva. La
            // bitácora de una cita es de recepción y administración: la celda de la
            // cita le da al odontólogo la lectura de las suyas, pero no la de quién
            // ordenó cada transición.
            de(POST, "/citas", roles(Rol.PACIENTE, Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)),
            de(GET, "/citas", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(GET, "/citas/mias", roles(Rol.PACIENTE)),
            de(PATCH, "/citas/{id}/cancelar", roles(Rol.PACIENTE, Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)),
            de(GET, "/citas/pendientes-cierre", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(PATCH, "/citas/{id}/resultado", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(GET, "/citas/{id}/historial", roles(Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)),

            // Ficha de paciente. Una ficha concreta la alcanzan los cuatro roles,
            // pero cuál decide la propiedad del dato, no la ruta. El paciente no
            // busca pacientes: su ficha la abre por su identificador.
            de(POST, "/pacientes", roles(Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)),
            de(GET, "/pacientes", roles(Rol.RECEPCIONISTA, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(GET, "/pacientes/{id}", TODOS),
            de(PUT, "/pacientes/{id}", roles(Rol.RECEPCIONISTA, Rol.ADMINISTRADOR)),

            // Plan de tratamiento y sesiones. El seguimiento es la lista de trabajo
            // del odontólogo; administración lee cada plan por su ficha o su id.
            de(POST, "/planes", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(GET, "/planes", roles(Rol.PACIENTE, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(GET, "/planes/seguimiento", roles(Rol.ODONTOLOGO)),
            de(GET, "/planes/{id}", roles(Rol.PACIENTE, Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(PATCH, "/planes/{id}/suspender", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),
            de(POST, "/planes/{id}/sesiones/{numero}/cierre", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),

            // El catálogo de recomendaciones lo leen quienes cierran sesiones.
            de(GET, "/recomendaciones", roles(Rol.ODONTOLOGO, Rol.ADMINISTRADOR)),

            // Lo que el visitante recorre sin cuenta.
            publica(GET, "/publico/calendario"),
            publica(GET, "/publico/tratamientos"),
            publica(GET, "/publico/especialidades"),
            publica(GET, "/publico/especialidades/{id}"),
            publica(GET, "/publico/odontologos"));

    private MatrizDeControlDeAcceso() {
    }
}
