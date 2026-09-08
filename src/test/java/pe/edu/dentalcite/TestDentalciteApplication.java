package pe.edu.dentalcite;

import org.springframework.boot.SpringApplication;

public class TestDentalciteApplication {

	public static void main(String[] args) {
		SpringApplication.from(DentalciteApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
