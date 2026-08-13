package com.flightmonitor.core.agent.control;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * O que o worker entendeu de um pedido em texto livre — etapa E3.1.
 *
 * <h2>Le em snake_case, escreve em camelCase</h2>
 *
 * O worker fala snake_case, como o resto do contrato entre os dois servicos. A
 * API do core fala camelCase, como todos os outros endpoints. Este record fica
 * no meio, e por isso usa {@code @JsonAlias} em vez de {@code @JsonNaming}: o
 * alias vale so na <b>leitura</b>, enquanto a estrategia de nomes valeria
 * tambem na escrita e faria este endpoint devolver snake_case — o unico da API
 * a fazer isso, obrigando o painel a lidar com duas convencoes.
 *
 * <h2>Campo nulo significa "nao foi dito"</h2>
 *
 * E nunca um padrao escolhido pelo interpretador. Um monitor montado a partir
 * de chute vigiaria a rota errada por meses, em silencio — parece que esta
 * funcionando, e nao esta.
 *
 * @param prefereVooDireto {@code Boolean} e nao {@code boolean}: o campo pode
 *        vir ausente ou nulo, e um primitivo faria a desserializacao inteira
 *        falhar por causa disso
 * @param provider quem interpretou: {@code regras} ou {@code claude}. Vai na
 *        resposta porque muda o quanto se pode confiar no resultado
 * @param confianca 0 a 1 — a fracao dos cinco campos essenciais que foi
 *        encontrada. <b>Nao mede se a interpretacao esta certa</b>, mede o
 *        quanto dela existe
 */
public record MonitorIntent(
        String origin,
        String destination,
        @JsonAlias("departure_from") LocalDate departureFrom,
        @JsonAlias("departure_to") LocalDate departureTo,
        @JsonAlias("min_stay_days") Short minStayDays,
        @JsonAlias("max_stay_days") Short maxStayDays,
        @JsonAlias("max_price") BigDecimal maxPrice,
        String currency,
        @JsonAlias("max_stops") Short maxStops,
        Short passengers,
        @JsonAlias("prefere_voo_direto") Boolean prefereVooDireto,
        @JsonAlias("avoided_airlines") List<String> avoidedAirlines,
        String label,
        String provider,
        BigDecimal confianca,
        List<String> avisos) {

    public MonitorIntent {
        currency = currency == null || currency.isBlank() ? "BRL" : currency;
        prefereVooDireto = prefereVooDireto != null && prefereVooDireto;
        avoidedAirlines = avoidedAirlines == null ? List.of() : List.copyOf(avoidedAirlines);
        avisos = avisos == null ? List.of() : List.copyOf(avisos);
        confianca = confianca == null ? BigDecimal.ZERO : confianca;
    }

    /** Tem o minimo para virar monitor: rota, janela e teto de preco. */
    public boolean completo() {
        return origin != null && destination != null
                && departureFrom != null && departureTo != null
                && maxPrice != null
                && !origin.equals(destination);
    }

    /**
     * O que impede este pedido de virar monitor.
     *
     * <p>Calculado aqui, e nao lido do worker: e o <b>core</b> que conhece as
     * regras do que um monitor precisa ter. O worker so relata o que leu.
     */
    public List<String> faltando() {
        List<String> ausentes = new ArrayList<>();

        if (origin == null) {
            ausentes.add("origem");
        }
        if (destination == null) {
            ausentes.add("destino");
        }
        if (departureFrom == null || departureTo == null) {
            ausentes.add("periodo da viagem");
        }
        if (maxPrice == null) {
            ausentes.add("preco maximo");
        }
        if (origin != null && origin.equals(destination)) {
            ausentes.add("origem e destino iguais");
        }
        return ausentes;
    }
}
