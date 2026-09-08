package pe.edu.dentalcite;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Fijada a la misma versión que docker-compose.yml: con `latest` las pruebas
		// podían pasar sobre un PostgreSQL distinto al de despliegue.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"));
	}

	@Bean
	@ServiceConnection(name = "redis")
	@SuppressWarnings("resource")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);
	}

}
