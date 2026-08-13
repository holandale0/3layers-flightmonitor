package com.flightmonitor.core.alert.entity;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsAppConfigRepository extends JpaRepository<WhatsAppConfig, Long> {

    /**
     * A unica linha, se alguem ja configurou pela tela.
     *
     * <p>{@code Optional.empty()} nao e erro: significa que vale o {@code .env}.
     */
    default Optional<WhatsAppConfig> carregar() {
        return findById(WhatsAppConfig.ID_UNICO);
    }
}
