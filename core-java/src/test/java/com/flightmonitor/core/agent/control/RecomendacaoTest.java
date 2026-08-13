package com.flightmonitor.core.agent.control;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.flightmonitor.core.agent.control.Recomendacao.Razao;
import com.flightmonitor.core.agent.control.Recomendacao.Razao.Aspecto;
import com.flightmonitor.core.agent.control.Recomendacao.Razao.Peso;
import com.flightmonitor.core.agent.control.Recomendacao.Veredito;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.stats.control.AspectoDoVoo;
import com.flightmonitor.core.stats.control.DirecaoDaTendencia;
import com.flightmonitor.core.stats.control.FlightScore;
import com.flightmonitor.core.stats.control.FlightScore.ComponenteDoScore;
import com.flightmonitor.core.stats.control.FonteDeStats;
import com.flightmonitor.core.stats.entity.GrauDeAnomalia;
import com.flightmonitor.core.stats.control.PriceAnomaly;
import com.flightmonitor.core.stats.control.PriceTrend;
import com.flightmonitor.core.stats.control.RouteStats;

/**
 * Recomendação em linguagem natural — etapa E3.3.
 *
 * <p>As quatro análises entram montadas à mão. Cada uma já tem testes próprios;
 * o que se testa aqui é a <b>composição</b> — quais viram argumento, de que
 * lado, e como o texto sai.
 */
@SpringBootTest
class RecomendacaoTest {

    @Autowired
    private RecomendacaoService service;

    private PriceObservation oferta() {
        PriceObservation o = new PriceObservation(
                null, "GRU", "LIS", LocalDate.of(2027, 3, 10),
                new BigDecimal("2900.00"), PriceSource.FAST_FLIGHTS);
        o.setStops((short) 0);
        o.setDurationMinutes(600);
        o.setDepartureAt(LocalDateTime.of(2027, 3, 10, 9, 0));
        o.setConfirmed(true);
        return o;
    }

    private RouteStats referencia(boolean confiavel) {
        return new RouteStats(
                "GRU", "LIS", FonteDeStats.CONFIRMADAS, confiavel ? 22 : 3, confiavel,
                new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("3500"),
                new BigDecimal("3600"), new BigDecimal("4000"), new BigDecimal("6000"),
                new BigDecimal("700"), Instant.now(), Instant.now());
    }

    private PriceAnomaly anomalia(GrauDeAnomalia grau, String frase) {
        return new PriceAnomaly(new BigDecimal("2900.00"), grau, new BigDecimal("17.1"),
                new BigDecimal("1500.00"), frase, referencia(true));
    }

    private FlightScore nota(int valor, boolean confiavel) {
        return new FlightScore(valor, confiavel, new BigDecimal("1.00"),
                List.of(
                        new ComponenteDoScore(AspectoDoVoo.PRECO, valor, 50, "abaixo da mediana"),
                        new ComponenteDoScore(AspectoDoVoo.ESCALAS, valor, 20, "voo direto"),
                        new ComponenteDoScore(AspectoDoVoo.DURACAO, valor, 20, "10h00, o melhor da rota"),
                        new ComponenteDoScore(AspectoDoVoo.HORARIO, valor, 10, "parte de manha")),
                "bom");
    }

    private FlightScore notaDesigual(int melhor, int pior) {
        return new FlightScore((melhor + pior) / 2, true, new BigDecimal("1.00"),
                List.of(
                        new ComponenteDoScore(AspectoDoVoo.PRECO, melhor, 50, "menor preco ja visto"),
                        new ComponenteDoScore(AspectoDoVoo.ESCALAS, pior, 20, "2 escala(s)"),
                        new ComponenteDoScore(AspectoDoVoo.DURACAO, melhor, 20, "10h00"),
                        new ComponenteDoScore(AspectoDoVoo.HORARIO, melhor, 10, "parte de manha")),
                "bom");
    }

    private PriceTrend tendencia(DirecaoDaTendencia direcao, String frase) {
        return new PriceTrend("GRU", "LIS", FonteDeStats.CONFIRMADAS, direcao,
                new BigDecimal("-7.0"), 12, 40, true, List.of(), frase);
    }

    private Recomendacao compor(
            GrauDeAnomalia grau, int notaDoVoo, DirecaoDaTendencia direcao, boolean confiavel) {

        return service.compor(
                oferta(),
                referencia(confiavel),
                anomalia(grau, "17.1% abaixo da mediana de R$ 3.500"),
                nota(notaDoVoo, confiavel),
                tendencia(direcao, "o preco esta estavel nos ultimos 12 dias com observacao"));
    }

    private Razao razaoDe(Recomendacao r, Aspecto aspecto) {
        return r.razoes().stream()
                .filter(x -> x.aspecto() == aspecto)
                .findFirst()
                .orElseThrow(() -> new AssertionError("sem razao de " + aspecto));
    }

    // ------------------------------------------------------------ veredito

    @Test
    @DisplayName("preço recorde e voo bom viram a recomendação mais forte")
    void ofertaExcelente() {
        Recomendacao r = compor(GrauDeAnomalia.RECORDE, 92, DirecaoDaTendencia.SUBINDO, true);

        assertThat(r.veredito()).isEqualTo(Veredito.VALE_MUITO);
        assertThat(r.confiavel()).isTrue();
        assertThat(r.resumo()).startsWith("vale muito a pena");
    }

    @Test
    @DisplayName("bom preço com voo mediano ainda vale")
    void bomPrecoVooMediano() {
        Recomendacao r = compor(GrauDeAnomalia.BOM, 65, DirecaoDaTendencia.ESTAVEL, true);

        assertThat(r.veredito()).isEqualTo(Veredito.VALE);
    }

    @Test
    @DisplayName("preço comum e voo ruim não são recomendados")
    void ofertaRuim() {
        Recomendacao r = compor(GrauDeAnomalia.NORMAL, 35, DirecaoDaTendencia.ESTAVEL, true);

        assertThat(r.veredito()).isEqualTo(Veredito.NAO_RECOMENDO);
        assertThat(r.resumo()).startsWith("nao me parece uma boa oferta");
    }

    @Test
    @DisplayName("nada de especial em nenhuma direção é 'oferta comum'")
    void ofertaComum() {
        Recomendacao r = compor(GrauDeAnomalia.NORMAL, 65, DirecaoDaTendencia.ESTAVEL, true);

        assertThat(r.veredito()).isEqualTo(Veredito.TALVEZ);
    }

    @Test
    @DisplayName("o preço pesa mais que o conforto")
    void precoPesaMais() {
        // Preço recorde com voo ruim ainda vale; o inverso, não.
        Recomendacao precoBom = service.compor(oferta(), referencia(true),
                anomalia(GrauDeAnomalia.RECORDE, "menor preco visto nos ultimos 90 dias"),
                nota(40, true),
                tendencia(DirecaoDaTendencia.ESTAVEL, "estavel"));

        // O sistema existe para achar passagem barata; premiar conforto acima
        // de preço contradiria o produto.
        assertThat(precoBom.veredito()).isEqualTo(Veredito.VALE);
    }

    // ------------------------------------------------- sem base para opinar

    @Nested
    @DisplayName("sem histórico, não opina")
    class SemBase {

        @Test
        @DisplayName("estatística fraca zera o veredito, mesmo com preço ótimo")
        void estatisticaFracaViraSemBase() {
            Recomendacao r = compor(GrauDeAnomalia.RECORDE, 95, DirecaoDaTendencia.SUBINDO, false);

            // A E2.2 já se recusa a julgar aqui; seria estranho a E3.3 julgar
            // assim mesmo.
            assertThat(r.veredito()).isEqualTo(Veredito.SEM_BASE);
            assertThat(r.confiavel()).isFalse();
            assertThat(r.resumo()).startsWith("ainda nao da para dizer");
        }

        @Test
        @DisplayName("o texto sem base explica o motivo, e não fica no vago")
        void textoSemBaseExplica() {
            Recomendacao r = compor(GrauDeAnomalia.BOM, 80, DirecaoDaTendencia.ESTAVEL, false);

            assertThat(r.resumo()).contains("3 preco(s) confirmado(s)");
        }

        @Test
        @DisplayName("oferta nula não estoura")
        void ofertaNula() {
            Recomendacao r = service.recomendar(null, null);

            assertThat(r.veredito()).isEqualTo(Veredito.SEM_BASE);
            assertThat(r.razoes()).isEmpty();
        }
    }

    // ------------------------------------------------------------- razões

    @Test
    @DisplayName("cada razão diz de que lado está")
    void razoesTemLado() {
        Recomendacao r = compor(GrauDeAnomalia.RECORDE, 92, DirecaoDaTendencia.SUBINDO, true);

        assertThat(razaoDe(r, Aspecto.PRECO).peso()).isEqualTo(Peso.A_FAVOR);
        assertThat(razaoDe(r, Aspecto.VOO).peso()).isEqualTo(Peso.A_FAVOR);
        assertThat(razaoDe(r, Aspecto.TENDENCIA).peso()).isEqualTo(Peso.A_FAVOR);
        // O histórico é contexto: nunca empurra para um lado.
        assertThat(razaoDe(r, Aspecto.HISTORICO).peso()).isEqualTo(Peso.A_PONDERAR);
    }

    @Test
    @DisplayName("preço em queda é para ponderar, e não um argumento contra")
    void quedaEParaPonderar() {
        Recomendacao r = compor(GrauDeAnomalia.BOM, 85, DirecaoDaTendencia.CAINDO, true);

        // Queda não piora a oferta — torna razoável esperar. Marcar como
        // "contra" seria conselho disfarçado de fato.
        assertThat(razaoDe(r, Aspecto.TENDENCIA).peso()).isEqualTo(Peso.A_PONDERAR);
        assertThat(r.veredito()).isEqualTo(Veredito.VALE_MUITO);
    }

    @Test
    @DisplayName("análise sem dados não vira razão nenhuma")
    void semDadosNaoViraRazao() {
        Recomendacao r = service.compor(
                oferta(),
                referencia(true),
                anomalia(GrauDeAnomalia.SEM_DADOS, "so ha 3 observacoes"),
                nota(80, false),
                new PriceTrend("GRU", "LIS", FonteDeStats.CONFIRMADAS,
                        DirecaoDaTendencia.SEM_DADOS, null, 1, 1, false, List.of(), ""));

        // "Não medi" não é argumento. Sobra só o histórico, que é contexto.
        assertThat(r.razoes()).extracting(Razao::aspecto).containsExactly(Aspecto.HISTORICO);
        assertThat(r.veredito()).isEqualTo(Veredito.TALVEZ);
    }

    @Test
    @DisplayName("nota não confiável não vira razão sobre o voo")
    void notaPreliminarNaoViraRazao() {
        Recomendacao r = service.compor(oferta(), referencia(true),
                anomalia(GrauDeAnomalia.BOM, "17.1% abaixo da mediana"),
                nota(95, false),
                tendencia(DirecaoDaTendencia.ESTAVEL, "estavel"));

        assertThat(r.razoes()).extracting(Razao::aspecto).doesNotContain(Aspecto.VOO);
    }

    @Test
    @DisplayName("o ponto fraco do voo é nomeado, e não escondido")
    void pontoFracoENomeado() {
        Recomendacao r = service.compor(oferta(), referencia(true),
                anomalia(GrauDeAnomalia.RECORDE, "menor preco visto"),
                notaDesigual(95, 30),
                tendencia(DirecaoDaTendencia.ESTAVEL, "estavel"));

        // Nota média 62: o texto precisa dizer o que puxou para baixo.
        assertThat(razaoDe(r, Aspecto.VOO).frase()).contains("2 escala(s)");
    }

    // -------------------------------------------------------------- texto

    @Test
    @DisplayName("a razão sobre o voo não fala de preço")
    void razaoDeVooNaoFalaDePreco() {
        // Visto em execução real: o componente de preço era o mais alto da
        // nota, e a frase sobre o VOO virava "voo bom: menor preco ja visto na
        // rota" — repetindo, com outras palavras, o argumento da linha anterior.
        Recomendacao r = service.compor(oferta(), referencia(true),
                anomalia(GrauDeAnomalia.RECORDE, "menor preco visto nos ultimos 90 dias"),
                new FlightScore(83, true, new BigDecimal("1.00"),
                        List.of(
                                new ComponenteDoScore(AspectoDoVoo.PRECO, 100, 50, "menor preco ja visto na rota"),
                                new ComponenteDoScore(AspectoDoVoo.ESCALAS, 100, 20, "voo direto"),
                                new ComponenteDoScore(AspectoDoVoo.DURACAO, 60, 20, "12h00, contra 10h00 do melhor da rota"),
                                new ComponenteDoScore(AspectoDoVoo.HORARIO, 100, 10, "parte de manha")),
                        "bom"),
                tendencia(DirecaoDaTendencia.ESTAVEL, "estavel"));

        String frase = razaoDe(r, Aspecto.VOO).frase();
        assertThat(frase).doesNotContain("preco");
        assertThat(frase).containsAnyOf("voo direto", "parte de manha");
    }

    @Test
    @DisplayName("o texto separa o que é a favor, contra e a ponderar")
    void textoSeparaOsLados() {
        Recomendacao r = service.compor(oferta(), referencia(true),
                anomalia(GrauDeAnomalia.RECORDE, "menor preco visto nos ultimos 90 dias"),
                notaDesigual(30, 30),
                tendencia(DirecaoDaTendencia.CAINDO, "o preco vem caindo cerca de 7% por semana"));

        assertThat(r.resumo())
                .contains("menor preco visto")
                .contains("Por outro lado")
                .contains("Para ponderar")
                .contains("vem caindo");
    }

    @Test
    @DisplayName("não dá conselho de compra em nenhum veredito")
    void nuncaDaConselho() {
        for (GrauDeAnomalia grau : GrauDeAnomalia.values()) {
            for (DirecaoDaTendencia direcao : DirecaoDaTendencia.values()) {
                String texto = compor(grau, 85, direcao, true).resumo().toLowerCase();

                // A D-072 vale aqui também: "vale muito a pena" é veredito sobre
                // a oferta; "compre agora" seria conselho sobre a decisão, que
                // não é do sistema.
                assertThat(texto)
                        .doesNotContain("compre")
                        .doesNotContain("aproveite")
                        .doesNotContain("corra")
                        .doesNotContain("garanta")
                        .doesNotContain("nao perca");
            }
        }
    }

    @Test
    @DisplayName("o texto não usa jargão de estatística")
    void semJargao() {
        String texto = compor(GrauDeAnomalia.EXCELENTE, 88, DirecaoDaTendencia.CAINDO, true)
                .resumo().toLowerCase();

        assertThat(texto)
                .doesNotContain("quartil")
                .doesNotContain("desvio padrao")
                .doesNotContain("percentil")
                .doesNotContain("theil")
                .doesNotContain("tukey");
    }

    @Test
    @DisplayName("a recomendação sempre termina em ponto")
    void textoBemFormado() {
        for (GrauDeAnomalia grau : GrauDeAnomalia.values()) {
            String texto = compor(grau, 70, DirecaoDaTendencia.ESTAVEL, true).resumo();

            assertThat(texto).isNotBlank();
            assertThat(texto).endsWith(".");
            // Sem espaço duplo nem pontuação solta de concatenação torta.
            assertThat(texto).doesNotContain("  ").doesNotContain(" .").doesNotContain("..");
        }
    }

    @Test
    @DisplayName("o resumo é para ler; as razões são para conferir")
    void razoesAcompanhamOTexto() {
        Recomendacao r = compor(GrauDeAnomalia.BOM, 85, DirecaoDaTendencia.ESTAVEL, true);

        // Uma recomendação sem a lista seria opinião sem prestação de contas —
        // e a primeira vez que ela errasse, não haveria como descobrir de onde
        // saiu.
        assertThat(r.razoes()).isNotEmpty();
        for (Razao razao : r.razoes()) {
            assertThat(r.resumo()).contains(razao.frase());
        }
    }
}
