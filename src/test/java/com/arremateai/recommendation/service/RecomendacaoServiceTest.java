package com.arremateai.recommendation.service;

import com.arremateai.recommendation.client.PropertyCatalogClient;
import com.arremateai.recommendation.client.dto.LoteCatalogo;
import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import com.arremateai.recommendation.dto.LoteRecomendadoResponse;
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
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RecomendacaoService — testes unitários")
class RecomendacaoServiceTest {

    @Mock
    private PerfilImplicitoService perfilImplicitoService;

    @Mock
    private PropertyCatalogClient propertyCatalogClient;

    @Mock
    private EventoComportamentoRepository eventoComportamentoRepository;

    private RecomendacaoService service;
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void prepararCenario() {
        service = new RecomendacaoService(perfilImplicitoService, propertyCatalogClient, eventoComportamentoRepository);
    }

    @Test
    @DisplayName("Cold-start: usuário sem sinal deve receber o mix de vitrine, nunca lista vazia")
    void deveUsarVitrineQuandoUsuarioSemSinal() {
        when(perfilImplicitoService.construir(userId)).thenReturn(PerfilImplicitoUsuario.vazio());
        when(propertyCatalogClient.vitrine(anyInt())).thenReturn(List.of(
                lote("IMOVEL", BigDecimal.valueOf(100_000)), lote("VEICULO", BigDecimal.valueOf(50_000))));

        List<LoteRecomendadoResponse> resultado = service.paraVoce(userId, 12);

        assertThat(resultado).hasSize(2);
        verify(propertyCatalogClient, never()).listar(any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    @DisplayName("Usuário com perfil deve receber busca personalizada excluindo lotes já vistos")
    void deveExcluirLotesJaVistosNaBuscaPersonalizada() {
        UUID loteVisto = UUID.randomUUID();
        LoteCatalogo candidatoValido = lote("IMOVEL", BigDecimal.valueOf(150_000));
        LoteCatalogo candidatoJaVisto = new LoteCatalogo(loteVisto, "IMOVEL", "sub", "titulo",
                BigDecimal.valueOf(150_000), null, "SP", "cidade", null, "ATIVO", false, null);

        PerfilImplicitoUsuario perfil = new PerfilImplicitoUsuario("IMOVEL", "SP",
                BigDecimal.valueOf(80_000), BigDecimal.valueOf(240_000), List.of(loteVisto));
        when(perfilImplicitoService.construir(userId)).thenReturn(perfil);
        when(propertyCatalogClient.listar(eq("IMOVEL"), eq("ATIVO"), eq("SP"), any(), any(), anyInt()))
                .thenReturn(List.of(candidatoJaVisto, candidatoValido));

        List<LoteRecomendadoResponse> resultado = service.paraVoce(userId, 1);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).id()).isEqualTo(candidatoValido.id());
        assertThat(resultado.get(0).id()).isNotEqualTo(loteVisto);
        verify(propertyCatalogClient, never()).vitrine(anyInt());
    }

    @Test
    @DisplayName("Deve completar (top up) com vitrine quando busca personalizada retorna menos que o limite")
    void deveCompletarComVitrineQuandoInsuficiente() {
        LoteCatalogo personalizado = lote("IMOVEL", BigDecimal.valueOf(150_000));
        LoteCatalogo doVitrine1 = lote("VEICULO", BigDecimal.valueOf(50_000));
        LoteCatalogo doVitrine2 = lote("ELETRONICO", BigDecimal.valueOf(2_000));

        PerfilImplicitoUsuario perfil = new PerfilImplicitoUsuario("IMOVEL", "SP",
                BigDecimal.valueOf(80_000), BigDecimal.valueOf(240_000), List.of());
        when(perfilImplicitoService.construir(userId)).thenReturn(perfil);
        when(propertyCatalogClient.listar(any(), any(), any(), any(), any(), anyInt()))
                .thenReturn(List.of(personalizado));
        when(propertyCatalogClient.vitrine(anyInt())).thenReturn(List.of(personalizado, doVitrine1, doVitrine2));

        List<LoteRecomendadoResponse> resultado = service.paraVoce(userId, 3);

        assertThat(resultado).hasSize(3);
        assertThat(resultado).extracting(LoteRecomendadoResponse::id)
                .containsExactlyInAnyOrder(personalizado.id(), doVitrine1.id(), doVitrine2.id());
    }

    @Test
    @DisplayName("Top-up de vitrine NÃO deve reintroduzir lote que o usuário já visualizou "
            + "(regressão encontrada no smoke test real E30-H3)")
    void naoDeveReintroduzirLoteJaVistoNoTopUpDeVitrine() {
        UUID loteVisto = UUID.randomUUID();
        LoteCatalogo doVitrine = lote("VEICULO", BigDecimal.valueOf(50_000));
        LoteCatalogo mesmoLoteJaVistoNaVitrine = new LoteCatalogo(loteVisto, "AGRONEGOCIO", "sub", "titulo",
                BigDecimal.valueOf(80_000), null, "MT", "cidade", null, "ATIVO", false, null);

        PerfilImplicitoUsuario perfil = new PerfilImplicitoUsuario("AGRONEGOCIO", "MT",
                BigDecimal.valueOf(70_000), BigDecimal.valueOf(90_000), List.of(loteVisto));
        when(perfilImplicitoService.construir(userId)).thenReturn(perfil);
        when(propertyCatalogClient.listar(any(), any(), any(), any(), any(), anyInt())).thenReturn(List.of());
        when(propertyCatalogClient.vitrine(anyInt()))
                .thenReturn(List.of(mesmoLoteJaVistoNaVitrine, doVitrine));

        List<LoteRecomendadoResponse> resultado = service.paraVoce(userId, 12);

        assertThat(resultado).extracting(LoteRecomendadoResponse::id)
                .containsExactly(doVitrine.id())
                .doesNotContain(loteVisto);
    }

    @Test
    @DisplayName("Não deve chamar vitrine quando a busca personalizada já preenche o limite")
    void naoDeveChamarVitrineQuandoPersonalizadoSuficiente() {
        List<LoteCatalogo> doze = IntStream.range(0, 12)
                .mapToObj(i -> lote("IMOVEL", BigDecimal.valueOf(100_000)))
                .toList();
        PerfilImplicitoUsuario perfil = new PerfilImplicitoUsuario("IMOVEL", "SP",
                BigDecimal.valueOf(80_000), BigDecimal.valueOf(240_000), List.of());
        when(perfilImplicitoService.construir(userId)).thenReturn(perfil);
        when(propertyCatalogClient.listar(any(), any(), any(), any(), any(), anyInt())).thenReturn(doze);

        List<LoteRecomendadoResponse> resultado = service.paraVoce(userId, 12);

        assertThat(resultado).hasSize(12);
        verify(propertyCatalogClient, never()).vitrine(anyInt());
    }

    @Test
    @DisplayName("Deve normalizar limite não positivo para o padrão (12)")
    void deveNormalizarLimiteNaoPositivo() {
        when(perfilImplicitoService.construir(userId)).thenReturn(PerfilImplicitoUsuario.vazio());
        when(propertyCatalogClient.vitrine(anyInt())).thenReturn(List.of());

        service.paraVoce(userId, 0);

        verify(propertyCatalogClient).vitrine(12);
    }

    @Test
    @DisplayName("Deve limitar o limite máximo pedido a 50")
    void deveLimitarLimiteMaximo() {
        when(perfilImplicitoService.construir(userId)).thenReturn(PerfilImplicitoUsuario.vazio());
        when(propertyCatalogClient.vitrine(anyInt())).thenReturn(List.of());

        service.paraVoce(userId, 999);

        verify(propertyCatalogClient).vitrine(50);
    }

    @Test
    @DisplayName("Vistos recentemente: deve deduplicar por loteId mantendo a ocorrência mais recente")
    void deveDeduplicarVistosRecentementeMantendoMaisRecente() {
        UUID loteId = UUID.randomUUID();
        EventoComportamento maisRecente = eventoView(loteId, LocalDateTime.now());
        EventoComportamento maisAntigo = eventoView(loteId, LocalDateTime.now().minusHours(1));
        when(eventoComportamentoRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any())).thenReturn(List.of(maisRecente, maisAntigo));
        when(propertyCatalogClient.buscarPorId(loteId)).thenReturn(Optional.of(lote("IMOVEL", BigDecimal.TEN)));

        List<LoteRecomendadoResponse> resultado = service.vistosRecentemente(userId, 12);

        assertThat(resultado).hasSize(1);
        verify(propertyCatalogClient, times(1)).buscarPorId(loteId);
    }

    @Test
    @DisplayName("Vistos recentemente: deve ignorar graciosamente lote removido do catálogo (404)")
    void deveIgnorarLoteRemovidoEmVistosRecentemente() {
        UUID loteRemovido = UUID.randomUUID();
        UUID loteValido = UUID.randomUUID();
        when(eventoComportamentoRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any()))
                .thenReturn(List.of(eventoView(loteRemovido, LocalDateTime.now()),
                        eventoView(loteValido, LocalDateTime.now().minusMinutes(5))));
        when(propertyCatalogClient.buscarPorId(loteRemovido)).thenReturn(Optional.empty());
        when(propertyCatalogClient.buscarPorId(loteValido)).thenReturn(Optional.of(lote("IMOVEL", BigDecimal.TEN)));

        List<LoteRecomendadoResponse> resultado = service.vistosRecentemente(userId, 12);

        assertThat(resultado).hasSize(1);
    }

    @Test
    @DisplayName("Vistos recentemente: usuário sem eventos VIEW deve receber lista vazia (não é cold-start)")
    void deveRetornarListaVaziaQuandoSemEventosView() {
        when(eventoComportamentoRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any())).thenReturn(List.of());

        List<LoteRecomendadoResponse> resultado = service.vistosRecentemente(userId, 12);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Vistos recentemente: deve parar de acumular assim que atingir o limite pedido")
    void deveRespeitarLimiteEmVistosRecentemente() {
        UUID lote1 = UUID.randomUUID();
        UUID lote2 = UUID.randomUUID();
        UUID lote3 = UUID.randomUUID();
        when(eventoComportamentoRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any()))
                .thenReturn(List.of(eventoView(lote1, LocalDateTime.now()),
                        eventoView(lote2, LocalDateTime.now().minusMinutes(1)),
                        eventoView(lote3, LocalDateTime.now().minusMinutes(2))));
        when(propertyCatalogClient.buscarPorId(lote1)).thenReturn(Optional.of(lote("IMOVEL", BigDecimal.TEN)));
        when(propertyCatalogClient.buscarPorId(lote2)).thenReturn(Optional.of(lote("IMOVEL", BigDecimal.TEN)));

        List<LoteRecomendadoResponse> resultado = service.vistosRecentemente(userId, 2);

        assertThat(resultado).hasSize(2);
        verify(propertyCatalogClient, never()).buscarPorId(lote3);
    }

    @Test
    @DisplayName("Deve mapear valor do card usando lanceAtual quando presente")
    void deveMapearValorUsandoLanceAtualQuandoPresente() {
        UUID loteId = UUID.randomUUID();
        LoteCatalogo loteComLance = new LoteCatalogo(loteId, "IMOVEL", "sub", "titulo",
                BigDecimal.valueOf(100_000), BigDecimal.valueOf(120_000), "SP", "cidade",
                null, "ATIVO", true, new String[]{"foto1.jpg"});
        when(eventoComportamentoRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any())).thenReturn(List.of(eventoView(loteId, LocalDateTime.now())));
        when(propertyCatalogClient.buscarPorId(loteId)).thenReturn(Optional.of(loteComLance));

        List<LoteRecomendadoResponse> resultado = service.vistosRecentemente(userId, 12);

        assertThat(resultado.get(0).valor()).isEqualByComparingTo(BigDecimal.valueOf(120_000));
        assertThat(resultado.get(0).destaque()).isTrue();
    }

    @Test
    @DisplayName("Deve mapear valor do card usando valorAvaliacao quando lanceAtual está ausente")
    void deveMapearValorUsandoValorAvaliacaoQuandoLanceAtualAusente() {
        UUID loteId = UUID.randomUUID();
        LoteCatalogo loteSemLance = lote("IMOVEL", BigDecimal.valueOf(100_000));
        when(eventoComportamentoRepository.findByUserIdAndEventTypeOrderByOccurredAtDesc(
                eq(userId), eq(TipoEvento.VIEW), any())).thenReturn(List.of(eventoView(loteId, LocalDateTime.now())));
        when(propertyCatalogClient.buscarPorId(loteId)).thenReturn(Optional.of(loteSemLance));

        List<LoteRecomendadoResponse> resultado = service.vistosRecentemente(userId, 12);

        assertThat(resultado.get(0).valor()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
    }

    private EventoComportamento eventoView(UUID loteId, LocalDateTime occurredAt) {
        EventoComportamento evento = new EventoComportamento();
        evento.setEventType(TipoEvento.VIEW);
        evento.setLoteId(loteId);
        evento.setOccurredAt(occurredAt);
        return evento;
    }

    private LoteCatalogo lote(String categoria, BigDecimal valorAvaliacao) {
        return new LoteCatalogo(UUID.randomUUID(), categoria, "sub", "titulo",
                valorAvaliacao, null, "SP", "cidade", null, "ATIVO", false, null);
    }
}
