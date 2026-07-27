package com.arremateai.recommendation.service;

import com.arremateai.recommendation.client.PropertyCatalogClient;
import com.arremateai.recommendation.client.dto.LoteCatalogo;
import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import com.arremateai.recommendation.dto.PerfilImplicitoUsuario;
import com.arremateai.recommendation.repository.EventoComportamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PerfilImplicitoService — testes unitários")
class PerfilImplicitoServiceTest {

    @Mock
    private EventoComportamentoRepository eventoComportamentoRepository;

    @Mock
    private PropertyCatalogClient propertyCatalogClient;

    private PerfilImplicitoService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void prepararCenario() {
        service = new PerfilImplicitoService(eventoComportamentoRepository, propertyCatalogClient);
    }

    @Test
    @DisplayName("Deve retornar perfil vazio (cold-start) quando usuário não tem eventos VIEW")
    void deveRetornarPerfilVazioQuandoSemEventos() {
        when(eventoComportamentoRepository.findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any(), any())).thenReturn(List.of());

        PerfilImplicitoUsuario perfil = service.construir(userId);

        assertThat(perfil.temSinalSuficiente()).isFalse();
        assertThat(perfil.categoriaMaisFrequente()).isNull();
        assertThat(perfil.loteIdsVistosRecentemente()).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar perfil sem sinal quando nenhum lote visto é resolvido no catálogo")
    void deveRetornarPerfilSemSinalQuandoLotesNaoResolvidos() {
        UUID loteId = UUID.randomUUID();
        EventoComportamento evento = eventoView(loteId);
        when(eventoComportamentoRepository.findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any(), any())).thenReturn(List.of(evento));
        when(propertyCatalogClient.buscarPorId(loteId)).thenReturn(Optional.empty());

        PerfilImplicitoUsuario perfil = service.construir(userId);

        assertThat(perfil.temSinalSuficiente()).isFalse();
        assertThat(perfil.loteIdsVistosRecentemente()).containsExactly(loteId);
    }

    @Test
    @DisplayName("Deve identificar categoria e UF mais frequentes entre os lotes vistos")
    void deveIdentificarCategoriaEUfMaisFrequentes() {
        UUID lote1 = UUID.randomUUID();
        UUID lote2 = UUID.randomUUID();
        UUID lote3 = UUID.randomUUID();
        when(eventoComportamentoRepository.findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any(), any()))
                .thenReturn(List.of(eventoView(lote1), eventoView(lote2), eventoView(lote3)));
        when(propertyCatalogClient.buscarPorId(lote1))
                .thenReturn(Optional.of(lote("IMOVEL", "SP", BigDecimal.valueOf(100_000))));
        when(propertyCatalogClient.buscarPorId(lote2))
                .thenReturn(Optional.of(lote("IMOVEL", "SP", BigDecimal.valueOf(200_000))));
        when(propertyCatalogClient.buscarPorId(lote3))
                .thenReturn(Optional.of(lote("VEICULO", "RJ", BigDecimal.valueOf(50_000))));

        PerfilImplicitoUsuario perfil = service.construir(userId);

        assertThat(perfil.temSinalSuficiente()).isTrue();
        assertThat(perfil.categoriaMaisFrequente()).isEqualTo("IMOVEL");
        assertThat(perfil.ufMaisFrequente()).isEqualTo("SP");
    }

    @Test
    @DisplayName("Deve calcular faixa de preço com folga de 20% para baixo e para cima")
    void deveCalcularFaixaDePrecoComFolga() {
        UUID lote1 = UUID.randomUUID();
        UUID lote2 = UUID.randomUUID();
        when(eventoComportamentoRepository.findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any(), any()))
                .thenReturn(List.of(eventoView(lote1), eventoView(lote2)));
        when(propertyCatalogClient.buscarPorId(lote1))
                .thenReturn(Optional.of(lote("IMOVEL", "SP", BigDecimal.valueOf(100_000))));
        when(propertyCatalogClient.buscarPorId(lote2))
                .thenReturn(Optional.of(lote("IMOVEL", "SP", BigDecimal.valueOf(200_000))));

        PerfilImplicitoUsuario perfil = service.construir(userId);

        assertThat(perfil.valorMinimo()).isEqualByComparingTo(BigDecimal.valueOf(80_000));
        assertThat(perfil.valorMaximo()).isEqualByComparingTo(BigDecimal.valueOf(240_000));
    }

    @Test
    @DisplayName("Deve usar lanceAtual em vez de valorAvaliacao quando presente para calcular faixa de preço")
    void deveUsarLanceAtualQuandoPresente() {
        UUID loteId = UUID.randomUUID();
        when(eventoComportamentoRepository.findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any(), any()))
                .thenReturn(List.of(eventoView(loteId)));
        LoteCatalogo loteComLance = new LoteCatalogo(loteId, "IMOVEL", "apto", "titulo",
                BigDecimal.valueOf(100_000), BigDecimal.valueOf(150_000), "SP", "SP", null, "ATIVO", false, null);
        when(propertyCatalogClient.buscarPorId(loteId)).thenReturn(Optional.of(loteComLance));

        PerfilImplicitoUsuario perfil = service.construir(userId);

        assertThat(perfil.valorMinimo()).isEqualByComparingTo(BigDecimal.valueOf(120_000));
        assertThat(perfil.valorMaximo()).isEqualByComparingTo(BigDecimal.valueOf(180_000));
    }

    @Test
    @DisplayName("Deve deduplicar loteId repetidos entre múltiplos eventos VIEW do mesmo lote")
    void deveDeduplicarLoteIdRepetidos() {
        UUID loteId = UUID.randomUUID();
        when(eventoComportamentoRepository.findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any(), any()))
                .thenReturn(List.of(eventoView(loteId), eventoView(loteId), eventoView(loteId)));
        when(propertyCatalogClient.buscarPorId(loteId))
                .thenReturn(Optional.of(lote("IMOVEL", "SP", BigDecimal.valueOf(100_000))));

        PerfilImplicitoUsuario perfil = service.construir(userId);

        assertThat(perfil.loteIdsVistosRecentemente()).containsExactly(loteId);
    }

    @Test
    @DisplayName("Deve ignorar eventos sem loteId (ex.: SEARCH) ao montar a lista de lotes vistos")
    void deveIgnorarEventosSemLoteId() {
        EventoComportamento eventoSemLote = new EventoComportamento();
        eventoSemLote.setEventType(TipoEvento.VIEW);
        eventoSemLote.setLoteId(null);
        eventoSemLote.setOccurredAt(LocalDateTime.now());

        when(eventoComportamentoRepository.findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any(), any())).thenReturn(List.of(eventoSemLote));

        PerfilImplicitoUsuario perfil = service.construir(userId);

        assertThat(perfil.temSinalSuficiente()).isFalse();
        assertThat(perfil.loteIdsVistosRecentemente()).isEmpty();
    }

    private EventoComportamento eventoView(UUID loteId) {
        EventoComportamento evento = new EventoComportamento();
        evento.setEventType(TipoEvento.VIEW);
        evento.setLoteId(loteId);
        evento.setOccurredAt(LocalDateTime.now());
        return evento;
    }

    private LoteCatalogo lote(String categoria, String uf, BigDecimal valorAvaliacao) {
        return new LoteCatalogo(UUID.randomUUID(), categoria, "sub", "titulo",
                valorAvaliacao, null, uf, "cidade", null, "ATIVO", false, null);
    }
}
