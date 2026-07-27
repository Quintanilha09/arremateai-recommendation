package com.arremateai.recommendation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Cache das recomendacoes heuristicas (E30-H3, ADR-009 Fase 1: "Cache
 * (Redis/Caffeine), TTL curto").
 *
 * <p><strong>Decisao de design:</strong> Caffeine (in-memory, por instancia) em vez
 * de Redis nesta historia. O property-catalog ja usa Redis para seus caches, mas
 * introduzir Redis aqui exigiria mais um container/dependencia so para um cache de
 * poucas dezenas de segundos, cuja unica funcao e amortecer rajadas de requisicoes
 * repetidas do mesmo usuario (a Home costuma disparar as duas chamadas de
 * recomendacao a cada navegacao). Caffeine resolve isso com custo operacional zero.
 * Migrar para Redis fica documentado como trabalho futuro caso o servico passe a
 * rodar com multiplas instancias (cache local deixaria de ser coerente entre elas).</p>
 *
 * <p>TTL curto (padrao 90s, configuravel via {@code app.cache.recomendacoes.ttl-segundos}):
 * suficiente para absorver picos de tela sem servir recomendacoes desatualizadas por
 * muito tempo — o perfil implicito do usuario muda pouco em escala de segundos.</p>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CACHE_PARA_VOCE = "para-voce";
    public static final String CACHE_VISTOS_RECENTEMENTE = "vistos-recentemente";

    private static final long TAMANHO_MAXIMO_CACHE = 10_000L;

    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "caffeine", matchIfMissing = true)
    public CacheManager cacheManager(@Value("${app.cache.recomendacoes.ttl-segundos:90}") long ttlSegundos) {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_PARA_VOCE, CACHE_VISTOS_RECENTEMENTE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSegundos))
                .maximumSize(TAMANHO_MAXIMO_CACHE));
        return cacheManager;
    }
}
