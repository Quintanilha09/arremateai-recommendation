package com.arremateai.recommendation.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler — testes unitários")
class GlobalExceptionHandlerTest {

    private static final String ENDPOINT = "/api/recomendacoes/eventos";

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("Deve montar ProblemDetail 400 a partir de ConstraintViolationException com mensagem presente")
    void deveMontarProblemaParaConstraintViolationComMensagem() {
        ConstraintViolationException excecao = new ConstraintViolationException(
                Set.of(violacao("anonId", "não pode ser vazio")));

        ProblemDetail problema = handler.handleConstraintViolation(excecao, requisicao());

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getTitle()).isEqualTo("Dados de entrada inválidos");
        assertThat(problema.getType().toString()).isEqualTo("urn:arremateai:error:validation");
        assertThat(problema.getInstance().toString()).isEqualTo(ENDPOINT);
        assertThat(problema.getProperties()).containsKey("errors");
    }

    @Test
    @DisplayName("Deve usar mensagem padrão 'inválido' quando ConstraintViolation não possui mensagem")
    void deveUsarMensagemPadraoQuandoConstraintViolationSemMensagem() {
        ConstraintViolationException excecao = new ConstraintViolationException(
                Set.of(violacao("eventType", null)));

        ProblemDetail problema = handler.handleConstraintViolation(excecao, requisicao());

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problema.getProperties()).containsKey("errors");
    }

    @Test
    @DisplayName("Deve montar ProblemDetail 500 para exceção genérica não tratada")
    void deveMontarProblema500ParaExcecaoGenerica() {
        ProblemDetail problema = handler.handleGeneric(new RuntimeException("erro simulado"), requisicao());

        assertThat(problema.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problema.getTitle()).isEqualTo("Erro interno do servidor");
        assertThat(problema.getType().toString()).isEqualTo("urn:arremateai:error:internal");
        assertThat(problema.getInstance().toString()).isEqualTo(ENDPOINT);
    }

    private ConstraintViolation<Object> violacao(String propriedade, String mensagem) {
        @SuppressWarnings("unchecked")
        ConstraintViolation<Object> violacao = mock(ConstraintViolation.class);
        Path caminho = mock(Path.class);
        when(caminho.toString()).thenReturn(propriedade);
        when(violacao.getPropertyPath()).thenReturn(caminho);
        when(violacao.getMessage()).thenReturn(mensagem);
        return violacao;
    }

    private MockHttpServletRequest requisicao() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ENDPOINT);
        request.setRequestURI(ENDPOINT);
        return request;
    }
}
