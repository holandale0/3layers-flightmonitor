package com.flightmonitor.core.alert.boundary;

import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.recipient.entity.Recipient;
import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.control.NotificationChannel;
import com.flightmonitor.core.alert.control.DeliveryResult;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Canal de desenvolvimento: registra a mensagem no log em vez de enviar.
 *
 * <p>Tem tres usos, e nenhum deles e provisorio:
 *
 * <ol>
 *   <li>permite fechar o fluxo completo antes de existir credencial da Meta;</li>
 *   <li>e o dublê dos testes E2E das etapas E1.15 e E1.16 — por isso o
 *       {@code AlertChannel.LOG} esta no schema desde a E1.1;</li>
 *   <li>vira valvula de escape se o WhatsApp cair: trocar o canal por
 *       configuracao mantem o motor rodando e os alertas visiveis no log,
 *       em vez de acumular pendencia silenciosa.</li>
 * </ol>
 */
@Component
public class LogNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(LogNotificationChannel.class);

    @Override
    public AlertChannel canal() {
        return AlertChannel.LOG;
    }

    /**
     * Quem recebeu, para quem le o console.
     *
     * <p>Le telefone <b>ou</b> e-mail: desde a E4.6 o telefone e opcional, e a
     * primeira versao disto imprimia {@code para: null} para quem so tem
     * e-mail — bem na tela que alguem ve ao rodar o projeto pela primeira vez.
     */
    private static String descrever(Recipient destinatario) {
        if (destinatario == null) {
            return "(sem destinatario)";
        }
        String contato = destinatario.getPhoneE164() != null
                ? destinatario.getPhoneE164()
                : destinatario.getEmail();

        return contato != null
                ? "%s <%s>".formatted(destinatario.getName(), contato)
                : destinatario.getName();
    }

    @Override
    public DeliveryResult enviar(Alert alerta) {
        String destino = descrever(alerta.getRecipient());

        // A mensagem tem acento e emoji de proposito — e o texto que iria para o
        // WhatsApp. Impressa via println e nao pelo logger: o console do Windows
        // corromperia os acentos no logger (BUG-001), e aqui o ponto e justamente
        // conferir como a mensagem fica.
        log.info("ALERTA (canal LOG) para {} — {} caracteres",
                destino, alerta.getMessage().length());
        System.out.println("""

                ┌─────────── ALERTA (canal LOG) ───────────
                │ para: %s
                ├──────────────────────────────────────────
                %s
                └──────────────────────────────────────────
                """.formatted(destino, alerta.getMessage()));

        return DeliveryResult.entregue("log:" + UUID.randomUUID());
    }
}
