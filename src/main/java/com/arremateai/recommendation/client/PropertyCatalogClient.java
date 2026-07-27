package com.arremateai.recommendation.client;

import com.arremateai.recommendation.client.dto.LoteCatalogo;
import com.arremateai.recommendation.client.dto.PaginaLotesCatalogo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cliente HTTP para o read-model de lotes do {@code arremateai-property-catalog}
 * (E30-H3, ADR-009 Fase 1). O recommendation-service nao possui os dados do catalogo —
 * so os eventos de comportamento — entao toda heuristica content-based depende
 * destas chamadas para reidratar categoria, uf, preco e demais atributos do lote.
 *
 * <p>GET em {@code /api/lotes/**} e publico no property-catalog (seu
 * {@code GatewayAuthFilter} libera GET nesses caminhos via {@code shouldNotFilter}),
 * entao nenhum header de autenticacao mutua e necessario aqui.</p>
 *
 * <p><strong>Resiliencia:</strong> qualquer falha de comunicacao (timeout, 5xx,
 * catalogo fora do ar) e capturada e loga em WARN, retornando um resultado vazio
 * em vez de propagar a excecao — a recomendacao degrada graciosamente (lista menor
 * ou cold-start) em vez de quebrar a experiencia do usuario.</p>
 */
@Component
@Slf4j
public class PropertyCatalogClient {

    private final RestTemplate restTemplate;
    private final String propertyCatalogUrl;

    public PropertyCatalogClient(RestTemplate restTemplate,
                                  @Value("${services.property-catalog.url:http://localhost:8082}") String propertyCatalogUrl) {
        this.restTemplate = restTemplate;
        this.propertyCatalogUrl = propertyCatalogUrl;
    }

    /**
     * Busca um lote por id. Retorna {@link Optional#empty()} tanto para 404 (lote
     * removido/desativado) quanto para qualquer outra falha de comunicacao —
     * o chamador deve pular o lote sem quebrar a resposta inteira.
     */
    public Optional<LoteCatalogo> buscarPorId(UUID loteId) {
        String url = propertyCatalogUrl + "/api/lotes/" + loteId;
        try {
            return Optional.ofNullable(restTemplate.getForObject(url, LoteCatalogo.class));
        } catch (HttpClientErrorException.NotFound ex) {
            log.debug("Lote {} nao encontrado no catalogo (removido/desativado) — ignorando", loteId);
            return Optional.empty();
        } catch (RestClientException ex) {
            log.warn("Falha ao buscar lote {} no property-catalog: {}", loteId, ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Lista lotes filtrados — usada para a busca de "lotes similares" a partir
     * do perfil implicito do usuario (E30-H3). Filtros ausentes (null) sao
     * omitidos da query, deixando o property-catalog aplicar apenas os presentes.
     */
    public List<LoteCatalogo> listar(String categoria, String status, String uf,
                                      BigDecimal valorMin, BigDecimal valorMax, int tamanho) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(propertyCatalogUrl + "/api/lotes")
                .queryParam("page", 0)
                .queryParam("size", tamanho);
        adicionarSePresente(builder, "categoria", categoria);
        adicionarSePresente(builder, "status", status);
        adicionarSePresente(builder, "uf", uf);
        adicionarSePresente(builder, "valorMin", valorMin);
        adicionarSePresente(builder, "valorMax", valorMax);

        try {
            PaginaLotesCatalogo pagina = restTemplate.getForObject(builder.toUriString(), PaginaLotesCatalogo.class);
            return pagina != null && pagina.content() != null ? pagina.content() : List.of();
        } catch (RestClientException ex) {
            log.warn("Falha ao listar lotes personalizados no property-catalog: {}", ex.getMessage());
            return List.of();
        }
    }

    /**
     * Mix diverso (popular + balanceado por categoria) — fallback de cold-start
     * e top-up quando a busca personalizada nao preenche o limite pedido.
     */
    public List<LoteCatalogo> vitrine(int porCategoria) {
        String url = UriComponentsBuilder.fromUriString(propertyCatalogUrl + "/api/lotes/vitrine")
                .queryParam("porCategoria", porCategoria)
                .toUriString();
        try {
            LoteCatalogo[] lotes = restTemplate.getForObject(url, LoteCatalogo[].class);
            return lotes != null ? List.of(lotes) : List.of();
        } catch (RestClientException ex) {
            log.warn("Falha ao buscar vitrine no property-catalog: {}", ex.getMessage());
            return List.of();
        }
    }

    private void adicionarSePresente(UriComponentsBuilder builder, String nome, Object valor) {
        if (valor != null) {
            builder.queryParam(nome, valor);
        }
    }
}
