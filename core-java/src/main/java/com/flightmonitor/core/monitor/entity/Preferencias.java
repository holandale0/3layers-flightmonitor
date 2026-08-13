package com.flightmonitor.core.monitor.entity;

import java.util.Locale;
import java.util.Set;

/**
 * As preferencias de um monitor, com as regras que dependem delas — etapa E2.6.
 *
 * <p>Fica fora da entidade porque sao <b>regras</b>, e nao estado: casar o nome
 * de uma companhia com o que a fonte devolveu e uma decisao que merece teste
 * proprio, sem precisar de banco.
 */
public final class Preferencias {

    private Preferencias() {
    }

    /**
     * A companhia observada esta na lista de evitadas?
     *
     * <h2>O problema real: a mesma companhia tem dois nomes</h2>
     *
     * A camada 1 devolve o codigo IATA — {@code "IB"}. A camada 2 devolve o nome
     * por extenso — {@code "Iberia"}. Sao a mesma empresa, e uma comparacao
     * literal deixaria metade das ofertas passar.
     *
     * <p>Por isso a comparacao e por <b>prefixo, nos dois sentidos</b>: quem
     * escreve "IB" tambem exclui "Iberia", e quem escreve "Iberia" tambem exclui
     * "IB". Sem a simetria, a preferencia funcionaria ou nao dependendo de qual
     * camada encontrou a oferta — o pior tipo de comportamento, porque parece
     * aleatorio.
     *
     * <h2>Duas regras de prefixo, e nao uma</h2>
     *
     * <ul>
     *   <li><b>prefixo de palavra inteira</b> — "AZUL" casa com
     *       "AZUL LINHAS AEREAS", porque o que vem depois e um espaco. E o caso
     *       mais comum: a pessoa digita o nome curto e a fonte devolve a razao
     *       social;</li>
     *   <li><b>prefixo curto</b>, ate tres letras — "IB" casa com "IBERIA",
     *       sem espaco no meio. E o unico jeito de um codigo IATA alcancar o
     *       nome por extenso.</li>
     * </ul>
     *
     * A segunda regra fica limitada a tres letras de proposito: sem esse teto,
     * "AZUL" casaria com "AZULAO AIRWAYS", e qualquer prefixo viraria exclusao.
     *
     * <p><b>Limite conhecido:</b> um codigo que <em>nao</em> prefixa o nome nao
     * e alcancado — "TP" nao casa com "Tap Air Portugal", porque textualmente
     * nao ha relacao. Resolver isso exigiria uma tabela de codigos IATA, que
     * este projeto nao tem motivo para carregar. Na duvida, use o nome.
     */
    public static boolean companhiaEvitada(String observada, Set<String> evitadas) {
        if (observada == null || evitadas == null || evitadas.isEmpty()) {
            return false;
        }

        String obs = normalizar(observada);
        if (obs.isEmpty()) {
            return false;
        }

        for (String evitada : evitadas) {
            String alvo = normalizar(evitada);
            if (alvo.isEmpty()) {
                continue;
            }
            if (casa(obs, alvo)) {
                return true;
            }
            // A camada 2 pode devolver varias companhias juntas, separadas por
            // virgula: "Tap Air Portugal, Iberia". Basta uma casar.
            if (algumTrechoCasa(obs, alvo)) {
                return true;
            }
        }
        return false;
    }

    private static boolean casa(String a, String b) {
        return a.equals(b)
                || prefixoDePalavra(a, b) || prefixoDePalavra(b, a)
                || prefixoDeCodigo(a, b) || prefixoDeCodigo(b, a);
    }

    /** {@code curto} e a primeira palavra inteira de {@code longo}? */
    private static boolean prefixoDePalavra(String curto, String longo) {
        return longo.startsWith(curto + " ");
    }

    /**
     * {@code curto} e um codigo IATA que prefixa {@code longo}?
     *
     * <p>Ate tres letras. Sem o teto, qualquer prefixo viraria exclusao.
     */
    private static boolean prefixoDeCodigo(String curto, String longo) {
        return curto.length() <= 3 && longo.startsWith(curto);
    }

    private static boolean algumTrechoCasa(String observada, String alvo) {
        if (!observada.contains(",")) {
            return false;
        }
        for (String trecho : observada.split(",")) {
            if (casa(trecho.trim(), alvo)) {
                return true;
            }
        }
        return false;
    }

    /** Maiuscula e sem espaco nas pontas — o mesmo formato gravado no banco. */
    public static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toUpperCase(Locale.ROOT);
    }
}
