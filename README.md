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
| `redis` | 6379 | Caché de vigencia de token y de intentos de login |

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

---

## 👥 Credenciales de demostración

Flyway las siembra en el arranque. **Las cuatro usan la contraseña `Password123`** y sirven para probar cada rol sin ningún paso manual:

| Correo | Rol |
|---|---|
| `admin@dentalcite.com` | ADMINISTRADOR |
| `recepcion@dentalcite.com` | RECEPCIONISTA |
| `dr.perez@dentalcite.com` | ODONTOLOGO — con su ficha y su registro de odontólogo (COP-10001) |
| `paciente@demo.com` | PACIENTE |

Junto a ellas se cargan las especialidades, los consultorios, los feriados y el catálogo de recomendaciones (F-12: son datos semilla, no tienen pantalla de mantenimiento).

> El endpoint público `POST /api/v1/auth/registro` siempre crea cuentas con rol `PACIENTE`. Para dar de alta personal, inicia sesión como `admin@dentalcite.com` y usa `POST /api/v1/usuarios` (RF-04). **Ya no hace falta tocar la base de datos a mano.**

### Probar la API desde Swagger

1. `POST /api/v1/auth/login` con una de las credenciales de arriba y copia el `token`.
2. En <http://localhost:8080/swagger-ui.html>, pulsa **Authorize** y pega el token bajo *bearerAuth* (sin el prefijo `Bearer`, Swagger lo añade).
3. Las peticiones siguientes irán firmadas con ese rol.

---

## 💻 Opcion 02 - Desarrollo sin contenedor

Si prefieres ejecutar la aplicación desde consola, levanta solo sus dependencias:

```bash
docker compose up -d postgres redis
mvn spring-boot:run
```

## 🧪 Pruebas

Las pruebas de integración usan Testcontainers, así que **Docker debe estar corriendo** (levanta sus propios contenedores; no necesitas `docker compose up`).

```bash
mvnw test      # solo pruebas
mvnw verify    # pruebas + control de cobertura (≥75 % en dominio y servicio, RNF-10)
```
