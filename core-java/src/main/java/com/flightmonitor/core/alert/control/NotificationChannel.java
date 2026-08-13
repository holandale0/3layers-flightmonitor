package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.alert.entity.Alert;

/**
 * Canal de entrega de alertas.
 *
 * <p>Mesmo padrao da camada 2 de coleta ([D-026]): Strategy, com a escolha
 * acontecendo na composicao e nao no codigo que consome. O motor de
 * monitoramento nao sabe que WhatsApp existe — regra 3 da secao 3 de
 * docs/PLANO-DE-ACAO.md.
 *
 * <p>Implementacoes <b>nao devem lancar excecao</b>: devolvem
 * {@link DeliveryResult} classificando a falha como transitoria ou permanente.
 * Deixar excecao escapar tiraria do despachante a chance de decidir se vale
 * retentar.
 */
public interface NotificationChannel {

    /** Qual canal esta implementacao atende. */
    AlertChannel canal();

    /**
     * A entrega deste canal so se confirma depois, por aviso do provedor?
     *
     * <p>Esta pergunta decide se um envio bem-sucedido vira {@code SENT} ou
     * {@code ACCEPTED}. E a diferenca entre "chegou" e "a Meta disse que
     * recebeu" — o que o BUG-007 mostrou serem coisas bem diferentes.
     *
     * <p>Padrao {@code false}: um canal novo e tratado como confiavel apenas se
     * declarar o contrario. Errar para o lado de "confio" e mais seguro do que
     * deixar alertas presos em ACCEPTED esperando um webhook que nunca vem.
     */
    default boolean confirmacaoAssincrona() {
        return false;
    }

    DeliveryResult enviar(Alert alerta);
}
