package com.arremateai.recommendation.controller;

import com.arremateai.recommendation.dto.EventoComportamentoRequest;
import com.arremateai.recommendation.service.EventoComportamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Ingestao de eventos de comportamento — stream que alimenta a recomendacao
 * heuristica (ADR-009, E30-H2).
 */
@RestController
@RequestMapping("/api/recomendacoes")
@RequiredArgsConstructor
public class EventoComportamentoController {

    private final EventoComportamentoService eventoComportamentoService;

    /**
     * Registra um evento de comportamento (VIEW, CLICK, SEARCH, FAVORITE, DWELL).
     *
     * <p>Funciona tanto para usuario anonimo quanto logado. Fire-and-forget:
     * sempre responde 202, mesmo sem garantia de leitura imediata pelo cliente.</p>
     */
    @PostMapping("/eventos")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void registrarEvento(@Valid @RequestBody EventoComportamentoRequest requisicao,
                                 @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        eventoComportamentoService.registrarEvento(requisicao, userIdHeader);
    }
}
