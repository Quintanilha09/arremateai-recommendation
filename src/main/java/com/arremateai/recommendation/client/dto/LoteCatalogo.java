package com.arremateai.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Representacao (client-side) do {@code LoteResponse} do {@code arremateai-property-catalog}
 * (E27-H1/E27-H2, ADR-008), consumida via HTTP pelo {@code arremateai-recommendation}.
 *
 * <p>Propositalmente NAO reexporta o DTO do property-catalog: os servicos sao desacoplados
 * (Database-per-Service / API-per-Service) e este tipo carrega apenas os campos que a
 * recomendacao heuristica realmente usa (E30-H3). {@code ignoreUnknown = true} torna a
 * integracao tolerante a evolucao do contrato do catalogo (novos campos nao quebram o parse).</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoteCatalogo(
        UUID id,
        String categoria,
        String subcategoria,
        String titulo,
        BigDecimal valorAvaliacao,
        BigDecimal lanceAtual,
        String uf,
        String cidade,
        LocalDateTime dataEncerramento,
        String status,
        Boolean destaque,
        String[] fotosUrls
) {
}
