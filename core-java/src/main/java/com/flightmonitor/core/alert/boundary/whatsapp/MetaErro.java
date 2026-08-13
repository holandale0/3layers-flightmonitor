package com.flightmonitor.core.alert.boundary.whatsapp;

/**
 * Classificacao dos erros da Graph API.
 *
 * <p>Decidir entre <b>transitorio</b> e <b>permanente</b> e o que separa
 * retentativa util de teimosia: retentar um numero nao verificado tres vezes so
 * adia o diagnostico, e desistir de um HTTP 500 na primeira perderia um alerta
 * por soluco de rede. Ver D-045.
 *
 * <p>Os codigos vem da documentacao de erros do WhatsApp Cloud API.
 */
public final class MetaErro {

    private MetaErro() {
    }

    /** Numero do destinatario nao esta na lista de permitidos do numero de teste. */
    public static final int DESTINATARIO_NAO_PERMITIDO = 131030;

    /** Token invalido ou expirado. */
    public static final int TOKEN_INVALIDO = 190;

    /** Template nao existe, ou o idioma nao confere. */
    public static final int TEMPLATE_INEXISTENTE = 132001;

    /** Parametro do template em formato invalido. */
    public static final int PARAMETRO_INVALIDO = 132000;

    // ------------------------------------------------------------------------
    // Codigos que so aparecem no WEBHOOK. A chamada de envio devolve 200 com
    // wamid, e a recusa acontece depois. Eram invisiveis antes da E1.17.
    // ------------------------------------------------------------------------

    /**
     * A conta esta proibida de enviar para usuarios deste pais.
     *
     * <p>O BUG-007 em pessoa. Numero remetente americano, destinatario
     * brasileiro: a Meta aceita, cobra nada, e nao entrega. Custou horas de
     * investigacao porque a chamada de envio dizia sucesso.
     */
    public static final int RESTRICAO_DE_PAIS = 130497;

    /** O destinatario nao pode receber: numero sem WhatsApp, ou que bloqueou. */
    public static final int DESTINATARIO_INALCANCAVEL = 131026;

    /** A Meta optou por nao entregar, tipicamente por qualidade da conta. */
    public static final int ENTREGA_RECUSADA_POR_QUALIDADE = 131049;

    /** Tentativa de texto livre fora da janela de 24h. */
    public static final int FORA_DA_JANELA = 131047;

    /** Limite de requisicoes atingido. */
    public static final int LIMITE_DE_TAXA = 130429;

    /** Erro interno da Meta. */
    public static final int ERRO_INTERNO = 131000;

    /**
     * Se vale tentar de novo.
     *
     * <p>Na duvida, classificamos como <b>permanente</b>: retentar um erro que
     * nao vai passar gasta cota e mantem o defeito escondido atras de tentativas.
     * Um alerta em FAILED com a mensagem da Meta e mais util que um alerta
     * pendente para sempre.
     */
    public static boolean transitorio(int codigo, int httpStatus) {
        if (codigo == LIMITE_DE_TAXA || codigo == ERRO_INTERNO) {
            return true;
        }
        if (codigo == DESTINATARIO_NAO_PERMITIDO
                || codigo == TOKEN_INVALIDO
                || codigo == TEMPLATE_INEXISTENTE
                || codigo == PARAMETRO_INVALIDO
                || codigo == FORA_DA_JANELA) {
            return false;
        }
        // Sem codigo reconhecido, o status HTTP decide: 5xx e problema do
        // servidor e passa; 4xx e problema do nosso pedido e nao passa.
        return httpStatus >= 500 || httpStatus == 429;
    }

    /** Explicacao em portugues para o que der para reconhecer. */
    public static String explicar(int codigo) {
        return switch (codigo) {
            case DESTINATARIO_NAO_PERMITIDO ->
                "o numero nao esta na lista de destinatarios verificados do numero de teste";
            case TOKEN_INVALIDO ->
                "token invalido ou expirado; se usou o token temporario, ele dura so 24h";
            case TEMPLATE_INEXISTENTE ->
                // A causa menos obvia vem primeiro de proposito: template pertence
                // a UMA conta (WABA). Aprovado na conta A, ele nao existe para um
                // numero da conta B, e a Meta reporta isso como "nao encontrado".
                // Foi o BUG-009, e custou uma investigacao inteira.
                "template nao encontrado para ESTE numero; confira se ele foi aprovado na "
                        + "mesma conta (WABA) do remetente, e depois nome, idioma e aprovacao";
            case PARAMETRO_INVALIDO ->
                "parametro do template invalido; nao pode ser vazio nem conter quebra de linha";
            case FORA_DA_JANELA ->
                "texto livre fora da janela de 24h; alerta precisa de template aprovado";
            case LIMITE_DE_TAXA -> "limite de requisicoes atingido";
            case RESTRICAO_DE_PAIS ->
                "a conta esta proibida de enviar para este pais; remetente e destinatario "
                        + "precisam estar no mesmo pais (foi o BUG-007)";
            case DESTINATARIO_INALCANCAVEL ->
                "o destinatario nao pode receber: numero sem WhatsApp, desativado ou que bloqueou";
            case ENTREGA_RECUSADA_POR_QUALIDADE ->
                "a Meta optou por nao entregar, tipicamente por qualidade da conta ou "
                        + "excesso de mensagens iniciadas pela empresa";
            default -> null;
        };
    }
}
