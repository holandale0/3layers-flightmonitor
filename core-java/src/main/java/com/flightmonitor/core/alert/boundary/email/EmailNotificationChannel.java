package com.flightmonitor.core.alert.boundary.email;

import com.flightmonitor.core.alert.control.DeliveryResult;
import com.flightmonitor.core.alert.control.NotificationChannel;
import com.flightmonitor.core.alert.entity.Alert;
import com.flightmonitor.core.alert.entity.AlertChannel;
import com.flightmonitor.core.search.entity.PriceObservation;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailAuthenticationException;
import org.springframework.mail.MailParseException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Entrega o alerta por e-mail, via SMTP — etapa E4.6.
 *
 * <h2>Por que este canal existe</h2>
 *
 * O escopo original do projeto dizia explicitamente <b>sem e-mail</b>. O que
 * mudou nao foi a preferencia: o WhatsApp se mostrou <b>bloqueavel por
 * terceiro</b>. O template ficou dias em analise na Meta, e nesse periodo o
 * sistema varria, confirmava, pontuava, decidia alertar — e nao conseguia avisar
 * ninguem. Um canal cujo funcionamento depende da aprovacao de outra empresa nao
 * pode ser o unico. Ver D-097.
 *
 * <h2>O que ele NAO faz, de proposito</h2>
 *
 * Nao rastreia abertura. O truque comum — imagem invisivel de 1 pixel — foi
 * recusado porque falha calado sempre que o cliente bloqueia imagens, que e o
 * padrao em boa parte deles: produziria {@code read_at} vazio para e-mails que
 * <b>foram</b> lidos. Pior que nao ter a informacao, porque parece informacao.
 *
 * <p>Por isso {@link #confirmacaoAssincrona()} fica no padrao {@code false}: o
 * alerta para em {@code SENT}, e {@code SENT} aqui quer dizer exatamente "o
 * servidor SMTP aceitou". E menos do que o WhatsApp entrega, e o canal nao finge
 * o contrario.
 */
@Component
public class EmailNotificationChannel implements NotificationChannel {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationChannel.class);

    private final JavaMailSender remetente;
    private final EmailProperties props;

    public EmailNotificationChannel(JavaMailSender remetente, EmailProperties props) {
        this.remetente = remetente;
        this.props = props;

        if (!props.configurado()) {
            log.warn("canal EMAIL sem remetente configurado: defina MAIL_FROM no .env. "
                    + "Envios serao recusados com falha permanente ate la");
        }
    }

    @Override
    public AlertChannel canal() {
        return AlertChannel.EMAIL;
    }

    @Override
    public DeliveryResult enviar(Alert alerta) {
        if (!props.configurado()) {
            return DeliveryResult.falhaPermanente(
                    "canal EMAIL sem remetente configurado (flightmonitor.email.remetente)");
        }
        if (alerta.getRecipient() == null) {
            return DeliveryResult.falhaPermanente("alerta sem destinatario");
        }

        String destino = alerta.getRecipient().getEmail();
        if (destino == null || destino.isBlank()) {
            // Permanente, e nao transitoria: nenhuma retentativa faz um e-mail
            // aparecer no cadastro. O motivo fica gravado em `error_message`, que
            // e onde alguem vai procurar quando notar que o alerta nao chegou.
            return DeliveryResult.falhaPermanente(
                    "destinatario '%s' nao tem e-mail cadastrado".formatted(
                            alerta.getRecipient().getName()));
        }

        try {
            MimeMessage mensagem = montar(alerta, destino);
            remetente.send(mensagem);

            // O Message-ID e gerado pelo JavaMail no envio, entao so existe DEPOIS
            // do send(). E o unico identificador que o SMTP oferece — nao ha
            // equivalente do wamid da Meta, e e com ele que se rastreia a entrega
            // nos logs do provedor.
            String id = mensagem.getMessageID();
            log.info("alerta {} enviado por e-mail para {} ({})", alerta.getId(), destino, id);
            return DeliveryResult.entregue(id);

        } catch (MailAuthenticationException e) {
            // Senha de app errada ou revogada. Retentar so repete o mesmo erro e
            // gasta as tres tentativas antes de mostrar o problema real.
            return DeliveryResult.falhaPermanente(
                    "SMTP recusou a autenticacao: verifique MAIL_USERNAME e a senha de app");

        } catch (MailParseException | MessagingException | UnsupportedEncodingException e) {
            // Endereco malformado ou mensagem invalida: culpa dos dados, e eles
            // nao mudam sozinhos entre uma tentativa e outra.
            return DeliveryResult.falhaPermanente("mensagem invalida: " + e.getMessage());

        } catch (org.springframework.mail.MailException e) {
            // Sobra o que e de rede: servidor fora do ar, timeout, DNS. Vale
            // tentar de novo — e a mesma classificacao que o adaptador do
            // WhatsApp faz entre 5xx e 4xx.
            return DeliveryResult.falhaTransitoria("falha ao enviar: " + e.getMessage());
        }
    }

    private MimeMessage montar(Alert alerta, String destino)
            throws MessagingException, UnsupportedEncodingException {

        MimeMessage mensagem = remetente.createMimeMessage();
        MimeMessageHelper ajudante = new MimeMessageHelper(mensagem, false, "UTF-8");

        ajudante.setFrom(props.remetente(), props.nomeExibido());
        ajudante.setTo(destino);
        ajudante.setSubject(assunto(alerta));
        ajudante.setText(alerta.getMessage());
        return mensagem;
    }

    /**
     * O assunto, que precisa <b>variar</b> a cada alerta.
     *
     * <p>Nao e capricho de redacao: o Gmail agrupa mensagens de assunto identico
     * na mesma conversa, mesmo vindas de remetente diferente. Com assunto fixo, o
     * quinto alerta apareceria colapsado dentro de uma thread velha — que e o
     * oposto do que um monitor precisa fazer. Ver D-098.
     *
     * <p>Rota e preco tambem tornam o alerta legivel <b>na propria notificacao do
     * celular</b>, sem abrir o e-mail. Para um sistema que existe para avisar,
     * essa e a tela que mais importa.
     */
    private String assunto(Alert alerta) {
        PriceObservation oferta = alerta.getPriceObservation();
        if (oferta == null) {
            // Alerta sem observacao nao deveria existir, mas assunto vazio faria
            // o e-mail parecer spam. Melhor generico do que em branco.
            return "Oportunidade de passagem encontrada";
        }
        return "%s %s %s por %s".formatted(
                "✈",
                oferta.getOrigin(),
                "→ " + oferta.getDestination(),
                dinheiro(oferta.getPrice(), oferta.getCurrency()));
    }

    private String dinheiro(BigDecimal valor, String moeda) {
        if (valor == null) {
            return "preco indisponivel";
        }
        if (!"BRL".equals(moeda)) {
            return moeda + " " + valor;
        }
        return NumberFormat.getCurrencyInstance(Locale.of("pt", "BR")).format(valor);
    }
}
