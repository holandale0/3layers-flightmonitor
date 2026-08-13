package com.flightmonitor.core.search.control.client;

import com.flightmonitor.core.search.control.client.dto.CalendarSearchCommand;
import com.flightmonitor.core.search.control.client.dto.CalendarSearchResult;
import com.flightmonitor.core.search.control.client.dto.ConfirmCommand;
import com.flightmonitor.core.search.control.client.dto.ConfirmResult;

/**
 * Porta de acesso ao worker de busca.
 *
 * <p>Existe como interface por dois motivos concretos:
 *
 * <ol>
 *   <li>o teste E2E do motor (etapa E1.15) substitui a implementacao sem
 *       precisar de servidor HTTP;</li>
 *   <li>a migracao para RabbitMQ (etapa E4.1) troca o adaptador REST por um de
 *       mensageria, sem tocar em nada que consome esta interface.</li>
 * </ol>
 *
 * <p><b>Contrato de erro:</b> a varredura lanca {@link WorkerUnavailableException}
 * quando o worker nao responde — sem preco, nao ha o que fazer. Ja a confirmacao
 * <b>nunca</b> lanca por indisponibilidade: devolve resultado degradado, porque
 * derrubar a varredura por causa de uma camada opcional seria pior do que
 * seguir sem ela.
 */
public interface SearchClient {

    /** Camada 1: varre uma janela de datas. Lanca se o worker nao responder. */
    CalendarSearchResult scanCalendar(CalendarSearchCommand cmd);

    /** Camada 2: confirma um candidato. Degrada em vez de lancar. */
    ConfirmResult confirm(ConfirmCommand cmd);
}
