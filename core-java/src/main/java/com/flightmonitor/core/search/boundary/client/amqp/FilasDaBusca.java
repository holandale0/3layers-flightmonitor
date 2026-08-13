package com.flightmonitor.core.search.boundary.client.amqp;

/**
 * Os nomes que os dois servicos precisam combinar — etapa E4.1.
 *
 * <p>Ficam numa classe so, e nao espalhados por anotacao, porque o outro lado
 * do contrato esta em <b>outra linguagem</b>. Um nome digitado errado aqui nao
 * da erro de compilacao em lugar nenhum: a mensagem simplesmente vai para uma
 * fila que ninguem escuta, e a varredura falha por timeout — sintoma que nao
 * aponta para a causa.
 *
 * <p>O espelho destes nomes esta em {@code worker-python/app/amqp/filas.py},
 * e o {@code E2EServicosTest} com transporte AMQP e quem prova que os dois
 * concordam.
 *
 * <h2>Por que duas filas de pedido, e nao uma</h2>
 *
 * O roteiro fala de um par logico — {@code flight.search.requested} e
 * {@code flight.search.completed}. Na pratica o pedido se divide em duas filas
 * porque as duas camadas de coleta tem <b>tempos deliberadamente diferentes</b>
 * (E1.7): a varredura consulta um cache e responde em menos de um segundo; a
 * confirmacao vai ao Google ao vivo e pode levar dezenas. Uma fila unica
 * obrigaria um timeout unico, que seria curto demais para a camada 2 ou longo
 * demais para detectar a camada 1 travada.
 *
 * <p>A resposta usa a fila temporaria do proprio pedido ({@code reply_to}), que
 * e o padrao de request/reply do AMQP — dai nao existir uma fila nomeada de
 * {@code completed}.
 */
public final class FilasDaBusca {

    private FilasDaBusca() {
    }

    /** Troca direta: o roteamento e por nome exato, sem curinga. */
    public static final String EXCHANGE = "flight.search";

    public static final String ROTA_CALENDARIO = "flight.search.requested.calendar";
    public static final String ROTA_CONFIRMACAO = "flight.search.requested.confirm";

    public static final String FILA_CALENDARIO = "flight.search.requested.calendar";
    public static final String FILA_CONFIRMACAO = "flight.search.requested.confirm";

    /**
     * Para onde vai o que o worker nao conseguiu processar.
     *
     * <p>Sem dead-letter, uma mensagem que o worker rejeita volta para a fila e
     * e reentregue para sempre — o <b>laco de veneno</b>, que consome CPU dos
     * dois lados e esconde o defeito atras de um sintoma de lentidao. Com ela, a
     * mensagem sai do caminho e fica visivel no painel.
     */
    public static final String EXCHANGE_MORTA = "flight.search.dlx";
    public static final String FILA_MORTA = "flight.search.dead";

    /**
     * Cabecalho que diz "a FONTE falhou", e nao "nao havia oferta".
     *
     * <p>E o equivalente AMQP do HTTP 502 do transporte REST, e existe porque a
     * primeira versao nao tinha: o worker respondia varredura vazia quando a
     * fonte caia, e o core registrava a busca como <b>bem-sucedida sem
     * ofertas</b>. O monitor voltaria a fila no intervalo normal em vez de
     * retentar, e o painel mostraria "nenhuma oferta" para uma fonte fora do ar.
     *
     * <p>A distincao entre "a fonte morreu" e "a janela esta vazia" e a mesma
     * que {@code returned} e {@code kept} preservam dentro da resposta — e ela
     * nao podia ser perdida so por trocar de transporte. Quem pegou foi o E2E
     * entre servicos, rodando os mesmos testes nos dois meios.
     */
    public static final String CABECALHO_FALHA = "x-fonte-falhou";
}
