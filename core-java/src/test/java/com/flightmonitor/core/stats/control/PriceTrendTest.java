package com.flightmonitor.core.stats.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.flightmonitor.core.stats.control.PriceTrend.PontoDaSerie;

/**
 * Tendência de preço — etapa E2.5.
 *
 * <p>As séries são montadas à mão, com números escolhidos para a inclinação ser
 * calculável de cabeça. Testar Theil-Sen contra dados do banco exigiria
 * reimplementar Theil-Sen no teste — e um teste que reimplementa a produção
 * concorda com ela até nos erros.
 */
@SpringBootTest
class PriceTrendTest {

    @Autowired
    private PriceTrendService service;

    @Autowired
    private TrendProperties props;

    private static final LocalDate DIA_ZERO = LocalDate.of(2027, 1, 10);

    /** Uma série com um ponto por dia, a partir dos preços dados. */
    private List<PontoDaSerie> serie(String... precos) {
        List<PontoDaSerie> pontos = new ArrayList<>();
        for (int i = 0; i < precos.length; i++) {
            pontos.add(new PontoDaSerie(DIA_ZERO.plusDays(i), 3, new BigDecimal(precos[i])));
        }
        return pontos;
    }

    private PriceTrend analisar(List<PontoDaSerie> serie) {
        return service.calcular("GRU", "LIS", FonteDeStats.TODAS, serie);
    }

    // ------------------------------------------------------------ direção

    @Test
    @DisplayName("série que desce vira CAINDO")
    void serieDescendente() {
        // 3.000 caindo 30 por dia: 210 por semana sobre mediana 2.850 = 7,4%.
        PriceTrend t = analisar(serie("3000", "2970", "2940", "2910", "2880",
                "2850", "2820", "2790", "2760", "2730"));

        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.CAINDO);
        assertThat(t.variacaoSemanal()).isNegative();
        assertThat(t.explicacao()).contains("vem caindo");
    }

    @Test
    @DisplayName("série que sobe vira SUBINDO")
    void serieAscendente() {
        PriceTrend t = analisar(serie("2730", "2760", "2790", "2820", "2850",
                "2880", "2910", "2940", "2970", "3000"));

        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.SUBINDO);
        assertThat(t.variacaoSemanal()).isPositive();
    }

    @Test
    @DisplayName("oscilação pequena é ESTÁVEL, e não uma direção qualquer")
    void oscilacaoPequenaEEstavel() {
        // Sobe e desce alguns reais: sem limiar, isso viraria "subindo".
        PriceTrend t = analisar(serie("3000", "3005", "2998", "3002", "3001",
                "2999", "3003", "2997"));

        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.ESTAVEL);
        assertThat(t.explicacao()).contains("estavel");
    }

    @Test
    @DisplayName("série perfeitamente plana é estável, com variação zero")
    void seriePlana() {
        PriceTrend t = analisar(serie("3000", "3000", "3000", "3000", "3000", "3000", "3000"));

        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.ESTAVEL);
        assertThat(t.variacaoSemanal()).isEqualByComparingTo("0.0");
    }

    @Test
    @DisplayName("a variação é percentual, então rotas de preços diferentes se comparam")
    void variacaoEPercentual() {
        // Mesmos 10% de queda por semana em duas rotas de patamares distintos.
        PriceTrend barata = analisar(serie("600", "590", "580", "570", "560", "550", "540", "530"));
        PriceTrend cara = analisar(serie("6000", "5900", "5800", "5700", "5600",
                "5500", "5400", "5300"));

        // R$ 10/dia e R$ 100/dia dizem a mesma coisa quando normalizados.
        assertThat(barata.variacaoSemanal()).isEqualByComparingTo(cara.variacaoSemanal());
    }

    // ---------------------------------------------------- robustez

    @Nested
    @DisplayName("por que Theil-Sen, e não mínimos quadrados")
    class PorQueTheilSen {

        @Test
        @DisplayName("um dia atípico não inverte a conclusão")
        void diaAtipicoNaoInverte() {
            List<PontoDaSerie> comPromocao = new ArrayList<>(
                    serie("3000", "3030", "3060", "3090", "3120", "3150", "3180", "3210"));
            // Uma promoção relâmpago de um dia, no meio de uma alta clara.
            comPromocao.set(4, new PontoDaSerie(DIA_ZERO.plusDays(4), 3, new BigDecimal("900")));

            PriceTrend t = analisar(comPromocao);

            // Mínimos quadrados puxaria a reta para baixo e poderia chamar de
            // estável — ou até de queda. A mediana das inclinações ignora o
            // ponto solto.
            assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.SUBINDO);
        }

        @Test
        @DisplayName("dois dias atípicos ainda não derrubam a conclusão")
        void doisDiasAtipicos() {
            List<PontoDaSerie> ruidosa = new ArrayList<>(
                    serie("3000", "2970", "2940", "2910", "2880", "2850", "2820",
                            "2790", "2760", "2730"));
            ruidosa.set(2, new PontoDaSerie(DIA_ZERO.plusDays(2), 3, new BigDecimal("9000")));
            ruidosa.set(7, new PontoDaSerie(DIA_ZERO.plusDays(7), 3, new BigDecimal("8500")));

            assertThat(analisar(ruidosa).direcao()).isEqualTo(DirecaoDaTendencia.CAINDO);
        }
    }

    // ------------------------------------------------------- sem veredito

    @Test
    @DisplayName("poucos dias não viram tendência — nem estável")
    void poucosDiasNaoViramTendencia() {
        PriceTrend t = analisar(serie("3000", "2000"));

        // Com dois pontos, qualquer reta passa exatamente por eles: a
        // "tendência" seria só o ruído entre duas medições.
        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.SEM_DADOS);
        assertThat(t.variacaoSemanal()).isNull();
        assertThat(t.explicacao()).contains("ao menos %d dias".formatted(props.minimoDeDias()));
    }

    @Test
    @DisplayName("série vazia responde SEM_DADOS, e não erro")
    void serieVazia() {
        PriceTrend t = analisar(List.of());

        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.SEM_DADOS);
        assertThat(t.diasComDados()).isZero();
    }

    @Test
    @DisplayName("acima do mínimo mas abaixo do confiável, a tendência sai marcada")
    void tendenciaPreliminar() {
        PriceTrend t = analisar(serie("3000", "2900", "2800"));

        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.CAINDO);
        // Três dias já dizem alguma coisa, e não o bastante para virar veredito.
        assertThat(t.confiavel()).isFalse();
    }

    @Test
    @DisplayName("com dias suficientes, a tendência passa a valer como referência")
    void tendenciaConfiavel() {
        List<PontoDaSerie> longa = new ArrayList<>();
        for (int i = 0; i < props.diasParaConfiar(); i++) {
            longa.add(new PontoDaSerie(
                    DIA_ZERO.plusDays(i), 3, new BigDecimal(3000 - i * 30)));
        }

        assertThat(analisar(longa).confiavel()).isTrue();
    }

    // -------------------------------------------------------- contagem

    @Test
    @DisplayName("dias com dados e amostras são números diferentes, e ambos aparecem")
    void diasEAmostrasSaoDiferentes() {
        List<PontoDaSerie> serie = List.of(
                new PontoDaSerie(DIA_ZERO, 10, new BigDecimal("3000")),
                new PontoDaSerie(DIA_ZERO.plusDays(1), 5, new BigDecimal("2950")),
                new PontoDaSerie(DIA_ZERO.plusDays(2), 1, new BigDecimal("2900")));

        PriceTrend t = analisar(serie);

        // Dezesseis observações em três dias. O que sustenta uma tendência são
        // os três dias, e não as dezesseis observações.
        assertThat(t.diasComDados()).isEqualTo(3);
        assertThat(t.amostras()).isEqualTo(16);
    }

    @Test
    @DisplayName("dias faltando na série não quebram a inclinação")
    void diasFaltandoNaSerie() {
        // Varredura não roda todo dia: buracos são o caso normal, não exceção.
        List<PontoDaSerie> comBuracos = List.of(
                new PontoDaSerie(DIA_ZERO, 3, new BigDecimal("3000")),
                new PontoDaSerie(DIA_ZERO.plusDays(5), 3, new BigDecimal("2850")),
                new PontoDaSerie(DIA_ZERO.plusDays(12), 3, new BigDecimal("2640")),
                new PontoDaSerie(DIA_ZERO.plusDays(20), 3, new BigDecimal("2400")));

        PriceTrend t = analisar(comBuracos);

        // Todos os pares dao exatamente -30 por dia, seja qual for o intervalo
        // entre eles: -210 por semana. A referencia e a mediana da serie,
        // (2850 + 2640) / 2 = 2745, e -210 / 2745 = -7,7%.
        assertThat(t.direcao()).isEqualTo(DirecaoDaTendencia.CAINDO);
        assertThat(t.variacaoSemanal()).isEqualByComparingTo("-7.7");
    }

    // -------------------------------------------------------- explicação

    @Test
    @DisplayName("a explicação informa o movimento, e não dá conselho de compra")
    void naoDaConselho() {
        String caindo = analisar(serie("3000", "2900", "2800", "2700", "2600", "2500")).explicacao();
        String subindo = analisar(serie("2500", "2600", "2700", "2800", "2900", "3000")).explicacao();

        // Tendência recente é indício, não previsão. Transformar indício em
        // conselho seria prometer o que este sistema não sabe.
        for (String texto : List.of(caindo, subindo)) {
            assertThat(texto.toLowerCase())
                    .doesNotContain("compre")
                    .doesNotContain("espere")
                    .doesNotContain("aguarde");
        }
        assertThat(caindo).contains("vem caindo");
        assertThat(subindo).contains("vem subindo");
    }

    @Test
    @DisplayName("a explicação não usa jargão de estatística")
    void semJargao() {
        String texto = analisar(serie("3000", "2900", "2800", "2700", "2600")).explicacao();

        assertThat(texto.toLowerCase())
                .doesNotContain("inclinacao")
                .doesNotContain("regressao")
                .doesNotContain("theil")
                .doesNotContain("mediana");
    }

    @Test
    @DisplayName("a série usada volta na resposta, para conferência")
    void serieVoltaNaResposta() {
        List<PontoDaSerie> entrada = serie("3000", "2900", "2800", "2700");

        PriceTrend t = analisar(entrada);

        // Sem os pontos, um resultado estranho seria impossível de conferir — e
        // é deles que o gráfico do painel vai sair.
        assertThat(t.serie()).isEqualTo(entrada);
    }
}
