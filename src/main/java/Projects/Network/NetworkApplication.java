package Projects.Network;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NetworkApplication
 *
 * Main entry point of the Spring Boot application.
 *
 * This class is responsible for bootstrapping and launching the application.
 * It triggers Spring Boot’s auto-configuration mechanism, component scanning,
 * and application context initialization.
 *
 * The {@code @SpringBootApplication} annotation is a convenience annotation
 * that combines:
 * - {@code @Configuration}
 * - {@code @EnableAutoConfiguration}
 * - {@code @ComponentScan}
 *
 * No business logic should be placed in this class.
 * Its sole responsibility is application startup.
 */
@SpringBootApplication
public class NetworkApplication {

	/**
	 * Application main method.
	 *
	 * This method delegates to {@link SpringApplication#run(Class, String[])}
	 * to launch the embedded server and initialize the Spring context.
	 *
	 * @param args command-line arguments passed at application startup
	 */
	public static void main(String[] args) {
		SpringApplication.run(NetworkApplication.class, args);
	}
}
