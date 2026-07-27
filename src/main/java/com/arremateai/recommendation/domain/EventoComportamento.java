package com.arremateai.recommendation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Evento de comportamento do usuario — view, click, search, favorite, dwell (ADR-009).
 *
 * <p>Tabela append-only: nunca ha UPDATE, apenas INSERT. Todo evento carrega um
 * {@code anonId} (mesmo de usuario logado), permitindo o stitching anonimo->usuario
 * em fases futuras. {@code userId} so e preenchido a partir do header {@code X-User-Id}
 * propagado pelo Gateway — nunca a partir do corpo da requisicao.</p>
 */
@Entity
@Table(name = "evento_comportamento", indexes = {
        @Index(name = "idx_evento_anon_id", columnList = "anon_id"),
        @Index(name = "idx_evento_user_id", columnList = "user_id"),
        @Index(name = "idx_evento_lote_event_type", columnList = "lote_id, event_type"),
        @Index(name = "idx_evento_occurred_at", columnList = "occurred_at")
})
@Getter @Setter @NoArgsConstructor
public class EventoComportamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "anon_id", nullable = false, length = 100)
    private String anonId;

    @Column(name = "lote_id")
    private UUID loteId;

    @Column(length = 30)
    private String categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private TipoEvento eventType;

    /** Dados adicionais do evento (ex.: termo de busca, filtros, tempo em pagina). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata = new HashMap<>();

    /** Momento em que o evento ocorreu no cliente (pode divergir de createdAt). */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
