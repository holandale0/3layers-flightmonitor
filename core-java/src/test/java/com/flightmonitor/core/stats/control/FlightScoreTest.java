package com.flightmonitor.core.stats.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.stats.control.FlightScore.ComponenteDoScore;

/**
 * Flight Score — etapa E2.3.
 *
 * <p>Sem banco: a observação e a referência são montadas à mão. O que se testa é
 * a regra de composição, e ela é aritmética sobre números escolhidos para dar
 * contas redondas.
 */
@SpringBootTest
class FlightScoreTest {

    @Autowired
    private FlightScoreService service;

    @Autowired
    private ScoreProperties props;

    @Autowired
    private StatsProperties statsProps;

    /** Rota com quartis em 3.000 e 4.000, mediana 3.500, mínimo 2.000. */
    private RouteStats referencia() {
        return new RouteStats(
                "GRU", "LIS", FonteDeStats.CONFIRMADAS,
                20, true,
                new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("3500"),
                new BigDecimal("3600"), new BigDecimal("4000"), new BigDecimal("6000"),
                new BigDecimal("700"),
                Instant.now().minusSeconds(86400 * 60), Instant.now());
    }

    /** Voo completo: todos os quatro aspectos avaliáveis. */
    private PriceObservation voo(String preco, Short escalas, Integer duracao, Integer hora) {
        PriceObservation o = new PriceObservation(
                null, "GRU", "LIS", LocalDate.now().plusMonths(3),
                new BigDecimal(preco), PriceSource.FAST_FLIGHTS);
        o.setStops(escalas);
        o.setDurationMinutes(duracao);
        if (hora != null) {
            o.setDepartureAt(LocalDateTime.of(2027, 3, 10, hora, 30));
        }
        o.setConfirmed(true);
        return o;
    }

    private ComponenteDoScore componente(FlightScore s, AspectoDoVoo aspecto) {
        return s.componentes().stream()
                .filter(c -> c.aspecto() == aspecto)
                .findFirst()
                .orElseThrow();
    }

    // ------------------------------------------------------ caminho feliz

    @Test
    @DisplayName("voo direto, barato, curto e de manhã tira nota máxima")
    void vooPerfeito() {
        FlightScore s = service.pontuar(voo("2000", (short) 0, 600, 8), referencia(), 600);

        assertThat(s.nota()).isEqualTo(100);
        assertThat(s.confiavel()).isTrue();
        assertThat(s.cobertura()).isEqualByComparingTo("1.00");
        assertThat(s.explicacao()).startsWith("excelente");
    }

    @Test
    @DisplayName("cada aspecto aparece com nota, peso e motivo")
    void componentesSaoAuditaveis() {
        FlightScore s = service.pontuar(voo("3500", (short) 1, 900, 14), referencia(), 600);

        assertThat(s.componentes()).hasSize(4);
        assertThat(componente(s, AspectoDoVoo.ESCALAS).nota()).isEqualTo(65);
        assertThat(componente(s, AspectoDoVoo.ESCALAS).detalhe()).contains("1 escala");
        assertThat(componente(s, AspectoDoVoo.HORARIO).detalhe()).isEqualTo("parte a tarde");
        // Sem isto a nota seria inauditável: "62" não diz se o problema foi o
        // preço, a escala ou o horário.
        assertThat(s.componentes()).allSatisfy(c -> assertThat(c.peso()).isPositive());
    }

    @Test
    @DisplayName("a média é ponderada pelos pesos configurados")
    void mediaPonderada() {
        // preço 100 (é o mínimo), escalas 100 (direto), duração 100 (é o melhor
        // da rota), horário 25 (madrugada). Peso do horário é 10 de 100.
        FlightScore s = service.pontuar(voo("2000", (short) 0, 600, 3), referencia(), 600);

        int esperado = Math.round(
                (100f * props.pesoPreco() + 100f * props.pesoEscalas()
                        + 100f * props.pesoDuracao() + 25f * props.pesoHorario())
                        / props.pesoTotal());

        assertThat(s.nota()).isEqualTo(esperado);
    }

    // ------------------------------------------- a regra central: nulo ≠ zero

    @Nested
    @DisplayName("dado ausente não é nota zero")
    class DadoAusente {

        @Test
        @DisplayName("oferta da camada 1 é avaliada só pelo que tem, e diz isso")
        void ofertaDeCache() {
            // Sem duração e sem horário: é exatamente o que a Travelpayouts
            // devolve.
            PriceObservation cache = voo("2000", (short) 0, null, null);

            FlightScore s = service.pontuar(cache, referencia(), 600);

            assertThat(componente(s, AspectoDoVoo.DURACAO).nota()).isNull();
            assertThat(componente(s, AspectoDoVoo.HORARIO).nota()).isNull();
            // Preço 100 e escalas 100, renormalizados: a nota é 100, e não 70.
            // Pontuar zero nos ausentes faria toda oferta não confirmada parecer
            // ruim — e o sistema passaria a preferir voos por terem mais dados,
            // não por serem melhores.
            assertThat(s.nota()).isEqualTo(100);
            assertThat(s.explicacao()).contains("avaliado sem 2 aspecto(s)");
        }

        @Test
        @DisplayName("a cobertura diz de quanto do peso a nota veio")
        void coberturaEExplicita() {
            FlightScore completo = service.pontuar(voo("3000", (short) 0, 600, 8), referencia(), 600);
            FlightScore parcial = service.pontuar(voo("3000", (short) 0, null, null), referencia(), 600);

            assertThat(completo.cobertura()).isEqualByComparingTo("1.00");
            // 50 + 20 de 100.
            assertThat(parcial.cobertura()).isEqualByComparingTo("0.70");
        }

        @Test
        @DisplayName("nota igual com coberturas diferentes não é a mesma coisa")
        void notaIgualComCoberturaDiferente() {
            FlightScore completo = service.pontuar(voo("2000", (short) 0, 600, 8), referencia(), 600);
            FlightScore parcial = service.pontuar(voo("2000", (short) 0, null, null), referencia(), 600);

            assertThat(completo.nota()).isEqualTo(parcial.nota());
            // Mesmo número, informações diferentes por trás. Apresentar os dois
            // como "100" sem mais nada seria mentira por omissão.
            assertThat(completo.cobertura()).isGreaterThan(parcial.cobertura());
        }

        @Test
        @DisplayName("sem histórico da rota, o preço não é avaliado — e não vira zero")
        void semHistoricoDaRota() {
            FlightScore s = service.pontuar(
                    voo("2000", (short) 0, 600, 8),
                    RouteStats.vazio("GRU", "LIS", FonteDeStats.CONFIRMADAS),
                    600);

            assertThat(componente(s, AspectoDoVoo.PRECO).nota()).isNull();
            assertThat(componente(s, AspectoDoVoo.PRECO).detalhe()).contains("sem historico");
            // Os outros três ainda valem: 100, 100 e 100.
            assertThat(s.nota()).isEqualTo(100);
            assertThat(s.confiavel()).isFalse();
        }

        @Test
        @DisplayName("rota sem duração de referência não inventa limite em horas")
        void semDuracaoDeReferencia() {
            FlightScore s = service.pontuar(voo("3000", (short) 0, 900, 8), referencia(), null);

            assertThat(componente(s, AspectoDoVoo.DURACAO).nota()).isNull();
            assertThat(componente(s, AspectoDoVoo.DURACAO).detalhe())
                    .contains("sem duracao de referencia");
        }

        @Test
        @DisplayName("sem nenhum dado avaliável, não há nota")
        void nadaAvaliavel() {
            PriceObservation vazia = new PriceObservation(
                    null, "GRU", "LIS", LocalDate.now().plusMonths(3),
                    new BigDecimal("3000"), PriceSource.TRAVELPAYOUTS);

            FlightScore s = service.pontuar(
                    vazia, RouteStats.vazio("GRU", "LIS", FonteDeStats.TODAS), null);

            assertThat(s.temNota()).isFalse();
            assertThat(s.nota()).isNull();
            assertThat(s.cobertura()).isEqualByComparingTo("0");
        }
    }

    // ------------------------------------------------------------- preço

    @Test
    @DisplayName("o preço é pontuado pela posição na distribuição da rota")
    void precoPelaPosicao() {
        assertThat(componente(service.pontuar(voo("2000", null, null, null), referencia(), null),
                AspectoDoVoo.PRECO).nota()).isEqualTo(100);
        assertThat(componente(service.pontuar(voo("3000", null, null, null), referencia(), null),
                AspectoDoVoo.PRECO).nota()).isEqualTo(80);
        assertThat(componente(service.pontuar(voo("3500", null, null, null), referencia(), null),
                AspectoDoVoo.PRECO).nota()).isEqualTo(60);
        assertThat(componente(service.pontuar(voo("4000", null, null, null), referencia(), null),
                AspectoDoVoo.PRECO).nota()).isEqualTo(35);
        assertThat(componente(service.pontuar(voo("6000", null, null, null), referencia(), null),
                AspectoDoVoo.PRECO).nota()).isZero();
    }

    @Test
    @DisplayName("um preço absurdo no histórico não comprime a escala")
    void precoAbsurdoNaoComprimeAEscala() {
        RouteStats comOutlier = new RouteStats(
                "GRU", "LIS", FonteDeStats.CONFIRMADAS, 20, true,
                new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("3500"),
                new BigDecimal("9000"), new BigDecimal("4000"), new BigDecimal("90000"),
                new BigDecimal("700"), Instant.now(), Instant.now());

        // Interpolar entre mínimo e máximo faria 3.000 valer quase 100 numa
        // escala de 2.000 a 90.000. Ancorado nos quantis, a nota não se move.
        assertThat(componente(service.pontuar(voo("3000", null, null, null), comOutlier, null),
                AspectoDoVoo.PRECO).nota()).isEqualTo(80);
    }

    // ----------------------------------------------------------- escalas

    @Test
    @DisplayName("a maior queda é do direto para a primeira escala")
    void quedaMaiorNaPrimeiraEscala() {
        int direto = componente(service.pontuar(voo("3000", (short) 0, null, null), referencia(), null),
                AspectoDoVoo.ESCALAS).nota();
        int uma = componente(service.pontuar(voo("3000", (short) 1, null, null), referencia(), null),
                AspectoDoVoo.ESCALAS).nota();
        int duas = componente(service.pontuar(voo("3000", (short) 2, null, null), referencia(), null),
                AspectoDoVoo.ESCALAS).nota();
        int tres = componente(service.pontuar(voo("3000", (short) 3, null, null), referencia(), null),
                AspectoDoVoo.ESCALAS).nota();

        // É onde está a diferença real de experiência: conexão perdida e espera
        // em aeroporto acontecem na primeira escala.
        assertThat(direto - uma).isGreaterThan(uma - duas);
        assertThat(uma - duas).isGreaterThan(duas - tres);
    }

    @Test
    @DisplayName("mais de três escalas não quebra a conta")
    void muitasEscalas() {
        assertThat(componente(service.pontuar(voo("3000", (short) 7, null, null), referencia(), null),
                AspectoDoVoo.ESCALAS).nota()).isEqualTo(10);
    }

    // ---------------------------------------------------------- duração

    @Test
    @DisplayName("a duração é relativa à melhor da própria rota")
    void duracaoRelativaARota() {
        // 600 min é o melhor da rota: nota 100. O dobro: nota 0.
        assertThat(componente(service.pontuar(voo("3000", null, 600, null), referencia(), 600),
                AspectoDoVoo.DURACAO).nota()).isEqualTo(100);
        assertThat(componente(service.pontuar(voo("3000", null, 900, null), referencia(), 600),
                AspectoDoVoo.DURACAO).nota()).isEqualTo(50);
        assertThat(componente(service.pontuar(voo("3000", null, 1200, null), referencia(), 600),
                AspectoDoVoo.DURACAO).nota()).isZero();
    }

    @Test
    @DisplayName("a mesma duração vale coisas diferentes em rotas diferentes")
    void mesmaDuracaoNotasDiferentes() {
        PriceObservation dezHoras = voo("3000", null, 600, null);

        // Dez horas numa rota cujo melhor é dez horas: perfeito.
        assertThat(componente(service.pontuar(dezHoras, referencia(), 600),
                AspectoDoVoo.DURACAO).nota()).isEqualTo(100);
        // Dez horas numa rota cujo melhor é uma hora: péssimo.
        assertThat(componente(service.pontuar(dezHoras, referencia(), 60),
                AspectoDoVoo.DURACAO).nota()).isZero();
    }

    @Test
    @DisplayName("o detalhe da duração mostra a comparação, e não só o número")
    void detalheDaDuracao() {
        String detalhe = componente(
                service.pontuar(voo("3000", null, 905, null), referencia(), 600),
                AspectoDoVoo.DURACAO).detalhe();

        assertThat(detalhe).isEqualTo("15h05, contra 10h00 do melhor da rota");
    }

    @Test
    @DisplayName("a melhor duração da rota não é comparada consigo mesma")
    void detalheDaMelhorDuracao() {
        String detalhe = componente(
                service.pontuar(voo("3000", null, 600, null), referencia(), 600),
                AspectoDoVoo.DURACAO).detalhe();

        // "10h00, contra 10h00 do melhor da rota" é verdadeiro e ridículo.
        // Apareceu na saída real da E3.3.
        assertThat(detalhe).isEqualTo("10h00, a melhor duracao ja vista na rota");
    }

    // ---------------------------------------------------------- horário

    @Test
    @DisplayName("madrugada pontua menos que manhã")
    void horarioPorFaixa() {
        int manha = componente(service.pontuar(voo("3000", null, null, 8), referencia(), null),
                AspectoDoVoo.HORARIO).nota();
        int tarde = componente(service.pontuar(voo("3000", null, null, 15), referencia(), null),
                AspectoDoVoo.HORARIO).nota();
        int noite = componente(service.pontuar(voo("3000", null, null, 20), referencia(), null),
                AspectoDoVoo.HORARIO).nota();
        int madrugada = componente(service.pontuar(voo("3000", null, null, 3), referencia(), null),
                AspectoDoVoo.HORARIO).nota();

        assertThat(manha).isGreaterThan(tarde);
        assertThat(tarde).isGreaterThan(noite);
        assertThat(noite).isGreaterThan(madrugada);
    }

    // ------------------------------------------------------ confiança

    @Test
    @DisplayName("estatística não confiável derruba a confiança da nota")
    void estatisticaFracaDerrubaAConfianca() {
        RouteStats poucas = new RouteStats(
                "GRU", "LIS", FonteDeStats.CONFIRMADAS,
                statsProps.minimoAmostras() - 1, false,
                new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("3500"),
                new BigDecimal("3600"), new BigDecimal("4000"), new BigDecimal("6000"),
                new BigDecimal("700"), Instant.now(), Instant.now());

        FlightScore s = service.pontuar(voo("2000", (short) 0, 600, 8), poucas, 600);

        assertThat(s.nota()).isEqualTo(100);
        // A nota sai — é informação —, mas não pode ser apresentada como
        // veredito: a régua que a produziu é frouxa.
        assertThat(s.confiavel()).isFalse();
        assertThat(s.explicacao()).endsWith("nota preliminar");
    }

    @Test
    @DisplayName("cobertura abaixo do mínimo também derruba a confiança")
    void coberturaBaixaDerrubaAConfianca() {
        // Só escalas: 20 de 100, abaixo do mínimo de 0,5.
        PriceObservation soEscalas = new PriceObservation(
                null, "GRU", "LIS", LocalDate.now().plusMonths(3),
                new BigDecimal("3000"), PriceSource.TRAVELPAYOUTS);
        soEscalas.setStops((short) 0);

        FlightScore s = service.pontuar(
                soEscalas, RouteStats.vazio("GRU", "LIS", FonteDeStats.TODAS), null);

        assertThat(s.cobertura()).isEqualByComparingTo("0.20");
        assertThat(s.confiavel()).isFalse();
    }

    // --------------------------------------------------------- explicação

    @Test
    @DisplayName("a explicação nomeia o que puxou a nota para cima e para baixo")
    void explicacaoNomeiaOsExtremos() {
        FlightScore s = service.pontuar(voo("2000", (short) 2, 600, 8), referencia(), 600);

        assertThat(s.explicacao())
                .contains("a favor:")
                .contains("contra:")
                .contains("2 escala(s)");
    }

    @Test
    @DisplayName("voo bom em tudo não recebe crítica inventada")
    void semCriticaQuandoNaoHaPontoFraco() {
        FlightScore s = service.pontuar(voo("2000", (short) 0, 600, 8), referencia(), 600);

        // Apontar defeito num voo sem defeito é ruído.
        assertThat(s.explicacao()).doesNotContain("contra:");
    }

    @Test
    @DisplayName("a faixa da nota aparece em português no começo da frase")
    void faixaEmPortugues() {
        assertThat(service.pontuar(voo("2000", (short) 0, 600, 8), referencia(), 600)
                .explicacao()).startsWith("excelente");
        assertThat(service.pontuar(voo("6000", (short) 3, 1200, 3), referencia(), 600)
                .explicacao()).startsWith("fraco");
    }

    @Test
    @DisplayName("oferta nula não estoura")
    void ofertaNula() {
        FlightScore s = service.pontuar(null, referencia(), 600);

        assertThat(s.temNota()).isFalse();
        assertThat(s.explicacao()).contains("nenhuma oferta");
    }
}
