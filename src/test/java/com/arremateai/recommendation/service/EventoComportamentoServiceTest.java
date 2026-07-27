package com.arremateai.recommendation.service;

import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import com.arremateai.recommendation.dto.EventoComportamentoRequest;
import com.arremateai.recommendation.repository.EventoComportamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("EventoComportamentoService — testes unitários")
class EventoComportamentoServiceTest {

    @Mock
    private EventoComportamentoRepository eventoComportamentoRepository;

    private EventoComportamentoService service;

    @BeforeEach
    void prepararCenario() {
        service = new EventoComportamentoService(eventoComportamentoRepository);
        when(eventoComportamentoRepository.save(any())).thenAnswer(invocacao -> invocacao.getArgument(0));
    }

    @Test
    @DisplayName("Deve gravar evento anônimo quando header X-User-Id está ausente")
    void deveGravarEventoAnonimoQuandoHeaderAusente() {
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-123", null, null, TipoEvento.VIEW, null, null);

        service.registrarEvento(requisicao, null);

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getAnonId()).isEqualTo("anon-123");
        assertThat(salvo.getUserId()).isNull();
        assertThat(salvo.getEventType()).isEqualTo(TipoEvento.VIEW);
    }

    @Test
    @DisplayName("Deve gravar evento anônimo quando header X-User-Id está em branco")
    void deveGravarEventoAnonimoQuandoHeaderEmBranco() {
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-456", null, null, TipoEvento.CLICK, null, null);

        service.registrarEvento(requisicao, "   ");

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getUserId()).isNull();
    }

    @Test
    @DisplayName("Deve gravar userId a partir do header X-User-Id quando presente e válido")
    void deveGravarUserIdDoHeaderQuandoValido() {
        UUID userId = UUID.randomUUID();
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-789", UUID.randomUUID(), "veiculo", TipoEvento.FAVORITE, null, null);

        service.registrarEvento(requisicao, userId.toString());

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getUserId()).isEqualTo(userId);
        assertThat(salvo.getCategoria()).isEqualTo("veiculo");
    }

    @Test
    @DisplayName("Deve tratar como anônimo quando header X-User-Id não é um UUID válido")
    void deveTratarComoAnonimoQuandoHeaderInvalido() {
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-000", null, null, TipoEvento.SEARCH, null, null);

        service.registrarEvento(requisicao, "nao-e-um-uuid");

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getUserId()).isNull();
    }

    @Test
    @DisplayName("Deve usar occurredAt informado na requisição quando presente")
    void deveUsarOccurredAtInformado() {
        LocalDateTime occurredAt = LocalDateTime.now().minusHours(2);
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-001", null, null, TipoEvento.DWELL, null, occurredAt);

        service.registrarEvento(requisicao, null);

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getOccurredAt()).isEqualTo(occurredAt);
    }

    @Test
    @DisplayName("Deve usar now() como occurredAt quando ausente na requisição")
    void deveUsarNowQuandoOccurredAtAusente() {
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-002", null, null, TipoEvento.VIEW, null, null);

        LocalDateTime antes = LocalDateTime.now();
        service.registrarEvento(requisicao, null);
        LocalDateTime depois = LocalDateTime.now();

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getOccurredAt()).isBetween(antes.minusSeconds(1), depois.plusSeconds(1));
    }

    @Test
    @DisplayName("Deve usar metadata vazio quando ausente na requisição")
    void deveUsarMetadataVazioQuandoAusente() {
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-003", null, null, TipoEvento.CLICK, null, null);

        service.registrarEvento(requisicao, null);

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getMetadata()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("Deve preservar metadata informado na requisição")
    void devePreservarMetadataInformado() {
        Map<String, Object> metadata = Map.of("termo", "leilao carro");
        EventoComportamentoRequest requisicao = new EventoComportamentoRequest(
                "anon-004", null, null, TipoEvento.SEARCH, metadata, null);

        service.registrarEvento(requisicao, null);

        EventoComportamento salvo = capturarEventoSalvo();
        assertThat(salvo.getMetadata()).isEqualTo(metadata);
    }

    private EventoComportamento capturarEventoSalvo() {
        ArgumentCaptor<EventoComportamento> captor = ArgumentCaptor.forClass(EventoComportamento.class);
        verify(eventoComportamentoRepository).save(captor.capture());
        return captor.getValue();
    }
}
