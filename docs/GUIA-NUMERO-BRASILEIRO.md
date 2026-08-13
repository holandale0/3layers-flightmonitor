# Guia — número brasileiro dedicado para o WhatsApp

> Passo a passo para o número que vai **enviar** os alertas.
> Necessário porque o número de teste da Meta não entrega no Brasil ([BUG-007](BUGS.md)).

**Escrito em:** 2026-08-11

---

## A resposta curta à dúvida principal

> *"Devo ativar o número, colocar crédito, mas **não** devo criar uma conta WhatsApp, certo?"*

**Exatamente isso.** Três pontos:

| O que fazer | Por quê |
|---|---|
| ✅ Ativar o chip e deixar receber SMS/ligação | A Meta manda um código de 6 dígitos para confirmar que o número é seu |
| ✅ Ter crédito | Só o suficiente para receber. Recebimento costuma ser gratuito, mas linha sem crédito às vezes é bloqueada pela operadora |
| ❌ **NÃO instalar WhatsApp nesse número** | Se o número tiver conta de WhatsApp, o registro na Cloud API **falha** |

**Por que não pode ter WhatsApp:** um número existe no WhatsApp em **um lugar só**. Ou ele é
uma conta comum de aplicativo, ou é um número da Cloud API. Não dá para ser os dois. Se você
instalar o WhatsApp e registrar o número, vai precisar excluir a conta antes de continuar — e
esperar.

> ⚠️ **Isto é definitivo:** depois que o número entra na Cloud API, ele **nunca mais** funciona
> no aplicativo do WhatsApp. Por isso o número precisa ser **dedicado** — jamais o seu pessoal.

---

## Passo 1 — Escolher o número

Precisa ser **brasileiro**. É o ponto central: a Meta bloqueia mensagens entre países
envolvendo o Brasil, então remetente e destinatário precisam estar no mesmo país.

### Opções

| Opção | Custo | Observação |
|---|---|---|
| **Chip pré-pago** (Vivo, Claro, TIM, Algar) | R$ 10–30 | O mais simples. Qualquer loja ou banca |
| **eSIM pré-pago** | similar | Se o seu celular aceita, evita chip físico |
| **Número virtual brasileiro** | varia | Precisa receber SMS **ou ligação** |
| **Telefone fixo** | — | Funciona: a verificação aceita ligação com o código falado |

### O que o número precisa

- ✅ receber SMS **ou** chamada de voz — basta um dos dois
- ✅ estar ativo (com crédito, se pré-pago)
- ❌ **não** ter WhatsApp instalado/registrado
- ❌ não ser um número que você use para outra coisa

> **Dica sobre chip pré-pago:** operadoras desativam linhas sem uso após alguns meses. Se o
> número morrer, o WhatsApp Business para de funcionar. Uma recarga pequena por semestre, ou
> deixar o chip num aparelho antigo ligado, evita a dor de cabeça.

---

## Passo 2 — Ativar o chip

1. Coloque o chip em **qualquer** aparelho (pode ser um celular antigo)
2. Ative pela operadora — normalmente uma ligação ou site
3. Faça uma recarga mínima, se for pré-pago
4. **Confirme que recebe SMS**, mandando uma mensagem de outro número para ele
5. **Não abra o WhatsApp nesse aparelho com esse chip**

> Se o aparelho já tem WhatsApp instalado com outro número, tudo bem — o problema seria
> registrar o WhatsApp **neste** número novo.

---

## Passo 3 — Registrar na Meta

1. Acesse `developers.facebook.com` → seu app **flight-price-monitor-notifyer**
2. **Casos de uso** → **Personalizar**
3. **Configuração básica** → **Etapa 2. Configuração de produção**

   > Sim, a mesma etapa que os guias anteriores mandavam evitar. Ela deixou de ser opcional.

4. Abra **"Registre seu número de telefone do WhatsApp"**
5. Preencha:
   - **Nome de exibição**: o que o destinatário vê. Algo como `Flight Monitor`
     > A Meta revisa esse nome. Evite nome de marca conhecida ou algo que pareça enganoso
   - **Categoria**: escolha a mais próxima, por exemplo "Viagens e transporte"
   - **Número**: o brasileiro, com DDD
6. **Método de verificação**: SMS ou ligação
7. Digite o **código de 6 dígitos** que chegar
8. Defina um **PIN de 6 dígitos**
   > 📋 **Anote esse PIN.** Ele é pedido em re-registro do número, e a Meta **não permite
   > recuperar** — só redefinir. Não é o código da verificação, que é temporário e chega por
   > SMS ou ligação; o PIN é uma senha permanente que você escolhe.
   >
   > Neste projeto ele fica no `.env`, sob `WHATSAPP_PIN`, fora do Git. A aplicação não o usa
   > — é cofre para o dia em que o número precisar ser re-registrado.

---

## Passo 4 — Cadastrar forma de pagamento

Sem isso as mensagens iniciadas pela empresa não saem, e o nosso alerta é sempre iniciada
pela empresa.

1. Ainda na Etapa 2, abra **"Adicione informações de pagamento"**
2. Cadastre um cartão de crédito
3. Confirme

### O que você vai gastar de verdade

| Item | Valor |
|---|---|
| Registro do número | R$ 0 |
| Mensagem Utility, Brasil | centavos por envio |
| Mensagens de teste que você mandar | os mesmos centavos |

Com o anti-spam configurado (queda mínima de 5% e 12h de intervalo), o volume esperado é de
poucos alertas por mês. **A ordem de grandeza é R$ 1 a R$ 2 mensais.**

> A cobrança é por mensagem entregue. Varredura de preço não custa nada — o sistema faz
> milhares de consultas e manda pouquíssimas mensagens, que foi o desenho desde o começo.

---

## Passo 5 — Pegar o novo Phone Number ID

Depois do registro, a tela mostra o número novo com seu identificador.

📋 **Copie o `Phone Number ID`.** Ele é **diferente** do que usamos com o número de teste
(`1259875263877919`, que pode ser descartado).

---

## Passo 6 — Atualizar o projeto

No `.env` da raiz:

```properties
WHATSAPP_PHONE_NUMBER_ID=<o novo id>
WHATSAPP_ACCESS_TOKEN=<o mesmo token permanente, continua valendo>
NOTIFICATION_CHANNEL=WHATSAPP
```

**Nenhuma linha de código muda.**

---

## O que NÃO precisa refazer

| Item | Refazer? |
|---|---|
| App no developers.facebook.com | ❌ não |
| Conta comercial (Business Manager) | ❌ não |
| Usuário de sistema `flight-monitor` | ❌ não |
| Token permanente | ❌ não |
| Template `alerta_preco_voo` | ❌ não — pertence à conta, não ao número |
| Lista de destinatários verificados | ❌ **deixa de existir** — número de produção envia para qualquer um |

O único valor que muda é o `WHATSAPP_PHONE_NUMBER_ID`.

---

## Perguntas que provavelmente vão surgir

**Preciso verificar a empresa (CNPJ)?**
Não para começar. Conta não verificada envia para até 250 destinatários distintos por dia —
muito acima do que este projeto precisa. A verificação só interessa para aumentar esse limite.

**Posso usar meu número pessoal se eu não usar mais o WhatsApp nele?**
Tecnicamente sim, mas **não faça**. O número deixa de funcionar no aplicativo para sempre.
Um chip de R$ 15 evita um arrependimento irreversível.

**Quando o PIN é realmente usado?**
No `POST /{phone_number_id}/register`, que ativa o número na Cloud API. Se o painel já fez
isso sozinho — como aconteceu aqui — você nunca precisa dele. Ele volta a importar se o
número for migrado para outra conta, ou se precisar ser registrado de novo.

**E se eu registrar sem querer o WhatsApp nesse número?**
Dá para desfazer: abra o WhatsApp → Configurações → Conta → **Excluir minha conta**. Espere
alguns minutos e tente registrar de novo na Meta.

**O destinatário precisa fazer alguma coisa?**
Não. Com número de produção não há lista de permitidos. Basta o número estar cadastrado no
sistema e ter WhatsApp.

**Posso testar antes de gastar com mensagem?**
Sim: mantenha `NOTIFICATION_CHANNEL=LOG` e confira o texto no log. Só troque para `WHATSAPP`
quando quiser o envio de verdade.

---

## Como saber que deu certo

Depois de trocar o `.env` e reiniciar o core:

```bash
# 1. as credenciais estao validas?
curl -s "https://graph.facebook.com/v21.0/NOVO_PHONE_NUMBER_ID?fields=display_phone_number,verified_name,status" \
  -H "Authorization: Bearer SEU_TOKEN"
```

Espere ver o número brasileiro e `"status": "CONNECTED"`.

```sql
-- 2. o alerta saiu?
SELECT status, attempts, error_message, provider_message_id
FROM alert ORDER BY id DESC LIMIT 3;
```

**E principalmente: confira o celular.** Se aparecer `SENT` e nada chegar, volte ao
visualizador de webhooks em *Etapa 1 → rodapé* — foi exatamente assim que o erro 130497
apareceu. `SENT` hoje significa "a Meta aceitou", não "chegou"; a etapa E1.17 vai fechar essa
diferença.
