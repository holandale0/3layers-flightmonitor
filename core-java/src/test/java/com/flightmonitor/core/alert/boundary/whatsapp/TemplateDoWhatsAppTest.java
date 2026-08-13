package com.flightmonitor.core.alert.boundary.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.flightmonitor.core.alert.control.AlertMessageFormatter;
import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.search.entity.PriceObservation;
import com.flightmonitor.core.search.entity.PriceSource;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Amarra a configuracao ao template versionado em {@code docs/template-alerta.json}.
 *
 * <h2>O buraco que este teste fecha</h2>
 *
 * O {@code WhatsAppChannelTest} monta {@link WhatsAppProperties} a mao, entao
 * nunca enxerga o {@code application.yml}. Foi por isso que o YAML ficou dois
 * dias apontando para {@code alerta_passagem} — um template que <b>nao existe
 * mais</b> — sem nenhum teste reclamar. Trocar o canal para WHATSAPP teria feito
 * todo alerta falhar com <i>132001: template nao encontrado</i>, e a suite
 * inteira continuaria verde.
 *
 * <p>Aqui as propriedades vem do contexto Spring de verdade, como em producao.
 *
 * <h2>Por que conferir tambem a quantidade de parametros</h2>
 *
 * A Meta recusa a mensagem inteira se o numero de parametros enviados nao bater
 * com o do template aprovado. A primeira versao tinha seis e foi recusada por
 * <i>"a proporcao entre palavras e parametros excede o limite"</i>; origem e
 * destino viraram um so. Se alguem acrescentar um sexto parametro ao formatador
 * sem republicar o template, este teste falha antes de a Meta recusar.
 */
@SpringBootTest
class TemplateDoWhatsAppTest {

    /** Onde o template aprovado esta versionado. Caminho relativo ao core-java. */
    private static final Path ARQUIVO = Path.of("..", "docs", "template-alerta.json");

    private static final Pattern PARAMETRO = Pattern.compile("\\{\\{(\\d+)}}");

    private static JsonNode template;

    @Autowired
    private WhatsAppProperties props;

    @Autowired
    private AlertMessageFormatter formatador;

    @BeforeAll
    static void lerTemplateVersionado() throws IOException {
        assertThat(ARQUIVO)
                .as("o template aprovado precisa continuar versionado no repositorio")
                .exists();
        template = new ObjectMapper().readTree(Files.readString(ARQUIVO));
    }

    @Test
    @DisplayName("a configuracao aponta para o template que realmente foi aprovado")
    void configuracaoApontaParaOTemplateCerto() {
        assertThat(props.templateName()).isEqualTo(template.get("name").asString());
        assertThat(props.templateLanguage()).isEqualTo(template.get("language").asString());
    }

    @Test
    @DisplayName("o formatador produz exatamente os parametros que o template espera")
    void quantidadeDeParametrosBate() {
        List<Integer> posicoes = posicoesNoCorpo();

        // Numerados de 1 a N, sem buraco: a Meta casa por posicao, entao um
        // salto silencioso deslocaria todos os valores seguintes.
        assertThat(posicoes).containsExactlyElementsOf(
                java.util.stream.IntStream.rangeClosed(1, posicoes.size()).boxed().toList());

        assertThat(formatador.parametrosDoTemplate(monitorExemplo(), ofertaExemplo()))
                .hasSameSizeAs(posicoes);
    }

    @Test
    @DisplayName("nenhum parametro viola o que a Meta proibe")
    void parametrosRespeitamAsRegrasDaMeta() {
        List<String> params = formatador.parametrosDoTemplate(monitorExemplo(), ofertaExemplo());

        assertThat(params).allSatisfy(p -> {
            // Vazio, quebra de linha, tabulacao ou 5 espacos seguidos fazem a
            // API recusar a mensagem INTEIRA, nao so o parametro.
            assertThat(p).isNotBlank();
            assertThat(p).doesNotContain("\n", "\r", "\t");
            assertThat(p).doesNotContain("    ");
            // O NumberFormat do pt-BR separa "R$" do valor com espaco NAO
            // QUEBRAVEL (U+00A0). Invisivel, e vaza para o WhatsApp do usuario.
            assertThat(p).doesNotContain(" ");
        });
    }

    private List<Integer> posicoesNoCorpo() {
        String corpo = null;
        for (JsonNode componente : template.get("components")) {
            if ("BODY".equals(componente.get("type").asString())) {
                corpo = componente.get("text").asString();
            }
        }
        assertThat(corpo).as("o template precisa ter um componente BODY").isNotNull();

        List<Integer> posicoes = new ArrayList<>();
        Matcher m = PARAMETRO.matcher(corpo);
        while (m.find()) {
            posicoes.add(Integer.valueOf(m.group(1)));
        }
        return posicoes;
    }

    private Monitor monitorExemplo() {
        Monitor m = new Monitor();
        m.setOrigin("GRU");
        m.setDestination("LIS");
        m.setMaxPrice(new BigDecimal("9000.00"));
        m.setCurrency("BRL");
        return m;
    }

    private PriceObservation ofertaExemplo() {
        LocalDate ida = LocalDate.now().plusMonths(2);
        PriceObservation o = new PriceObservation(
                null, "GRU", "LIS", ida, new BigDecimal("5602.00"), PriceSource.FAST_FLIGHTS);
        o.setReturnDate(ida.plusDays(13));
        o.setCurrency("BRL");
        o.setAirline("Air Europa");
        o.setStops((short) 1);
        o.setConfirmed(true);
        return o;
    }
}
