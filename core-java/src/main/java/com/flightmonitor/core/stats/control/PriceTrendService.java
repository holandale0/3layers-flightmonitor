package com.flightmonitor.core.stats.control;

import com.flightmonitor.core.stats.entity.RouteStatsRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flightmonitor.core.stats.control.PriceTrend.PontoDaSerie;

/**
 * Diz se o preco da rota vem subindo, caindo ou parado — etapa E2.5.
 *
 * <h2>Theil-Sen, e nao minimos quadrados</h2>
 *
 * A regressao linear classica minimiza o quadrado dos erros, e por isso um unico
 * dia atipico — uma promocao relampago, um erro da fonte — inclina a reta
 * inteira. Numa serie de dez ou quinze pontos, que e o que este projeto vai ter
 * por muito tempo, um ponto pesa 10% da conclusao.
 *
 * <p>O estimador de <b>Theil-Sen</b> calcula a inclinacao entre <em>todos</em> os
 * pares de pontos e devolve a <b>mediana</b> dessas inclinacoes. Ele tolera ate
 * ~29% de pontos corrompidos sem mudar de resposta.
 *
 * <p>E a mesma escolha que ja foi feita duas vezes neste projeto — mediana em
 * vez de media na E2.1, Tukey em vez de z-score na E2.2. Preco de passagem tem
 * cauda longa, e todo estimador sensivel a extremo erra na mesma direcao.
 *
 * <h2>Por que a inclinacao vira porcentagem por semana</h2>
 *
 * Reais por dia nao dizem nada sozinhos: R$ 20/dia e ruido numa rota de R$ 8.000
 * e movimento forte numa de R$ 600. Dividir pela mediana da serie torna rotas
 * comparaveis, e a semana e a unidade em que uma pessoa pensa ao decidir se
 * espera mais um pouco.
 */
@Service
public class PriceTrendService {

    private static final BigDecimal CEM = new BigDecimal("100");
    private static final int DIAS_DA_SEMANA = 7;

    private final RouteStatsRepository repositorio;
    private final TrendProperties props;

    public PriceTrendService(RouteStatsRepository repositorio, TrendProperties props) {
        this.repositorio = repositorio;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public PriceTrend analisar(String origin, String destination, FonteDeStats fonte) {
        return analisar(origin, destination, fonte, props.janela());
    }

    @Transactional(readOnly = true)
    public PriceTrend analisar(
            String origin, String destination, FonteDeStats fonte, Duration janela) {

        String o = origin == null ? null : origin.trim().toUpperCase();
        String d = destination == null ? null : destination.trim().toUpperCase();
        FonteDeStats f = fonte == null ? FonteDeStats.TODAS : fonte;
        Duration efetiva = janela == null ? props.janela() : janela;

        List<PontoDaSerie> serie = repositorio
                .serieDiaria(o, d, Instant.now().minus(efetiva), f == FonteDeStats.CONFIRMADAS)
                .stream()
                .map(linha -> new PontoDaSerie(
                        linha.getDia(),
                        (int) linha.getAmostras(),
                        arredondar(linha.getMediana())))
                .toList();

        return calcular(o, d, f, serie);
    }

    /** Separado da consulta para poder ser testado com series montadas a mao. */
    public PriceTrend calcular(
            String origin, String destination, FonteDeStats fonte, List<PontoDaSerie> serie) {

        if (serie == null || serie.size() < props.minimoDeDias()) {
            // Dias distintos, e nao observacoes: cem precos coletados no mesmo
            // dia nao dizem nada sobre tendencia.
            return PriceTrend.semDados(origin, destination, fonte, serie,
                    "sao necessarios ao menos %d dias com observacao para falar em tendencia"
                            .formatted(props.minimoDeDias()));
        }

        BigDecimal inclinacaoDiaria = theilSen(serie);
        if (inclinacaoDiaria == null) {
            return PriceTrend.semDados(origin, destination, fonte, serie,
                    "os pontos nao permitem estimar inclinacao");
        }

        BigDecimal referencia = medianaDaSerie(serie);
        if (referencia == null || referencia.signum() <= 0) {
            return PriceTrend.semDados(origin, destination, fonte, serie,
                    "sem preco de referencia para normalizar a variacao");
        }

        BigDecimal variacaoSemanal = inclinacaoDiaria
                .multiply(BigDecimal.valueOf(DIAS_DA_SEMANA))
                .multiply(CEM)
                .divide(referencia, 1, RoundingMode.HALF_UP);

        DirecaoDaTendencia direcao = classificar(variacaoSemanal);
        int amostras = serie.stream().mapToInt(PontoDaSerie::amostras).sum();

        return new PriceTrend(
                origin, destination, fonte, direcao, variacaoSemanal,
                serie.size(), amostras,
                serie.size() >= props.diasParaConfiar(),
                serie,
                explicar(direcao, variacaoSemanal, serie.size()));
    }

    /**
     * Mediana das inclinacoes entre todos os pares de pontos.
     *
     * <p>O custo e quadratico no numero de dias. Com a janela padrao isso da
     * algumas centenas de pares — irrelevante. Se um dia a janela crescer para
     * anos, vale reavaliar.
     */
    private BigDecimal theilSen(List<PontoDaSerie> serie) {
        List<BigDecimal> inclinacoes = new ArrayList<>();

        for (int i = 0; i < serie.size(); i++) {
            for (int j = i + 1; j < serie.size(); j++) {
                PontoDaSerie a = serie.get(i);
                PontoDaSerie b = serie.get(j);

                long dias = ChronoUnit.DAYS.between(a.dia(), b.dia());
                if (dias == 0 || a.mediana() == null || b.mediana() == null) {
                    continue;
                }
                inclinacoes.add(b.mediana()
                        .subtract(a.mediana())
                        .divide(BigDecimal.valueOf(dias), 6, RoundingMode.HALF_UP));
            }
        }

        return mediana(inclinacoes);
    }

    private BigDecimal medianaDaSerie(List<PontoDaSerie> serie) {
        return mediana(serie.stream()
                .map(PontoDaSerie::mediana)
                .filter(java.util.Objects::nonNull)
                .toList());
    }

    /** Mediana de uma lista qualquer; interpola quando o tamanho e par. */
    private BigDecimal mediana(List<BigDecimal> valores) {
        if (valores.isEmpty()) {
            return null;
        }
        List<BigDecimal> ordenados = new ArrayList<>(valores);
        Collections.sort(ordenados);

        int meio = ordenados.size() / 2;
        if (ordenados.size() % 2 == 1) {
            return ordenados.get(meio);
        }
        return ordenados.get(meio - 1)
                .add(ordenados.get(meio))
                .divide(BigDecimal.TWO, 6, RoundingMode.HALF_UP);
    }

    /**
     * O limiar existe para o sistema poder dizer "nao esta acontecendo nada".
     *
     * <p>Sem ele, qualquer oscilacao de centavos viraria "subindo" ou "caindo",
     * e a informacao perderia o sentido por nunca ser estavel.
     */
    private DirecaoDaTendencia classificar(BigDecimal variacaoSemanal) {
        if (variacaoSemanal.abs().compareTo(props.limiarPercentualSemanal()) < 0) {
            return DirecaoDaTendencia.ESTAVEL;
        }
        return variacaoSemanal.signum() < 0
                ? DirecaoDaTendencia.CAINDO
                : DirecaoDaTendencia.SUBINDO;
    }

    /**
     * Frase pronta, sem jargao.
     *
     * <p>Nao diz "compre agora" nem "espere": a tendencia recente e um indicio,
     * nao previsao, e transformar indicio em conselho seria prometer o que este
     * sistema nao sabe. O texto informa o movimento e deixa a decisao com quem
     * viaja.
     */
    private String explicar(DirecaoDaTendencia direcao, BigDecimal variacao, int dias) {
        String periodo = "nos ultimos %d dias com observacao".formatted(dias);

        return switch (direcao) {
            case CAINDO -> "o preco vem caindo cerca de %s%% por semana %s"
                    .formatted(variacao.abs(), periodo);
            case SUBINDO -> "o preco vem subindo cerca de %s%% por semana %s"
                    .formatted(variacao.abs(), periodo);
            case ESTAVEL -> "o preco esta estavel %s".formatted(periodo);
            case SEM_DADOS -> "";
        };
    }

    private BigDecimal arredondar(Number valor) {
        if (valor == null) {
            return null;
        }
        return BigDecimal.valueOf(valor.doubleValue()).setScale(2, RoundingMode.HALF_UP);
    }
}
