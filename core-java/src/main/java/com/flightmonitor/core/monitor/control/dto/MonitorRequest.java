package com.flightmonitor.core.monitor.control.dto;

import com.flightmonitor.core.monitor.entity.Preferencias;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Criacao ou atualizacao de um monitor.
 *
 * <p>Campos opcionais com default (moeda, passageiros, intervalo) sao
 * preenchidos no construtor compacto, espelhando os DEFAULT do banco.
 */
@JanelasCoerentes
public record MonitorRequest(

        @Size(max = 120, message = "no maximo 120 caracteres")
        String label,

        @NotBlank(message = "obrigatorio")
        @Pattern(regexp = "^[A-Z]{3}$", message = "deve ser um codigo IATA: 3 letras maiusculas")
        String origin,

        @NotBlank(message = "obrigatorio")
        @Pattern(regexp = "^[A-Z]{3}$", message = "deve ser um codigo IATA: 3 letras maiusculas")
        String destination,

        @NotNull(message = "obrigatorio")
        @FutureOrPresent(message = "nao faz sentido monitorar uma data que ja passou")
        LocalDate departureWindowStart,

        @NotNull(message = "obrigatorio")
        LocalDate departureWindowEnd,

        LocalDate returnWindowStart,

        LocalDate returnWindowEnd,

        @Min(value = 1, message = "no minimo 1 dia")
        Short minStayDays,

        @Min(value = 1, message = "no minimo 1 dia")
        Short maxStayDays,

        @NotNull(message = "obrigatorio")
        @DecimalMin(value = "0.01", message = "deve ser maior que zero")
        BigDecimal maxPrice,

        @Pattern(regexp = "^[A-Z]{3}$", message = "deve ser um codigo ISO de 3 letras maiusculas")
        String currency,

        @Min(value = 0, message = "nao pode ser negativo")
        @Max(value = 5, message = "no maximo 5 escalas")
        Short maxStops,

        @Min(value = 1, message = "no minimo 1 passageiro")
        @Max(value = 9, message = "no maximo 9 passageiros")
        Short passengers,

        Boolean active,

        @Min(
                value = 10,
                message = "intervalo minimo de 10 minutos: as fontes sao gratuitas e a camada 1 "
                        + "devolve dado cacheado, entao varrer mais rapido gasta cota para reler o mesmo preco")
        Integer searchIntervalMinutes,

        Set<Long> recipientIds,

        // ------------------------------------------------ preferencias (E2.6)

        /**
         * Penaliza escalas com mais forca na nota, sem excluir.
         *
         * <p>Diferente de {@code maxStops}: aquele e limite rigido, enviado a
         * fonte, e o voo com escala nem chega a ser buscado.
         */
        Boolean prefereVooDireto,

        /**
         * Companhias a evitar. Aceita codigo IATA ou nome por extenso.
         *
         * <p>O codigo de duas letras casa tambem com o nome — "IB" exclui
         * "Iberia" —, porque a camada 1 devolve codigo e a camada 2 devolve
         * nome. Ver {@code Preferencias}.
         */
        Set<@Size(max = 60, message = "nome de companhia longo demais") String> avoidedAirlines,

        /** Pesos do Flight Score. Ausente usa o global; zero e escolha valida. */
        @Min(value = 0, message = "peso nao pode ser negativo")
        @Max(value = 100, message = "peso no maximo 100")
        Short pesoPreco,

        @Min(value = 0, message = "peso nao pode ser negativo")
        @Max(value = 100, message = "peso no maximo 100")
        Short pesoEscalas,

        @Min(value = 0, message = "peso nao pode ser negativo")
        @Max(value = 100, message = "peso no maximo 100")
        Short pesoDuracao,

        @Min(value = 0, message = "peso nao pode ser negativo")
        @Max(value = 100, message = "peso no maximo 100")
        Short pesoHorario) {

    public MonitorRequest {
        if (currency == null || currency.isBlank()) {
            currency = "BRL";
        }
        if (passengers == null) {
            passengers = 1;
        }
        if (active == null) {
            active = Boolean.TRUE;
        }
        if (searchIntervalMinutes == null) {
            searchIntervalMinutes = 360;
        }
        if (recipientIds == null) {
            recipientIds = Set.of();
        }
        if (prefereVooDireto == null) {
            prefereVooDireto = Boolean.FALSE;
        }
        if (avoidedAirlines == null) {
            avoidedAirlines = Set.of();
        }
        if (origin != null) {
            origin = origin.trim().toUpperCase();
        }
        if (destination != null) {
            destination = destination.trim().toUpperCase();
        }
    }
}
