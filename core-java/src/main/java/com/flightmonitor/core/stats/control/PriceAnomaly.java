package com.flightmonitor.core.stats.control;

import com.flightmonitor.core.stats.entity.GrauDeAnomalia;

import java.math.BigDecimal;

/**
 * O veredito sobre um preco — etapa E2.2.
 *
 * @param quedaPercentual quanto o preco esta abaixo da mediana da rota, em
 *        pontos percentuais. <b>Negativo quando o preco esta acima</b> da
 *        mediana; nulo quando nao ha mediana com que comparar
 * @param limiteEstatistico abaixo deste valor o preco e atipico pela regra de
 *        Tukey. Nulo quando os quartis nao existem
 * @param explicacao frase pronta em portugues, para a mensagem da E2.4. Vazia
 *        quando nao ha o que dizer
 * @param referencia as estatisticas usadas no julgamento. Vai junto de proposito:
 *        um veredito sem a base que o produziu nao da para conferir nem depurar
 */
public record PriceAnomaly(
        BigDecimal preco,
        GrauDeAnomalia grau,
        BigDecimal quedaPercentual,
        BigDecimal limiteEstatistico,
        String explicacao,
        RouteStats referencia) {

    public static PriceAnomaly semDados(BigDecimal preco, RouteStats referencia, String motivo) {
        return new PriceAnomaly(preco, GrauDeAnomalia.SEM_DADOS, null, null, motivo, referencia);
    }

    /** Atalho para a regra de alerta: vale mencionar isto numa mensagem? */
    public boolean interessante() {
        return grau.vaiNaMensagem();
    }
}
