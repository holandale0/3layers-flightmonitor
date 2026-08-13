package com.flightmonitor.core.stats.control;

import com.flightmonitor.core.stats.entity.GrauDeAnomalia;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

import org.springframework.stereotype.Service;

/**
 * Diz se um preco e incomum para a rota — etapa E2.2.
 *
 * <h2>Regra robusta, e nao z-score</h2>
 *
 * A escolha obvia seria "quantos desvios padrao abaixo da media". Seria errada:
 * z-score pressupoe distribuicao simetrica, e preco de passagem nao e nem perto
 * disso. A cauda longa para cima infla o desvio padrao, e o resultado e um
 * detector que precisa de uma queda enorme para reagir — justamente na rota
 * onde uma passagem cara distorceu a amostra.
 *
 * <p>Usamos a <b>regra de Tukey</b>, a mesma que desenha os bigodes de um
 * boxplot: atipico e o que fica abaixo de {@code p25 - k x (p75 - p25)}. Ela e
 * construida sobre quartis, entao um preco absurdo na amostra nao a desloca.
 *
 * <h2>O preco avaliado costuma estar na propria referencia</h2>
 *
 * Quando a regra de alerta chama este servico, a observacao ja foi gravada — ela
 * entra na mediana contra a qual esta sendo comparada. O efeito e pequeno com
 * amostra suficiente, e vai sempre na direcao <b>conservadora</b>: um preco
 * muito baixo puxa a mediana para baixo e subestima a propria anomalia.
 *
 * <p>Errar para menos e o lado certo de errar num sistema de alerta. Excluir a
 * observacao exigiria uma consulta diferente por chamada, e o ganho seria menor
 * que a complexidade.
 */
@Service
public class PriceAnomalyService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final Locale BR = Locale.of("pt", "BR");

    private final RouteStatsService estatisticas;
    private final StatsProperties props;

    public PriceAnomalyService(RouteStatsService estatisticas, StatsProperties props) {
        this.estatisticas = estatisticas;
        this.props = props;
    }

    /**
     * Avalia um preco contra a historia da rota.
     *
     * @param fonte qual historia usar. Julgar um preco <b>confirmado</b> contra
     *        estatistica de cache e o erro que a D-060 descreve — o cache
     *        subestima, entao o preco real pareceria caro e o alerta nao sairia
     */
    public PriceAnomaly avaliar(
            String origin, String destination, BigDecimal preco, FonteDeStats fonte) {

        RouteStats ref = estatisticas.resumir(origin, destination, fonte);
        return avaliar(preco, ref);
    }

    /** Mesma avaliacao, com as estatisticas ja em maos — evita reconsultar. */
    public PriceAnomaly avaliar(BigDecimal preco, RouteStats ref) {
        if (preco == null || preco.signum() <= 0) {
            return PriceAnomaly.semDados(preco, ref, "preco ausente ou invalido");
        }
        if (ref == null || !ref.temDados()) {
            return PriceAnomaly.semDados(preco, ref, "esta rota ainda nao tem historico");
        }
        if (!ref.confiavel()) {
            // O numero existe, mas nao sustenta veredito. Deixar passar aqui
            // transformaria a E2.4 num gerador de superlativo sobre tres
            // observacoes — e alarme falso custa mais que alarme ausente.
            return PriceAnomaly.semDados(preco, ref,
                    "so ha %d observacao(oes); e pouco para dizer o que e normal"
                            .formatted(ref.amostras()));
        }

        BigDecimal queda = quedaPercentual(ref.mediana(), preco);
        BigDecimal limite = limiteDeTukey(ref);
        GrauDeAnomalia grau = classificar(preco, ref, limite);

        return new PriceAnomaly(preco, grau, queda, limite, explicar(grau, preco, queda, ref), ref);
    }

    /**
     * A ordem vai do mais forte para o mais fraco.
     *
     * <p>Um recorde tambem esta abaixo do primeiro quartil; dizer "entre os 25%
     * mais baratos" quando e o mais barato de todos seria verdade e diria menos.
     */
    private GrauDeAnomalia classificar(BigDecimal preco, RouteStats ref, BigDecimal limite) {
        if (ref.minimo() != null && preco.compareTo(ref.minimo()) <= 0) {
            return GrauDeAnomalia.RECORDE;
        }
        if (limite != null && preco.compareTo(limite) < 0) {
            return GrauDeAnomalia.EXCELENTE;
        }
        if (ref.p25() != null && preco.compareTo(ref.p25()) <= 0) {
            return GrauDeAnomalia.BOM;
        }
        return GrauDeAnomalia.NORMAL;
    }

    /**
     * {@code p25 - k x (p75 - p25)}, o bigode inferior do boxplot.
     *
     * @return nulo quando os quartis nao existem, ou quando o limite cairia em
     *         zero ou abaixo — numa rota muito instavel a formula produz um
     *         limite negativo, que nenhum preco alcanca. Devolver esse limite
     *         faria a resposta prometer um patamar impossivel
     */
    private BigDecimal limiteDeTukey(RouteStats ref) {
        if (ref.p25() == null || ref.p75() == null) {
            return null;
        }
        BigDecimal intervalo = ref.p75().subtract(ref.p25());
        BigDecimal limite = ref.p25()
                .subtract(intervalo.multiply(props.fatorDeAnomalia()))
                .setScale(2, RoundingMode.HALF_UP);

        return limite.signum() > 0 ? limite : null;
    }

    /** Quanto o preco esta abaixo da mediana. Negativo quando esta acima. */
    private BigDecimal quedaPercentual(BigDecimal mediana, BigDecimal preco) {
        if (mediana == null || mediana.signum() <= 0) {
            return null;
        }
        return mediana.subtract(preco)
                .multiply(CEM)
                .divide(mediana, 1, RoundingMode.HALF_UP);
    }

    /**
     * A frase que a E2.4 vai colar na mensagem.
     *
     * <p>Escrita para quem nao quer saber de estatistica: fala em "mais barato
     * que", "entre os 25% mais baratos" e "menor preco", nunca em quartil ou
     * desvio padrao. O numero da base vai junto porque uma porcentagem sem
     * referencia nao significa nada.
     */
    private String explicar(
            GrauDeAnomalia grau, BigDecimal preco, BigDecimal queda, RouteStats ref) {

        String periodo = "nos ultimos %d dias".formatted(props.janela().toDays());

        return switch (grau) {
            case RECORDE -> "menor preco visto %s nesta rota".formatted(periodo);
            case EXCELENTE -> "%s%% abaixo da mediana de %s — preco atipico para esta rota"
                    .formatted(queda, dinheiro(ref.mediana()));
            case BOM -> "%s%% abaixo da mediana de %s, entre os 25%% mais baratos %s"
                    .formatted(queda, dinheiro(ref.mediana()), periodo);
            case NORMAL -> "dentro da faixa normal da rota (mediana %s)"
                    .formatted(dinheiro(ref.mediana()));
            case SEM_DADOS -> "";
        };
    }

    private String dinheiro(BigDecimal valor) {
        if (valor == null) {
            return "-";
        }
        // Espaco nao-quebravel do pt-BR trocado por espaco comum: ele viaja ate
        // o parametro do template do WhatsApp, onde a Meta o recusa (BUG do
        // formatador, na E1.12).
        return NumberFormat.getCurrencyInstance(BR).format(valor).replace(' ', ' ');
    }
}
