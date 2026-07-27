package com.arremateai.recommendation.repository;

import com.arremateai.recommendation.domain.EventoComportamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface EventoComportamentoRepository extends JpaRepository<EventoComportamento, UUID> {
}
