package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.Alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceSource;
import com.flightmonitor.core.stats.control.AspectoDoVoo;
import com.flightmonitor.core.stats.control.FlightScore;
import com.flightmonitor.core.stats.control.FlightScore.ComponenteDoScore;
import com.flightmonitor.core.stats.control.FonteDeStats;
import com.flightmonitor.core.stats.entity.GrauDeAnomalia;
import com.flightmonitor.core.stats.control.PriceAnomaly;
import com.flightmonitor.core.stats.control.RouteStats;

/**
 * Alerta enriquecido — etapa E2.4.
 *
 * <p>Sem Spring: o formatador é uma função pura sobre os dados que recebe. Subir
 * o contexto aqui só tornaria os testes lentos sem cobrir nada a mais.
 */
class AlertEnriquecidoTest {

    private final AlertMessageFormatter formatador = new AlertMessageFormatter();

    private Monitor monitor() {
        Monitor m = new Monitor();
        m.setLabel("Lisboa");
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setMaxPrice(new BigDecimal("4000.00"));
        m.setCurrency("BRL");
        return m;
    }

    private PriceObservation oferta() {
        PriceObservation o = new PriceObservation(
                null, "GRU", "LIS", LocalDate.of(2027, 3, 10),
                new BigDecimal("2900.00"), PriceSource.FAST_FLIGHTS);
        o.setReturnDate(LocalDate.of(2027, 3, 22));
        o.setCurrency("BRL");
        o.setAirline("Iberia");
        o.setStops((short) 1);
        o.setDurationMinutes(745);
        o.setDepartureAt(LocalDateTime.of(2027, 3, 10, 9, 15));
        o.setConfirmed(true);
        return o;
    }

    private RouteStats referencia(boolean confiavel) {
        return new RouteStats(
                "GRU", "LIS", FonteDeStats.CONFIRMADAS, confiavel ? 20 : 3, confiavel,
                new BigDecimal("2000"), new BigDecimal("3000"), new BigDecimal("3500"),
                new BigDecimal("3600"), new BigDecimal("4000"), new BigDecimal("6000"),
                new BigDecimal("700"), Instant.now(), Instant.now());
    }

    private PriceAnomaly anomalia(GrauDeAnomalia grau, String queda, String explicacao) {
        return new PriceAnomaly(
                new BigDecimal("2900.00"), grau,
                queda == null ? null : new BigDecimal(queda),
                new BigDecimal("1500.00"), explicacao, referencia(true));
    }

    private FlightScore score(int nota, boolean confiavel) {
        return new FlightScore(nota, confiavel, new BigDecimal("1.00"),
                List.of(
                        new ComponenteDoScore(AspectoDoVoo.PRECO, 88, 50, "abaixo da mediana da rota"),
                        new ComponenteDoScore(AspectoDoVoo.ESCALAS, 65, 20, "1 escala(s)"),
                        new ComponenteDoScore(AspectoDoVoo.DURACAO, 70, 20, "12h25, contra 10h00 do melhor da rota"),
                        new ComponenteDoScore(AspectoDoVoo.HORARIO, 100, 10, "parte de manha")),
                "bom");
    }

    // ------------------------------------------------------ texto livre

    @Test
    @DisplayName("a mensagem ganha a comparação histórica e a nota")
    void mensagemEnriquecida() {
        AlertInsights analise = AlertInsights.de(
                anomalia(GrauDeAnomalia.BOM, "17.1", "17.1% abaixo da mediana de R$ 3.500"),
                score(82, true));

        String msg = formatador.formatar(monitor(), oferta(), analise);

        assertThat(msg)
                .contains("GRU → LIS")
                // Sem o "R$" junto: no texto livre ele vem colado ao valor por
                // um espaço NÃO-QUEBRÁVEL, e não por um espaço comum. Ver o
                // teste espacoNaoQuebravelSoSaiDosParametros.
                .contains("2.900,00")
                .contains("17.1% abaixo da mediana")
                .contains("Nota do voo: 82/100")
                // A nota vem acompanhada do que mais pesou a favor — sozinha,
                // ela é inauditável.
                .contains("parte de manha");
    }

    @Test
    @DisplayName("o texto continua legível: cada informação em sua linha")
    void mensagemMantemAEstrutura() {
        AlertInsights analise = AlertInsights.de(
                anomalia(GrauDeAnomalia.RECORDE, "25.0", "menor preco visto nos ultimos 90 dias"),
                score(91, true));

        String msg = formatador.formatar(monitor(), oferta(), analise);

        assertThat(msg.lines().filter(l -> l.startsWith("📉"))).hasSize(1);
        assertThat(msg.lines().filter(l -> l.startsWith("⭐"))).hasSize(1);
        // O rodapé com o apelido do monitor continua por último.
        assertThat(msg).endsWith("_Monitor: Lisboa_");
    }

    // ------------------------------------------ a regra: na dúvida, silêncio

    @Nested
    @DisplayName("o que não se sustenta não entra na mensagem")
    class SilencioQuandoNaoSabe {

        @Test
        @DisplayName("monitor novo recebe exatamente a mensagem de antes da E2.4")
        void semAnaliseAMensagemNaoMuda() {
            String antes = formatador.formatar(monitor(), oferta());
            String comAnaliseVazia = formatador.formatar(monitor(), oferta(), AlertInsights.vazio());

            assertThat(comAnaliseVazia).isEqualTo(antes);
            // Nem ressalva, nem "dados insuficientes", nem nota preliminar.
            assertThat(comAnaliseVazia).doesNotContain("Nota do voo").doesNotContain("mediana");
        }

        @Test
        @DisplayName("preço normal não vira mensagem — ninguém quer saber que está na média")
        void anomaliaNormalNaoEntra() {
            AlertInsights analise = AlertInsights.de(
                    anomalia(GrauDeAnomalia.NORMAL, "2.9", "dentro da faixa normal da rota"),
                    null);

            assertThat(analise.temAnomalia()).isFalse();
            assertThat(formatador.formatar(monitor(), oferta(), analise))
                    .doesNotContain("faixa normal");
        }

        @Test
        @DisplayName("SEM_DADOS não entra, e não vira 'não sabemos'")
        void anomaliaSemDadosNaoEntra() {
            AlertInsights analise = AlertInsights.de(
                    anomalia(GrauDeAnomalia.SEM_DADOS, null, "so ha 3 observacao(oes)"),
                    null);

            // Escrever "não temos histórico" numa mensagem de oportunidade é
            // ruído: o usuário quer saber da passagem, não do nosso banco.
            assertThat(formatador.formatar(monitor(), oferta(), analise))
                    .doesNotContain("observacao")
                    .doesNotContain("📉");
        }

        @Test
        @DisplayName("nota não confiável fica de fora — 87/100 é autoridade que ela não tem")
        void scorePreliminarNaoEntra() {
            AlertInsights analise = AlertInsights.de(null, score(87, false));

            assertThat(analise.temScore()).isFalse();
            assertThat(formatador.formatar(monitor(), oferta(), analise))
                    .doesNotContain("Nota do voo");
        }

        @Test
        @DisplayName("uma parte pode entrar sem a outra")
        void enriquecimentoEIndependentePorCampo() {
            AlertInsights soAnomalia = AlertInsights.de(
                    anomalia(GrauDeAnomalia.RECORDE, "25.0", "menor preco visto nos ultimos 90 dias"),
                    score(87, false));

            String msg = formatador.formatar(monitor(), oferta(), soAnomalia);

            assertThat(msg).contains("menor preco visto");
            assertThat(msg).doesNotContain("Nota do voo");
        }
    }

    // ------------------------------------------------- parâmetros do template

    @Nested
    @DisplayName("template do WhatsApp: cinco continuam sendo cinco")
    class ParametrosDoTemplate {

        @Test
        @DisplayName("o enriquecimento cabe dentro dos parâmetros existentes")
        void enriquecimentoSemMudarAEstrutura() {
            AlertInsights analise = AlertInsights.de(
                    anomalia(GrauDeAnomalia.BOM, "17.1", "17.1% abaixo da mediana de R$ 3.500"),
                    score(82, true));

            List<String> params = formatador.parametrosDoTemplate(monitor(), oferta(), analise);

            // Mudar a quantidade exigiria template novo e nova aprovação da
            // Meta — que já custou duas rodadas neste projeto.
            assertThat(params).hasSize(5);
            assertThat(params.get(2)).isEqualTo("Iberia, 1 escala, nota 82/100");
            assertThat(params.get(3)).isEqualTo("R$ 2.900,00 (17.1% abaixo da mediana)");
        }

        @Test
        @DisplayName("recorde vira uma expressão curta, e não a frase inteira")
        void recordeEmParametro() {
            AlertInsights analise = AlertInsights.de(
                    anomalia(GrauDeAnomalia.RECORDE, "25.0",
                            "menor preco visto nos ultimos 90 dias nesta rota"),
                    null);

            List<String> params = formatador.parametrosDoTemplate(monitor(), oferta(), analise);

            // A frase completa cabe no texto livre; aqui ela entraria no meio de
            // uma linha que já tem preço e rótulo.
            assertThat(params.get(3)).isEqualTo("R$ 2.900,00 (menor preco ja visto)");
        }

        @Test
        @DisplayName("sem análise, os parâmetros são idênticos aos de antes")
        void semAnaliseParametrosNaoMudam() {
            List<String> antes = formatador.parametrosDoTemplate(monitor(), oferta());
            List<String> vazios = formatador.parametrosDoTemplate(
                    monitor(), oferta(), AlertInsights.vazio());

            assertThat(vazios).isEqualTo(antes);
        }

        @Test
        @DisplayName("os parâmetros enriquecidos continuam respeitando as regras da Meta")
        void regrasDaMetaContinuamValendo() {
            AlertInsights analise = AlertInsights.de(
                    anomalia(GrauDeAnomalia.EXCELENTE, "31.4",
                            "31.4% abaixo da mediana de R$ 3.500 — preco atipico"),
                    score(93, true));

            List<String> params = formatador.parametrosDoTemplate(monitor(), oferta(), analise);

            assertThat(params).allSatisfy(p -> {
                // Vazio, quebra de linha, tabulação ou espaço não-quebrável
                // fazem a API recusar a mensagem INTEIRA.
                assertThat(p).isNotBlank();
                assertThat(p).doesNotContain("\n", "\r", "\t");
                assertThat(p).doesNotContain("    ");
                assertThat(p).doesNotContain(" ");
            });
        }
    }

    @Test
    @DisplayName("o espaço não-quebrável fica no texto livre e some dos parâmetros")
    void espacoNaoQuebravelSoSaiDosParametros() {
        AlertInsights analise = AlertInsights.de(
                anomalia(GrauDeAnomalia.BOM, "17.1", "17.1% abaixo da mediana"),
                score(82, true));

        String texto = formatador.formatar(monitor(), oferta(), analise);
        List<String> params = formatador.parametrosDoTemplate(monitor(), oferta(), analise);

        // O NumberFormat do pt-BR separa "R$" do valor com U+00A0. No texto
        // livre isso é correto — é o que evita a moeda ficar órfã numa quebra
        // de linha.
        assertThat(texto).contains("R$ 2.900,00");
        // Nos parâmetros do template ele precisa sair: a Meta recusa.
        assertThat(params).allSatisfy(p -> assertThat(p).doesNotContain(" "));
        assertThat(params.get(3)).startsWith("R$ 2.900,00");
    }

    // -------------------------------------------------------- persistência

    @Test
    @DisplayName("o alerta guarda o que sabia, para a entrega e para o histórico")
    void alertaGuardaAAnalise() {
        Alert alerta = new Alert(monitor(), oferta(), null, "mensagem");
        AlertInsights analise = AlertInsights.de(
                anomalia(GrauDeAnomalia.BOM, "17.1", "17.1% abaixo da mediana"),
                score(82, true));

        alerta.registrarAnalise(analise.nota(), analise.grau(), analise.quedaPercentual());

        assertThat(alerta.getFlightScore()).isEqualTo((short) 82);
        assertThat(alerta.getAnomalyGrade()).isEqualTo(GrauDeAnomalia.BOM);
        assertThat(alerta.getAnomalyDropPct()).isEqualByComparingTo("17.1");
    }

    @Test
    @DisplayName("o que foi gravado reconstrói os parâmetros na hora da entrega")
    void analiseGravadaReconstroiOsParametros() {
        Alert alerta = new Alert(monitor(), oferta(), null, "mensagem");
        AlertInsights daCriacao = AlertInsights.de(
                anomalia(GrauDeAnomalia.BOM, "17.1", "17.1% abaixo da mediana"),
                score(82, true));
        alerta.registrarAnalise(
                daCriacao.nota(), daCriacao.grau(), daCriacao.quedaPercentual());

        // É o caminho do canal do WhatsApp: entidade desanexada, fora de
        // transação, sem poder recalcular nada.
        AlertInsights daEntrega = AlertInsights.doAlerta(alerta);
        List<String> params = formatador.parametrosDoTemplate(monitor(), oferta(), daEntrega);

        assertThat(params.get(2)).isEqualTo("Iberia, 1 escala, nota 82/100");
        assertThat(params.get(3)).isEqualTo("R$ 2.900,00 (17.1% abaixo da mediana)");
    }

    @Test
    @DisplayName("alerta sem análise não grava zero — grava nulo")
    void semAnaliseGravaNulo() {
        Alert alerta = new Alert(monitor(), oferta(), null, "mensagem");

        AlertInsights vazia = AlertInsights.vazio();
        alerta.registrarAnalise(vazia.nota(), vazia.grau(), vazia.quedaPercentual());

        // Zero seria "nota zero", que é outra coisa.
        assertThat(alerta.getFlightScore()).isNull();
        assertThat(alerta.getAnomalyGrade()).isNull();
        assertThat(alerta.getAnomalyDropPct()).isNull();
    }
}
