package com.flightmonitor.core.alert.control;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.flightmonitor.core.monitor.entity.Monitor;
import com.flightmonitor.core.search.entity.PriceObservation;

/**
 * Monta a mensagem do alerta.
 *
 * <p>Diferente das mensagens de log, esta vai para o WhatsApp e para o banco:
 * acentos e emoji sao bem-vindos. A restricao ASCII do BUG-001 vale so para o
 * que passa pelo logger no console do Windows.
 */
@Component
public class AlertMessageFormatter {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale BR = Locale.of("pt", "BR");

    /** Sem enriquecimento — usado por quem nao tem a analise em maos. */
    public String formatar(Monitor monitor, PriceObservation oferta) {
        return formatar(monitor, oferta, AlertInsights.vazio());
    }

    /**
     * Mensagem completa, com o que a Fase 2 souber dizer.
     *
     * <p>O canal de texto livre nao tem limite de estrutura, entao aqui cabe
     * tudo: a comparacao historica e a nota, cada uma condicionada a se
     * sustentar. Ver {@link AlertInsights}.
     */
    public String formatar(Monitor monitor, PriceObservation oferta, AlertInsights analise) {
        StringBuilder msg = new StringBuilder();

        msg.append("✈️ *Oportunidade encontrada*\n\n");
        msg.append(oferta.getOrigin()).append(" → ").append(oferta.getDestination()).append('\n');

        msg.append(oferta.getDepartureDate().format(DATA));
        if (oferta.getReturnDate() != null) {
            msg.append(" → ").append(oferta.getReturnDate().format(DATA));
        } else {
            msg.append(" (somente ida)");
        }
        msg.append("\n\n");

        if (oferta.getAirline() != null) {
            msg.append(oferta.getAirline());
            if (oferta.getStops() != null) {
                msg.append(" · ").append(escalas(oferta.getStops()));
            }
            msg.append('\n');
        } else if (oferta.getStops() != null) {
            msg.append(escalas(oferta.getStops())).append('\n');
        }

        msg.append("*").append(dinheiro(oferta.getPrice(), oferta.getCurrency())).append("*\n");
        msg.append("Seu limite: ").append(dinheiro(monitor.getMaxPrice(), monitor.getCurrency()));

        BigDecimal economia = monitor.getMaxPrice().subtract(oferta.getPrice());
        if (economia.signum() > 0) {
            msg.append("  (")
               .append(dinheiro(economia, monitor.getCurrency()))
               .append(" abaixo)");
        }

        // ---- enriquecimento da E2.4, cada parte so se ela se sustentar

        if (analise != null && analise.explicacaoDaAnomalia() != null) {
            msg.append("\n\n📉 ").append(analise.explicacaoDaAnomalia());
        }

        if (analise != null && analise.temScore()) {
            msg.append("\n⭐ Nota do voo: ").append(analise.nota()).append("/100");

            if (analise.resumoDoScore() != null && !analise.resumoDoScore().isBlank()) {
                // A nota sozinha e inauditavel: 87 nao diz por que.
                msg.append(" — ").append(analise.resumoDoScore());
            }
        }

        if (!oferta.isConfirmed()) {
            // Nao deveria acontecer com a politica padrao, mas se alguem ligar
            // alertarSemConfirmacao, a mensagem precisa ser honesta.
            msg.append("\n\n⚠️ _Preço não confirmado: a verificação ao vivo estava indisponível._");
        }

        if (monitor.getLabel() != null && !monitor.getLabel().isBlank()) {
            msg.append("\n\n_Monitor: ").append(monitor.getLabel()).append("_");
        }

        return msg.toString();
    }

    /**
     * Os cinco parametros do template do WhatsApp, na ordem em que aparecem.
     *
     * <p>Alerta e mensagem iniciada pela empresa, e a Meta so aceita template
     * aprovado nesse caso — texto livre vale apenas dentro da janela de 24h
     * depois de o destinatario escrever. Por isso a mesma oferta precisa existir
     * em dois formatos: texto corrido para o canal de log, e parametros para o
     * WhatsApp. Ver docs/GUIA-WHATSAPP.md.
     *
     * <p><b>Por que cinco e nao seis:</b> a primeira versao separava origem e
     * destino em dois parametros, e a Meta recusou o template com
     * <i>"a proporcao entre palavras e parametros excede o limite"</i>. Ela exige
     * texto fixo suficiente em relacao as variaveis. Origem e destino foram
     * unidos em um so.
     *
     * <p><b>Regras que a Meta impoe aos parametros</b> e que este metodo respeita:
     * nenhum pode ser vazio, nenhum pode conter quebra de linha ou tabulacao, e
     * nenhum pode ter mais de 4 espacos seguidos. Violar qualquer uma faz a API
     * recusar a mensagem inteira.
     */
    public List<String> parametrosDoTemplate(Monitor monitor, PriceObservation oferta) {
        return parametrosDoTemplate(monitor, oferta, AlertInsights.vazio());
    }

    /**
     * Os mesmos cinco parametros, enriquecidos por dentro.
     *
     * <p><b>Cinco continuam sendo cinco.</b> O template aprovado tem cinco
     * espacos posicionais, e mudar essa estrutura exige criar um template novo e
     * esperar nova aprovacao da Meta — que ja custou duas rodadas neste projeto,
     * uma por reclassificacao para MARKETING e outra por conta errada
     * (BUG-009). Enriquecer o <i>conteudo</i> dos parametros nao precisa de
     * aprovacao nenhuma: eles sao valores de execucao.
     *
     * <p>Dois recebem a carga:
     *
     * <ul>
     *   <li><b>{{3}} detalhes do voo</b> ganha a nota — "Iberia, 1 escala, nota 87/100";</li>
     *   <li><b>{{4}} preco encontrado</b> ganha a comparacao — "R$ 3.720,00 (18% abaixo da mediana)".</li>
     * </ul>
     *
     * Os dois foram escolhidos porque o texto fixo do template ja os introduz de
     * um jeito que aceita o complemento sem virar frase torta.
     */
    public List<String> parametrosDoTemplate(
            Monitor monitor, PriceObservation oferta, AlertInsights analise) {

        return List.of(
                limpar(oferta.getOrigin() + " para " + oferta.getDestination()),
                limpar(periodo(oferta)),
                limpar(detalhesDoVoo(oferta) + sufixoDaNota(analise)),
                limpar(dinheiro(oferta.getPrice(), oferta.getCurrency()) + sufixoDaComparacao(analise)),
                limpar(dinheiro(monitor.getMaxPrice(), monitor.getCurrency())));
    }

    /** Complemento de {{3}}: a nota, quando ela se sustenta. */
    private String sufixoDaNota(AlertInsights analise) {
        if (analise == null || !analise.temScore()) {
            return "";
        }
        return ", nota %d/100".formatted(analise.nota());
    }

    /**
     * Complemento de {{4}}: a comparacao historica, curta.
     *
     * <p>Curta de proposito. A explicacao completa da E2.2 cabe no texto livre,
     * mas aqui ela entraria no meio de uma linha que ja tem preco e rotulo, e a
     * Meta recusa parametro com quebra de linha — o resultado seria um paragrafo
     * espremido numa linha so.
     */
    private String sufixoDaComparacao(AlertInsights analise) {
        if (analise == null || !analise.temAnomalia()) {
            return "";
        }
        return switch (analise.grau()) {
            case RECORDE -> " (menor preco ja visto)";
            case EXCELENTE, BOM -> analise.quedaPercentual() == null
                    ? ""
                    : " (%s%% abaixo da mediana)".formatted(analise.quedaPercentual());
            default -> "";
        };
    }


    private String periodo(PriceObservation oferta) {
        String ida = oferta.getDepartureDate().format(DATA);
        return oferta.getReturnDate() == null
                ? ida + " (somente ida)"
                : ida + " a " + oferta.getReturnDate().format(DATA);
    }

    private String detalhesDoVoo(PriceObservation oferta) {
        String cia = oferta.getAirline();
        Short escalas = oferta.getStops();

        if (cia != null && escalas != null) {
            return cia + ", " + escalas(escalas);
        }
        if (cia != null) {
            return cia;
        }
        if (escalas != null) {
            return escalas(escalas);
        }
        // Nunca pode sair vazio: a Meta recusa a mensagem inteira.
        return "sem detalhes do voo";
    }

    /** Remove o que a Meta proibe em parametro de template, e normaliza espacos. */
    private String limpar(String valor) {
        if (valor == null || valor.isBlank()) {
            return "-";
        }
        return valor
                // O NumberFormat do portugues separa "R$" do valor com espaco
                // NAO-QUEBRAVEL (U+00A0), nao com espaco comum. Caractere
                // invisivel diferente do esperado e fonte classica de bug dificil:
                // quebra comparacao de texto e pode aparecer torto no destino.
                .replace(' ', ' ')
                .replaceAll("[\\r\\n\\t]", " ")
                .replaceAll(" {2,}", " ")
                .trim();
    }

    private String escalas(short escalas) {
        return switch (escalas) {
            case 0 -> "voo direto";
            case 1 -> "1 escala";
            default -> escalas + " escalas";
        };
    }

    private String dinheiro(BigDecimal valor, String moeda) {
        if ("BRL".equals(moeda)) {
            return NumberFormat.getCurrencyInstance(BR).format(valor);
        }
        return moeda + " " + valor.toPlainString();
    }
}
