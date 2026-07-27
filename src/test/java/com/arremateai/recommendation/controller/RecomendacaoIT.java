package com.arremateai.recommendation.controller;

import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import com.arremateai.recommendation.integration.AbstractIntegrationTest;
import com.arremateai.recommendation.repository.EventoComportamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Cobre {@code GET /api/recomendacoes/para-voce} e {@code /vistos-recentemente}
 * de ponta a ponta (E30-H3, ADR-009 Fase 1): filtro de gateway, controller,
 * services, persistencia real de eventos via Postgres (Testcontainers) e o
 * cliente HTTP do property-catalog mockado via {@link MockRestServiceServer}.
 */
class RecomendacaoIT extends AbstractIntegrationTest {

    private static final String GATEWAY_SECRET = "teste-gateway-secret-e30h2";
    private static final String CATALOG_BASE_URL = "http://property-catalog.invalid.test";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventoComportamentoRepository eventoComportamentoRepository;

    @Autowired
    private RestTemplate propertyCatalogRestTemplate;

    private MockRestServiceServer mockCatalog;

    @BeforeEach
    void prepararMockCatalog() {
        mockCatalog = MockRestServiceServer.bindTo(propertyCatalogRestTemplate).ignoreExpectOrder(true).build();
    }

    @Test
    @DisplayName("para-voce: deve retornar 401 quando X-User-Id está ausente")
    void paraVoceDeveRetornar401SemUserId() {
        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/recomendacoes/para-voce", HttpMethod.GET,
                new HttpEntity<>(montarHeaders(null)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("para-voce: deve retornar 401 quando X-User-Id não é um UUID válido")
    void paraVoceDeveRetornar401ComUserIdInvalido() {
        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/recomendacoes/para-voce", HttpMethod.GET,
                new HttpEntity<>(montarHeaders("nao-e-um-uuid")), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("vistos-recentemente: deve retornar 401 quando X-User-Id está ausente")
    void vistosRecentementeDeveRetornar401SemUserId() {
        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/recomendacoes/vistos-recentemente", HttpMethod.GET,
                new HttpEntity<>(montarHeaders(null)), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("para-voce: cold-start (usuário sem eventos) deve retornar o mix de vitrine, nunca vazio")
    void paraVoceDeveRetornarVitrineParaUsuarioSemEventos() {
        UUID userId = UUID.randomUUID();
        UUID loteVitrine = UUID.randomUUID();
        mockCatalog.expect(requestTo(startsWith(CATALOG_BASE_URL + "/api/lotes/vitrine")))
                .andRespond(withSuccess("[" + loteJson(loteVitrine, "IMOVEL") + "]", MediaType.APPLICATION_JSON));

        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/recomendacoes/para-voce", HttpMethod.GET,
                new HttpEntity<>(montarHeaders(userId.toString())), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(loteVitrine.toString());
    }

    @Test
    @DisplayName("para-voce: usuário com perfil implícito deve receber busca personalizada excluindo o já visto")
    void paraVoceDeveExcluirLoteJaVistoParaUsuarioComPerfil() {
        UUID userId = UUID.randomUUID();
        UUID loteVisto = UUID.randomUUID();
        UUID loteRecomendado = UUID.randomUUID();

        registrarEventoView(userId, loteVisto);

        mockCatalog.expect(requestTo(startsWith(CATALOG_BASE_URL + "/api/lotes/" + loteVisto)))
                .andRespond(withSuccess(loteJson(loteVisto, "IMOVEL"), MediaType.APPLICATION_JSON));
        mockCatalog.expect(requestTo(startsWith(CATALOG_BASE_URL + "/api/lotes?")))
                .andRespond(withSuccess(paginaComContent(loteVisto, loteRecomendado), MediaType.APPLICATION_JSON));

        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/recomendacoes/para-voce?limite=1", HttpMethod.GET,
                new HttpEntity<>(montarHeaders(userId.toString())), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(loteRecomendado.toString());
        assertThat(resposta.getBody()).doesNotContain(loteVisto.toString());
    }

    @Test
    @DisplayName("vistos-recentemente: deve retornar lista vazia para usuário sem eventos VIEW")
    void vistosRecentementeDeveRetornarListaVaziaSemEventos() {
        UUID userId = UUID.randomUUID();

        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/recomendacoes/vistos-recentemente", HttpMethod.GET,
                new HttpEntity<>(montarHeaders(userId.toString())), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).isEqualTo("[]");
    }

    @Test
    @DisplayName("vistos-recentemente: deve reidratar os últimos lotes vistos e ignorar falha no catálogo")
    void vistosRecentementeDeveReidratarUltimosLotesVistos() {
        UUID userId = UUID.randomUUID();
        UUID loteOk = UUID.randomUUID();
        UUID loteComFalha = UUID.randomUUID();

        registrarEventoView(userId, loteComFalha);
        registrarEventoView(userId, loteOk);

        mockCatalog.expect(requestTo(startsWith(CATALOG_BASE_URL + "/api/lotes/" + loteOk)))
                .andRespond(withSuccess(loteJson(loteOk, "VEICULO"), MediaType.APPLICATION_JSON));
        mockCatalog.expect(requestTo(startsWith(CATALOG_BASE_URL + "/api/lotes/" + loteComFalha)))
                .andRespond(withServerError());

        ResponseEntity<String> resposta = restTemplate.exchange(
                "/api/recomendacoes/vistos-recentemente", HttpMethod.GET,
                new HttpEntity<>(montarHeaders(userId.toString())), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resposta.getBody()).contains(loteOk.toString());
        assertThat(resposta.getBody()).doesNotContain(loteComFalha.toString());
    }

    private void registrarEventoView(UUID userId, UUID loteId) {
        EventoComportamento evento = new EventoComportamento();
        evento.setAnonId("anon-" + UUID.randomUUID());
        evento.setUserId(userId);
        evento.setLoteId(loteId);
        evento.setEventType(TipoEvento.VIEW);
        evento.setOccurredAt(LocalDateTime.now());
        eventoComportamentoRepository.save(evento);
    }

    private HttpHeaders montarHeaders(String userIdHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Gateway-Auth", GATEWAY_SECRET);
        if (userIdHeader != null) {
            headers.set("X-User-Id", userIdHeader);
        }
        return headers;
    }

    private String loteJson(UUID id, String categoria) {
        return """
                {"id":"%s","categoria":"%s","subcategoria":"sub","titulo":"Lote teste",
                "valorAvaliacao":150000.00,"lanceAtual":null,"uf":"SP","cidade":"Cidade Teste",
                "dataEncerramento":"2026-12-01T10:00:00","status":"ATIVO","destaque":false,
                "fotosUrls":["http://foto.teste/1.jpg"]}
                """.formatted(id, categoria);
    }

    private String paginaComContent(UUID... ids) {
        StringBuilder content = new StringBuilder();
        for (UUID id : ids) {
            if (!content.isEmpty()) {
                content.append(",");
            }
            content.append(loteJson(id, "IMOVEL"));
        }
        return "{\"content\":[" + content + "],\"totalElements\":" + ids.length + "}";
    }
}
