package com.arremateai.recommendation.dto;

import com.arremateai.recommendation.domain.TipoEvento;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Corpo da requisicao de ingestao de evento de comportamento (ADR-009).
 *
 * <p>Propositalmente NAO possui campo {@code userId}: a identidade do usuario
 * logado so pode vir do header {@code X-User-Id} propagado pelo Gateway, nunca
 * do corpo da requisicao (evita spoofing). Qualquer {@code userId} enviado no
 * JSON e silenciosamente ignorado pela desserializacao.</p>
 */
public record EventoComportamentoRequest(

        @NotBlank(message = "anonId é obrigatório")
        String anonId,

        UUID loteId,

        String categoria,

        @NotNull(message = "eventType é obrigatório")
        TipoEvento eventType,

        Map<String, Object> metadata,

        LocalDateTime occurredAt
) {
}
