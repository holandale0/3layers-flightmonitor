package com.flightmonitor.core.recipient.control.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O aplicativo se apresentando: "sou este aparelho, e quero receber os alertas
 * deste destinatario".
 *
 * <p>Quem preenche e o aplicativo, nao uma pessoa: o token vem do Firebase e o
 * nome do proprio Android. Mesmo assim validado, porque a rota e publica atras
 * da senha do painel — e senha nao e garantia de que o corpo faz sentido.
 */
public record PushDeviceRequest(

        @NotBlank(message = "obrigatorio")
        @Size(min = 20, max = 4096, message = "nao parece um token do Firebase")
        String token,

        @NotBlank(message = "obrigatorio")
        @Size(max = 120, message = "no maximo 120 caracteres")
        String name) {

    public PushDeviceRequest {
        if (token != null) {
            token = token.trim();
        }
        if (name != null) {
            name = name.trim();
        }
    }
}
