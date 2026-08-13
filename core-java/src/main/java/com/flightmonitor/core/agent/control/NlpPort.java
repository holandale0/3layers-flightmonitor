package com.flightmonitor.core.agent.control;

import java.time.LocalDate;

/**
 * Porta de interpretacao de linguagem natural — etapa E3.1.
 *
 * <p>Existe pelo mesmo motivo da {@code SearchClient}: o controle precisa
 * <b>pedir uma interpretacao</b>, e nao saber que ela chega por HTTP a um
 * servico Python. Trocar o meio — ou o serviço — nao deve tocar no
 * {@code AgentService}.
 *
 * <p>Na reorganizacao BCE isto deixou de ser opcional: com a classe concreta na
 * borda, o controle dependia da borda, e o teste de arquitetura reprovou.
 *
 * <p><b>Contrato de erro:</b> lanca quando nao consegue interpretar. Nao ha
 * resposta parcial util — devolver uma intencao vazia faria o usuario achar que
 * o pedido dele nao continha nada.
 */
public interface NlpPort {

    /**
     * @param hoje enviado pelo chamador, e nao lido do relogio de quem
     *        interpreta: "em marco" depende de quando se pergunta, e os dois
     *        processos podem estar em fusos diferentes (D-079)
     */
    MonitorIntent interpretar(String texto, String origemPadrao, LocalDate hoje);
}
