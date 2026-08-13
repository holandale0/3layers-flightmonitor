package com.flightmonitor.core.alert.boundary.email;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ajustes do canal de e-mail — etapa E4.6.
 *
 * <p>As credenciais do servidor SMTP (host, porta, usuario, senha) ficam em
 * {@code spring.mail.*}, que e o que o {@code JavaMailSender} do Spring le. Aqui
 * moram apenas as decisoes que sao <b>nossas</b>, e nao do protocolo.
 *
 * @param remetente endereco que aparece no campo De. Precisa ser <b>a mesma
 *        conta autenticada</b> no SMTP do Gmail, ou um alias ja verificado em
 *        "Enviar e-mail como" — qualquer outro endereco o Gmail reescreve ou
 *        recusa. Ver D-098 e docs/GUIA-EMAIL.md
 * @param nomeExibido nome amigavel ao lado do endereco. Sem ele, a caixa de
 *        entrada mostra o endereco cru da conta de sistema, que nao diz nada a
 *        quem recebe
 */
@ConfigurationProperties(prefix = "flightmonitor.email")
public record EmailProperties(String remetente, String nomeExibido) {

    public EmailProperties {
        nomeExibido = vazio(nomeExibido) ? "Monitor de Passagens" : nomeExibido;
    }

    /**
     * Ha remetente configurado?
     *
     * <p>Mesma disciplina do {@code WhatsAppProperties.configurado()}: sem isto,
     * a falta de configuracao viraria uma excecao do JavaMail no meio do
     * despacho, em vez de uma mensagem que diz o que fazer.
     */
    public boolean configurado() {
        return !vazio(remetente);
    }

    private static boolean vazio(String v) {
        return v == null || v.isBlank();
    }
}
