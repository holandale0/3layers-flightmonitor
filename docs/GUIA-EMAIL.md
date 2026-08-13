# Guia — canal de e-mail

> O que **você** precisa providenciar para a etapa E4.6, e por quê.
> Decisão registrada em [D-097](DECISOES.md).

## Resumo

São **cinco minutos** de configuração e **zero custo**. Não precisa de domínio próprio, DNS,
cadastro em serviço nenhum, nem cartão de crédito.

O código não depende disto para ser escrito nem testado — a suíte usa um SMTP falso. Isto só é
necessário para o sistema mandar e-mail **de verdade** para você.

**A regra que organiza tudo aqui:** o sistema tem a **própria conta** para enviar. A sua conta
pessoal só **recebe**, e nenhuma credencial dela entra no projeto.

---

## Por que uma conta separada, e não a sua

A pergunta apareceu naturalmente: se a conta que envia for a mesma que recebe, o sistema estaria
mandando e-mail para você mesmo. Estaria — e isso quebra três coisas:

| O que quebra | Por quê |
|---|---|
| **A caixa de entrada** | o Gmail exibe o remetente como *eu* e agrupa alertas de assunto parecido na **mesma conversa**. O quinto alerta aparece colapsado dentro de uma thread velha |
| **A notificação no celular** | o Gmail trata mensagem que você mesmo mandou de forma diferente para efeito de aviso. Varia com versão e configuração — e num sistema cujo único propósito é avisar, "às vezes não notifica" já basta para descartar |
| **O raio do estrago** | a senha de app seria da sua conta principal. Vazando daqui, alguém manda e-mail em seu nome a partir dela |

**Dois contornos que não funcionam,** para você não perder tempo tentando:

- **Trocar só o `MAIL_FROM`.** O SMTP do Gmail exige que o remetente seja a conta autenticada ou
  um alias já verificado em "Enviar e-mail como". Qualquer outro endereço ele reescreve ou recusa.
- **O truque do `+`** (`voce+voos@gmail.com`). Continua sendo a mesma conta — logo, continua sendo
  você mandando para você.

---

## O que providenciar

### 1. Uma conta Google nova, só do sistema ✅ feito

`flightmonitor.seunome@gmail.com` — já criada e já configurada no `.env`.

Ela existe para **enviar**. Você nunca vai precisar abrir essa caixa.

### 2. Verificação em duas etapas **nessa conta nova**

Sem ela, o Google não deixa gerar senha de app. Em `myaccount.google.com/security` →
**Verificação em duas etapas**, logado na conta do sistema.

### 3. Uma senha de app, também na conta nova

Em `myaccount.google.com/apppasswords`, crie uma com um nome que você reconheça depois
(sugestão: `flight-monitor`). O Google mostra **16 caracteres** — copie na hora, porque ele não
mostra de novo.

**Por que senha de app e não a senha da conta:** ela vale só para SMTP, não dá acesso ao Gmail
pelo navegador, e você pode revogá-la sozinha sem trocar a senha da conta. Somada à conta
dedicada, o pior caso vira "alguém manda alerta de passagem falso" — e não "alguém manda e-mail
em seu nome".

### 4. Confirmar o endereço que recebe

O seu pessoal — `voce@gmail.com`. Ele entra no cadastro de **destinatário**, junto do
nome, e não em variável de ambiente: destinatário é dado do sistema, não configuração
([D-097](DECISOES.md)).

---

## O que fazer com isso

Já está tudo no `.env` (que é **gitignored**), com **uma linha em branco** esperando por você —
a `MAIL_PASSWORD`:

```dotenv
# Canal de e-mail (etapa E4.6). Conta do SISTEMA, nao a pessoal.
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=flightmonitor.seunome@gmail.com
MAIL_PASSWORD=as16letrasdasenhadeapp
MAIL_FROM=flightmonitor.seunome@gmail.com

# So quando quiser ATIVAR o canal. O padrao continua WHATSAPP.
NOTIFICATION_CHANNEL=EMAIL
```

`MAIL_FROM` igual ao `MAIL_USERNAME` não é redundância preguiçosa: é a única combinação que o
Gmail aceita sem alias verificado.

A senha de app vem com espaços quando o Google a exibe (`abcd efgh ijkl mnop`). **Tire os
espaços** — eles não fazem parte da senha, são só para facilitar a leitura.

---

## Duas coisas que a implementação já cuidou

**O assunto varia.** Mesmo vindo de outro remetente, o Gmail agrupa mensagens de assunto idêntico.
O assunto carrega rota e preço — `✈ GRU → SSA por R$ 1.401,00` — o que resolve o agrupamento e
ainda faz o alerta ser legível **sem abrir o e-mail**, direto na notificação. Há teste garantindo
que ele cabe em 60 caracteres, que é o que costuma sobreviver na tela do celular.

**O corpo reaproveita o que já existia.** O `AlertMessageFormatter` produz texto completo desde a
E1.11, porque o canal LOG precisava disso. Não há uma segunda redação de mensagem para manter em
dia.

---

## Limites, e por que não incomodam aqui

| Limite do Gmail | O que este projeto usa |
|---|---|
| ~500 destinatários por dia | unidades por dia: só há alerta quando cai abaixo do teto, e o anti-spam de 12h segura o resto |
| ~100 por vez | um alerta por destinatário, um destinatário por pessoa |

Se um dia isso apertar, o `MAIL_HOST` aponta para outro servidor e nada mais muda — foi por isso
que a decisão foi SMTP configurável, e não a API do Gmail ([D-097](DECISOES.md)).

---

## O que o e-mail **não** vai fazer

**Não vai dizer se você leu.** O WhatsApp avisa por webhook quando a mensagem foi entregue e
quando foi lida, e o sistema guarda isso em `delivered_at` e `read_at`. E-mail não tem
equivalente confiável.

Existe um truque comum — uma imagem invisível de 1 pixel que registra a abertura — e ele foi
**recusado**. Ele falha silenciosamente sempre que o cliente de e-mail bloqueia imagens, que é o
padrão em boa parte deles. O resultado seria um `read_at` vazio para e-mails que **foram** lidos:
pior que não ter a informação, porque parece informação.

Então um alerta por e-mail para em `SENT`, e `SENT` aqui quer dizer exatamente "o servidor SMTP
aceitou". É menos do que o WhatsApp entrega, e o sistema não vai fingir o contrário.

---

## Diferença importante em relação ao WhatsApp

A E1.12 dependeu de a Meta **aprovar um template**, e essa aprovação levou dias. Nada aqui depende
de aprovação de ninguém: o canal funciona no minuto em que a senha de app existir.

Foi exatamente por isso que este canal entrou ([D-097](DECISOES.md)).
