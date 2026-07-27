package com.arremateai.recommendation.service;

import com.arremateai.recommendation.client.dto.LoteCatalogo;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Pos-processamento de re-ranking da feed de recomendacoes (E30-H4, ADR-009 secao
 * "Diversidade") — garante diversidade proposital para que a lista final nunca fique
 * dominada por uma unica categoria, faixa de preco ou status, respeitando o que
 * efetivamente existe no pool de candidatos (nao inventa diversidade que os dados
 * nao suportam).
 *
 * <p>Recebe uma lista de candidatos ja ordenada por relevancia (busca personalizada
 * ou vitrine) e devolve uma lista de mesmo tamanho (ou menor, se o pool for menor
 * que o limite), reordenada/substituida em 3 passos independentes e determinísticos,
 * cada um documentado e testavel isoladamente:</p>
 *
 * <ol>
 *   <li><b>Balanceamento por categoria (round-robin):</b> nenhuma categoria ocupa mais
 *       que {@code ceil(limite / 2)} posicoes da lista final — a metade+1 quando o
 *       limite e impar. Constroi a lista intercalando um item de cada categoria por
 *       vez (na ordem de relevancia dentro de cada categoria). Se, apos respeitar o
 *       teto, ainda sobrarem posicoes vagas porque as demais categorias se esgotaram,
 *       o teto e relaxado para preencher o limite (preferir lista cheia e diversa
 *       quando possivel, mas nunca lista incompleta so para forcar diversidade que
 *       o pool nao tem).</li>
 *   <li><b>Cota minima de alto valor:</b> os candidatos sao ranqueados por preco
 *       (lance atual, ou avaliacao quando nao ha lance) e divididos em tercios —
 *       BAIXO/MEDIO/ALTO — relativos ao proprio pool recebido (nao faixas fixas em
 *       R$, que variam demais entre categorias como joia e imovel). Garante ao menos
 *       {@code max(1, ceil(tamanhoLista * 20%))} itens da faixa ALTO na lista final
 *       quando houver candidatos ALTO disponiveis no pool, substituindo os itens
 *       menos relevantes (a partir do fim da lista) que nao sejam ALTO.</li>
 *   <li><b>Mistura de status:</b> status efetivo e ATIVO/ENCERRANDO (campo do lote) ou
 *       NOVO — derivado, sobrepondo os demais, quando {@code createdAt} esta dentro dos
 *       ultimos {@value #DIAS_CONSIDERADO_NOVO} dias (nao existe enum "NOVO" no dominio
 *       do Lote). Para cada status presente no pool mas ausente da lista final, garante
 *       ao menos 1 representante, substituindo o item menos relevante que nao seja
 *       daquele status.</li>
 * </ol>
 *
 * <p>Os passos 2 e 3 substituem itens (nunca aumentam o tamanho da lista) e sempre
 * respeitam o teto por categoria calculado no passo 1 — uma substituicao so acontece
 * se a categoria do item entrando ainda tiver espaco sob o teto (ou for a mesma
 * categoria do item saindo). Isso evita que a cota de preco/status "desfaca" o
 * balanceamento por categoria.</p>
 */
@Component
public class DiversidadeReRanker {

    private static final String SEM_CATEGORIA = "SEM_CATEGORIA";

    /** Nenhuma categoria pode exceder metade (arredondada pra cima) da lista final. */
    private static final double FATOR_TETO_CATEGORIA = 0.5;

    /** Minimo de 20% da lista final na faixa ALTO de preco, quando houver candidatos disponiveis. */
    private static final double PERCENTUAL_MINIMO_ALTO_VALOR = 0.2;

    /** Numero de faixas de preco (tercios) usadas para classificar o pool de candidatos. */
    private static final int QUANTIDADE_FAIXAS_PRECO = 3;

    private static final int INDICE_FAIXA_ALTO = QUANTIDADE_FAIXAS_PRECO - 1;

    /** Lote criado ha ate esta quantidade de dias e considerado "NOVO" (status derivado). */
    private static final int DIAS_CONSIDERADO_NOVO = 7;

    private static final String STATUS_NOVO = "NOVO";

    /**
     * Reordena {@code candidatos} aplicando os 3 passos de diversidade, truncando ao
     * {@code limite}. Lista vazia/nula ou {@code limite <= 0} retornam lista vazia.
     */
    public List<LoteCatalogo> reordenar(List<LoteCatalogo> candidatos, int limite) {
        if (candidatos == null || candidatos.isEmpty() || limite <= 0) {
            return List.of();
        }

        int capPorCategoria = tetoPorCategoria(limite);
        List<LoteCatalogo> balanceadoPorCategoria = balancearPorCategoria(candidatos, limite, capPorCategoria);
        List<LoteCatalogo> comCotaAltoValor = garantirCotaAltoValor(balanceadoPorCategoria, candidatos, capPorCategoria);
        return garantirMisturaStatus(comCotaAltoValor, candidatos, capPorCategoria);
    }

    private int tetoPorCategoria(int limite) {
        return (int) Math.ceil(limite * FATOR_TETO_CATEGORIA);
    }

    /**
     * Round-robin por categoria: agrupa os candidatos preservando a ordem de relevancia
     * dentro de cada categoria e intercala um item de cada grupo por rodada, respeitando
     * o teto por categoria. Uma segunda passada (sem teto) preenche vagas remanescentes
     * quando as demais categorias se esgotaram antes do limite.
     */
    private List<LoteCatalogo> balancearPorCategoria(List<LoteCatalogo> candidatos, int limite, int capPorCategoria) {
        Map<String, Deque<LoteCatalogo>> porCategoria = agruparPorCategoria(candidatos);

        List<LoteCatalogo> resultado = new ArrayList<>(Math.min(limite, candidatos.size()));
        Set<UUID> incluidos = new HashSet<>();
        Map<String, Integer> contagem = new HashMap<>();

        preencherRoundRobin(resultado, incluidos, contagem, porCategoria, limite, capPorCategoria);
        preencherRoundRobin(resultado, incluidos, contagem, porCategoria, limite, Integer.MAX_VALUE);

        return resultado;
    }

    private void preencherRoundRobin(List<LoteCatalogo> resultado, Set<UUID> incluidos,
                                      Map<String, Integer> contagem, Map<String, Deque<LoteCatalogo>> porCategoria,
                                      int limite, int tetoPorRodada) {
        boolean progrediu = true;
        while (resultado.size() < limite && progrediu) {
            progrediu = false;
            for (var entrada : porCategoria.entrySet()) {
                if (resultado.size() >= limite) {
                    break;
                }
                String categoria = entrada.getKey();
                Deque<LoteCatalogo> fila = entrada.getValue();
                int usados = contagem.getOrDefault(categoria, 0);
                if (fila.isEmpty() || usados >= tetoPorRodada) {
                    continue;
                }
                LoteCatalogo lote = fila.poll();
                if (incluidos.add(lote.id())) {
                    resultado.add(lote);
                    contagem.merge(categoria, 1, Integer::sum);
                    progrediu = true;
                }
            }
        }
    }

    private Map<String, Deque<LoteCatalogo>> agruparPorCategoria(List<LoteCatalogo> candidatos) {
        Map<String, Deque<LoteCatalogo>> porCategoria = new LinkedHashMap<>();
        for (LoteCatalogo lote : candidatos) {
            porCategoria.computeIfAbsent(categoriaDe(lote), c -> new ArrayDeque<>()).add(lote);
        }
        return porCategoria;
    }

    private String categoriaDe(LoteCatalogo lote) {
        return lote.categoria() != null ? lote.categoria() : SEM_CATEGORIA;
    }

    private List<LoteCatalogo> garantirCotaAltoValor(List<LoteCatalogo> atual, List<LoteCatalogo> poolCompleto,
                                                       int capPorCategoria) {
        int minimo = Math.max(1, (int) Math.ceil(atual.size() * PERCENTUAL_MINIMO_ALTO_VALOR));
        Map<UUID, Integer> faixaPorId = classificarFaixasPreco(poolCompleto);
        Predicate<LoteCatalogo> ehAltoValor = lote -> faixaPorId.getOrDefault(lote.id(), -1) == INDICE_FAIXA_ALTO;
        return garantirRepresentacaoMinima(atual, poolCompleto, ehAltoValor, minimo, capPorCategoria);
    }

    /**
     * Classifica cada lote precificado do pool em uma faixa (0=BAIXO .. {@value #INDICE_FAIXA_ALTO}=ALTO),
     * por posicao relativa no ranking de preco do proprio pool (tercios). Lotes sem
     * preco (nulo) ficam de fora do mapa (nunca contam como ALTO).
     */
    private Map<UUID, Integer> classificarFaixasPreco(List<LoteCatalogo> pool) {
        List<LoteCatalogo> ordenadoPorPreco = pool.stream()
                .filter(lote -> precoEfetivo(lote) != null)
                .sorted(Comparator.comparing(this::precoEfetivo))
                .toList();

        int total = ordenadoPorPreco.size();
        Map<UUID, Integer> faixaPorId = new HashMap<>();
        for (int indice = 0; indice < total; indice++) {
            int faixa = Math.min(INDICE_FAIXA_ALTO, (indice * QUANTIDADE_FAIXAS_PRECO) / total);
            faixaPorId.put(ordenadoPorPreco.get(indice).id(), faixa);
        }
        return faixaPorId;
    }

    private BigDecimal precoEfetivo(LoteCatalogo lote) {
        return lote.lanceAtual() != null ? lote.lanceAtual() : lote.valorAvaliacao();
    }

    private List<LoteCatalogo> garantirMisturaStatus(List<LoteCatalogo> atual, List<LoteCatalogo> poolCompleto,
                                                       int capPorCategoria) {
        Set<String> statusNoPool = poolCompleto.stream()
                .map(this::statusEfetivo)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (statusNoPool.size() <= 1) {
            return atual;
        }

        List<LoteCatalogo> ajustado = atual;
        for (String status : statusNoPool) {
            boolean presente = ajustado.stream().anyMatch(lote -> statusEfetivo(lote).equals(status));
            if (!presente) {
                ajustado = garantirRepresentacaoMinima(
                        ajustado, poolCompleto, lote -> statusEfetivo(lote).equals(status), 1, capPorCategoria);
            }
        }
        return ajustado;
    }

    /**
     * Status efetivo do lote: "NOVO" quando criado ha no maximo {@value #DIAS_CONSIDERADO_NOVO}
     * dias (sobrepoe o status bruto), caso contrario o proprio {@link LoteCatalogo#status()}
     * (tipicamente ATIVO ou ENCERRANDO).
     */
    private String statusEfetivo(LoteCatalogo lote) {
        LocalDateTime criadoEm = lote.createdAt();
        if (criadoEm != null && criadoEm.isAfter(LocalDateTime.now().minusDays(DIAS_CONSIDERADO_NOVO))) {
            return STATUS_NOVO;
        }
        return lote.status();
    }

    /**
     * Garante que ao menos {@code minimoDesejado} itens de {@code atual} satisfacam
     * {@code pertenceAoGrupo}, substituindo — a partir do fim da lista, onde estao os
     * itens menos relevantes — itens que nao pertencem ao grupo por candidatos do
     * {@code poolCompleto} que pertencem e ainda nao estao presentes. Uma substituicao
     * so ocorre se a categoria do substituto ainda tiver espaco sob {@code capPorCategoria}
     * (ou for a mesma categoria do item saindo) — a cota de preco/status nunca reabre o
     * teto por categoria do passo 1. Sem candidatos suficientes no pool, faz o possivel
     * e mantem a lista como estava (nunca lanca excecao nem esvazia a lista).
     */
    private List<LoteCatalogo> garantirRepresentacaoMinima(List<LoteCatalogo> atual, List<LoteCatalogo> poolCompleto,
                                                             Predicate<LoteCatalogo> pertenceAoGrupo,
                                                             int minimoDesejado, int capPorCategoria) {
        long presentes = atual.stream().filter(pertenceAoGrupo).count();
        if (presentes >= minimoDesejado) {
            return atual;
        }

        Set<UUID> idsAtuais = atual.stream().map(LoteCatalogo::id).collect(Collectors.toSet());
        LinkedList<LoteCatalogo> disponiveis = poolCompleto.stream()
                .filter(pertenceAoGrupo)
                .filter(lote -> !idsAtuais.contains(lote.id()))
                .collect(Collectors.toCollection(LinkedList::new));

        if (disponiveis.isEmpty()) {
            return atual;
        }

        List<LoteCatalogo> ajustado = new ArrayList<>(atual);
        Map<String, Long> contagemPorCategoria = contarPorCategoria(ajustado);
        int necessarios = (int) (minimoDesejado - presentes);

        for (int i = ajustado.size() - 1; i >= 0 && necessarios > 0 && !disponiveis.isEmpty(); i--) {
            LoteCatalogo candidatoSaida = ajustado.get(i);
            if (pertenceAoGrupo.test(candidatoSaida)) {
                continue;
            }
            LoteCatalogo substituto = buscarSubstitutoNoTeto(
                    disponiveis, categoriaDe(candidatoSaida), contagemPorCategoria, capPorCategoria);
            if (substituto == null) {
                continue;
            }
            ajustado.set(i, substituto);
            disponiveis.remove(substituto);
            contagemPorCategoria.merge(categoriaDe(candidatoSaida), -1L, Long::sum);
            contagemPorCategoria.merge(categoriaDe(substituto), 1L, Long::sum);
            necessarios--;
        }
        return ajustado;
    }

    private LoteCatalogo buscarSubstitutoNoTeto(LinkedList<LoteCatalogo> disponiveis, String categoriaSaindo,
                                                 Map<String, Long> contagemPorCategoria, int capPorCategoria) {
        for (LoteCatalogo candidato : disponiveis) {
            String categoriaCandidato = categoriaDe(candidato);
            long contagemAtual = contagemPorCategoria.getOrDefault(categoriaCandidato, 0L);
            boolean respeitaTeto = categoriaCandidato.equals(categoriaSaindo) || contagemAtual < capPorCategoria;
            if (respeitaTeto) {
                return candidato;
            }
        }
        return null;
    }

    private Map<String, Long> contarPorCategoria(List<LoteCatalogo> lotes) {
        return lotes.stream().collect(Collectors.groupingBy(this::categoriaDe, Collectors.counting()));
    }
}
