package com.arremateai.recommendation.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Perfil implicito do usuario (E30-H3, ADR-009 Fase 1) — agregado interno,
 * NAO exposto via REST. Derivado dos eventos VIEW recentes do usuario:
 * categoria e UF mais frequentes entre os lotes vistos, e a faixa de preco
 * observada (com folga), usados para buscar lotes "similares" no catalogo.
 *
 * @param categoriaMaisFrequente categoria dominante entre os lotes vistos; {@code null} sem sinal suficiente
 * @param ufMaisFrequente uf dominante entre os lotes vistos; {@code null} sem sinal suficiente
 * @param valorMinimo piso da faixa de preco observada (com folga); {@code null} sem sinal suficiente
 * @param valorMaximo teto da faixa de preco observada (com folga); {@code null} sem sinal suficiente
 * @param loteIdsVistosRecentemente todos os loteId distintos vistos na janela considerada — usado para
 *                                  excluir da recomendacao personalizada lotes que o usuario acabou de ver
 */
public record PerfilImplicitoUsuario(
        String categoriaMaisFrequente,
        String ufMaisFrequente,
        BigDecimal valorMinimo,
        BigDecimal valorMaximo,
        List<UUID> loteIdsVistosRecentemente
) {

    public static PerfilImplicitoUsuario vazio() {
        return new PerfilImplicitoUsuario(null, null, null, null, List.of());
    }

    /**
     * Sinal suficiente para montar uma busca personalizada no catalogo. Quando falso
     * (usuario sem eventos, ou eventos cujos lotes ja saíram do catalogo), o servico
     * de recomendacao deve cair para o mix de cold-start.
     */
    public boolean temSinalSuficiente() {
        return categoriaMaisFrequente != null;
    }
}
