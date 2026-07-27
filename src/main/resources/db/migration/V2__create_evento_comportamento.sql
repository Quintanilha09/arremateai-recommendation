-- V2__create_evento_comportamento.sql
-- E30-H2 (ADR-009): stream de eventos de comportamento (ingestao).
-- Tabela append-only: somente INSERT, nunca UPDATE. Materia-prima das
-- heuristicas de recomendacao (E30-H3/H4) e das futuras fases de CF/ML.

CREATE TABLE IF NOT EXISTS evento_comportamento (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID,
    anon_id      VARCHAR(100) NOT NULL,
    lote_id      UUID,
    categoria    VARCHAR(30),
    event_type   VARCHAR(20)  NOT NULL,
    metadata     JSONB,
    occurred_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- anon_id: todo evento tem um (mesmo de usuario logado) — stitching anonimo->usuario futuro.
CREATE INDEX IF NOT EXISTS idx_evento_anon_id ON evento_comportamento(anon_id);
-- user_id: perfil implicito do usuario logado (heuristica "recomendados para voce").
CREATE INDEX IF NOT EXISTS idx_evento_user_id ON evento_comportamento(user_id);
-- (lote_id, event_type): contagem de VIEW por lote ("mais vistos"), co-view futuro (Fase 2).
CREATE INDEX IF NOT EXISTS idx_evento_lote_event_type ON evento_comportamento(lote_id, event_type);
-- occurred_at: janelas de tempo recentes (heuristicas + batch de similaridade).
CREATE INDEX IF NOT EXISTS idx_evento_occurred_at ON evento_comportamento(occurred_at);
