package com.flightmonitor.core.stats.control;

import com.flightmonitor.core.stats.entity.GrauDeAnomalia;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Detecção de anomalia de preço — etapa E2.2.
 *
 * <p>Os testes montam {@link RouteStats} <b>a mão</b>, sem passar pelo banco.
 * A E2.1 já provou que os números saem certos do PostgreSQL; repetir aquilo aqui
 * só tornaria estes testes lentos e faria uma falha de agregação quebrar duas
 * suítes com a mesma causa.
 *
 * <p>O que se testa aqui é a <b>regra de julgamento</b> — e ela é aritmética
 * pura sobre números conhecidos.
 */
@SpringBootTest
class PriceAnomalyTest {

    @Autowired
    private PriceAnomalyService service;

    @Autowired
    private StatsProperties props;

    /**
     * Rota comum: quartis em 3.000 e 4.000, mínimo já bem baixo.
     *
     * <p>Intervalo interquartil de 1.000, então o limite de Tukey fica em
     * {@code 3000 - 1,5 x 1000 = 1500} — <b>abaixo do mínimo de 1.800</b>.
     *
     * <p>Isso não é descuido na montagem: é o caso mais comum, e tem uma
     * consequência que o {@link #excelenteEInalcancavelQuandoOLimiteFicaAbaixoDoMinimo()}
     * documenta — aqui o grau EXCELENTE é inalcançável, porque todo preço abaixo
     * do limite também é recorde.
     */
    private RouteStats referencia() {
        return referencia(20, "1800", "3000", "3500", "3600", "4000", "6000");
    }

    /**
     * Rota que já viu um preço extremo: mínimo de 1.000, bem abaixo do limite
     * de 1.500. É a configuração em que EXCELENTE existe de fato.
     */
    private RouteStats comExtremoNoHistorico() {
        return referencia(20, "1000", "3000", "3500", "3600", "4000", "6000");
    }

    private RouteStats referencia(
            int amostras, String min, String p25, String mediana,
            String media, String p75, String max) {

        return new RouteStats(
                "GRU", "LIS", FonteDeStats.CONFIRMADAS,
                amostras,
                amostras >= props.minimoAmostras(),
                new BigDecimal(min),
                new BigDecimal(p25),
                new BigDecimal(mediana),
                new BigDecimal(media),
                new BigDecimal(p75),
                new BigDecimal(max),
                new BigDecimal("700"),
                Instant.now().minusSeconds(86400 * 60),
                Instant.now());
    }

    private PriceAnomaly avaliar(String preco) {
        return service.avaliar(new BigDecimal(preco), referencia());
    }

    // ----------------------------------------------------------- os graus

    @Test
    @DisplayName("preço acima do primeiro quartil é normal, e não vira mensagem")
    void precoComumENormal() {
        PriceAnomaly a = avaliar("3400");

        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.NORMAL);
        assertThat(a.interessante()).isFalse();
        // 3500 -> 3400 é queda de 2,9%: verdadeira, e não é notícia.
        assertThat(a.quedaPercentual()).isEqualByComparingTo("2.9");
    }

    @Test
    @DisplayName("no primeiro quartil já entra como bom — o limite é inclusivo")
    void noQuartilEBom() {
        assertThat(avaliar("3000").grau()).isEqualTo(GrauDeAnomalia.BOM);
    }

    @Test
    @DisplayName("abaixo do limite de Tukey, e acima do mínimo, é excelente")
    void abaixoDoLimiteEExcelente() {
        // Limite = 3000 - 1,5 x (4000 - 3000) = 1500. O mínimo histórico é
        // 1.000, então 1.490 fica na faixa "tão bom quanto os extremos já
        // vistos, sem ser um novo recorde".
        PriceAnomaly a = service.avaliar(new BigDecimal("1490"), comExtremoNoHistorico());

        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.EXCELENTE);
        assertThat(a.limiteEstatistico()).isEqualByComparingTo("1500.00");
    }

    @Test
    @DisplayName("exatamente no limite ainda não é atípico")
    void exatamenteNoLimiteNaoEAtipico() {
        // Estritamente abaixo: o limite pertence à faixa comum.
        assertThat(service.avaliar(new BigDecimal("1500"), comExtremoNoHistorico()).grau())
                .isEqualTo(GrauDeAnomalia.BOM);
    }

    @Test
    @DisplayName("quando o limite cai abaixo do mínimo, EXCELENTE deixa de existir")
    void excelenteEInalcancavelQuandoOLimiteFicaAbaixoDoMinimo() {
        // Na referência comum, limite = 1.500 e mínimo = 1.800. Qualquer preço
        // abaixo do limite é, por construção, também um recorde.
        //
        // Não é defeito: é o que a escala diz. Uma rota cujo menor preço já
        // visto está DENTRO da faixa comum nunca teve um extremo — então o
        // próximo preço muito baixo é, corretamente, notícia de recorde e não
        // "parecido com os extremos de antes".
        PriceAnomaly a = avaliar("1490");

        assertThat(a.limiteEstatistico()).isEqualByComparingTo("1500.00");
        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.RECORDE);
    }

    @Test
    @DisplayName("igualar o menor preço já visto conta como recorde")
    void igualarOMinimoERecorde() {
        assertThat(avaliar("1800").grau()).isEqualTo(GrauDeAnomalia.RECORDE);
    }

    @Test
    @DisplayName("recorde vence excelente — o grau mais forte é o que se diz")
    void recordeVenceExcelente() {
        // 1200 está abaixo do limite de Tukey E abaixo do mínimo. Os dois são
        // verdade; dizer "entre os mais baratos" quando é O mais barato diria
        // menos do que se sabe.
        PriceAnomaly a = avaliar("1200");

        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.RECORDE);
        assertThat(a.explicacao()).contains("menor preco visto");
    }

    @Test
    @DisplayName("preço acima da mediana devolve queda negativa, e não zero")
    void precoAcimaDaMedianaTemQuedaNegativa() {
        PriceAnomaly a = avaliar("4200");

        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.NORMAL);
        // -20% diz "está 20% acima". Zerar aqui esconderia a informação.
        assertThat(a.quedaPercentual()).isEqualByComparingTo("-20.0");
    }

    // ------------------------------------------------------- sem veredito

    @Test
    @DisplayName("amostra insuficiente não vira veredito, mesmo com preço ótimo")
    void amostraInsuficienteNaoJulga() {
        RouteStats poucas = referencia(
                props.minimoAmostras() - 1, "1800", "3000", "3500", "3600", "4000", "6000");

        PriceAnomaly a = service.avaliar(new BigDecimal("1000"), poucas);

        // R$ 1.000 é espetacular — e sobre três observações isso é chute.
        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.SEM_DADOS);
        assertThat(a.interessante()).isFalse();
        assertThat(a.explicacao()).contains("e pouco para dizer o que e normal");
    }

    @Test
    @DisplayName("rota sem histórico devolve SEM_DADOS, e não NORMAL")
    void rotaSemHistorico() {
        PriceAnomaly a = service.avaliar(
                new BigDecimal("3000"),
                RouteStats.vazio("GRU", "LIS", FonteDeStats.CONFIRMADAS));

        // A distinção que importa: NORMAL afirma "medi e não tem nada de
        // especial". SEM_DADOS diz "não medi". A E2.4 escreveria bobagem se os
        // dois se confundissem.
        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.SEM_DADOS);
        assertThat(a.explicacao()).contains("ainda nao tem historico");
    }

    @Test
    @DisplayName("preço ausente ou negativo não derruba a avaliação")
    void precoInvalido() {
        assertThat(service.avaliar(null, referencia()).grau()).isEqualTo(GrauDeAnomalia.SEM_DADOS);
        assertThat(service.avaliar(BigDecimal.ZERO, referencia()).grau())
                .isEqualTo(GrauDeAnomalia.SEM_DADOS);
    }

    // ------------------------------------------------- robustez da regra

    @Nested
    @DisplayName("por que Tukey, e não z-score")
    class PorQueTukey {

        @Test
        @DisplayName("um preço absurdo na amostra não desloca o julgamento")
        void precoAbsurdoNaoDeslocaOLimite() {
            // Mesma rota, mesmos quartis — só o máximo explodiu, como acontece
            // quando alguém observa uma passagem de última hora.
            RouteStats comOutlier = referencia(
                    20, "1000", "3000", "3500", "9000", "4000", "40000");

            PriceAnomaly a = service.avaliar(new BigDecimal("1490"), comOutlier);

            // O limite continua em 1.500, porque nasce dos quartis. Uma regra de
            // média e desvio padrão teria inflado o desvio e passado a exigir
            // uma queda absurda para reagir.
            assertThat(a.limiteEstatistico()).isEqualByComparingTo("1500.00");
            assertThat(a.grau()).isEqualTo(GrauDeAnomalia.EXCELENTE);
        }

        @Test
        @DisplayName("rota muito instável não promete um limite impossível")
        void limiteNegativoViraNulo() {
            // p25 = 1.000 e p75 = 5.000 dão limite = 1000 - 1,5 x 4000 = -5000.
            RouteStats instavel = referencia(
                    20, "900", "1000", "2000", "2500", "5000", "12000");

            PriceAnomaly a = service.avaliar(new BigDecimal("950"), instavel);

            // Devolver -5.000 seria prometer um patamar que preço nenhum
            // alcança. Sem limite, o grau cai para o que ainda é dizível.
            assertThat(a.limiteEstatistico()).isNull();
            assertThat(a.grau()).isEqualTo(GrauDeAnomalia.BOM);
        }

        @Test
        @DisplayName("rota estável reage a uma queda menor que uma rota volátil")
        void rotaEstavelEMaisSensivel() {
            // Estável: quartis 3.400 e 3.600 -> limite 3.100.
            RouteStats estavel = referencia(20, "3000", "3400", "3500", "3500", "3600", "4000");
            // Volátil: quartis 2.500 e 5.500 -> limite negativo, ou seja, nenhum.
            RouteStats volatil = referencia(20, "1000", "2500", "3500", "3600", "5500", "9000");

            BigDecimal preco = new BigDecimal("3050");

            // O MESMO preço: atípico na rota que quase não varia, apenas bom na
            // que varia muito. É exatamente o que um limiar percentual fixo não
            // conseguiria distinguir.
            assertThat(service.avaliar(preco, estavel).grau()).isEqualTo(GrauDeAnomalia.EXCELENTE);
            assertThat(service.avaliar(preco, volatil).grau()).isEqualTo(GrauDeAnomalia.NORMAL);
        }
    }

    // ------------------------------------------------------- a explicação

    @Test
    @DisplayName("a explicação traz a base, e não só a porcentagem")
    void explicacaoTrazAReferencia() {
        PriceAnomaly a = avaliar("2900");

        assertThat(a.grau()).isEqualTo(GrauDeAnomalia.BOM);
        assertThat(a.explicacao())
                .contains("17.1%")
                // Porcentagem sem referência não significa nada: 17% abaixo do
                // quê? A mediana precisa aparecer.
                .contains("3.500")
                .contains("25% mais baratos");
    }

    @Test
    @DisplayName("a explicação não usa jargão de estatística")
    void explicacaoSemJargao() {
        for (String preco : List.of("1200", "1490", "2900", "3400")) {
            String texto = avaliar(preco).explicacao();
            assertThat(texto).isNotBlank();
            assertThat(texto.toLowerCase())
                    .doesNotContain("quartil")
                    .doesNotContain("desvio")
                    .doesNotContain("percentil")
                    .doesNotContain("tukey");
        }
    }

    @Test
    @DisplayName("nenhum valor monetário sai com espaço não-quebrável")
    void semEspacoNaoQuebravel() {
        // O NumberFormat do pt-BR separa "R$" do valor com U+00A0. Ele viajaria
        // até o parâmetro do template do WhatsApp, onde a Meta recusa.
        for (String preco : List.of("1200", "1490", "2900", "3400")) {
            assertThat(avaliar(preco).explicacao()).doesNotContain(" ");
        }
    }

    @Test
    @DisplayName("o veredito carrega a referência que o produziu")
    void vereditoCarregaAReferencia() {
        PriceAnomaly a = avaliar("2900");

        // Sem isso, um alerta estranho no histórico seria impossível de
        // depurar: não daria para saber contra o que ele foi comparado.
        assertThat(a.referencia()).isNotNull();
        assertThat(a.referencia().amostras()).isEqualTo(20);
        assertThat(a.referencia().fonte()).isEqualTo(FonteDeStats.CONFIRMADAS);
    }
}
