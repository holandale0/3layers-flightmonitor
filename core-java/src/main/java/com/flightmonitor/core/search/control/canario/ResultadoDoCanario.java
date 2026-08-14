package com.flightmonitor.core.search.control.canario;

import java.util.List;

/**
 * O que o canario viu nas fontes externas — etapa E4.5.
 *
 * @param indisponivel preenchido quando nao foi possivel <b>consultar</b> o
 *        canario. Diferente de "as fontes estao com problema": aqui nem se sabe.
 *        Confundir os dois seria o mesmo erro que o sistema evita em toda parte
 */
public record ResultadoDoCanario(boolean saudavel, List<Camada> camadas, String indisponivel) {

    public record Camada(
            String numero,
            String provider,
            boolean formatoOk,
            String erro,
            List<String> achados) {
    }

    public static ResultadoDoCanario indisponivel(String motivo) {
        return new ResultadoDoCanario(false, List.of(), motivo);
    }

    public boolean consultou() {
        return indisponivel == null;
    }

    /** As camadas com problema, para o log e o painel citarem o que quebrou. */
    public List<Camada> comProblema() {
        return camadas.stream().filter(c -> !c.formatoOk()).toList();
    }
}
