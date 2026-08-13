package com.flightmonitor.core.search.entity;

/**
 * Fonte de preco. Os nomes precisam bater com o CHECK do banco.
 *
 * <p>Estrategia em duas camadas (ver docs/PLANO-DE-ACAO.md secao 4):
 * a camada 1 varre datas de forma ampla e barata; a camada 2 confirma o
 * candidato com dados reais de voo antes de disparar um alerta.
 */
public enum PriceSource {

    /** Camada 1 — varredura ampla, dados cacheados, sem detalhe de voo. */
    TRAVELPAYOUTS,

    /** Camada 2 — confirmacao pontual, com companhia, escalas e horarios. */
    FAST_FLIGHTS
}
