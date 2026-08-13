package com.flightmonitor.core.recipient.entity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientRepository extends JpaRepository<Recipient, Long> {

    Optional<Recipient> findByPhoneE164(String phoneE164);

    boolean existsByPhoneE164(String phoneE164);

    /** Contato alternativo desde a E4.6. Tem indice unico parcial no banco. */
    Optional<Recipient> findByEmail(String email);

    List<Recipient> findByActiveTrue();
}
