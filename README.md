# DentalCite — Backend

API REST para la gestión de clínicas odontológicas. Java 21, Spring Boot 4, PostgreSQL y Redis. El cliente Angular está en el repositorio `dentalcite-ui`.

## Requisitos

- Docker y Docker Compose
- JDK 21 (solo para ejecutar sin contenedor o correr las pruebas)

## Levantar el sistema

```bash
docker compose up --build
```

- API: <http://localhost:8080>
- Swagger: <http://localhost:8080/swagger-ui.html>

Flyway crea el esquema y carga los datos de demostración al arrancar.

## Desarrollo sin contenedor

```bash
docker compose up -d postgres redis
./mvnw spring-boot:run
```

## Pruebas

Requieren Docker en ejecución (usan Testcontainers).

```bash
./mvnw test
./mvnw verify
```

## Usuarios de demostración

Contraseña de todos: `Password123`

| Correo | Rol |
|---|---|
| `admin@dentalcite.com` | Administrador |
| `recepcion@dentalcite.com` | Recepcionista |
| `dr.perez@dentalcite.com` | Odontólogo |
| `paciente@demo.com` | Paciente |
