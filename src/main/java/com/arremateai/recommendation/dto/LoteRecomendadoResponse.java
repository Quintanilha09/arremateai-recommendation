package com.arremateai.recommendation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO de resposta dos endpoints de recomendacao (E30-H3, ADR-009 Fase 1).
 *
 * <p>Propositalmente proprio do recommendation-service — NAO reexporta o
 * {@code LoteResponse} do property-catalog, mantendo os servicos desacoplados.
 * Carrega apenas os campos que o card de lote no frontend precisa exibir.</p>
 *
 * @param valor preco de exibicao do card: lance atual quando ha leilao em andamento,
 *              caso contrario o valor de avaliacao (mais relevante para o usuario
 *              decidir se vale a pena abrir o card)
 */
public record LoteRecomendadoResponse(
        UUID id,
        String titulo,
        String categoria,
        String subcategoria,
        BigDecimal valor,
        String status,
        LocalDateTime dataEncerramento,
        String uf,
        String cidade,
        String[] fotosUrls,
        boolean destaque
) {
}
