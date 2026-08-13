package com.flightmonitor.core.stats.entity;

import com.flightmonitor.core.stats.control.FonteDeStats;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.flightmonitor.core.search.entity.PriceObservation;

/**
 * As agregacoes da E2.1, em SQL nativo.
 *
 * <h2>Por que nativo, e nao JPQL</h2>
 *
 * {@code percentile_cont} e {@code stddev_samp} nao existem em JPQL. Traduzir
 * mediana para JPQL exigiria ordenar e contar em duas consultas, ou trazer todos
 * os precos para a memoria — e mediana e justamente a estatistica que mais
 * importa aqui, porque preco de passagem tem cauda longa que desloca a media.
 *
 * <h2>Por que uma consulta so por resposta</h2>
 *
 * Todos os numeros saem da mesma varredura da tabela. Fazer sete consultas, uma
 * por estatistica, custaria sete leituras e — pior — abriria a chance de os
 * numeros virem de conjuntos diferentes, se uma varredura gravasse entre elas.
 */
public interface RouteStatsRepository extends Repository<PriceObservation, Long> {

    /**
     * Estatisticas da rota inteira no periodo.
     *
     * <p>Por <b>rota</b> e nao por monitor, seguindo a D-016: dois monitores da
     * mesma rota veem a mesma historia, e ela nao pode se partir em duas.
     *
     * @param somenteConfirmadas quando verdadeiro, ignora o preco de cache. Ver
     *        {@link FonteDeStats} para o motivo de isto nao ser um detalhe
     */
    @Query(value = """
            select count(*)                                                  as amostras,
                   min(price)                                                as minimo,
                   percentile_cont(0.25) within group (order by price)       as p25,
                   percentile_cont(0.5)  within group (order by price)       as mediana,
                   avg(price)                                                as media,
                   percentile_cont(0.75) within group (order by price)       as p75,
                   max(price)                                                as maximo,
                   stddev_samp(price)                                        as desvio,
                   min(observed_at)                                          as primeira,
                   max(observed_at)                                          as ultima
              from price_observation
             where origin = :origin
               and destination = :destination
               and observed_at >= :desde
               and (:somenteConfirmadas = false or confirmed = true)
            """, nativeQuery = true)
    ResumoDaRota resumir(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("desde") Instant desde,
            @Param("somenteConfirmadas") boolean somenteConfirmadas);

    /**
     * O mesmo, quebrado por mes de <b>partida</b>.
     *
     * <p>Mes de partida, e nao de observacao: a pergunta que isto responde e
     * "quando sai mais barato viajar", nao "quando eu estava olhando". Agrupar
     * pelo instante da observacao produziria um grafico sobre o nosso proprio
     * horario de varredura, que nao interessa a ninguem.
     *
     * <p>Meses com poucas amostras vem junto — filtrar aqui esconderia do
     * chamador que existe pouca informacao para aquele mes, que e em si um dado.
     */
    @Query(value = """
            select to_char(departure_date, 'YYYY-MM')                        as mes,
                   count(*)                                                  as amostras,
                   min(price)                                                as minimo,
                   percentile_cont(0.5) within group (order by price)        as mediana,
                   avg(price)                                                as media,
                   max(price)                                                as maximo
              from price_observation
             where origin = :origin
               and destination = :destination
               and observed_at >= :desde
               and (:somenteConfirmadas = false or confirmed = true)
             group by 1
             order by 1
            """, nativeQuery = true)
    List<ResumoMensal> resumirPorMes(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("desde") Instant desde,
            @Param("somenteConfirmadas") boolean somenteConfirmadas);

    /**
     * Um ponto por DIA de observacao — a serie temporal da E2.5.
     *
     * <p>Agrupa por dia de <b>observacao</b>, e nao de partida. Aqui a pergunta
     * e outra: nao "quando sai mais barato viajar", mas "o preco vem subindo ou
     * caindo ao longo do tempo".
     *
     * <p>Mediana e nao media, pela mesma razao da E2.1: um preco absurdo num
     * unico dia moveria a media e inventaria um degrau que a serie nao tem.
     *
     * <p><b>Confundidor conhecido:</b> a mistura de datas de partida observadas
     * pode mudar ao longo do tempo, e datas diferentes tem precos diferentes. O
     * desenho do sistema atenua isso — cada monitor tem janela de partida fixa e
     * o scheduler varre a mesma janela a cada ciclo, entao a mistura e
     * aproximadamente constante. Nao e perfeito, e esta registrado em RISCO-009.
     */
    @Query(value = """
            select (observed_at at time zone 'UTC')::date                     as dia,
                   count(*)                                                   as amostras,
                   percentile_cont(0.5) within group (order by price)         as mediana
              from price_observation
             where origin = :origin
               and destination = :destination
               and observed_at >= :desde
               and (:somenteConfirmadas = false or confirmed = true)
             group by 1
             order by 1
            """, nativeQuery = true)
    List<PontoDiario> serieDiaria(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("desde") Instant desde,
            @Param("somenteConfirmadas") boolean somenteConfirmadas);

    interface PontoDiario {
        java.time.LocalDate getDia();

        long getAmostras();

        Double getMediana();
    }

    /**
     * Menor duracao ja observada na rota, em minutos — insumo do score (E2.3).
     *
     * <p>A referencia tem que ser a propria rota. Limite absoluto em horas seria
     * inutil: dez horas e otimo para GRU-LIS e absurdo para GRU-CWB.
     *
     * <p>Devolve nulo quando nenhuma observacao trouxe duracao — a camada 1 nao
     * traz esse dado, entao uma rota que nunca chegou a camada 2 nao tem
     * referencia nenhuma, e o aspecto DURACAO fica sem nota.
     */
    @Query(value = """
            select min(duration_minutes)
              from price_observation
             where origin = :origin
               and destination = :destination
               and observed_at >= :desde
               and duration_minutes is not null
            """, nativeQuery = true)
    Integer duracaoMinimaDaRota(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("desde") Instant desde);

    /**
     * Projecao da consulta principal.
     *
     * <p>Os tipos seguem o que o PostgreSQL devolve, e nao o que seria
     * conveniente: {@code percentile_cont} sobre numeric devolve
     * {@code double precision}, enquanto {@code avg} e {@code stddev_samp}
     * devolvem {@code numeric}. Declarar tudo como BigDecimal quebraria na
     * conversao.
     */
    interface ResumoDaRota {
        long getAmostras();

        java.math.BigDecimal getMinimo();

        Double getP25();

        Double getMediana();

        java.math.BigDecimal getMedia();

        Double getP75();

        java.math.BigDecimal getMaximo();

        /** Nulo quando ha menos de duas amostras: desvio de um ponto nao existe. */
        java.math.BigDecimal getDesvio();

        Instant getPrimeira();

        Instant getUltima();
    }

    interface ResumoMensal {
        String getMes();

        long getAmostras();

        java.math.BigDecimal getMinimo();

        Double getMediana();

        java.math.BigDecimal getMedia();

        java.math.BigDecimal getMaximo();
    }
}
