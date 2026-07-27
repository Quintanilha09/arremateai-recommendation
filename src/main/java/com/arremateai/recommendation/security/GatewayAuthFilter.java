package com.arremateai.recommendation.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Autenticacao mutua gateway -> downstream conforme ADR-001, espelhando
 * exatamente o padrao do {@code arremateai-property-catalog} (pendencia
 * apontada pela auditoria de seguranca de E30-H1).
 *
 * <p>Skip: apenas {@code /actuator/*}. Todos os demais endpoints (incluindo
 * a ingestao de eventos, que aceita tanto usuario anonimo quanto logado)
 * exigem o header {@code X-Gateway-Auth} valido.</p>
 *
 * <p>Fail-secure: se {@code GATEWAY_SHARED_SECRET} nao estiver configurado,
 * requisicoes nao-skip retornam 503.</p>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GatewayAuthFilter extends OncePerRequestFilter {

    @Value("${app.gateway.secret:}")
    private String gatewaySharedSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getServletPath().startsWith("/actuator/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (gatewaySharedSecret == null || gatewaySharedSecret.isBlank()) {
            log.warn("GATEWAY_SHARED_SECRET nao configurado — rejeitando requisicao para {}", request.getServletPath());
            writeError(response, 503, "Servico indisponivel — configuracao ausente");
            return;
        }

        String received = request.getHeader("X-Gateway-Auth");
        if (received == null || !constantTimeEquals(gatewaySharedSecret, received)) {
            log.warn("Requisicao rejeitada — X-Gateway-Auth invalido ou ausente para: {}", request.getServletPath());
            writeError(response, 401, "Acesso nao autorizado");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean constantTimeEquals(String expected, String received) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] receivedBytes = received.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, receivedBytes);
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
