package com.arremateai.recommendation.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Espelha o suficiente do {@code Page<LoteResponse>} JSON retornado por
 * {@code GET /api/lotes} no property-catalog para extrair o conteudo paginado.
 * Demais campos da pagina (totalElements, sort, etc.) sao ignorados — a
 * recomendacao heuristica so precisa da lista de lotes retornada.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaginaLotesCatalogo(List<LoteCatalogo> content) {
}
