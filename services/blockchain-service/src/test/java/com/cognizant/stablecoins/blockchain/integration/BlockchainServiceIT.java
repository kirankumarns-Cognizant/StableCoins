package com.cognizant.stablecoins.blockchain.integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: Spring context + PostgreSQL via Testcontainers + Flyway migrations.
 *
 * BLOCKED: Docker unavailable in this local Windows environment.
 * Enable this test when Docker Desktop is running and Testcontainers is configured.
 */
@Disabled("NOT_EXECUTED_BLOCKED_BY_DOCKER — Docker unavailable in local environment")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BlockchainServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("stablecoins")
            .withUsername("stablecoins")
            .withPassword("stablecoins");

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void contextLoads_flyway_runs_all_3_migrations() {
        // If context starts successfully, Flyway ran V1, V2, V3
        // health endpoint should return 200
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/health", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("ok");
    }

    @Test
    void health_endpoint_returns_200() {
        ResponseEntity<String> response = restTemplate.getForEntity(
            "http://localhost:" + port + "/health", String.class);
        assertThat(response.getStatusCode().value()).isEqualTo(200);
    }
}
