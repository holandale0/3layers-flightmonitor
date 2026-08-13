package com.flightmonitor.core.stats.control;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Tendencia de preco de uma rota — etapa E2.5.
 *
 * <p>Primeira analise do projeto que trata o historico como <b>serie
 * temporal</b>, e nao como distribuicao. As E2.1 a E2.3 perguntam "onde este
 * preco esta em relacao aos outros"; esta pergunta "para onde os precos vem
 * andando".
 *
 * @param variacaoSemanal variacao percentual estimada por semana. Negativa
 *        quando o preco cai. Nula quando nao ha inclinacao calculavel
 * @param diasComDados quantos dias distintos entraram na serie. E o numero que
 *        importa para a confianca — cem observacoes num unico dia nao dizem
 *        nada sobre tendencia
 * @param serie os pontos usados, para conferencia e para desenhar no painel
 */
public record PriceTrend(
        String origin,
        String destination,
        FonteDeStats fonte,
        DirecaoDaTendencia direcao,
        BigDecimal variacaoSemanal,
        int diasComDados,
        int amostras,
        boolean confiavel,
        List<PontoDaSerie> serie,
        String explicacao) {

    public PriceTrend {
        serie = serie == null ? List.of() : List.copyOf(serie);
    }

    public static PriceTrend semDados(
            String origin, String destination, FonteDeStats fonte,
            List<PontoDaSerie> serie, String motivo) {

        int dias = serie == null ? 0 : serie.size();
        int amostras = serie == null ? 0
                : serie.stream().mapToInt(PontoDaSerie::amostras).sum();

        return new PriceTrend(origin, destination, fonte, DirecaoDaTendencia.SEM_DADOS,
                null, dias, amostras, false, serie, motivo);
    }

    /**
     * Um dia da serie.
     *
     * @param mediana mediana dos precos observados naquele dia. Mediana e nao
     *        media pela mesma razao da E2.1: um preco absurdo num dia moveria a
     *        media e inventaria um degrau na serie
     */
    public record PontoDaSerie(LocalDate dia, int amostras, BigDecimal mediana) {
    }
}
