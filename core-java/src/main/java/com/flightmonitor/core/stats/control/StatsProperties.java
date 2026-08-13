package com.flightmonitor.core.stats.control;

import java.math.BigDecimal;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes das estatisticas de rota.
 *
 * @param janela periodo de observacoes considerado. Preco de seis meses atras
 *        nao descreve o mercado de hoje — combustivel, cambio e temporada mudam
 *        o patamar inteiro. Janela curta demais, por outro lado, nao acumula
 *        amostra suficiente para dizer o que e normal
 * @param minimoAmostras a partir de quantas observacoes tratamos o resultado
 *        como referencia. Abaixo disso os numeros ainda sao devolvidos — sao
 *        informacao —, mas marcados como nao confiaveis, e a E2.2 nao deve
 *        disparar anomalia com base neles. Estatistica de tres pontos alimenta
 *        um gerador de alarme falso, e alarme falso destroi a confianca no
 *        sistema mais rapido do que ausencia de alarme
 * @param fatorDeAnomalia o {@code k} da regra de Tukey (E2.2): atipico e o preco
 *        abaixo de {@code p25 - k x (p75 - p25)}. O valor classico e 1,5, que e
 *        o mesmo dos bigodes de um boxplot. Aumentar exige queda maior para o
 *        sistema se impressionar; diminuir faz quase toda oferta boa virar
 *        "atipica", e o superlativo perde o sentido por repeticao
 */
@ConfigurationProperties(prefix = "flightmonitor.stats")
public record StatsProperties(Duration janela, int minimoAmostras, BigDecimal fatorDeAnomalia) {

    public StatsProperties {
        janela = janela == null ? Duration.ofDays(90) : janela;
        if (minimoAmostras < 2) {
            // Menos de duas amostras nao permite nem desvio padrao.
            minimoAmostras = 2;
        }
        if (fatorDeAnomalia == null || fatorDeAnomalia.signum() <= 0) {
            fatorDeAnomalia = new BigDecimal("1.5");
        }
    }
}
