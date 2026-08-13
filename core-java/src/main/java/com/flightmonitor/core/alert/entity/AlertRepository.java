package com.flightmonitor.core.alert.entity;

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

public interface AlertRepository extends JpaRepository<Alert, Long> {

    List<Alert> findByStatus(AlertStatus status);

    /**
     * Encontra o alerta pelo identificador que o provedor devolveu.
     *
     * <p>E o unico elo que o webhook tem: a notificacao da Meta traz o
     * {@code wamid} e mais nada que nos identifique. Indice unico parcial em
     * {@code provider_message_id} sustenta esta consulta (migracao V4).
     */
    Optional<Alert> findByProviderMessageId(String providerMessageId);

    /**
     * Ultimo alerta deste monitor — base do anti-spam da etapa E1.10.
     * Desempata por id: varios destinatarios recebem na mesma transacao.
     */
    Optional<Alert> findFirstByMonitorIdOrderByCreatedAtDescIdDesc(Long monitorId);

    long countByMonitorIdAndCreatedAtAfter(Long monitorId, Instant desde);

    /**
     * Alertas recentes deste monitor que o usuario de fato pode ter visto.
     *
     * <p><b>FAILED fica de fora de proposito:</b> se a entrega falhou, o usuario
     * nunca recebeu a mensagem — deixar esse alerta bloquear um novo faria o
     * sistema silenciar justamente por causa do proprio defeito.
     *
     * <p>O {@code join fetch} traz a observacao junto porque o anti-spam precisa
     * comparar datas e preco; sem ele seria uma consulta por alerta.
     */
    @Query("""
            select a from Alert a
            join fetch a.priceObservation o
            where a.monitor.id = :monitorId
              and a.status <> com.flightmonitor.core.alert.entity.AlertStatus.FAILED
            order by a.createdAt desc, a.id desc
            """)
    List<Alert> recentesEntregaveis(@Param("monitorId") Long monitorId, Pageable limite);

    /**
     * Reivindica alertas pendentes para entrega, com trava de linha.
     *
     * <p>Mesmo padrao do scheduler de varredura ([D-038]): {@code SKIP LOCKED}
     * garante que duas instancias — ou o despacho imediato e a varredura de
     * recuperacao rodando juntos — nao entreguem <b>a mesma mensagem duas
     * vezes</b>. Aqui a duplicidade seria visivel: mensagem repetida no
     * WhatsApp do usuario.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select a from Alert a
            where a.status = com.flightmonitor.core.alert.entity.AlertStatus.PENDING
            order by a.createdAt asc, a.id asc
            """)
    List<Alert> reivindicarPendentes(Pageable lote);

    long countByStatus(AlertStatus status);

    /**
     * Carrega o alerta com tudo que o canal de entrega precisa ler.
     *
     * <p>O envio acontece <b>fora</b> de transacao, entao a entidade chega
     * desanexada ao canal. Sem este {@code join fetch}, qualquer acesso a
     * associacao LAZY — como o telefone do destinatario — estoura
     * {@code LazyInitializationException}. O canal trataria isso como falha
     * transitoria e o alerta ficaria retentando para sempre, sem nunca sair.
     */
    @Query("""
            select a from Alert a
            left join fetch a.recipient
            left join fetch a.monitor
            left join fetch a.priceObservation
            where a.id = :id
            """)
    Optional<Alert> findByIdParaEntrega(@Param("id") Long id);
}
