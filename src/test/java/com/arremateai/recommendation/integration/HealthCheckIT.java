package com.arremateai.recommendation.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova o scaffold end-to-end (E30-H1): contexto Spring sobe, Flyway migra
 * contra Postgres real (Testcontainers), e /actuator/health responde UP.
 */
class HealthCheckIT extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    @DisplayName("contexto sobe e /actuator/health responde 200 UP")
    void actuatorHealthUp() {
        ResponseEntity<String> resposta = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains("\"status\":\"UP\"");
    }
}
