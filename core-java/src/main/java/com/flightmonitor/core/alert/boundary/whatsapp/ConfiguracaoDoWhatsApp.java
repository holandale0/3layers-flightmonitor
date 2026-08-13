package com.flightmonitor.core.alert.boundary.whatsapp;

import com.flightmonitor.core.alert.entity.WhatsAppConfig;
import com.flightmonitor.core.alert.entity.WhatsAppConfigRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * A configuracao que o adaptador realmente usa — etapa E4.7.
 *
 * <h2>Banco primeiro, ambiente depois</h2>
 *
 * Se existe linha em {@code whatsapp_config}, ela vale. Se nao existe, valem os
 * valores do {@code .env}. Nessa ordem, e nao na inversa, por um motivo pratico:
 * quem configurou pela tela espera que a tela mande.
 *
 * <p><b>O fallback nao e cortesia, e compatibilidade.</b> Toda instalacao
 * anterior a esta etapa tem os valores no {@code .env} e nenhuma linha no banco.
 * Sem o fallback, a E4.7 quebraria o WhatsApp de quem ja o tinha funcionando —
 * um preco alto demais por uma tela.
 *
 * <h2>Lido a cada envio, e nao no construtor</h2>
 *
 * Era esse o incomodo que a etapa resolve: com {@code @ConfigurationProperties}
 * injetado uma vez, trocar de template exigia <b>reiniciar</b>. Aqui a leitura
 * acontece na hora do envio.
 *
 * <p>O custo e uma consulta por chave primaria por alerta enviado — e alerta e
 * evento raro, nao laco quente. Cache aqui economizaria microssegundos e
 * traria de volta exatamente o problema que a etapa veio resolver: valor velho
 * depois de salvar.
 *
 * <h2>Os segredos nao passam por aqui</h2>
 *
 * {@code accessToken}, {@code appSecret} e {@code webhookVerifyToken} continuam
 * vindo direto de {@link WhatsAppProperties}. Este resolvedor trata apenas do
 * que <b>identifica</b> conta e template — nada que autentique nada sozinho.
 */
@Component
public class ConfiguracaoDoWhatsApp {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracaoDoWhatsApp.class);

    private final WhatsAppConfigRepository repositorio;
    private final WhatsAppProperties ambiente;

    public ConfiguracaoDoWhatsApp(
            WhatsAppConfigRepository repositorio, WhatsAppProperties ambiente) {
        this.repositorio = repositorio;
        this.ambiente = ambiente;
    }

    /**
     * Os valores em vigor agora.
     *
     * @param origem de onde cada valor veio, para a tela poder dizer ao usuario
     *        se ele esta editando algo ou apenas vendo o que o ambiente definiu
     */
    public record Efetiva(
            String phoneNumberId,
            String wabaId,
            String templateName,
            String templateLanguage,
            Origem origem) {

        /** Ha o minimo para enviar? Sem numero remetente, nao ha o que fazer. */
        public boolean identificada() {
            return phoneNumberId != null && !phoneNumberId.isBlank();
        }
    }

    public enum Origem {
        /** Alguem salvou pela tela ou pela API. */
        BANCO,
        /** Ninguem configurou; valem as variaveis de ambiente. */
        AMBIENTE
    }

    public Efetiva atual() {
        return repositorio.carregar()
                .map(this::doBanco)
                .orElseGet(this::doAmbiente);
    }

    private Efetiva doBanco(WhatsAppConfig c) {
        // Numero e WABA podem estar em branco no banco mesmo com linha salva —
        // alguem que so quis trocar o template. Nesse caso, o ambiente completa,
        // campo a campo, em vez de tudo-ou-nada.
        return new Efetiva(
                ouEntao(c.getPhoneNumberId(), ambiente.phoneNumberId()),
                ouEntao(c.getWabaId(), null),
                ouEntao(c.getTemplateName(), ambiente.templateName()),
                ouEntao(c.getTemplateLanguage(), ambiente.templateLanguage()),
                Origem.BANCO);
    }

    private Efetiva doAmbiente() {
        Efetiva efetiva = new Efetiva(
                ambiente.phoneNumberId(),
                null,
                ambiente.templateName(),
                ambiente.templateLanguage(),
                Origem.AMBIENTE);

        if (!efetiva.identificada()) {
            log.debug("WhatsApp sem numero remetente: nem no banco, nem no ambiente");
        }
        return efetiva;
    }

    private static String ouEntao(String preferido, String reserva) {
        return preferido == null || preferido.isBlank() ? reserva : preferido;
    }
}
