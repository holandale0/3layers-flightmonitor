package com.flightmonitor.core.search.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PriceObservationRepository extends JpaRepository<PriceObservation, Long> {

    /**
     * Historico exibido no painel de um monitor (etapa E1.14).
     *
     * <p>O desempate por id garante ordem estavel quando duas observacoes
     * compartilham o mesmo instante.
     */
    List<PriceObservation> findByMonitorIdOrderByObservedAtDescIdDesc(Long monitorId, Pageable pageable);

    /**
     * Ultimo preco visto para uma data especifica deste monitor. Base do
     * anti-spam da etapa E1.10: so alertar de novo se o preco caiu de verdade.
     *
     * <p>Ordena por instante e desempata por id — numa mesma varredura a camada 1
     * e a camada 2 gravam para a mesma data, e a mais recente precisa vencer.
     */
    Optional<PriceObservation> findFirstByMonitorIdAndDepartureDateAndReturnDateOrderByObservedAtDescIdDesc(
            Long monitorId, LocalDate departureDate, LocalDate returnDate);

    /**
     * Menor preco ja observado para a rota. Consulta por ROTA e nao por monitor,
     * de proposito: o historico pertence a rota (ver D-016).
     */
    @Query("""
            select min(o.price) from PriceObservation o
            where o.origin = :origin and o.destination = :destination
              and o.observedAt >= :desde
            """)
    Optional<BigDecimal> menorPrecoDaRota(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("desde") Instant desde);

    /**
     * Preco medio da rota — insumo da Fase 2 para dizer "15% abaixo da media".
     * Tambem por rota, nao por monitor.
     */
    @Query("""
            select avg(o.price) from PriceObservation o
            where o.origin = :origin and o.destination = :destination
              and o.observedAt >= :desde
            """)
    Optional<Double> precoMedioDaRota(
            @Param("origin") String origin,
            @Param("destination") String destination,
            @Param("desde") Instant desde);

    /**
     * A melhor oferta CONFIRMADA deste monitor — insumo da recomendacao (E3.3).
     *
     * <p>So confirmadas: recomendar com base em preco de cache seria opinar
     * sobre um numero que divergiu 61% a 81% da realidade nas medicoes da E1.6.
     *
     * <p>Menor preco primeiro, desempatando pela observacao mais recente — entre
     * duas ofertas do mesmo valor, a de agora e a que ainda pode existir.
     */
    @Query("""
            select o from PriceObservation o
            where o.monitor.id = :monitorId and o.confirmed = true
            order by o.price asc, o.observedAt desc, o.id desc
            """)
    List<PriceObservation> melhorConfirmadaDoMonitor(
            @Param("monitorId") Long monitorId, Pageable limite);

    long countByMonitorId(Long monitorId);
}
