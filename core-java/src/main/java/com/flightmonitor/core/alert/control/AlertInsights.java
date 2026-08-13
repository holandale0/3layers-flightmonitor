package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.Alert;

import java.math.BigDecimal;

import com.flightmonitor.core.stats.control.FlightScore;
import com.flightmonitor.core.stats.entity.GrauDeAnomalia;
import com.flightmonitor.core.stats.control.PriceAnomaly;

/**
 * O que a Fase 2 soube sobre uma oferta, no momento em que o alerta foi criado
 * — etapa E2.4.
 *
 * <h2>A regra desta etapa: na duvida, nao diz nada</h2>
 *
 * As tres etapas anteriores foram construidas para admitir ignorancia — a E2.1
 * marca estatistica com poucas amostras, a E2.2 devolve {@code SEM_DADOS} em vez
 * de {@code NORMAL}, a E2.3 renormaliza o que nao pode medir. Seria uma pena
 * jogar isso fora no ultimo passo, escrevendo "nota preliminar 68" no WhatsApp
 * de alguem.
 *
 * <p>O filtro fica <b>aqui</b>, em {@link #de(PriceAnomaly, FlightScore)}, e nao
 * espalhado por quem escreve a mensagem: um campo que nao se sustenta simplesmente
 * nao existe neste objeto. Um monitor recem-criado recebe exatamente a mensagem
 * que recebia antes desta etapa — limpa, sem ressalva e sem numero inventado.
 *
 * <h2>Por que fica gravado no alerta</h2>
 *
 * A entrega acontece <b>fora de transacao</b>, com a entidade desanexada (a
 * licao do BUG-006). Recalcular estatistica no canal significaria ir ao banco de
 * dentro do adaptador do WhatsApp — camada errada, e no momento errado.
 *
 * <p>Ha um motivo melhor ainda: o alerta deve registrar o que ele <b>sabia
 * quando decidiu</b>. Recalcular na entrega poderia produzir um numero diferente
 * do que motivou o alerta, e o historico passaria a mentir sobre o proprio
 * passado.
 *
 * @param quedaPercentual quanto abaixo da mediana, em pontos percentuais
 * @param explicacaoDaAnomalia frase completa, so para o canal de texto livre
 * @param resumoDoScore o aspecto que mais puxou a nota para cima
 */
public record AlertInsights(
        Integer nota,
        GrauDeAnomalia grau,
        BigDecimal quedaPercentual,
        String explicacaoDaAnomalia,
        String resumoDoScore) {

    public static AlertInsights vazio() {
        return new AlertInsights(null, null, null, null, null);
    }

    /**
     * Aplica os filtros de sustentacao e guarda so o que pode ser dito.
     *
     * <p>A anomalia exige mais do que existir: {@code NORMAL} e um veredito
     * legitimo e nao vira mensagem — ninguem quer notificacao dizendo que o
     * preco esta na media. A nota exige {@code confiavel}: calculada sobre
     * estatistica frouxa, ou sobre metade dos aspectos, ela viraria autoridade
     * que nao tem ao aparecer como "87/100".
     */
    public static AlertInsights de(PriceAnomaly anomalia, FlightScore score) {
        boolean anomaliaVale = anomalia != null
                && anomalia.interessante()
                && anomalia.explicacao() != null
                && !anomalia.explicacao().isBlank();

        boolean scoreVale = score != null && score.temNota() && score.confiavel();

        return new AlertInsights(
                scoreVale ? score.nota() : null,
                anomaliaVale ? anomalia.grau() : null,
                anomaliaVale ? anomalia.quedaPercentual() : null,
                anomaliaVale ? anomalia.explicacao() : null,
                scoreVale ? melhorAspecto(score) : null);
    }

    /** Reconstroi o que foi gravado, para a mensagem de template na entrega. */
    public static AlertInsights doAlerta(Alert alerta) {
        if (alerta == null) {
            return vazio();
        }
        // A explicacao completa nao e regravada: ela ja esta dentro de
        // alerta.message, que e o texto do canal livre.
        return new AlertInsights(
                alerta.getFlightScore() == null ? null : alerta.getFlightScore().intValue(),
                alerta.getAnomalyGrade(),
                alerta.getAnomalyDropPct(),
                null,
                null);
    }

    public boolean temAnomalia() {
        return grau != null;
    }

    public boolean temScore() {
        return nota != null;
    }

    /**
     * O aspecto do VOO que mais pesou a favor — sem contar o preco.
     *
     * <p>O preco ja aparece na propria mensagem, duas linhas acima, e na
     * comparacao historica. Repeti-lo aqui produzia frases como
     * <i>"Nota do voo: 82/100 — abaixo da mediana da rota"</i>, em que a
     * explicacao da nota do <b>voo</b> falava de <b>preco</b>.
     */
    private static String melhorAspecto(FlightScore score) {
        return score.componentes().stream()
                .filter(FlightScore.ComponenteDoScore::avaliado)
                .filter(c -> c.aspecto() != com.flightmonitor.core.stats.control.AspectoDoVoo.PRECO)
                .max(java.util.Comparator.comparingInt(FlightScore.ComponenteDoScore::nota))
                .map(FlightScore.ComponenteDoScore::detalhe)
                .orElse(null);
    }
}
