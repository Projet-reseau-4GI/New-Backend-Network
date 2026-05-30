package com.projects;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NetworkApplication
 *
 * Main entry point of the Spring Boot application.
 */
@SpringBootApplication
public class NetworkApplication {

    private static final Logger log = LoggerFactory.getLogger(NetworkApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(NetworkApplication.class, args);
	}

    @Bean
    public CommandLineRunner diagnosticRunner() {
        return args -> {
            log.info("=== Startup Diagnostics ===");
            log.info("PORT env var: {}", System.getenv("PORT"));
            
            String[] criticalVars = {
                "DB_PASSWORD", 
                "BREVO_API_KEY", 
                "GEMINI_API_KEY", 
                "JWT_SECRET", 
                "ENCRYPTION_SECRET_KEY"
            };

            for (String var : criticalVars) {
                String val = System.getenv(var);
                if (val == null || val.isBlank()) {
                    log.warn("Variable {} is MISSING or EMPTY", var);
                } else {
                    log.info("Variable {} is present (length: {})", var, val.length());
                }
            }
            log.info("===========================");
        };
    }
}
