package com.arremateai.recommendation.controller;

import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import com.arremateai.recommendation.integration.AbstractIntegrationTest;
import com.arremateai.recommendation.repository.EventoComportamentoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cobre o endpoint {@code POST /api/recomendacoes/eventos} de ponta a ponta
 * (filtro de gateway + controller + service + persistencia real via Postgres),
 * incluindo os testes de seguranca explicitos da historia E30-H2: userId
 * NUNCA pode vir do corpo da requisicao, apenas do header X-User-Id.
 */
class EventoComportamentoIngestaoIT extends AbstractIntegrationTest {

    private static final String GATEWAY_SECRET = "teste-gateway-secret-e30h2";
    private static final String ENDPOINT = "/api/recomendacoes/eventos";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private EventoComportamentoRepository eventoComportamentoRepository;

    @Test
    @DisplayName("Deve retornar 401 quando X-Gateway-Auth está ausente")
    void deveRetornar401QuandoGatewayAuthAusente() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        String corpo = """
                {"anonId":"anon-sem-gateway-auth","eventType":"VIEW"}
                """;

        ResponseEntity<String> resposta = restTemplate.postForEntity(
                ENDPOINT, new HttpEntity<>(corpo, headers), String.class);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Deve gravar evento anônimo com 202 quando não há X-User-Id")
    void deveGravarEventoAnonimoComoAceito() {
        String anonId = "anon-" + UUID.randomUUID();
        String corpo = """
                {"anonId":"%s","eventType":"VIEW"}
                """.formatted(anonId);

        ResponseEntity<Void> resposta = enviarEvento(corpo, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        EventoComportamento salvo = buscarUnicoPorAnonId(anonId);
        assertThat(salvo.getUserId()).isNull();
        assertThat(salvo.getEventType()).isEqualTo(TipoEvento.VIEW);
        assertThat(salvo.getCreatedAt()).isNotNull();
        assertThat(salvo.getOccurredAt()).isNotNull();
    }

    @Test
    @DisplayName("Deve gravar userId do header X-User-Id quando usuário está logado")
    void deveGravarUserIdDoHeaderQuandoLogado() {
        String anonId = "anon-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String corpo = """
                {"anonId":"%s","eventType":"CLICK","loteId":"%s","categoria":"veiculo"}
                """.formatted(anonId, UUID.randomUUID());

        ResponseEntity<Void> resposta = enviarEvento(corpo, userId.toString());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        EventoComportamento salvo = buscarUnicoPorAnonId(anonId);
        assertThat(salvo.getUserId()).isEqualTo(userId);
        assertThat(salvo.getCategoria()).isEqualTo("veiculo");
    }

    @Test
    @DisplayName("SEGURANÇA: userId falso no corpo é ignorado quando não há X-User-Id (evento fica anônimo)")
    void deveIgnorarUserIdFalsoNoCorpoQuandoSemHeader() {
        String anonId = "anon-" + UUID.randomUUID();
        String userIdFalso = UUID.randomUUID().toString();
        String corpo = """
                {"anonId":"%s","eventType":"VIEW","userId":"%s"}
                """.formatted(anonId, userIdFalso);

        ResponseEntity<Void> resposta = enviarEvento(corpo, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        EventoComportamento salvo = buscarUnicoPorAnonId(anonId);
        assertThat(salvo.getUserId()).isNull();
    }

    @Test
    @DisplayName("SEGURANÇA: userId falso no corpo é ignorado — grava o do header X-User-Id, não o do body")
    void deveIgnorarUserIdFalsoNoCorpoEUsarOHeader() {
        String anonId = "anon-" + UUID.randomUUID();
        UUID userIdReal = UUID.randomUUID();
        String userIdFalso = UUID.randomUUID().toString();
        String corpo = """
                {"anonId":"%s","eventType":"FAVORITE","userId":"%s"}
                """.formatted(anonId, userIdFalso);

        ResponseEntity<Void> resposta = enviarEvento(corpo, userIdReal.toString());

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        EventoComportamento salvo = buscarUnicoPorAnonId(anonId);
        assertThat(salvo.getUserId()).isEqualTo(userIdReal);
        assertThat(salvo.getUserId()).isNotEqualTo(UUID.fromString(userIdFalso));
    }

    @Test
    @DisplayName("Deve retornar 400 quando eventType está ausente")
    void deveRetornar400QuandoEventTypeAusente() {
        String corpo = """
                {"anonId":"anon-sem-event-type"}
                """;

        ResponseEntity<String> resposta = enviarEventoComResposta(corpo, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deve retornar 400 quando anonId está ausente")
    void deveRetornar400QuandoAnonIdAusente() {
        String corpo = """
                {"eventType":"VIEW"}
                """;

        ResponseEntity<String> resposta = enviarEventoComResposta(corpo, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deve retornar 400 quando anonId está em branco")
    void deveRetornar400QuandoAnonIdEmBranco() {
        String corpo = """
                {"anonId":"   ","eventType":"VIEW"}
                """;

        ResponseEntity<String> resposta = enviarEventoComResposta(corpo, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deve retornar 400 quando eventType é um valor de enum desconhecido")
    void deveRetornar400QuandoEventTypeDesconhecido() {
        String corpo = """
                {"anonId":"anon-evento-invalido","eventType":"NAO_EXISTE"}
                """;

        ResponseEntity<String> resposta = enviarEventoComResposta(corpo, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("Deve persistir metadata JSONB corretamente")
    void devePersistirMetadataJsonb() {
        String anonId = "anon-" + UUID.randomUUID();
        String corpo = """
                {"anonId":"%s","eventType":"SEARCH","metadata":{"termo":"leilao carro","pagina":2}}
                """.formatted(anonId);

        ResponseEntity<Void> resposta = enviarEvento(corpo, null);

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        EventoComportamento salvo = buscarUnicoPorAnonId(anonId);
        assertThat(salvo.getMetadata()).containsEntry("termo", "leilao carro");
    }

    @Test
    @DisplayName("Deve tratar X-User-Id inválido (não-UUID) como anônimo, sem quebrar a requisição")
    void deveTratarXUserIdInvalidoComoAnonimo() {
        String anonId = "anon-" + UUID.randomUUID();
        String corpo = """
                {"anonId":"%s","eventType":"VIEW"}
                """.formatted(anonId);

        ResponseEntity<Void> resposta = enviarEvento(corpo, "nao-e-um-uuid-valido");

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        EventoComportamento salvo = buscarUnicoPorAnonId(anonId);
        assertThat(salvo.getUserId()).isNull();
    }

    private ResponseEntity<Void> enviarEvento(String corpoJson, String userIdHeader) {
        HttpHeaders headers = montarHeaders(userIdHeader);
        return restTemplate.postForEntity(ENDPOINT, new HttpEntity<>(corpoJson, headers), Void.class);
    }

    private ResponseEntity<String> enviarEventoComResposta(String corpoJson, String userIdHeader) {
        HttpHeaders headers = montarHeaders(userIdHeader);
        return restTemplate.postForEntity(ENDPOINT, new HttpEntity<>(corpoJson, headers), String.class);
    }

    private HttpHeaders montarHeaders(String userIdHeader) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Gateway-Auth", GATEWAY_SECRET);
        if (userIdHeader != null) {
            headers.set("X-User-Id", userIdHeader);
        }
        return headers;
    }

    private EventoComportamento buscarUnicoPorAnonId(String anonId) {
        List<EventoComportamento> encontrados = eventoComportamentoRepository.findAll().stream()
                .filter(evento -> anonId.equals(evento.getAnonId()))
                .toList();
        assertThat(encontrados).hasSize(1);
        return encontrados.get(0);
    }
}
