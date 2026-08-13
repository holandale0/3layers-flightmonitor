package com.flightmonitor.core.stats.control;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Estatisticas de preco de uma rota — etapa E2.1.
 *
 * <p>E o insumo de tudo que vem depois na Fase 2: a deteccao de anomalia (E2.2)
 * pergunta "quanto este preco esta abaixo do normal", e "normal" e o que este
 * objeto define.
 *
 * <h2>Por que ha tantos numeros, e nao so a media</h2>
 *
 * A media sozinha engana em preco de passagem. A distribuicao e assimetrica: ha
 * poucos precos muito altos (compra de ultima hora, alta temporada) que puxam a
 * media para cima e fazem um preco medíocre parecer bom. A <b>mediana</b> nao se
 * move com isso.
 *
 * <p>O <b>desvio padrao</b> responde outra pergunta: se a rota varia pouco, uma
 * queda de 10% e noticia; se varia muito, 10% e terca-feira. Sem ele, o mesmo
 * limiar percentual serviria para rotas que nao se parecem.
 *
 * <p>Os <b>quartis</b> dao a regra pratica: um preco abaixo do primeiro quartil
 * esta entre os 25% mais baratos ja vistos, e isso e dizivel para o usuario sem
 * explicar estatistica.
 *
 * @param fonte      quais observacoes entraram na conta. Misturar cache e preco
 *        confirmado produz um "normal" que nao existe — ver {@link FonteDeStats}
 * @param amostras   quantas observacoes sustentam estes numeros
 * @param confiavel  se ha amostras suficientes para tratar isto como referencia.
 *        Estatistica de tres observacoes nao e estatistica, e um detector de
 *        anomalia alimentado por ela vira gerador de alarme falso
 * @param desvioPadrao nulo quando ha menos de duas amostras — desvio de um
 *        unico ponto nao existe, e devolver zero mentiria dizendo "rota estavel"
 */
public record RouteStats(
        String origin,
        String destination,
        FonteDeStats fonte,
        int amostras,
        boolean confiavel,
        BigDecimal minimo,
        BigDecimal p25,
        BigDecimal mediana,
        BigDecimal media,
        BigDecimal p75,
        BigDecimal maximo,
        BigDecimal desvioPadrao,
        Instant primeiraObservacao,
        Instant ultimaObservacao) {

    /** Nenhuma observacao no periodo: a rota existe, a historia ainda nao. */
    public static RouteStats vazio(String origin, String destination, FonteDeStats fonte) {
        return new RouteStats(
                origin, destination, fonte, 0, false,
                null, null, null, null, null, null, null, null, null);
    }

    public boolean temDados() {
        return amostras > 0;
    }

    /**
     * Coeficiente de variacao: o desvio padrao como fracao da media.
     *
     * <p>E o que permite comparar a instabilidade de rotas com precos de ordens
     * diferentes — 200 reais de desvio significam coisas opostas numa rota de
     * 800 e numa de 8.000.
     *
     * @return nulo quando nao ha desvio calculavel
     */
    public BigDecimal coeficienteDeVariacao() {
        if (desvioPadrao == null || media == null || media.signum() <= 0) {
            return null;
        }
        return desvioPadrao.divide(media, 4, java.math.RoundingMode.HALF_UP);
    }
}
