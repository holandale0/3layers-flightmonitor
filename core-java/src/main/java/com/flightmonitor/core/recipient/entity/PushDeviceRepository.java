package com.flightmonitor.core.recipient.entity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface PushDeviceRepository extends JpaRepository<PushDevice, Long> {

    Optional<PushDevice> findByToken(String token);

    List<PushDevice> findByRecipientIdOrderByCreatedAtAscIdAsc(Long recipientId);

    /** Para quem o canal PUSH envia. */
    List<PushDevice> findByRecipientIdAndActiveTrue(Long recipientId);

    /** Se o canal PUSH alcanca o destinatario — decide se o alerta e criado. */
    boolean existsByRecipientIdAndActiveTrue(Long recipientId);

    /**
     * Desliga o aparelho cujo token o Firebase recusou, guardando o motivo.
     *
     * <p>Uma instrucao so, e nao carregar-alterar-salvar: quem chama e o canal,
     * fora de transacao, com a entidade desanexada — e o despacho nao pode
     * tropecar numa versao velha do aparelho.
     */
    @Modifying
    @Transactional
    @Query("update PushDevice d set d.active = false, d.lastError = :motivo where d.id = :id")
    int desativar(@Param("id") Long id, @Param("motivo") String motivo);
}
