package com.flightmonitor.core.recipient.control.dto;

import java.time.Instant;
import java.util.List;

import com.flightmonitor.core.recipient.entity.Recipient;

public record RecipientResponse(
        Long id,
        String name,
        String phoneE164,
        String email,
        boolean active,
        Instant createdAt,
        Instant updatedAt,
        /** Aparelhos com o aplicativo (D-113): o contato do canal PUSH. */
        List<PushDeviceResponse> pushDevices) {

    public static RecipientResponse de(Recipient r, List<PushDeviceResponse> aparelhos) {
        return new RecipientResponse(
                r.getId(),
                r.getName(),
                r.getPhoneE164(),
                r.getEmail(),
                r.isActive(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                aparelhos);
    }
}
