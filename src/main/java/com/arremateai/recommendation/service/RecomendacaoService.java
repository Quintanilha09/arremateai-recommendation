package com.arremateai.recommendation.service;

import com.arremateai.recommendation.client.PropertyCatalogClient;
import com.arremateai.recommendation.client.dto.LoteCatalogo;
import com.arremateai.recommendation.config.CacheConfig;
import com.arremateai.recommendation.domain.EventoComportamento;
import com.arremateai.recommendation.domain.TipoEvento;
import com.arremateai.recommendation.dto.LoteRecomendadoResponse;
import com.arremateai.recommendation.dto.PerfilImplicitoUsuario;
import com.arremateai.recommendation.repository.EventoComportamentoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Recomendacao heuristica content-based (E30-H3, ADR-009 Fase 1): "Recomendados
 * para voce" e "Voce viu recentemente". Sem ML — cruza o perfil implicito do
 * usuario (ver {@link PerfilImplicitoService}) com o read-model de lotes do
 * property-catalog (ver {@link PropertyCatalogClient}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecomendacaoService {

    private static final int LIMITE_PADRAO = 12;
    private static final int LIMITE_MAXIMO = 50;
    private static final String STATUS_ATIVO = "ATIVO";

    /** Quantos eventos VIEW mais recentes sao varridos para deduplicar "voce viu recentemente". */
    private static final int JANELA_VISTOS_RECENTEMENTE = 100;

    private final PerfilImplicitoService perfilImplicitoService;
    private final PropertyCatalogClient propertyCatalogClient;
    private final EventoComportamentoRepository eventoComportamentoRepository;

    /**
     * "Recomendados para voce": busca personalizada pelo perfil implicito, excluindo
     * lotes ja vistos recentemente, completada ("top up") com o mix de vitrine quando
     * insuficiente. Cold-start (usuario sem sinal) cai direto no mix de vitrine — nunca
     * retorna lista vazia enquanto houver ao menos 1 lote no catalogo.
     */
    @Cacheable(value = CacheConfig.CACHE_PARA_VOCE, key = "#userId + '-' + #limite")
    @Transactional(readOnly = true)
    public List<LoteRecomendadoResponse> paraVoce(UUID userId, int limite) {
        int limiteEfetivo = normalizarLimite(limite);
        PerfilImplicitoUsuario perfil = perfilImplicitoService.construir(userId);

        List<LoteCatalogo> candidatos = perfil.temSinalSuficiente()
                ? buscarPersonalizados(perfil, limiteEfetivo)
                : new ArrayList<>();

        if (candidatos.size() < limiteEfetivo) {
            candidatos = completarComVitrine(candidatos, limiteEfetivo, perfil.loteIdsVistosRecentemente());
        }

        return candidatos.stream()
                .limit(limiteEfetivo)
                .map(this::mapear)
                .toList();
    }

    /**
     * "Voce viu recentemente": ultimos VIEW do usuario, deduplicados por loteId
     * (mantendo a ocorrencia mais recente), reidratados no catalogo. Diferente de
     * "para-voce", aqui lista vazia e aceitavel — reflete literalmente o historico.
     */
    @Cacheable(value = CacheConfig.CACHE_VISTOS_RECENTEMENTE, key = "#userId + '-' + #limite")
    @Transactional(readOnly = true)
    public List<LoteRecomendadoResponse> vistosRecentemente(UUID userId, int limite) {
        int limiteEfetivo = normalizarLimite(limite);
        List<EventoComportamento> eventos = eventoComportamentoRepository
                .findByUserIdAndEventTypeOrderByOccurredAtDesc(
                        userId, TipoEvento.VIEW, PageRequest.of(0, JANELA_VISTOS_RECENTEMENTE));

        Set<UUID> idsDeduplicados = new LinkedHashSet<>();
        for (EventoComportamento evento : eventos) {
            if (evento.getLoteId() != null) {
                idsDeduplicados.add(evento.getLoteId());
            }
            if (idsDeduplicados.size() >= limiteEfetivo) {
                break;
            }
        }

        return idsDeduplicados.stream()
                .map(propertyCatalogClient::buscarPorId)
                .flatMap(Optional::stream)
                .map(this::mapear)
                .toList();
    }

    private List<LoteCatalogo> buscarPersonalizados(PerfilImplicitoUsuario perfil, int limite) {
        int tamanhoBusca = limite + perfil.loteIdsVistosRecentemente().size();
        List<LoteCatalogo> resultado = propertyCatalogClient.listar(
                perfil.categoriaMaisFrequente(), STATUS_ATIVO, perfil.ufMaisFrequente(),
                perfil.valorMinimo(), perfil.valorMaximo(), tamanhoBusca);

        return resultado.stream()
                .filter(lote -> !perfil.loteIdsVistosRecentemente().contains(lote.id()))
                .toList();
    }

    /**
     * Completa a lista com o mix de vitrine ate atingir o limite, deduplicando
     * por loteId e excluindo tambem os lotes ja vistos recentemente pelo usuario
     * (QUINT-300 smoke test real, E30-H3): sem essa exclusao, o top-up de vitrine
     * podia devolver de volta o mesmissimo lote que acabou de ser filtrado da
     * busca personalizada, anulando o proposito da exclusao.
     */
    private List<LoteCatalogo> completarComVitrine(List<LoteCatalogo> atual, int limite, List<UUID> idsVistos) {
        List<LoteCatalogo> combinado = new ArrayList<>(atual);
        Set<UUID> idsIncluidos = new HashSet<>(idsVistos);
        combinado.forEach(lote -> idsIncluidos.add(lote.id()));

        if (combinado.size() >= limite) {
            return combinado;
        }

        for (LoteCatalogo lote : propertyCatalogClient.vitrine(limite)) {
            if (combinado.size() >= limite) {
                break;
            }
            if (idsIncluidos.add(lote.id())) {
                combinado.add(lote);
            }
        }
        return combinado;
    }

    private int normalizarLimite(int limite) {
        if (limite <= 0) {
            return LIMITE_PADRAO;
        }
        return Math.min(limite, LIMITE_MAXIMO);
    }

    private LoteRecomendadoResponse mapear(LoteCatalogo lote) {
        var valor = lote.lanceAtual() != null ? lote.lanceAtual() : lote.valorAvaliacao();
        return new LoteRecomendadoResponse(
                lote.id(),
                lote.titulo(),
                lote.categoria(),
                lote.subcategoria(),
                valor,
                lote.status(),
                lote.dataEncerramento(),
                lote.uf(),
                lote.cidade(),
                lote.fotosUrls(),
                Boolean.TRUE.equals(lote.destaque()));
    }
}
