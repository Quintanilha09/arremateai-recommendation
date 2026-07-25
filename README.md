# arremateai-recommendation

Serviço de recomendação do ArremateAI — heurísticas de vitrine (Fase 1) e,
futuramente, filtragem colaborativa/ML (Fase 2+), conforme
[ADR-009](https://github.com/Quintanilha09/arremateai-docs/blob/main/adr/ADR-009-recomendacao-e-eventos-comportamento.md).

Parte da iniciativa **Vitrine Multi-Categoria + Recomendação** (épico E30).

- Stack: Java 17, Spring Boot 3.5.3, PostgreSQL (Database-per-Service), Flyway.
- Porta local: `8089`.
- Modo local-only (ver `arremateai-docs/.claude/agents/_REGRA_LOCAL_ONLY.md`).

Consulte `arremateai-docs/.planning/PLANO-VITRINE-MULTICATEGORIA.md` para o
plano completo e o grafo de dependências do épico E30.
