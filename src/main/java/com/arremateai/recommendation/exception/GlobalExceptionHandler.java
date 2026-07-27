package com.arremateai.recommendation.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tratador global de excecoes no formato RFC 7807 (application/problem+json),
 * seguindo o mesmo padrao adotado no {@code arremateai-property-catalog}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String TYPE_PREFIX = "urn:arremateai:error:";
    private static final String DETALHE_VALIDACAO = "Um ou mais campos não passaram na validação.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest requisicao) {
        List<Map<String, String>> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(campo -> Map.of(
                        "field", campo.getField(),
                        "message", Optional.ofNullable(campo.getDefaultMessage()).orElse("inválido")))
                .toList();
        log.warn("Validação falhou em {}: {} erro(s)", requisicao.getRequestURI(), erros.size());
        ProblemDetail problema = construirProblema(HttpStatus.BAD_REQUEST,
                "Dados de entrada inválidos", DETALHE_VALIDACAO, "validation", requisicao);
        problema.setProperty("errors", erros);
        return problema;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest requisicao) {
        List<Map<String, String>> erros = ex.getConstraintViolations().stream()
                .map(violacao -> Map.of(
                        "field", violacao.getPropertyPath().toString(),
                        "message", Optional.ofNullable(violacao.getMessage()).orElse("inválido")))
                .toList();
        log.warn("Violação de constraint em {}: {} erro(s)", requisicao.getRequestURI(), erros.size());
        ProblemDetail problema = construirProblema(HttpStatus.BAD_REQUEST,
                "Dados de entrada inválidos", DETALHE_VALIDACAO, "validation", requisicao);
        problema.setProperty("errors", erros);
        return problema;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                      HttpServletRequest requisicao) {
        log.warn("JSON inválido ou enum desconhecido em {}: {}",
                requisicao.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return construirProblema(HttpStatus.BAD_REQUEST, "JSON inválido ou enum desconhecido",
                "Corpo da requisição não pôde ser desserializado.", "invalid-payload", requisicao);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex, HttpServletRequest requisicao) {
        log.error("Erro interno não tratado em {}", requisicao.getRequestURI(), ex);
        return construirProblema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor",
                "Erro interno do servidor", "internal", requisicao);
    }

    private ProblemDetail construirProblema(HttpStatus status, String titulo, String detalhe,
                                             String tipoSufixo, HttpServletRequest requisicao) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(status,
                detalhe == null ? "" : detalhe);
        problema.setTitle(titulo);
        problema.setType(URI.create(TYPE_PREFIX + tipoSufixo));
        problema.setInstance(URI.create(requisicao.getRequestURI()));
        problema.setProperty("timestamp", OffsetDateTime.now().toString());
        problema.setProperty("path", requisicao.getRequestURI());
        return problema;
    }
}
