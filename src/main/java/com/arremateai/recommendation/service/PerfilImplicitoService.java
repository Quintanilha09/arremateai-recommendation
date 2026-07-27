package com.arremateai.recommendation.service;

import com.arremateai.recommendation.client.PropertyCatalogClient;
import com.arremateai.recommendation.client.dto.LoteCatalogo;
import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import com.arremateai.recommendation.dto.PerfilImplicitoUsuario;
import com.arremateai.recommendation.repository.EventoComportamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Constroi o perfil implicito do usuario a partir dos seus eventos de comportamento
 * (E30-H3, ADR-009 Fase 1 — "Recomendados para voce").
 *
 * <p><strong>Decisoes de design (documentadas conforme pedido na historia):</strong></p>
 * <ul>
 *   <li><b>Janela de tempo:</b> ultimos {@value #JANELA_DIAS} dias. Cobre navegacao
 *       recente sem deixar o perfil "preso" a um interesse antigo e ja esquecido.</li>
 *   <li><b>Tipo de evento:</b> apenas VIEW. E o sinal mais forte e inequivoco de
 *       interesse (abriu o detalhe do lote); CLICK/FAVORITE com peso maior fica
 *       registrado como evolucao possivel (Fase 2), mas a historia explicitamente
 *       nao exige complicar isso agora.</li>
 *   <li><b>Volume:</b> no maximo {@value #MAX_EVENTOS_CONSIDERADOS} eventos mais
 *       recentes entram na janela (bound de seguranca). Dos loteId distintos
 *       resultantes, so os {@value #MAX_LOTES_DISTINTOS_PERFIL} mais recentes sao
 *       reidratados no catalogo (uma chamada HTTP por lote) — o suficiente para
 *       calcular categoria/uf/faixa de preco dominantes sem gerar fan-out excessivo
 *       de chamadas ao property-catalog.</li>
 *   <li><b>Faixa de preco:</b> min/max observados com folga de 20% para os dois
 *       lados, evitando que a busca personalizada fique excessivamente restrita
 *       quando o usuario viu poucos lotes de valor muito proximo.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerfilImplicitoService {

    private static final int JANELA_DIAS = 90;
    private static final int MAX_EVENTOS_CONSIDERADOS = 200;
    private static final int MAX_LOTES_DISTINTOS_PERFIL = 20;
    private static final BigDecimal FOLGA_INFERIOR = BigDecimal.valueOf(0.8);
    private static final BigDecimal FOLGA_SUPERIOR = BigDecimal.valueOf(1.2);

    private final EventoComportamentoRepository eventoComportamentoRepository;
    private final PropertyCatalogClient propertyCatalogClient;

    @Transactional(readOnly = true)
    public PerfilImplicitoUsuario construir(UUID userId) {
        LocalDateTime desde = LocalDateTime.now().minusDays(JANELA_DIAS);
        List<EventoComportamento> eventos = eventoComportamentoRepository
                .findByUserIdAndEventTypeAndOccurredAtAfterOrderByOccurredAtDesc(
                        userId, TipoEvento.VIEW, desde, PageRequest.of(0, MAX_EVENTOS_CONSIDERADOS));

        if (eventos.isEmpty()) {
            log.debug("Usuario {} sem eventos VIEW na janela de {} dias — perfil vazio (cold-start)",
                    userId, JANELA_DIAS);
            return PerfilImplicitoUsuario.vazio();
        }

        List<UUID> loteIdsVistos = eventos.stream()
                .map(EventoComportamento::getLoteId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<LoteCatalogo> amostra = loteIdsVistos.stream()
                .limit(MAX_LOTES_DISTINTOS_PERFIL)
                .map(propertyCatalogClient::buscarPorId)
                .flatMap(Optional::stream)
                .toList();

        if (amostra.isEmpty()) {
            log.debug("Usuario {} tem eventos VIEW mas nenhum lote resolvido no catalogo — perfil sem sinal", userId);
            return new PerfilImplicitoUsuario(null, null, null, null, loteIdsVistos);
        }

        String categoriaTopo = maisFrequente(amostra, LoteCatalogo::categoria);
        String ufTopo = maisFrequente(amostra, LoteCatalogo::uf);
        BigDecimal[] faixa = faixaPreco(amostra);

        return new PerfilImplicitoUsuario(categoriaTopo, ufTopo, faixa[0], faixa[1], loteIdsVistos);
    }

    private String maisFrequente(List<LoteCatalogo> lotes, Function<LoteCatalogo, String> extrator) {
        Map<String, Long> contagem = lotes.stream()
                .map(extrator)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return contagem.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private BigDecimal[] faixaPreco(List<LoteCatalogo> lotes) {
        List<BigDecimal> valores = lotes.stream()
                .map(l -> l.lanceAtual() != null ? l.lanceAtual() : l.valorAvaliacao())
                .filter(Objects::nonNull)
                .toList();

        if (valores.isEmpty()) {
            return new BigDecimal[]{null, null};
        }

        BigDecimal min = valores.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = valores.stream().max(Comparator.naturalOrder()).orElseThrow();
        return new BigDecimal[]{min.multiply(FOLGA_INFERIOR), max.multiply(FOLGA_SUPERIOR)};
    }
}
