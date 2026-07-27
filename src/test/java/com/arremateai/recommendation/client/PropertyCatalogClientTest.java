package com.arremateai.recommendation.client;

import com.arremateai.recommendation.client.dto.LoteCatalogo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("PropertyCatalogClient — testes unitários")
class PropertyCatalogClientTest {

    private static final String BASE_URL = "http://property-catalog.test";

    private RestTemplate restTemplate;
    private MockRestServiceServer mockServer;
    private PropertyCatalogClient client;

    @BeforeEach
    void prepararCenario() {
        restTemplate = new RestTemplate();
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        client = new PropertyCatalogClient(restTemplate, BASE_URL);
    }

    @Test
    @DisplayName("Deve buscar lote por id com sucesso")
    void deveBuscarLotePorId() {
        UUID loteId = UUID.randomUUID();
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/lotes/{id}", loteId))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(loteJson(loteId, "IMOVEL", "SP"), MediaType.APPLICATION_JSON));

        Optional<LoteCatalogo> resultado = client.buscarPorId(loteId);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(loteId);
        assertThat(resultado.get().categoria()).isEqualTo("IMOVEL");
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve retornar Optional vazio graciosamente quando lote não existe (404)")
    void deveRetornarVazioQuandoLoteNaoExiste() {
        UUID loteId = UUID.randomUUID();
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/lotes/{id}", loteId))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        Optional<LoteCatalogo> resultado = client.buscarPorId(loteId);

        assertThat(resultado).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve retornar Optional vazio graciosamente em erro genérico de comunicação (5xx)")
    void deveRetornarVazioEmErroGenerico() {
        UUID loteId = UUID.randomUUID();
        mockServer.expect(requestToUriTemplate(BASE_URL + "/api/lotes/{id}", loteId))
                .andRespond(withServerError());

        Optional<LoteCatalogo> resultado = client.buscarPorId(loteId);

        assertThat(resultado).isEmpty();
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve montar query com todos os filtros presentes e retornar o conteúdo da página")
    void deveListarComFiltrosPresentes() {
        mockServer.expect(requestTo(startsWith(BASE_URL + "/api/lotes?")))
                .andExpect(queryParam("categoria", "IMOVEL"))
                .andExpect(queryParam("status", "ATIVO"))
                .andExpect(queryParam("uf", "SP"))
                .andExpect(queryParam("valorMin", "1000"))
                .andExpect(queryParam("valorMax", "2000"))
                .andExpect(queryParam("size", "12"))
                .andRespond(withSuccess(paginaJson(), MediaType.APPLICATION_JSON));

        List<LoteCatalogo> resultado = client.listar("IMOVEL", "ATIVO", "SP",
                BigDecimal.valueOf(1000), BigDecimal.valueOf(2000), 12);

        assertThat(resultado).hasSize(1);
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve omitir filtros ausentes da query (busca sem categoria/uf/faixa)")
    void deveOmitirFiltrosAusentes() {
        mockServer.expect(requestTo(startsWith(BASE_URL + "/api/lotes?")))
                .andExpect(queryParam("size", "12"))
                .andRespond(withSuccess(paginaJson(), MediaType.APPLICATION_JSON));

        List<LoteCatalogo> resultado = client.listar(null, null, null, null, null, 12);

        assertThat(resultado).hasSize(1);
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve retornar lista vazia (não propagar exceção) quando listar falha")
    void deveRetornarListaVaziaQuandoListarFalha() {
        mockServer.expect(requestTo(startsWith(BASE_URL + "/api/lotes?")))
                .andRespond(withServerError());

        List<LoteCatalogo> resultado = client.listar("IMOVEL", "ATIVO", null, null, null, 12);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("Deve buscar vitrine com sucesso")
    void deveBuscarVitrineComSucesso() {
        UUID loteId = UUID.randomUUID();
        mockServer.expect(requestTo(BASE_URL + "/api/lotes/vitrine?porCategoria=4"))
                .andRespond(withSuccess("[" + loteJson(loteId, "VEICULO", "RJ") + "]", MediaType.APPLICATION_JSON));

        List<LoteCatalogo> resultado = client.vitrine(4);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).id()).isEqualTo(loteId);
        mockServer.verify();
    }

    @Test
    @DisplayName("Deve retornar lista vazia (não propagar exceção) quando vitrine falha")
    void deveRetornarListaVaziaQuandoVitrineFalha() {
        mockServer.expect(requestTo(BASE_URL + "/api/lotes/vitrine?porCategoria=4"))
                .andRespond(withServerError());

        List<LoteCatalogo> resultado = client.vitrine(4);

        assertThat(resultado).isEmpty();
    }

    private String loteJson(UUID id, String categoria, String uf) {
        return """
                {"id":"%s","categoria":"%s","subcategoria":"apartamento","titulo":"Lote teste",
                "valorAvaliacao":1500.00,"lanceAtual":null,"uf":"%s","cidade":"Cidade Teste",
                "dataEncerramento":"2026-12-01T10:00:00","status":"ATIVO","destaque":false,
                "fotosUrls":["http://foto.teste/1.jpg"],"descricao":"ignorado","moeda":"BRL"}
                """.formatted(id, categoria, uf);
    }

    private String paginaJson() {
        return """
                {"content":[%s],"totalElements":1,"totalPages":1,"number":0,"size":12}
                """.formatted(loteJson(UUID.randomUUID(), "IMOVEL", "SP"));
    }
}
