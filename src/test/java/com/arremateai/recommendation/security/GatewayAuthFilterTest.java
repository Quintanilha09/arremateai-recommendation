package com.arremateai.recommendation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("GatewayAuthFilter — testes unitários")
class GatewayAuthFilterTest {

    private static final String SECRET = "segredo-correto";

    private GatewayAuthFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void prepararCenario() {
        filter = new GatewayAuthFilter();
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Deve ignorar requisições do actuator")
    void deveIgnorarPathActuator() {
        assertThat(filter.shouldNotFilter(requisicao("GET", "/actuator/health"))).isTrue();
    }

    @Test
    @DisplayName("Não deve ignorar o endpoint de ingestão de eventos")
    void naoDeveIgnorarEndpointDeEventos() {
        assertThat(filter.shouldNotFilter(requisicao("POST", "/api/recomendacoes/eventos"))).isFalse();
    }

    @Test
    @DisplayName("Deve retornar 503 quando o secret não está configurado")
    void deveRetornar503QuandoSecretAusente() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "gatewaySharedSecret", "");

        MockHttpServletRequest request = requisicao("POST", "/api/recomendacoes/eventos");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Deve retornar 503 quando o secret é null")
    void deveRetornar503QuandoSecretNull() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "gatewaySharedSecret", null);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(requisicao("POST", "/api/recomendacoes/eventos"), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(503);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Deve retornar 401 quando o header X-Gateway-Auth está ausente")
    void deveRetornar401QuandoHeaderAusente() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "gatewaySharedSecret", SECRET);

        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(requisicao("POST", "/api/recomendacoes/eventos"), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Deve retornar 401 quando o header X-Gateway-Auth está incorreto")
    void deveRetornar401QuandoHeaderIncorreto() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "gatewaySharedSecret", SECRET);

        MockHttpServletRequest request = requisicao("POST", "/api/recomendacoes/eventos");
        request.addHeader("X-Gateway-Auth", "segredo-errado");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(filterChain);
    }

    @Test
    @DisplayName("Deve liberar a requisição quando o secret presente confere com o header")
    void devePassarQuandoSecretConfere() throws ServletException, IOException {
        ReflectionTestUtils.setField(filter, "gatewaySharedSecret", SECRET);

        MockHttpServletRequest request = requisicao("POST", "/api/recomendacoes/eventos");
        request.addHeader("X-Gateway-Auth", SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(filterChain).doFilter(request, response);
    }

    private MockHttpServletRequest requisicao(String metodo, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(metodo, path);
        request.setServletPath(path);
        return request;
    }
}
