package com.flightmonitor.core.stats.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.monitor.entity.MonitorRepository;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceObservationRepository;
import com.flightmonitor.core.search.entity.PriceSource;

/**
 * Estatisticas de rota — etapa E2.1.
 *
 * <p>Os precos dos cenarios sao escolhidos para que a resposta certa seja
 * calculavel de cabeca. Testar mediana com numeros aleatorios exigiria
 * reimplementar a mediana no teste, e um teste que reimplementa a producao
 * concorda com ela ate nos erros.
 */
@SpringBootTest
class RouteStatsTest {

    @Autowired
    private RouteStatsService service;

    @Autowired
    private MonitorRepository monitores;

    @Autowired
    private PriceObservationRepository observacoes;

    @Autowired
    private StatsProperties props;

    @Autowired
    private JdbcTemplate jdbc;

    private Monitor monitor;

    private final LocalDate ida = LocalDate.of(2027, 3, 10);

    @BeforeEach
    void preparar() {
        limparBanco();

        Monitor m = new Monitor();
        m.setLabel("stats");
        m.setOrigin("POA");
        m.setDestination("MAD");
        m.setDepartureWindowStart(ida);
        m.setDepartureWindowEnd(ida.plusDays(60));
        m.setMaxPrice(new BigDecimal("5000.00"));
        monitor = monitores.saveAndFlush(m);
    }

    @AfterEach
    void limpar() {
        limparBanco();
    }

    private void limparBanco() {
        observacoes.deleteAll();
        monitores.deleteAll();
    }

    private PriceObservation observar(String preco, boolean confirmada) {
        return observar(preco, confirmada, ida);
    }

    private PriceObservation observar(String preco, boolean confirmada, LocalDate partida) {
        PriceObservation o = new PriceObservation(
                monitor, "POA", "MAD", partida, new BigDecimal(preco),
                confirmada ? PriceSource.FAST_FLIGHTS : PriceSource.TRAVELPAYOUTS);
        o.setConfirmed(confirmada);
        o.setCurrency("BRL");
        return observacoes.saveAndFlush(o);
    }

    /** Empurra observacoes para tras no tempo, para testar a janela. */
    private void envelhecer(int dias) {
        jdbc.update("update price_observation set observed_at = observed_at - make_interval(days => ?)", dias);
    }

    // ------------------------------------------------------------- basico

    @Test
    @DisplayName("rota sem historico devolve vazio, e nao erro")
    void rotaSemHistorico() {
        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        assertThat(r.temDados()).isFalse();
        assertThat(r.amostras()).isZero();
        assertThat(r.confiavel()).isFalse();
        assertThat(r.mediana()).isNull();
        // Um monitor recem-criado cai aqui. Devolver 404 ou estourar faria o
        // painel tratar "ainda nao sei" como falha.
        assertThat(r.origin()).isEqualTo("POA");
    }

    @Test
    @DisplayName("os cinco numeros saem certos para uma serie conhecida")
    void estatisticasDeUmaSerieConhecida() {
        // 1000, 2000, 3000, 4000, 5000 -> media 3000, mediana 3000,
        // quartis 2000 e 4000, desvio amostral 1581.14
        for (String preco : List.of("1000", "2000", "3000", "4000", "5000")) {
            observar(preco, false);
        }

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        assertThat(r.amostras()).isEqualTo(5);
        assertThat(r.minimo()).isEqualByComparingTo("1000.00");
        assertThat(r.p25()).isEqualByComparingTo("2000.00");
        assertThat(r.mediana()).isEqualByComparingTo("3000.00");
        assertThat(r.media()).isEqualByComparingTo("3000.00");
        assertThat(r.p75()).isEqualByComparingTo("4000.00");
        assertThat(r.maximo()).isEqualByComparingTo("5000.00");
        assertThat(r.desvioPadrao()).isEqualByComparingTo("1581.14");
    }

    @Test
    @DisplayName("mediana com numero par de amostras interpola, como manda a definicao")
    void medianaComNumeroPar() {
        for (String preco : List.of("1000", "2000", "3000", "4000")) {
            observar(preco, false);
        }

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        assertThat(r.mediana()).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("a mediana ignora o preco absurdo que desloca a media")
    void medianaResisteACaudaLonga() {
        // O caso real: quatro precos normais e uma compra de ultima hora.
        for (String preco : List.of("3000", "3100", "3200", "3300")) {
            observar(preco, false);
        }
        observar("20000", false);

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        // A media viaja para 6.520 e faria R$ 4.000 parecer barato.
        assertThat(r.media()).isEqualByComparingTo("6520.00");
        // A mediana nao se move. E por isso que ela existe nesta resposta.
        assertThat(r.mediana()).isEqualByComparingTo("3200.00");
    }

    @Test
    @DisplayName("com uma amostra so, o desvio e nulo e nao zero")
    void desvioComUmaAmostra() {
        observar("3000", false);

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        assertThat(r.amostras()).isEqualTo(1);
        // Zero afirmaria "esta rota nao varia", que e uma conclusao. Nulo diz
        // "nao da para saber", que e a verdade.
        assertThat(r.desvioPadrao()).isNull();
        assertThat(r.coeficienteDeVariacao()).isNull();
    }

    @Test
    @DisplayName("coeficiente de variacao permite comparar rotas de precos diferentes")
    void coeficienteDeVariacao() {
        for (String preco : List.of("1000", "2000", "3000", "4000", "5000")) {
            observar(preco, false);
        }

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        // 1581.14 / 3000 = 0.5270
        assertThat(r.coeficienteDeVariacao()).isEqualByComparingTo("0.5270");
    }

    // -------------------------------------------------- confiabilidade

    @Test
    @DisplayName("poucas amostras devolvem numeros, mas marcados como nao confiaveis")
    void poucasAmostrasNaoSaoConfiaveis() {
        for (int i = 0; i < props.minimoAmostras() - 1; i++) {
            observar("3000", false);
        }

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        assertThat(r.temDados()).isTrue();
        assertThat(r.mediana()).isNotNull();
        // O numero existe e e devolvido — esconde-lo seria pior. O que muda e
        // que a E2.2 nao pode disparar anomalia com base nele.
        assertThat(r.confiavel()).isFalse();
    }

    @Test
    @DisplayName("atingido o minimo, o resultado passa a valer como referencia")
    void minimoDeAmostrasTornaConfiavel() {
        for (int i = 0; i < props.minimoAmostras(); i++) {
            observar("3000", false);
        }

        assertThat(service.resumir("POA", "MAD", FonteDeStats.TODAS).confiavel()).isTrue();
    }

    // ------------------------------------------------------------- fonte

    @Test
    @DisplayName("misturar cache e preco confirmado produz um 'normal' que nao existe")
    void fonteSeparaCacheDePrecoReal() {
        // Numeros da divergencia real medida na E1.6: o cache subestima muito.
        for (String preco : List.of("3300", "3350", "3375", "3400")) {
            observar(preco, false);
        }
        for (String preco : List.of("5600", "5714", "5800")) {
            observar(preco, true);
        }

        RouteStats todas = service.resumir("POA", "MAD", FonteDeStats.TODAS);
        RouteStats confirmadas = service.resumir("POA", "MAD", FonteDeStats.CONFIRMADAS);

        assertThat(todas.amostras()).isEqualTo(7);
        assertThat(confirmadas.amostras()).isEqualTo(3);

        // A armadilha inteira em duas linhas: comparado com a mistura, um preco
        // REAL de 5.600 parece caro; comparado com a realidade, e o mais barato
        // ja visto. Uma deteccao de anomalia alimentada pela mistura ficaria
        // calada exatamente quando deveria falar.
        assertThat(todas.mediana()).isLessThan(confirmadas.mediana());
        assertThat(confirmadas.minimo()).isEqualByComparingTo("5600.00");
    }

    @Test
    @DisplayName("sem observacao confirmada, a fonte CONFIRMADAS devolve vazio em vez de mentir")
    void confirmadasSemDadoNaoCaiParaOCache() {
        for (String preco : List.of("3300", "3350", "3375")) {
            observar(preco, false);
        }

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.CONFIRMADAS);

        // Cair para o cache aqui seria a pior das opcoes: devolveria numeros
        // com a etiqueta errada.
        assertThat(r.temDados()).isFalse();
        assertThat(r.fonte()).isEqualTo(FonteDeStats.CONFIRMADAS);
    }

    // ------------------------------------------------------------ janela

    @Test
    @DisplayName("observacao mais velha que a janela nao entra na conta")
    void janelaDescartaOAntigo() {
        observar("9000", false);
        envelhecer((int) props.janela().toDays() + 10);
        observar("3000", false);

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        assertThat(r.amostras()).isEqualTo(1);
        assertThat(r.media()).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("janela maior recupera o historico antigo")
    void janelaMaiorAlcancaOAntigo() {
        observar("9000", false);
        envelhecer((int) props.janela().toDays() + 10);
        observar("3000", false);

        RouteStats r = service.resumir(
                "POA", "MAD", FonteDeStats.TODAS, Duration.ofDays(3650));

        assertThat(r.amostras()).isEqualTo(2);
    }

    // -------------------------------------------------------------- rota

    @Test
    @DisplayName("a estatistica e da ROTA, e nao do monitor")
    void estatisticaPertenceARota() {
        observar("3000", false);

        Monitor outro = new Monitor();
        outro.setLabel("outro monitor, mesma rota");
        outro.setOrigin("POA");
        outro.setDestination("MAD");
        outro.setDepartureWindowStart(ida);
        outro.setDepartureWindowEnd(ida.plusDays(30));
        outro.setMaxPrice(new BigDecimal("4000.00"));
        outro = monitores.saveAndFlush(outro);

        PriceObservation o = new PriceObservation(
                outro, "POA", "MAD", ida, new BigDecimal("4000"), PriceSource.TRAVELPAYOUTS);
        o.setCurrency("BRL");
        observacoes.saveAndFlush(o);

        RouteStats r = service.resumir("POA", "MAD", FonteDeStats.TODAS);

        // D-016: o historico pertence a rota. Um monitor novo nasce sabendo o
        // que a rota ja mostrou, em vez de comecar cego.
        assertThat(r.amostras()).isEqualTo(2);
        assertThat(r.media()).isEqualByComparingTo("3500.00");
    }

    @Test
    @DisplayName("outra rota nao contamina o resultado")
    void outraRotaNaoEntra() {
        observar("3000", false);

        PriceObservation outra = new PriceObservation(
                monitor, "GRU", "LIS", ida, new BigDecimal("9999"), PriceSource.TRAVELPAYOUTS);
        outra.setCurrency("BRL");
        observacoes.saveAndFlush(outra);

        assertThat(service.resumir("POA", "MAD", FonteDeStats.TODAS).amostras()).isEqualTo(1);
    }

    @Test
    @DisplayName("codigo IATA em minuscula ou com espaco tambem encontra a rota")
    void normalizaOCodigoDaRota() {
        observar("3000", false);

        RouteStats r = service.resumir(" poa ", "mad", FonteDeStats.TODAS);

        assertThat(r.amostras()).isEqualTo(1);
        assertThat(r.origin()).isEqualTo("POA");
    }

    // -------------------------------------------------------------- meses

    @Test
    @DisplayName("o corte mensal usa o mes de PARTIDA, nao o da observacao")
    void mesesUsamADataDePartida() {
        // Todas observadas hoje, para tres meses de viagem diferentes.
        observar("3000", false, LocalDate.of(2027, 3, 10));
        observar("3200", false, LocalDate.of(2027, 3, 20));
        observar("5000", false, LocalDate.of(2027, 4, 5));
        observar("2500", false, LocalDate.of(2027, 5, 8));

        List<RouteStatsService.MesDaRota> meses =
                service.resumirPorMes("POA", "MAD", FonteDeStats.TODAS);

        assertThat(meses).extracting(RouteStatsService.MesDaRota::mes)
                .containsExactly("2027-03", "2027-04", "2027-05");

        // Agrupar pelo instante da observacao colocaria as quatro no mesmo
        // balde — um grafico sobre o nosso horario de varredura, que nao
        // responde "quando sai mais barato viajar".
        assertThat(meses.get(0).amostras()).isEqualTo(2);
        assertThat(meses.get(0).media()).isEqualByComparingTo("3100.00");
        assertThat(meses.get(2).minimo()).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("mes com poucas amostras aparece, marcado como nao confiavel")
    void mesComPoucaAmostraApareceMarcado() {
        observar("2500", false, LocalDate.of(2027, 5, 8));

        List<RouteStatsService.MesDaRota> meses =
                service.resumirPorMes("POA", "MAD", FonteDeStats.TODAS);

        assertThat(meses).singleElement().satisfies(m -> {
            // Sumir com o mes esconderia que existe pouca informacao sobre ele,
            // e isso e em si um dado.
            assertThat(m.amostras()).isEqualTo(1);
            assertThat(m.confiavel()).isFalse();
        });
    }

    @Test
    @DisplayName("os meses saem em ordem cronologica")
    void mesesEmOrdem() {
        observar("2500", false, LocalDate.of(2027, 5, 8));
        observar("3000", false, LocalDate.of(2027, 3, 10));
        observar("5000", false, LocalDate.of(2027, 4, 5));

        assertThat(service.resumirPorMes("POA", "MAD", FonteDeStats.TODAS))
                .extracting(RouteStatsService.MesDaRota::mes)
                .isSorted();
    }
}
