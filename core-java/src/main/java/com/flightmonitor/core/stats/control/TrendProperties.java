package com.flightmonitor.core.stats.control;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes da tendencia de preco — etapa E2.5.
 *
 * @param janela periodo da serie. Mais curta que a janela das estatisticas de
 *        propositO: tendencia e sobre o movimento <b>recente</b>, e noventa dias
 *        de historico diluiriam uma queda que comecou na semana passada
 * @param minimoDeDias abaixo disso nao ha serie, e a resposta e SEM_DADOS.
 *        Conta <b>dias distintos</b>, e nao observacoes: cem precos coletados no
 *        mesmo dia nao dizem nada sobre movimento no tempo
 * @param diasParaConfiar a partir de quantos dias o resultado vale como
 *        referencia. Entre o minimo e este numero a tendencia e devolvida
 *        marcada como nao confiavel — e informacao, mas nao veredito
 * @param limiarPercentualSemanal variacao abaixo da qual chamamos de estavel.
 *        Sem ele, oscilacao de centavos viraria "subindo" ou "caindo" e a
 *        informacao perderia o sentido por nunca ser estavel
 */
@ConfigurationProperties(prefix = "flightmonitor.trend")
public record TrendProperties(
        Duration janela,
        int minimoDeDias,
        int diasParaConfiar,
        BigDecimal limiarPercentualSemanal) {

    public TrendProperties {
        janela = janela == null ? Duration.ofDays(30) : janela;

        if (minimoDeDias < 3) {
            // Com dois pontos qualquer reta passa exatamente por eles, e a
            // "tendencia" seria so o ruido entre duas medicoes.
            minimoDeDias = 3;
        }
        if (diasParaConfiar < minimoDeDias) {
            diasParaConfiar = Math.max(minimoDeDias, 7);
        }
        if (limiarPercentualSemanal == null || limiarPercentualSemanal.signum() <= 0) {
            limiarPercentualSemanal = new BigDecimal("2.0");
        }
    }
}
