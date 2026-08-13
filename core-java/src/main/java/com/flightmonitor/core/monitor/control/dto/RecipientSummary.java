package com.flightmonitor.core.monitor.control.dto;

import com.flightmonitor.core.recipient.entity.Recipient;

/** Visao enxuta do destinatario dentro da resposta de um monitor. */
public record RecipientSummary(Long id, String name, String phoneE164, boolean active) {

    public static RecipientSummary de(Recipient r) {
        return new RecipientSummary(r.getId(), r.getName(), r.getPhoneE164(), r.isActive());
    }
}
