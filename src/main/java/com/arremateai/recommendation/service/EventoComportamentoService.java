package com.arremateai.recommendation.service;

import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.dto.EventoComportamentoRequest;
import com.arremateai.recommendation.repository.EventoComportamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.UUID;

/**
 * Ingestao de eventos de comportamento (ADR-009, E30-H2).
 *
 * <p>Grava sempre — tabela append-only, sem update. Fire-and-forget: o
 * controller responde 202 independentemente de particularidades do evento.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EventoComportamentoService {

    private final EventoComportamentoRepository eventoComportamentoRepository;

    /**
     * Registra um evento de comportamento.
     *
     * <p><strong>Seguranca:</strong> {@code userIdHeader} deve vir SEMPRE do header
     * {@code X-User-Id} propagado pelo Gateway (nunca do corpo da requisicao). A
     * {@link EventoComportamentoRequest} nem sequer possui campo {@code userId},
     * entao um valor falso enviado no corpo e ignorado na desserializacao.</p>
     */
    @Transactional
    public void registrarEvento(EventoComportamentoRequest requisicao, String userIdHeader) {
        EventoComportamento evento = new EventoComportamento();
        evento.setAnonId(requisicao.anonId());
        evento.setLoteId(requisicao.loteId());
        evento.setCategoria(requisicao.categoria());
        evento.setEventType(requisicao.eventType());
        evento.setMetadata(requisicao.metadata() != null ? requisicao.metadata() : new HashMap<>());
        evento.setOccurredAt(requisicao.occurredAt() != null ? requisicao.occurredAt() : LocalDateTime.now());
        evento.setUserId(extrairUserId(userIdHeader));

        eventoComportamentoRepository.save(evento);

        log.debug("Evento de comportamento registrado: anonId={}, eventType={}, loteId={}, autenticado={}",
                requisicao.anonId(), requisicao.eventType(), requisicao.loteId(), evento.getUserId() != null);
    }

    private UUID extrairUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException ex) {
            log.warn("Header X-User-Id com valor invalido recebido — tratando evento como anonimo");
            return null;
        }
    }
}
