package com.arremateai.recommendation.controller;

import com.arremateai.recommendation.dto.LoteRecomendadoResponse;
import com.arremateai.recommendation.exception.UsuarioNaoAutenticadoException;
import com.arremateai.recommendation.service.RecomendacaoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Recomendacao heuristica content-based (E30-H3, ADR-009 Fase 1).
 *
 * <p>Ambos os endpoints exigem usuario autenticado: o Gateway ja exige JWT por
 * padrao em qualquer path {@code /api/recomendacoes/**} que nao seja o POST de
 * eventos (autenticacao opcional, E30-H2), e propaga {@code X-User-Id} valido
 * nessa situacao. Se o header estiver ausente ou invalido aqui, e sempre um
 * erro — nunca degradamos silenciosamente para anonimo, ao contrario do
 * endpoint de ingestao de eventos.</p>
 */
@RestController
@RequestMapping("/api/recomendacoes")
@RequiredArgsConstructor
public class RecomendacaoController {

    private final RecomendacaoService recomendacaoService;

    /**
     * "Recomendados para voce": personalizado pelo perfil implicito do usuario,
     * com fallback para mix popular+diverso quando nao ha sinal suficiente
     * (cold-start). Nunca retorna lista vazia enquanto houver lote no catalogo.
     */
    @GetMapping("/para-voce")
    public ResponseEntity<List<LoteRecomendadoResponse>> paraVoce(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "12") int limite) {
        UUID userId = exigirUserId(userIdHeader);
        return ResponseEntity.ok(recomendacaoService.paraVoce(userId, limite));
    }

    /** "Voce viu recentemente": ultimos lotes com evento VIEW do usuario. */
    @GetMapping("/vistos-recentemente")
    public ResponseEntity<List<LoteRecomendadoResponse>> vistosRecentemente(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestParam(defaultValue = "12") int limite) {
        UUID userId = exigirUserId(userIdHeader);
        return ResponseEntity.ok(recomendacaoService.vistosRecentemente(userId, limite));
    }

    private UUID exigirUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new UsuarioNaoAutenticadoException();
        }
        try {
            return UUID.fromString(userIdHeader);
        } catch (IllegalArgumentException ex) {
            throw new UsuarioNaoAutenticadoException();
        }
    }
}
