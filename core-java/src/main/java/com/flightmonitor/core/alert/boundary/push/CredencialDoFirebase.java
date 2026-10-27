package com.flightmonitor.core.alert.boundary.push;

import java.io.IOException;

/**
 * O projeto do Firebase e o token OAuth2 que autoriza o envio.
 *
 * <p>Interface, e nao a conta de servico direto, para os testes nao
 * precisarem de chave privada de verdade: com uma credencial falsa, o
 * adaptador conversa com um FCM falso do mesmo jeito.
 */
public interface CredencialDoFirebase {

    /** Ha arquivo de conta de servico legivel? */
    boolean configurada();

    String projeto();

    /**
     * Token de acesso valido agora. O Google o emite por uma hora; quem
     * implementa renova sozinho.
     *
     * @throws IOException quando o Google nao responde ou recusa a chave
     */
    String tokenDeAcesso() throws IOException;
}
