package com.flightmonitor.core.monitor.entity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;

public interface MonitorRepository extends JpaRepository<Monitor, Long> {

    List<Monitor> findByActiveTrue();

    /**
     * Monitores ativos cuja proxima varredura ja venceu. E a consulta que o
     * scheduler roda a cada ciclo (etapa E1.9) — atendida pelo indice parcial
     * {@code idx_monitor_proxima_busca}.
     */
    List<Monitor> findByActiveTrueAndNextSearchAtLessThanEqualOrderByNextSearchAtAsc(Instant momento);

    /** Carrega o monitor junto com os destinatarios, evitando N+1 no envio de alertas. */
    @Query("select m from Monitor m left join fetch m.recipients where m.id = :id")
    Optional<Monitor> findByIdComDestinatarios(@Param("id") Long id);

    /**
     * Carrega o monitor com as companhias evitadas ja resolvidas.
     *
     * <p>A varredura roda <b>fora de transacao</b> — a chamada HTTP pode levar
     * dezenas de segundos, e prender conexao do pool durante isso a esgotaria
     * (D-034). Entao o monitor chega desanexado ao filtro de preferencias, e uma
     * colecao LAZY estoura ali.
     *
     * <p>E a mesma armadilha do BUG-006, agora do lado da busca: naquele caso
     * era o telefone do destinatario no canal de entrega. O E2E do motor pegou
     * os dois — e nos dois a correcao foi trazer o dado enquanto ainda ha
     * sessao, em vez de esticar a transacao.
     */
    @Query("select m from Monitor m left join fetch m.avoidedAirlines where m.id = :id")
    Optional<Monitor> findByIdComPreferencias(@Param("id") Long id);

    List<Monitor> findByOriginAndDestination(String origin, String destination);

    /**
     * Reivindica monitores vencidos para varredura, com trava de linha.
     *
     * <p>{@code PESSIMISTIC_WRITE} + {@code lock.timeout = -2} (o
     * {@code SKIP LOCKED} do Hibernate) faz cada instancia pegar um lote
     * <b>diferente</b> em vez de esperar pela outra. Hoje rodamos uma instancia
     * so, mas isso e o que permitira escalar horizontalmente na Fase 4 sem que
     * duas instancias varram o mesmo monitor e gerem alertas duplicados.
     *
     * <p>Sem o {@code SKIP LOCKED}, a segunda instancia ficaria bloqueada
     * esperando a primeira soltar a trava — trocando duplicidade por lentidao.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select m from Monitor m
            where m.active = true and m.nextSearchAt <= :agora
            order by m.nextSearchAt asc
            """)
    List<Monitor> reivindicarVencidos(@Param("agora") Instant agora, Pageable lote);
}
