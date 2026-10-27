package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.recipient.entity.PushDeviceRepository;
import com.flightmonitor.core.recipient.entity.Recipient;

import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Por quais dos canais ativos cada destinatario pode ser alcancado — D-113.
 *
 * <p>Com um canal so, a pergunta nao existia: o alerta ia por ele, e se o
 * destinatario nao tinha o contato, falhava com o motivo gravado. Com varios
 * em paralelo, criar alerta em todos produziria uma falha certa para cada
 * contato ausente — quem so tem e-mail ganharia, a cada oferta, um alerta de
 * WhatsApp FAILED ao lado do e-mail que chegou. Ruido que esconde a falha que
 * importa.
 */
@Component
public class AlcanceDosCanais {

    private final PushDeviceRepository aparelhos;

    public AlcanceDosCanais(PushDeviceRepository aparelhos) {
        this.aparelhos = aparelhos;
    }

    /**
     * Os canais ativos que alcancam o destinatario, na ordem configurada.
     *
     * <p><b>Se nenhum alcanca, devolve todos.</b> Uma lista vazia faria o alerta
     * simplesmente nao existir, e a pessoa perderia a passagem sem rastro em
     * lugar nenhum. Criados mesmo assim, os alertas falham com o motivo em
     * {@code error_message} — que e onde alguem procura quando nota que nao
     * recebeu nada.
     */
    public List<AlertChannel> canaisPara(Recipient destinatario, List<AlertChannel> ativos) {
        List<AlertChannel> alcancam = ativos.stream()
                .filter(canal -> alcanca(canal, destinatario))
                .toList();
        return alcancam.isEmpty() ? ativos : alcancam;
    }

    public boolean alcanca(AlertChannel canal, Recipient destinatario) {
        return switch (canal) {
            case WHATSAPP -> preenchido(destinatario.getPhoneE164());
            case EMAIL -> preenchido(destinatario.getEmail());
            case PUSH -> destinatario.getId() != null
                    && aparelhos.existsByRecipientIdAndActiveTrue(destinatario.getId());
            case LOG -> true;
        };
    }

    private static boolean preenchido(String contato) {
        return contato != null && !contato.isBlank();
    }
}
