package com.flightmonitor.core.alert.control;

import com.flightmonitor.core.alert.control.dto.WhatsAppConfigRequest;
import com.flightmonitor.core.alert.entity.WhatsAppConfig;
import com.flightmonitor.core.alert.entity.WhatsAppConfigRepository;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grava a configuracao nao-secreta do canal WhatsApp — etapa E4.7.
 *
 * <h2>Por que este servico e so persistencia</h2>
 *
 * Ele nao sabe de onde a configuracao "em vigor" vem, e nem deveria: a regra
 * <i>banco primeiro, ambiente depois</i> mora em {@code ConfiguracaoDoWhatsApp},
 * na borda, junto do adaptador que a consome.
 *
 * <p>Isso nao e preciosismo de camada — e o teste de arquitetura falando. O
 * fallback precisa de {@code WhatsAppProperties}, que e configuracao do
 * <b>adaptador</b>; traze-la para ca faria o controle depender da borda, e
 * faria o motor saber que WhatsApp existe. Que e exatamente a regra 3 da secao 3
 * do plano.
 *
 * <p>Quem junta as duas metades e o controller, que pode falar com as duas.
 */
@Service
public class WhatsAppConfigService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppConfigService.class);

    private final WhatsAppConfigRepository repositorio;

    public WhatsAppConfigService(WhatsAppConfigRepository repositorio) {
        this.repositorio = repositorio;
    }

    /** Vazio significa "ninguem configurou pela tela", e nao erro. */
    public Optional<WhatsAppConfig> carregar() {
        return repositorio.carregar();
    }

    /**
     * Salva a unica linha, criando-a se ainda nao existir.
     *
     * <p>Nao ha "criar" e "atualizar" separados de proposito: do ponto de vista
     * de quem usa a tela existe <b>uma</b> configuracao, que ora esta preenchida
     * e ora nao. Dois verbos exigiriam que a tela soubesse se a linha existe —
     * detalhe de armazenamento que nao deveria vazar para a interface.
     */
    @Transactional
    public WhatsAppConfig salvar(WhatsAppConfigRequest req) {
        WhatsAppConfig config = repositorio.carregar().orElseGet(WhatsAppConfig::new);

        config.setPhoneNumberId(req.phoneNumberId());
        config.setWabaId(req.wabaId());
        config.setTemplateName(req.templateName());
        config.setTemplateLanguage(req.templateLanguage());

        WhatsAppConfig salvo = repositorio.saveAndFlush(config);

        // Sem segredo no log: os campos aqui identificam conta e template, e
        // nenhum deles autentica nada sozinho.
        log.info("configuracao do WhatsApp salva: template={} idioma={} numero={}",
                salvo.getTemplateName(),
                salvo.getTemplateLanguage(),
                salvo.getPhoneNumberId() == null ? "(do ambiente)" : salvo.getPhoneNumberId());

        return salvo;
    }

    /**
     * Apaga a linha, devolvendo o controle ao {@code .env}.
     *
     * <p>Existe porque "voltar ao padrao" precisa ser uma acao, e nao um ritual
     * de apagar campo por campo na tela e torcer para que o vazio seja
     * interpretado como ausencia.
     */
    @Transactional
    public void restaurarAmbiente() {
        repositorio.carregar().ifPresent(c -> {
            repositorio.delete(c);
            log.info("configuracao do WhatsApp removida: valem de novo as variaveis de ambiente");
        });
    }
}
