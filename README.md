# DentalCite — Backend (API REST)

DentalCite es un sistema de gestión para clínicas odontológicas. Este repositorio contiene el **backend**, en **Java 21** con **Spring Boot 4**. El cliente Angular vive en su propio repositorio.

---

## 🚀 Requisitos previos

- **Docker** y **Docker Compose** — es lo único indispensable para levantar el sistema.
- **JDK 21** — solo si vas a ejecutar la aplicación fuera de contenedor o correr las pruebas.

---

## 🛠 Opcion 01 - Levantar el entorno completo (una sola orden)

RNF-11 exige que el sistema se levante con una única orden de composición y sin pasos manuales:

```bash
docker compose up --build
```

Esto arranca los tres servicios de este repositorio:

| Servicio | Puerto | Notas |
|---|---|---|
| `api` | 8080 | Espera a que PostgreSQL y Redis estén *healthy* antes de arrancar |
| `postgres` | 5433 → 5432 | Flyway aplica las migraciones y siembra los datos de demostración |
| `redis` | 6379 | Vigencia de token, intentos de login, caché de franjas y bloqueo de reserva. Todo *best-effort*: si se cae, el sistema sigue funcionando contra PostgreSQL (RNF-12) |

- API: <http://localhost:8080>
- Contrato OpenAPI navegable: <http://localhost:8080/swagger-ui.html>

El cliente Angular vive en su propio repositorio (`../dentalcite-ui`) y se levanta con la orden equivalente allí. Si tienes ese checkout al lado, `docker compose --profile cliente up` lo añade a esta composición; sin el perfil, `docker compose up` levanta solo los tres servicios de arriba.

### Variables de entorno

Todas tienen un valor por defecto apto para desarrollo local; en un despliegue real conviene fijarlas (por ejemplo, en un `.env` junto al `docker-compose.yml`):

| Variable | Por defecto |
|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | `admin` / `admin_password` / `dentalcite_db` |
| `JWT_SECRET` | clave de desarrollo, definida en `docker-compose.yml` (y en `.env.example`). **No está en `application.yml`**: el jar no lleva secreto, así que un despliegue que olvide la variable no arranca en vez de firmar con una clave pública. Para `./mvnw spring-boot:run` la aporta el `spring-boot-maven-plugin` (`-Ddev.jwt.secret=…` para cambiarla) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:4200` (varios orígenes, separados por comas) |
| `APP_ZONA_HORARIA` | `America/Lima`. Los horarios se declaran en hora local y las citas se guardan en UTC; esta es la zona que los reconcilia |
| `APP_CITAS_ANTELACION_MINIMA_HORAS` | `2` — antelación mínima para reservar (RN-05) |
| `APP_CITAS_HORIZONTE_MAXIMO_DIAS` | `90` — cuánto se puede reservar a futuro (RN-05) |
| `APP_CITAS_MAXIMO_ACTIVAS` | `3` — citas activas simultáneas por paciente (RN-07) |
| `APP_CITAS_BLOQUEO_TTL` | `10` s — vida del bloqueo distribuido de franja (HU-10) |
| `APP_DISPONIBILIDAD_DIAS_MAXIMOS` | `14` — rango máximo consultable (RNF-01). **Es la palanca de contingencia del riesgo R-01**: si no se alcanza el umbral de rendimiento, se baja a 7 sin recompilar |
| `APP_DISPONIBILIDAD_CACHE_TTL` | `60` s — vida de la caché de franjas |

> Las seis `APP_*` de negocio existen porque **RN-17** exige que los umbrales sean ajustables sin recompilar. Cambiarlas es un cambio de despliegue, no de código.

---

## 👥 Credenciales de demostración

Flyway las siembra en el arranque. **Las cuatro usan la contraseña `Password123`** y sirven para probar cada rol sin ningún paso manual:

| Correo | Rol |
|---|---|
| `admin@dentalcite.com` | ADMINISTRADOR |
| `recepcion@dentalcite.com` | RECEPCIONISTA |
| `dr.perez@dentalcite.com` | ODONTOLOGO — con su ficha y su registro de odontólogo (COP-10001) |
| `paciente@demo.com` | PACIENTE |

Junto a ellas se cargan las especialidades, los consultorios, los feriados y el catálogo de recomendaciones (F-12: son datos semilla, no tienen pantalla de mantenimiento). Desde `V15` se siembran también **el catálogo de tratamientos, una segunda odontóloga (COP-10002, con otras especialidades) y el horario de atención de ambos**: sin ellos el motor de disponibilidad no tendría nada que ofrecer y la demostración empezaría dando de alta catálogo y horario a mano, que es el paso manual que RNF-11 prohíbe.

> El endpoint público `POST /api/v1/auth/registro` siempre crea cuentas con rol `PACIENTE`. Para dar de alta personal, inicia sesión como `admin@dentalcite.com` y usa `POST /api/v1/usuarios` (RF-04). **Ya no hace falta tocar la base de datos a mano.**

### Probar la API desde Swagger

1. `POST /api/v1/auth/login` con una de las credenciales de arriba y copia el `token`.
2. En <http://localhost:8080/swagger-ui.html>, pulsa **Authorize** y pega el token bajo *bearerAuth* (sin el prefijo `Bearer`, Swagger lo añade).
3. Las peticiones siguientes irán firmadas con ese rol.

Para recorrer el flujo de agenda del Sprint 2, en este orden:

| Paso | Endpoint | Rol |
|---|---|---|
| Ver qué franjas admite un tratamiento | `GET /api/v1/disponibilidad` | cualquiera autenticado |
| Reservar una de ellas, para uno mismo | `POST /api/v1/citas` | `PACIENTE` |
| Ver la agenda del día y sus filtros | `GET /api/v1/citas` | `RECEPCIONISTA`, `ADMINISTRADOR` |
| Cancelar con motivo, sin ventana | `PATCH /api/v1/citas/{id}/cancelar` | `RECEPCIONISTA`, `ADMINISTRADOR` |
| Ver quién hizo qué y cuándo | `GET /api/v1/citas/{id}/historial` | `RECEPCIONISTA`, `ADMINISTRADOR` |

Tras cancelar, vuelve a `GET /api/v1/disponibilidad`: la franja se ofrece otra vez de inmediato.

El mismo recorrido existe en el portal, que es donde se covalidan los criterios de interfaz —la agrupación de franjas en pasos de treinta minutos y los 360 px de RNF-09—: `/disponibilidad` para consultar y reservar, `/citas` para la agenda, la cancelación y el historial.

---

## 💻 Opcion 02 - Desarrollo sin contenedor

Si prefieres ejecutar la aplicación desde consola, levanta solo sus dependencias:

```bash
docker compose up -d postgres redis
./mvnw spring-boot:run
```

## 🧪 Pruebas

Las pruebas de integración usan Testcontainers, así que **Docker debe estar corriendo** (levanta sus propios contenedores; no necesitas `docker compose up`).

```bash
./mvnw test      # solo pruebas
./mvnw verify    # pruebas + control de cobertura (≥75 % en dominio y servicio, RNF-10)
```

Las dos mediciones de rendimiento están **desactivadas por defecto**, porque
sembrar las agendas y medir alarga la construcción y una máquina cargada las
volvería intermitentes:

```bash
./mvnw verify "-Dperf=true"
```

| Prueba | Requisito | Umbral |
|---|---|---|
| `DisponibilidadRendimientoIntegrationTest` | RNF-01 | p95 ≤ 1,5 s · 14 días, 5 odontólogos al 70 % de ocupación, caché fría |
| `ReservaRendimientoIntegrationTest` | RNF-02 | p95 ≤ 1 s · 10 usuarios concurrentes, sobre la reserva y sobre la consulta de agenda |
