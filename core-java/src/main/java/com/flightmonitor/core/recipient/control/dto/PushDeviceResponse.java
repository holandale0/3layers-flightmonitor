package com.flightmonitor.core.recipient.control.dto;

import java.time.Instant;

import com.flightmonitor.core.recipient.entity.PushDevice;

/**
 * Um aparelho, como a tela o mostra.
 *
 * <p><b>Sem o token.</b> Ele so serve para enviar push, e quem envia e o core.
 * Devolve-lo na listagem o deixaria no cache do navegador e no historico de
 * qualquer ferramenta que capture trafego, sem ninguem precisar dele la.
 */
public record PushDeviceResponse(
        Long id,
        Long recipientId,
        String name,
        boolean active,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public static PushDeviceResponse de(PushDevice d) {
        return new PushDeviceResponse(
                d.getId(),
                d.getRecipient().getId(),
                d.getName(),
                d.isActive(),
                d.getLastError(),
                d.getCreatedAt(),
                d.getUpdatedAt());
    }
}
