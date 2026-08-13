package com.flightmonitor.core.recipient.control.dto;

import java.time.Instant;

import com.flightmonitor.core.recipient.entity.Recipient;

public record RecipientResponse(
        Long id,
        String name,
        String phoneE164,
        String email,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {

    public static RecipientResponse de(Recipient r) {
        return new RecipientResponse(
                r.getId(),
                r.getName(),
                r.getPhoneE164(),
                r.getEmail(),
                r.isActive(),
                r.getCreatedAt(),
                r.getUpdatedAt());
    }
}
