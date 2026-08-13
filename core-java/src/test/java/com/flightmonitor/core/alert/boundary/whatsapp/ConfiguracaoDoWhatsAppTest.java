package com.flightmonitor.core.alert.boundary.whatsapp;

import static org.assertj.core.api.Assertions.assertThat;

import com.flightmonitor.core.alert.entity.WhatsAppConfig;
import com.flightmonitor.core.alert.entity.WhatsAppConfigRepository;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * A precedencia entre banco e ambiente — o coracao da E4.7.
 *
 * <p>Duas fontes para o mesmo valor e sempre um risco: se a ordem nao for
 * obvia e verificada, alguem salva pela tela, nada muda, e a conclusao vira
 * "a tela nao funciona".
 */
class ConfiguracaoDoWhatsAppTest {

    private static final WhatsAppProperties AMBIENTE = new WhatsAppProperties(
            "111-do-ambiente",
            "token",
            "https://graph.facebook.com",
            "v21.0",
            "template_do_ambiente",
            "pt_BR",
            null,
            null,
            Duration.ofSeconds(5));

    private ConfiguracaoDoWhatsApp com(WhatsAppConfig linha) {
        WhatsAppConfigRepository repo = Mockito.mock(WhatsAppConfigRepository.class);
        Mockito.when(repo.carregar()).thenReturn(Optional.ofNullable(linha));
        return new ConfiguracaoDoWhatsApp(repo, AMBIENTE);
    }

    private static WhatsAppConfig linha(String numero, String template) {
        WhatsAppConfig c = new WhatsAppConfig();
        c.setPhoneNumberId(numero);
        c.setTemplateName(template);
        c.setTemplateLanguage("en_US");
        return c;
    }

    @Nested
    @DisplayName("sem linha no banco")
    class SemLinha {

        @Test
        @DisplayName("valem as variaveis de ambiente")
        void caiNoAmbiente() {
            // E o estado de TODA instalacao anterior a E4.7. Sem isto, a etapa
            // quebraria o WhatsApp de quem ja o tinha funcionando.
            var efetiva = com(null).atual();

            assertThat(efetiva.phoneNumberId()).isEqualTo("111-do-ambiente");
            assertThat(efetiva.templateName()).isEqualTo("template_do_ambiente");
            assertThat(efetiva.origem()).isEqualTo(ConfiguracaoDoWhatsApp.Origem.AMBIENTE);
        }
    }

    @Nested
    @DisplayName("com linha no banco")
    class ComLinha {

        @Test
        @DisplayName("o banco vence o ambiente")
        void bancoTemPrecedencia() {
            // Quem configurou pela tela espera que a tela mande. A ordem inversa
            // faria salvar parecer que nao teve efeito.
            var efetiva = com(linha("999-do-banco", "template_do_banco")).atual();

            assertThat(efetiva.phoneNumberId()).isEqualTo("999-do-banco");
            assertThat(efetiva.templateName()).isEqualTo("template_do_banco");
            assertThat(efetiva.templateLanguage()).isEqualTo("en_US");
            assertThat(efetiva.origem()).isEqualTo(ConfiguracaoDoWhatsApp.Origem.BANCO);
        }

        @Test
        @DisplayName("campo em branco no banco volta a valer o ambiente, campo a campo")
        void completaCampoACampo() {
            // O caso de quem so quis trocar o template e deixou o numero em
            // branco. Tudo-ou-nada aqui obrigaria a pessoa a redigitar um
            // identificador que ela nem sabe de cor.
            var efetiva = com(linha(null, "so_o_template")).atual();

            assertThat(efetiva.templateName()).isEqualTo("so_o_template");
            assertThat(efetiva.phoneNumberId()).isEqualTo("111-do-ambiente");
        }

        @Test
        @DisplayName("string vazia conta como em branco")
        void vazioNaoEhValor() {
            var efetiva = com(linha("   ", "template")).atual();

            assertThat(efetiva.phoneNumberId()).isEqualTo("111-do-ambiente");
        }
    }

    @Test
    @DisplayName("sem numero em lugar nenhum, nao esta identificada")
    void semNumeroNemNoAmbiente() {
        WhatsAppProperties semNumero = new WhatsAppProperties(
                null, "token", null, null, null, null, null, null, null);
        WhatsAppConfigRepository vazio = Mockito.mock(WhatsAppConfigRepository.class);
        Mockito.when(vazio.carregar()).thenReturn(Optional.empty());

        var efetiva = new ConfiguracaoDoWhatsApp(vazio, semNumero).atual();

        // O canal usa isto para recusar com mensagem util, em vez de montar uma
        // URL com "null" no meio e receber 404 da Meta.
        assertThat(efetiva.identificada()).isFalse();
    }

    @Test
    @DisplayName("le o banco a cada chamada, e nao uma vez so")
    void naoCacheia() {
        // E o incomodo que a etapa veio resolver: com o valor preso no
        // construtor, trocar o template exigia REINICIAR. Um cache aqui
        // traria o problema de volta, disfarcado de otimizacao.
        WhatsAppConfigRepository repo = Mockito.mock(WhatsAppConfigRepository.class);
        Mockito.when(repo.carregar())
                .thenReturn(Optional.of(linha("1", "antes")))
                .thenReturn(Optional.of(linha("1", "depois")));

        ConfiguracaoDoWhatsApp configuracao = new ConfiguracaoDoWhatsApp(repo, AMBIENTE);

        assertThat(configuracao.atual().templateName()).isEqualTo("antes");
        assertThat(configuracao.atual().templateName()).isEqualTo("depois");
    }
}
