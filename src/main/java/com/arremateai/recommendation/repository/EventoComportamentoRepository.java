package com.arremateai.recommendation.repository;

import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface EventoComportamentoRepository extends JpaRepository<EventoComportamento, UUID> {

    /**
     * Eventos de um tipo especifico do usuario, dentro de uma janela de tempo,
     * do mais recente para o mais antigo (E30-H3: base do perfil implicito).
     */
    List<EventoComportamento> findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
            UUID userId, TipoEvento eventType, LocalDateTime after, Pageable pageable);

    /**
     * Eventos de um tipo especifico do usuario, do mais recente para o mais antigo,
     * sem restricao de janela (E30-H3: base de "voce viu recentemente" — reflete
     * literalmente os ultimos VIEW, independente de idade).
     */
    List<EventoComportamento> findByUserIdAndEventTypeOrderByOccurredAtDesc(
            UUID userId, TipoEvento eventType, Pageable pageable);
}
