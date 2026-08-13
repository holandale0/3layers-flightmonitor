package com.flightmonitor.core.search.control;

import com.flightmonitor.core.alert.control.AlertDecision;

/**
 * O que aconteceu ao processar um monitor: o que foi encontrado e o que foi
 * decidido a respeito.
 *
 * <p>Os dois juntos, de proposito. Ver a varredura sem a decisao levaria a
 * perguntar "encontrou oportunidade, entao por que nao recebi nada?" — que e
 * exatamente a duvida que o {@link AlertDecision#motivo()} responde.
 */
public record MonitorRunResult(SearchOutcome busca, AlertDecision alerta) {
}
