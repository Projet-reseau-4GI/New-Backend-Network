package com.projects.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * HealthController
 *
 * This controller is responsible for exposing a simple health-check endpoint.
 * It allows external systems (load balancers, monitoring tools, DevOps
 * pipelines)
 * or developers to verify that the application is running correctly.
 *
 * This controller belongs to the Controller layer of the application and does
 * not
 * contain any business logic. Its sole responsibility is to handle HTTP
 * requests
 * and return an appropriate response.
 *
 * The controller uses Spring WebFlux and returns reactive types in order to
 * support non-blocking and asynchronous communication.
 *
 * Author: Thomas Djotio Ndié
 * Creation date: 2026-01-02
 */
@RestController
public class HealthController {

    /**
     * Health check endpoint.
     *
     * This method exposes a GET HTTP endpoint at "/health".
     * It is typically used to check whether the application is up and running.
     *
     * The method returns a Mono containing a Map with basic status information:
     * - "status": indicates the global state of the application
     * - "message": provides a human-readable description
     *
     * The use of Mono ensures that the response is handled in a non-blocking
     * reactive manner, which is consistent with Spring WebFlux principles.
     *
     * @return a Mono emitting a Map<String, String> representing the health status
     */
    @GetMapping("/health")
    public Mono<Map<String, String>> health() {
        return Mono.just(
                Map.of(
                        "status", "UP",
                        "message", "Application is running"));
    }
}
