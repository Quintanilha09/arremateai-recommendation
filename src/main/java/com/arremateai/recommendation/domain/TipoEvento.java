package com.arremateai.recommendation.domain;

/**
 * Tipos de evento de comportamento coletados no cliente (ADR-009).
 *
 * <p>Materia-prima das heuristicas de recomendacao (E30-H3/H4) e das futuras
 * fases de filtragem colaborativa/ML.</p>
 */
public enum TipoEvento {
    VIEW,
    CLICK,
    SEARCH,
    FAVORITE,
    DWELL
}
