package com.flightmonitor.core.search.control.canario;

/**
 * Porta do canario — etapa E4.5.
 *
 * <p>Existe pelo mesmo motivo da {@code SearchClient}: o controle precisa
 * <b>perguntar como estao as fontes</b>, e nao saber que a resposta vem por HTTP
 * de um serviço Python.
 */
public interface CanarioPort {

    /** Nunca lanca: worker fora do ar vira {@code indisponivel}. */
    ResultadoDoCanario consultar();
}
