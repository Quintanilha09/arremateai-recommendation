package com.arremateai.recommendation.exception;

/**
 * Lancada quando um endpoint que exige usuario logado (E30-H3: "para-voce" e
 * "vistos-recentemente") recebe requisicao sem {@code X-User-Id} valido.
 *
 * <p>Diferente do endpoint de ingestao de eventos (E30-H2), que aceita autenticacao
 * opcional, estes dois endpoints sao exclusivos de usuario autenticado — o Gateway
 * ja exige JWT por padrao em qualquer path {@code /api/recomendacoes/**} que nao
 * seja o POST de eventos, entao chegar aqui sem o header e sempre um erro.</p>
 */
public class UsuarioNaoAutenticadoException extends RuntimeException {

    public UsuarioNaoAutenticadoException() {
        super("Requisicao exige usuario autenticado (X-User-Id ausente ou invalido)");
    }
}
