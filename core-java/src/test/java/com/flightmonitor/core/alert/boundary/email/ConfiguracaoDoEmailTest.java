package com.flightmonitor.core.alert.boundary.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;

/**
 * Guarda a configuracao do canal de e-mail — e nasceu de um bug.
 *
 * <h2>O bug</h2>
 *
 * O bloco {@code email:} foi inserido no {@code application.yml} ancorado em
 * {@code logging:}, sem olhar o que vinha antes — e caiu aninhado sob
 * {@code management:}. A propriedade virou {@code management.email.remetente},
 * que ninguem le. O canal subia dizendo "sem remetente configurado" e recusaria
 * <b>todo</b> envio com falha permanente.
 *
 * <h2>Por que a suite nao pegou</h2>
 *
 * {@code EmailNotificationChannelTest} constroi {@code EmailProperties} na mao,
 * para poder testar o canal sem subir o Spring. Isso e bom para testar
 * comportamento — e cego para configuracao, porque nunca passa pelo YAML.
 *
 * <p>E exatamente o mesmo formato do <b>BUG-008</b>, em que o
 * {@code application.yml} apontava para um template do WhatsApp que nao existia
 * mais e a suite seguia verde. A licao la foi criar um teste que le a
 * configuracao <b>como o Spring a carrega</b>. Este e o equivalente para o
 * e-mail.
 */
@SpringBootTest
class ConfiguracaoDoEmailTest {

    @Autowired
    private EmailProperties props;

    @Autowired
    private Environment ambiente;

    @Test
    @DisplayName("o bloco email esta sob flightmonitor, e nao sob outra chave")
    void oBlocoEstaNoLugarCerto() {
        // Se o bloco cair sob management: (ou qualquer outra raiz), este valor
        // vem nulo — que foi exatamente o sintoma.
        assertThat(ambiente.getProperty("flightmonitor.email.nome-exibido"))
                .as("flightmonitor.email.nome-exibido nao foi encontrado: "
                        + "o bloco email: provavelmente esta aninhado na chave errada")
                .isNotBlank();
    }

    @Test
    @DisplayName("o nome exibido chega ao bean, e nao so ao arquivo")
    void nomeExibidoChegaAoBean() {
        // Confere o caminho inteiro: YAML -> Environment -> binding do record.
        assertThat(props.nomeExibido()).isNotBlank();
    }

    @Test
    @DisplayName("o remetente vem de MAIL_FROM, e nao de endereco fixo no YAML")
    void remetenteNaoEstaChumbadoNoRepositorio() throws Exception {
        // Le o ARQUIVO, e nao o valor resolvido: o que se quer garantir e que o
        // endereco venha do .env. Testar o valor resolvido nao distingue "veio
        // do ambiente" de "esta escrito aqui", que e justamente a diferenca.
        String yaml = new String(
                new ClassPathResource("application.yml").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        assertThat(yaml)
                .as("o remetente precisa ser um placeholder de ambiente")
                .contains("remetente: ${MAIL_FROM:");

        // Se alguem "resolver" a falta de configuracao escrevendo o endereco
        // direto aqui, ele vai para o git junto — e enderecos de conta de
        // sistema seguem a mesma disciplina das credenciais (D-098).
        assertThat(yaml)
                .as("endereco de e-mail nao pode estar chumbado no application.yml")
                .doesNotContain("@gmail.com");
    }
}
