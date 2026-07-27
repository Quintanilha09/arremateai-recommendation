package com.arremateai.recommendation.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Bean de infraestrutura para chamadas cross-service (E30-H3): o recommendation
 * consome o read-model de lotes do {@code arremateai-property-catalog} via HTTP,
 * mesmo padrao ja usado por {@code VisualizacaoService} no property-catalog.
 *
 * <p>Timeouts curtos evitam que uma lentidao/queda do catalogo trave threads do
 * recommendation indefinidamente.</p>
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
