package com.flightmonitor.core.agent.control;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.agent.control.Recomendacao.Razao;
import com.flightmonitor.core.agent.control.Recomendacao.Razao.Aspecto;
import com.flightmonitor.core.agent.control.Recomendacao.Razao.Peso;
import com.flightmonitor.core.agent.control.Recomendacao.Veredito;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.stats.control.AspectoDoVoo;
import com.flightmonitor.core.stats.control.DirecaoDaTendencia;
import com.flightmonitor.core.stats.control.FlightScore;
import com.flightmonitor.core.stats.control.FlightScoreService;
import com.flightmonitor.core.stats.control.FonteDeStats;
import com.flightmonitor.core.stats.control.PriceAnomaly;
import com.flightmonitor.core.stats.control.PriceAnomalyService;
import com.flightmonitor.core.stats.control.PriceTrend;
import com.flightmonitor.core.stats.control.PriceTrendService;
import com.flightmonitor.core.stats.control.RouteStats;
import com.flightmonitor.core.stats.control.RouteStatsService;

/**
 * Explica por que uma oferta vale a pena — etapa E3.3.
 *
 * <h2>Por que isto NAO usa modelo de linguagem</h2>
 *
 * A tentacao e obvia: mandar as quatro analises para um LLM e pedir um paragrafo
 * bonito. Foi avaliado e descartado — ver D-084. Em resumo:
 *
 * <ul>
 *   <li><b>Nao ha o que interpretar.</b> A entrada ja e um conjunto de fatos
 *       calculados, cada um com frase pronta em portugues, produzida pelas
 *       etapas E2.2, E2.3 e E2.5. Sobra composicao, que e determinismo;</li>
 *   <li><b>O risco e assimetrico.</b> O ganho seria fluencia; a perda possivel e
 *       uma frase inventada — "melhor momento para comprar", "promocao por tempo
 *       limitado" — que este sistema passou tres fases se recusando a dizer.</li>
 * </ul>
 *
 * <p>O modelo continua util onde havia ambiguidade de verdade: interpretar o
 * pedido de quem escreve (E3.1). Aqui nao ha ambiguidade, ha aritmetica.
 *
 * <h2>Nada de conselho</h2>
 *
 * A D-072 vale integralmente: o texto informa e nao manda. "Vale muito a pena" e
 * um veredito sobre a <b>oferta</b>; "compre agora" seria um conselho sobre a
 * <b>decisao</b>, que nao e do sistema.
 */
@Service
public class RecomendacaoService {

    private final RouteStatsService estatisticas;
    private final PriceAnomalyService anomalias;
    private final FlightScoreService score;
    private final PriceTrendService tendencias;

    public RecomendacaoService(
            RouteStatsService estatisticas,
            PriceAnomalyService anomalias,
            FlightScoreService score,
            PriceTrendService tendencias) {
        this.estatisticas = estatisticas;
        this.anomalias = anomalias;
        this.score = score;
        this.tendencias = tendencias;
    }

    /**
     * Reune as quatro analises e compoe a recomendacao.
     *
     * <p>Base <b>CONFIRMADAS</b>: julgar um preco verificado ao vivo contra
     * estatistica de cache e o erro da D-060 — o cache subestima, e o preco real
     * pareceria caro.
     */
    @Transactional(readOnly = true)
    public Recomendacao recomendar(PriceObservation oferta, Monitor monitor) {
        if (oferta == null) {
            return new Recomendacao(Veredito.SEM_BASE, List.of(),
                    "nao ha oferta para avaliar", false);
        }

        RouteStats ref = estatisticas.resumir(
                oferta.getOrigin(), oferta.getDestination(), FonteDeStats.CONFIRMADAS);

        PriceAnomaly anomalia = anomalias.avaliar(oferta.getPrice(), ref);
        FlightScore nota = score.pontuar(oferta, FonteDeStats.CONFIRMADAS, monitor);
        PriceTrend tendencia = tendencias.analisar(
                oferta.getOrigin(), oferta.getDestination(), FonteDeStats.CONFIRMADAS);

        return compor(oferta, ref, anomalia, nota, tendencia);
    }

    /** Separado da busca para poder ser testado com analises montadas a mao. */
    public Recomendacao compor(
            PriceObservation oferta,
            RouteStats ref,
            PriceAnomaly anomalia,
            FlightScore nota,
            PriceTrend tendencia) {

        List<Razao> razoes = new ArrayList<>();

        razaoDePreco(anomalia).ifPresent(razoes::add);
        razaoDeVoo(nota).ifPresent(razoes::add);
        razaoDeTendencia(tendencia).ifPresent(razoes::add);
        razaoDeHistorico(ref).ifPresent(razoes::add);

        boolean confiavel = ref != null && ref.confiavel();
        Veredito veredito = decidir(confiavel, razoes);

        return new Recomendacao(veredito, razoes, redigir(veredito, razoes, confiavel), confiavel);
    }

    // ------------------------------------------------------------ razoes

    private java.util.Optional<Razao> razaoDePreco(PriceAnomaly anomalia) {
        if (anomalia == null || anomalia.explicacao() == null || anomalia.explicacao().isBlank()) {
            return java.util.Optional.empty();
        }

        return switch (anomalia.grau()) {
            case RECORDE, EXCELENTE, BOM -> java.util.Optional.of(
                    new Razao(Aspecto.PRECO, Peso.A_FAVOR, anomalia.explicacao()));
            case NORMAL -> java.util.Optional.of(
                    new Razao(Aspecto.PRECO, Peso.A_PONDERAR, anomalia.explicacao()));
            // SEM_DADOS nao vira razao: "nao medi" nao e argumento.
            case SEM_DADOS -> java.util.Optional.empty();
        };
    }

    private java.util.Optional<Razao> razaoDeVoo(FlightScore nota) {
        if (nota == null || !nota.temNota() || !nota.confiavel()) {
            return java.util.Optional.empty();
        }

        String melhor = detalheDoMelhor(nota);
        String pior = detalheDoPior(nota);

        if (nota.nota() >= 80) {
            return java.util.Optional.of(new Razao(Aspecto.VOO, Peso.A_FAVOR,
                    "voo bom: %s (nota %d/100)".formatted(melhor, nota.nota())));
        }
        if (nota.nota() < 50) {
            return java.util.Optional.of(new Razao(Aspecto.VOO, Peso.CONTRA,
                    "voo pouco confortavel: %s (nota %d/100)".formatted(pior, nota.nota())));
        }
        return java.util.Optional.of(new Razao(Aspecto.VOO, Peso.A_PONDERAR,
                "voo mediano: %s (nota %d/100)".formatted(pior, nota.nota())));
    }

    private java.util.Optional<Razao> razaoDeTendencia(PriceTrend tendencia) {
        if (tendencia == null || tendencia.direcao() == DirecaoDaTendencia.SEM_DADOS) {
            return java.util.Optional.empty();
        }

        return switch (tendencia.direcao()) {
            // Subindo pesa A FAVOR de agir: o preco de hoje tende a ser melhor
            // que o de depois. Continua sendo constatacao, e nao ordem.
            case SUBINDO -> java.util.Optional.of(
                    new Razao(Aspecto.TENDENCIA, Peso.A_FAVOR, tendencia.explicacao()));
            // Caindo NAO e "contra": nao piora a oferta, so torna razoavel
            // esperar. Marcar como contra seria conselho disfarcado de fato.
            case CAINDO -> java.util.Optional.of(
                    new Razao(Aspecto.TENDENCIA, Peso.A_PONDERAR, tendencia.explicacao()));
            case ESTAVEL -> java.util.Optional.of(
                    new Razao(Aspecto.TENDENCIA, Peso.A_PONDERAR, tendencia.explicacao()));
            case SEM_DADOS -> java.util.Optional.empty();
        };
    }

    private java.util.Optional<Razao> razaoDeHistorico(RouteStats ref) {
        if (ref == null || !ref.temDados()) {
            return java.util.Optional.empty();
        }

        String frase = ref.confiavel()
                ? "comparado com %d precos confirmados desta rota".formatted(ref.amostras())
                : "baseado em apenas %d preco(s) confirmado(s): ainda e pouco".formatted(ref.amostras());

        return java.util.Optional.of(new Razao(Aspecto.HISTORICO, Peso.A_PONDERAR, frase));
    }

    // ---------------------------------------------------------- veredito

    /**
     * Conta os lados, com o preco valendo mais.
     *
     * <p>O preco pesa dobrado pelo mesmo motivo da D-066: o sistema existe para
     * achar passagem barata, e uma recomendacao que premiasse conforto acima de
     * preco contradiria o produto.
     */
    private Veredito decidir(boolean confiavel, List<Razao> razoes) {
        if (!confiavel) {
            // Sem base estatistica, qualquer veredito seria chute com cara de
            // analise. A E2.2 ja recusa julgar aqui; seria estranho a E3.3
            // julgar assim mesmo.
            return Veredito.SEM_BASE;
        }

        int placar = 0;
        for (Razao r : razoes) {
            int peso = r.aspecto() == Aspecto.PRECO ? 2 : 1;
            if (r.peso() == Peso.A_FAVOR) {
                placar += peso;
            } else if (r.peso() == Peso.CONTRA) {
                placar -= peso;
            }
        }

        if (placar >= 3) {
            return Veredito.VALE_MUITO;
        }
        if (placar >= 1) {
            return Veredito.VALE;
        }
        if (placar <= -1) {
            return Veredito.NAO_RECOMENDO;
        }
        return Veredito.TALVEZ;
    }

    // ------------------------------------------------------------ texto

    /**
     * O paragrafo que a pessoa le.
     *
     * <p>Comeca pelo veredito, segue com o que pesa a favor, depois o que pesa
     * contra, e fecha com o que ha para ponderar. A ordem nao e estetica: quem
     * le uma notificacao no celular le a primeira linha e decide se continua.
     */
    private String redigir(Veredito veredito, List<Razao> razoes, boolean confiavel) {
        if (!confiavel) {
            String base = razoes.stream()
                    .filter(r -> r.aspecto() == Aspecto.HISTORICO)
                    .map(Razao::frase)
                    .findFirst()
                    .orElse("esta rota ainda nao tem historico confirmado");
            return "ainda nao da para dizer se o preco e bom: " + base;
        }

        StringBuilder texto = new StringBuilder(fraseDoVeredito(veredito));

        List<String> aFavor = frasesDe(razoes, Peso.A_FAVOR);
        List<String> contra = frasesDe(razoes, Peso.CONTRA);
        List<String> ponderar = frasesDe(razoes, Peso.A_PONDERAR);

        if (!aFavor.isEmpty()) {
            texto.append(" — ").append(String.join("; ", aFavor));
        }
        if (!contra.isEmpty()) {
            texto.append(". Por outro lado, ").append(String.join("; ", contra));
        }
        if (!ponderar.isEmpty()) {
            texto.append(". Para ponderar: ").append(String.join("; ", ponderar));
        }

        return texto.append('.').toString();
    }

    private String fraseDoVeredito(Veredito veredito) {
        return switch (veredito) {
            case VALE_MUITO -> "vale muito a pena";
            case VALE -> "vale a pena";
            case TALVEZ -> "e uma oferta comum";
            case NAO_RECOMENDO -> "nao me parece uma boa oferta";
            case SEM_BASE -> "ainda nao da para dizer";
        };
    }

    private List<String> frasesDe(List<Razao> razoes, Peso peso) {
        return razoes.stream().filter(r -> r.peso() == peso).map(Razao::frase).toList();
    }

    /**
     * O melhor aspecto do VOO — sem contar o preco.
     *
     * <p>O preco ja tem razao propria, vinda da anomalia. Deixa-lo entrar aqui
     * produzia frases confusas de verdade, vistas em execucao real:
     *
     * <pre>voo bom: menor preco ja visto na rota (nota 83/100)</pre>
     *
     * O componente de preco era o mais alto da nota, entao a frase sobre o
     * <b>voo</b> passava a falar de <b>preco</b> — e repetia, com outras
     * palavras, o argumento que a linha anterior ja tinha dado.
     */
    private String detalheDoMelhor(FlightScore nota) {
        return extremo(nota, true);
    }

    private String detalheDoPior(FlightScore nota) {
        return extremo(nota, false);
    }

    private String extremo(FlightScore nota, boolean melhor) {
        java.util.Comparator<FlightScore.ComponenteDoScore> porNota =
                java.util.Comparator.comparingInt(FlightScore.ComponenteDoScore::nota);

        java.util.stream.Stream<FlightScore.ComponenteDoScore> doVoo = nota.componentes().stream()
                .filter(FlightScore.ComponenteDoScore::avaliado)
                .filter(c -> c.aspecto() != AspectoDoVoo.PRECO);

        return (melhor ? doVoo.max(porNota) : doVoo.min(porNota))
                .map(FlightScore.ComponenteDoScore::detalhe)
                .orElse("sem detalhe do voo");
    }
}
