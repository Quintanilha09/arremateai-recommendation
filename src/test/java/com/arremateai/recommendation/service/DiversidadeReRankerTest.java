package com.arremateai.recommendation.service;

import com.arremateai.recommendation.client.dto.LoteCatalogo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes unitários isolados do {@link DiversidadeReRanker} (E30-H4). Cada teste monta um
 * pool de candidatos controlado e verifica números concretos de distribuição na saída —
 * não apenas "mudou em relação à entrada" — conforme o critério de aceite QUINT-301
 * ("distribuição verificável em teste").
 *
 * <p>Os testes de faixa de preço e mistura de status usam pools de categoria única e/ou
 * preço nulo para isolar cada um dos 3 passos do re-ranker (ver javadoc da classe): preço
 * nulo neutraliza o passo 2 (nenhum item é classificável como ALTO) e status único
 * neutraliza o passo 3, permitindo verificar cada regra sem interferência das demais.</p>
 */
@DisplayName("DiversidadeReRanker — testes unitários")
class DiversidadeReRankerTest {

    private final DiversidadeReRanker reRanker = new DiversidadeReRanker();

    @Test
    @DisplayName("Lista nula, vazia ou limite não positivo devolvem lista vazia sem exceção")
    void deveDevolverListaVaziaParaEntradasDegeneradas() {
        assertThat(reRanker.reordenar(null, 10)).isEmpty();
        assertThat(reRanker.reordenar(List.of(), 10)).isEmpty();
        assertThat(reRanker.reordenar(List.of(lote("IMOVEL")), 0)).isEmpty();
        assertThat(reRanker.reordenar(List.of(lote("IMOVEL")), -1)).isEmpty();
    }

    @Test
    @DisplayName("Pool menor que o limite: devolve todos os candidatos, sem duplicar nem preencher artificialmente")
    void devePreservarPoolMenorQueLimite() {
        List<LoteCatalogo> candidatos = List.of(lote("IMOVEL"), lote("IMOVEL"), lote("VEICULO"));

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 10);

        assertThat(resultado).hasSize(3).containsExactlyInAnyOrderElementsOf(candidatos);
    }

    @Test
    @DisplayName("Categoria dominante (18 IMOVEL + 2 VEICULO, limite 12): VEICULO entra por inteiro (2), "
            + "IMOVEL ultrapassa o teto de 6 só porque não há mais VEICULO no pool para substituir o excedente")
    void deveIncluirTodaCategoriaMinoritariaEEstourarTetoDaMajoritariaPorFaltaDeAlternativa() {
        List<LoteCatalogo> candidatos = new ArrayList<>();
        Stream.generate(() -> lote("IMOVEL")).limit(18).forEach(candidatos::add);
        Stream.generate(() -> lote("VEICULO")).limit(2).forEach(candidatos::add);

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 12);

        assertThat(resultado).hasSize(12);
        assertThat(contarCategoria(resultado, "IMOVEL")).isEqualTo(10);
        assertThat(contarCategoria(resultado, "VEICULO")).isEqualTo(2);
    }

    @Test
    @DisplayName("Categorias com estoque suficiente (8 IMOVEL + 8 VEICULO, limite 10): "
            + "nenhuma categoria excede o teto de ceil(10/2)=5 — fica exatamente 5/5")
    void deveRespeitarTetoQuandoHaAlternativaSuficiente() {
        List<LoteCatalogo> candidatos = new ArrayList<>();
        Stream.generate(() -> lote("IMOVEL")).limit(8).forEach(candidatos::add);
        Stream.generate(() -> lote("VEICULO")).limit(8).forEach(candidatos::add);

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 10);

        assertThat(resultado).hasSize(10);
        assertThat(contarCategoria(resultado, "IMOVEL")).isEqualTo(5);
        assertThat(contarCategoria(resultado, "VEICULO")).isEqualTo(5);
    }

    @Test
    @DisplayName("Caso-limite: pool 100% homogêneo (1 categoria só) não quebra nem esvazia a lista — "
            + "devolve o limite inteiro daquela única categoria, já que não há o que diversificar")
    void naoDeveQuebrarComPoolHomogeneo() {
        List<LoteCatalogo> candidatos = Stream.generate(() -> lote("IMOVEL")).limit(15).toList();

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 12);

        assertThat(resultado).hasSize(12);
        assertThat(contarCategoria(resultado, "IMOVEL")).isEqualTo(12);
    }

    @Test
    @DisplayName("Cota de alto valor: com 9 candidatos (preços 100..900, mais caro = menos relevante) e limite 6, "
            + "a truncagem pura ficaria 100% BAIXO/MÉDIO — o re-ranker garante 2 itens da faixa ALTO (>=20% de 6)")
    void deveGarantirCotaMinimaDeAltoValor() {
        List<LoteCatalogo> candidatos = new ArrayList<>();
        for (int i = 1; i <= 9; i++) {
            candidatos.add(lote("IMOVEL", BigDecimal.valueOf(i * 100)));
        }

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 6);

        assertThat(resultado).hasSize(6);
        List<BigDecimal> precos = resultado.stream().map(LoteCatalogo::valorAvaliacao).toList();
        long itensDeAltoValor = precos.stream().filter(preco -> preco.compareTo(BigDecimal.valueOf(700)) >= 0).count();
        assertThat(itensDeAltoValor)
                .as("pelo menos 2 itens da faixa ALTO (tercio superior do pool: 700/800/900) devem estar presentes")
                .isGreaterThanOrEqualTo(2);
        assertThat(precos).containsExactlyInAnyOrder(
                BigDecimal.valueOf(100), BigDecimal.valueOf(200), BigDecimal.valueOf(300), BigDecimal.valueOf(400),
                BigDecimal.valueOf(700), BigDecimal.valueOf(800));
    }

    @Test
    @DisplayName("Cota de alto valor não pode furar o teto por categoria: quando os únicos candidatos ALTO "
            + "disponíveis pertencem à categoria já no teto, a substituição só ocorre dentro da própria "
            + "categoria — nunca trocando um item da categoria minoritária (VEICULO) por um da majoritária "
            + "(IMOVEL), o que desfaria o balanceamento do passo 1")
    void naoDeveFurarTetoDeCategoriaAoAplicarCotaDeAltoValor() {
        List<LoteCatalogo> candidatos = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            candidatos.add(lote("IMOVEL", BigDecimal.valueOf(i * 100)));
        }
        for (int i = 1; i <= 8; i++) {
            candidatos.add(lote("VEICULO", BigDecimal.valueOf(i * 10)));
        }

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 6);

        assertThat(resultado).hasSize(6);
        assertThat(contarCategoria(resultado, "IMOVEL"))
                .as("teto de ceil(6/2)=3 deve se manter mesmo com a cota de alto valor tentando puxar mais IMOVEL")
                .isEqualTo(3);
        assertThat(contarCategoria(resultado, "VEICULO"))
                .as("VEICULO não pode perder espaço para IMOVEL só porque os itens ALTO estão todos em IMOVEL")
                .isEqualTo(3);
        long itensAltoValorDentroDoTeto = resultado.stream()
                .filter(l -> "IMOVEL".equals(l.categoria()) && l.valorAvaliacao().compareTo(BigDecimal.valueOf(400)) >= 0)
                .count();
        assertThat(itensAltoValorDentroDoTeto)
                .as("a cota de 20% (2 de 6) de alto valor ainda deve ser satisfeita via substituição "
                        + "dentro da própria categoria IMOVEL, sem violar o teto")
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Sem candidatos de alto valor no pool: cota é best-effort, lista permanece íntegra (sem exceção)")
    void naoDeveQuebrarQuandoNaoHaCandidatosDeAltoValor() {
        List<LoteCatalogo> candidatos = Stream.generate(() -> lote("IMOVEL", null)).limit(6).toList();

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 6);

        assertThat(resultado).hasSize(6);
    }

    @Test
    @DisplayName("Mistura de status: pool com 6 ATIVO + 3 ENCERRANDO (ENCERRANDO é o menos relevante, "
            + "cairia fora na truncagem pura de limite 6) — o re-ranker garante ao menos 1 ENCERRANDO na saída")
    void deveGarantirMisturaDeStatusQuandoDisponivel() {
        List<LoteCatalogo> candidatos = new ArrayList<>();
        Stream.generate(() -> lote("IMOVEL", null, "ATIVO", null)).limit(6).forEach(candidatos::add);
        Stream.generate(() -> lote("IMOVEL", null, "ENCERRANDO", null)).limit(3).forEach(candidatos::add);

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 6);

        assertThat(resultado).hasSize(6);
        long ativos = resultado.stream().filter(l -> "ATIVO".equals(l.status())).count();
        long encerrando = resultado.stream().filter(l -> "ENCERRANDO".equals(l.status())).count();
        assertThat(encerrando).as("ao menos 1 ENCERRANDO deve sobreviver ao re-ranking").isGreaterThanOrEqualTo(1);
        assertThat(ativos + encerrando).isEqualTo(6);
    }

    @Test
    @DisplayName("Status derivado NOVO: lote criado há 2 dias conta como NOVO mesmo com status bruto ATIVO, "
            + "e a mistura de status deve incluí-lo quando ausente da truncagem")
    void deveDerivarStatusNovoPorDataDeCriacaoRecente() {
        List<LoteCatalogo> candidatos = new ArrayList<>();
        Stream.generate(() -> lote("IMOVEL", null, "ATIVO", LocalDateTime.now().minusDays(90)))
                .limit(6).forEach(candidatos::add);
        candidatos.add(lote("IMOVEL", null, "ATIVO", LocalDateTime.now().minusDays(2)));
        candidatos.add(lote("IMOVEL", null, "ATIVO", LocalDateTime.now().minusDays(1)));

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 6);

        assertThat(resultado).hasSize(6);
        boolean contemLoteRecente = resultado.stream()
                .anyMatch(l -> l.createdAt() != null && l.createdAt().isAfter(LocalDateTime.now().minusDays(7)));
        assertThat(contemLoteRecente)
                .as("pelo menos 1 lote com createdAt recente (status efetivo NOVO) deve sobreviver ao re-ranking")
                .isTrue();
    }

    @Test
    @DisplayName("Status único no pool: mistura de status não faz nada (nada para misturar)")
    void naoDeveMexerNaListaQuandoSoHaUmStatusNoPool() {
        List<LoteCatalogo> candidatos = Stream.generate(() -> lote("IMOVEL", null, "ATIVO", null)).limit(9).toList();

        List<LoteCatalogo> resultado = reRanker.reordenar(candidatos, 6);

        assertThat(resultado).hasSize(6);
        assertThat(resultado).allMatch(l -> "ATIVO".equals(l.status()));
    }

    private long contarCategoria(List<LoteCatalogo> lotes, String categoria) {
        return lotes.stream().filter(l -> categoria.equals(l.categoria())).count();
    }

    private LoteCatalogo lote(String categoria) {
        return lote(categoria, null, "ATIVO", null);
    }

    private LoteCatalogo lote(String categoria, BigDecimal preco) {
        return lote(categoria, preco, "ATIVO", null);
    }

    private LoteCatalogo lote(String categoria, BigDecimal preco, String status, LocalDateTime createdAt) {
        return new LoteCatalogo(UUID.randomUUID(), categoria, "sub", "titulo",
                preco, null, "SP", "cidade", null, status, false, null, createdAt);
    }
}
