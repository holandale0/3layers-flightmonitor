# Guia — webhook de status do WhatsApp

> Como ligar o aviso de entrega da Meta ao sistema.
> Implementado na etapa **E1.17**. Motivo de existir: [BUG-007](BUGS.md) e [D-053](DECISOES.md).

**Escrito em:** 2026-08-12

---

## Por que isto importa

Quando o sistema manda um alerta, a Meta responde com um `wamid` e `message_status: accepted`.
É tentador tratar isso como "entregue". **Não é.**

No BUG-007, quatro mensagens receberam `wamid`, foram marcadas como enviadas, e nenhuma chegou.
O motivo — `130497`, *"Business account is restricted from messaging users in this country"* —
existia só no webhook, que ninguém estava lendo. A investigação levou horas porque o banco
dizia `SENT` para mensagens que nunca saíram.

Com o webhook ligado:

| Estado | O que significa |
|---|---|
| `PENDING` | criado, ainda não despachado |
| `ACCEPTED` | a Meta recebeu e devolveu `wamid`. **Não sabemos se chegou** |
| `SENT` | o webhook confirmou a entrega no aparelho |
| `FAILED` | a entrega falhou, e `error_message` diz por quê |

Sem o webhook, os alertas param em `ACCEPTED` para sempre. É desconfortável, e é honesto: sem
ele, não sabemos mesmo.

---

## Passo 1 — Expor o endpoint

A Meta precisa alcançar `POST https://SEU-DOMINIO/api/webhooks/whatsapp`. Ela **exige HTTPS** e
não aceita `localhost`.

Em desenvolvimento, um túnel resolve:

```bash
# Opção A — cloudflared, sem cadastro
cloudflared tunnel --url http://localhost:8081

# Opção B — ngrok
ngrok http 8081
```

Qualquer um imprime uma URL pública. O endereço a cadastrar é ela + `/api/webhooks/whatsapp`.

> ⚠️ A URL do túnel gratuito **muda a cada reinício**. Ao reiniciar, é preciso reeditar a URL no
> painel da Meta. Para uso contínuo, o webhook só vale a pena depois do deploy (E4.4).

---

## Passo 2 — Escolher os dois segredos

```properties
# Você inventa este. A Meta o devolve na verificação inicial.
WHATSAPP_WEBHOOK_VERIFY_TOKEN=uma-frase-longa-que-voce-inventou

# Este vem do painel: Configurações do app → Básico → Chave Secreta do App
WHATSAPP_APP_SECRET=<a chave secreta do app>
```

**Os dois têm papéis diferentes, e ambos importam:**

- o **verify token** é usado **uma vez**, quando a Meta confirma que a URL é sua;
- o **app secret** é usado em **toda** notificação, para conferir a assinatura
  `X-Hub-Signature-256`.

> 🔒 **Sem `WHATSAPP_APP_SECRET`, o endpoint aceita qualquer POST** — e ele é público por
> natureza, porque a Meta precisa alcançá-lo. Qualquer um que descubra a URL poderia marcar
> alertas como entregues, ou como falhos, apagando justamente o sinal que o webhook existe para
> capturar. O sistema registra aviso em log a cada requisição não conferida; o incômodo é
> proposital.

---

## Passo 3 — Cadastrar no painel

1. `developers.facebook.com` → app **flight-price-monitor-notifyer**
2. **WhatsApp → Configuração** (ou **Configuração da API**)
3. Na seção **Webhook**, clique em **Editar**
4. Preencha:

   | Campo | Valor |
   |---|---|
   | URL de retorno de chamada | `https://SEU-DOMINIO/api/webhooks/whatsapp` |
   | Verificar token | o mesmo de `WHATSAPP_WEBHOOK_VERIFY_TOKEN` |

5. **Verificar e salvar**
6. Em **Campos do webhook**, assine **`messages`**

O campo `messages` cobre tanto os status de entrega quanto as mensagens que o destinatário
enviar. O sistema ignora o segundo tipo — ver `WhatsAppWebhookService`.

### Se a verificação falhar

| Sintoma | Causa provável |
|---|---|
| "A URL de retorno de chamada ou o token de verificação não puderam ser validados" | o core não está no ar, o túnel caiu, ou o token não bate |
| Falha mesmo com tudo certo | a resposta precisa ser o desafio **puro**. Um proxy que embrulhe a resposta em JSON quebra isso |
| Erro de certificado | a Meta não aceita certificado autoassinado |

---

## Passo 4 — Conferir que está funcionando

Depois de um alerta sair:

```sql
SELECT id, status, sent_at, delivered_at, read_at, error_message
FROM alert ORDER BY id DESC LIMIT 5;
```

O que esperar:

```
status    sent_at              delivered_at         read_at
--------  -------------------  -------------------  -------------------
SENT      12:04:31             12:04:33             12:07:02
```

- `ACCEPTED` com `delivered_at` nulo por mais de alguns minutos → o webhook **não** está
  chegando. Confira o túnel e a assinatura do campo `messages`
- `FAILED` com `error_message` → **é para isso que ele existe**. O motivo está ali

No log da aplicação:

```
alerta 42 -> SENT (delivered)
alerta 43 NAO foi entregue: entrega recusada (codigo 130497): a conta esta proibida...
```

---

## Detalhes que o formato impõe

Estão tratados no código, e explicam decisões que parecem estranhas à primeira vista.

**A ordem não é garantida.** `read` pode chegar antes de `delivered`. Como leitura implica
entrega, um `read` isolado também confirma a entrega.

**Repetição é esperada.** Se a Meta não receber `200` rápido, ela reenvia o lote inteiro. Toda
atualização é idempotente: o horário de entrega registrado é o da **primeira** confirmação.

**O timestamp vem em segundos, como texto.** Interpretar como milissegundos jogaria a data para
1970 — e o `CHECK` de coerência do banco recusaria a linha.

**Responder `200` quase sempre.** A Meta retenta o que não recebe `200` e, depois de falhas
repetidas, **desativa** o webhook. Por isso corpo ilegível, `wamid` desconhecido e até uma
exceção nossa respondem `200`. A única exceção é assinatura inválida: aí não há assinatura da
Meta a preservar.

---

## O que ainda não existe

- **Reenvio automático** de um alerta que o webhook reportou como falho. Hoje ele vira `FAILED`
  com o motivo, e para aí. Reenviar sem entender a causa repetiria o BUG-007 mais rápido
- **Painel** mostrando o estado de entrega. O dado está no banco desde a migração V4
- **Alerta sobre o alerta**: nada avisa que uma entrega falhou além do log
