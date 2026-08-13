# Guia — obter as credenciais do WhatsApp Cloud API

> Passo a passo para conseguir `WHATSAPP_PHONE_NUMBER_ID` e `WHATSAPP_ACCESS_TOKEN`.
> Necessário para a etapa E1.12. Até lá o sistema funciona no canal `LOG`.

**Verificado em:** 2026-08-10 · A interface da Meta muda com frequência; se algum rótulo
estiver diferente, o caminho geral costuma se manter.

---

## 🔴 LEIA ANTES DE TUDO — o número de teste NÃO funciona no Brasil

Descoberto em 2026-08-11, após quatro mensagens aceitas pela Meta e nenhuma entregue
([BUG-007](BUGS.md)):

```json
"status": "failed",
"errors": [{ "code": 130497,
  "title": "Business account is restricted from messaging users in this country." }]
```

Desde 15/09 a Meta **bloqueia mensagens entre países envolvendo Brasil e Indonésia**. O número
de teste dela é sempre americano. Então:

> **Se os destinatários estão no Brasil, o número de teste nunca vai entregar.**
> Não adianta template, permissão, token permanente nem verificação de empresa.

Os passos 1 a 8 continuam válidos e valem a pena — eles montam o app, o token e o template,
que serão reaproveitados. Mas a entrega só funciona depois do **passo 9**.

---

## Antes de começar — três coisas que você precisa saber

**1. ~~Você não precisa de chip novo~~ — para destinatários no Brasil, precisa.**
O número de teste é gratuito e imediato, mas não entrega no Brasil (ver o aviso acima). Ele
ainda serve para validar credenciais e template. A entrega exige número brasileiro próprio,
no passo 9.

**2. O token temporário dura 24 horas — e isso não serve para nós.**
Um monitor que roda 24/7 pararia de alertar toda madrugada. O passo 6 cria um token
permanente. Faça o passo 6, não pule.

**3. ⚠️ Alerta é mensagem iniciada pela empresa, e isso exige template aprovado.**
Esta é a restrição que mais afeta o projeto. As regras da Meta:

| Situação | O que pode enviar |
|---|---|
| O destinatário te mandou mensagem há menos de 24h | **texto livre**, qualquer coisa |
| Fora dessa janela (o nosso caso) | **somente template aprovado** |

Nosso alerta chega sem que ninguém tenha escrito antes — é sempre "fora da janela". Então a
mensagem bonita que o sistema já monta precisa virar um **template com variáveis**. O passo 7
cuida disso.

---

## Passo 1 — Criar conta de desenvolvedor

1. Acesse **developers.facebook.com**
2. **Get Started** / **Começar** e entre com sua conta do Facebook
3. Confirme o e-mail e o telefone se for pedido

> Uma conta pessoal do Facebook basta. Não precisa de página nem de empresa.

## Passo 2 — Criar o app

1. **My Apps** → **Create App**
2. Nome do app: algo como `Flight Monitor` (só você vê)
3. E-mail de contato
4. Caso de uso: escolha **"Connect with customers through WhatsApp"**
   (em versões anteriores da interface era o tipo **Business**)
5. **Create app** — pode pedir sua senha do Facebook

## Passo 3 — Abrir a configuração do WhatsApp

> ⚠️ **Não procure por "Add Product" / "Adicionar produto".** A Meta trocou o modelo de
> *produtos* por *casos de uso*, e o botão não existe mais. Como o app foi criado já com o
> caso de uso do WhatsApp (passo 2), metade deste passo já está pronta.

No **Painel** do app, clique na primeira linha do bloco
*"Personalização do app e requisitos"*:

> **Personalizar o caso de uso "Conectar-se com os clientes pelo WhatsApp"** →

Ou use **Casos de uso** no menu lateral esquerdo — leva ao mesmo lugar.

Será pedido para vincular uma **Conta Comercial** (Meta Business Account). Pode criar na
hora: não exige CNPJ nem verificação nesta fase.

> O item **WhatsApp** só aparece no menu lateral depois que a conta comercial é vinculada.
> Enquanto isso, o acesso é pelo caso de uso.

### ⚠️ Escolha a etapa certa — e ignore as outras

Dentro de **Configuração básica**, no menu à esquerda, aparecem três etapas. **Só a primeira
interessa a este projeto:**

| Etapa | Serve para | Fazer? |
|---|---|---|
| **Etapa 1. Experimente** | número de teste da Meta, Phone Number ID, token, destinatários | ✅ **é esta** |
| Etapa 2. Configuração de produção | registrar **seu próprio** número e **cadastrar pagamento** | ❌ não |
| Etapa 3. Verificação da empresa | verificar CNPJ para sair do modo de teste | ❌ não |

A Etapa 2 pede literalmente *"Registre seu número de telefone do WhatsApp"* e *"Adicione
informações de pagamento"* — ou seja, comprar chip e pôr cartão, exatamente o que a
[D-011](DECISOES.md) descartou. Ela só faz sentido no dia em que o projeto precisar atender
mais de 5 destinatários.

**Clique em "Etapa 1. Experimente".**

## Passo 4 — Pegar o Phone Number ID

Dentro do caso de uso, procure **Configuração da API** / **API Setup**. A tela mostra:

```
De:  +1 555 0100 xxxx   (número de teste, fornecido pela Meta)
     Identificação do número de telefone:  123456789012345   ← ESTE VALOR
     Identificação da conta do WhatsApp Business: 987654321098765
```

📋 **Anote o `Phone number ID`.** É o `WHATSAPP_PHONE_NUMBER_ID`.

> É um número longo, **não** é o telefone. Se você anotou algo que começa com `+`, pegou o
> campo errado.

## Passo 5 — Cadastrar seu número como destinatário

Ainda na tela API Setup, no campo **To** / **Para**:

1. **Manage phone number list** → **Add phone number**
2. Informe seu número **com código do país**: `+55 11 9xxxx-xxxx`
3. A Meta manda um código pelo WhatsApp — digite para confirmar

📋 **Repita para cada pessoa que vai receber alertas** (até 5 no total).

> Sem essa verificação o envio falha. O número de teste só fala com quem foi confirmado.

## Passo 6 — Token permanente (não pule)

O botão **Gerar token de acesso** da tela "Etapa 1. Experimente" dá um token de **24 horas**.
Serve para o primeiro teste, mas o monitor pararia de alertar no dia seguinte.

### ⚠️ Por que você não vai achar isso no developers.facebook.com

**O token permanente fica em OUTRO SITE.** Esta é a confusão mais comum do processo inteiro:

| Site | Para quê |
|---|---|
| `developers.facebook.com` | criar o app, número de teste, token **temporário** |
| **`business.facebook.com`** | **usuários de sistema e token permanente** |

Não existe opção de token permanente no painel de desenvolvedores. Procurar lá é perder tempo.

### Link direto

O `business_id` aparece na URL do seu painel de desenvolvedores
(`...?business_id=SEU_ID`). Com ele, vá direto para:

```
https://business.facebook.com/settings/system-users?business_id=SEU_BUSINESS_ID
```

Sem o link direto: **business.facebook.com** → ícone de engrenagem (**Configurações**) →
menu esquerdo **Usuários** → **Usuários do sistema**.

> Se cair numa tela pedindo para "criar um portfólio empresarial", significa que você entrou
> com uma conta sem Business Manager. Volte ao painel de desenvolvedores, copie o
> `business_id` da URL e use o link direto acima.

### Criar o usuário de sistema

1. **Adicionar** (ou **Criar usuário de sistema**)
2. Nome: `flight-monitor` — é um robô, não uma pessoa
3. Função: **Administrador**
4. **Criar usuário de sistema**

> Um usuário de sistema é uma conta de serviço. O token fica preso a ele, e não à sua conta
> pessoal — então continua valendo mesmo se você trocar sua senha do Facebook.

### Dar acesso aos ativos

Com o usuário selecionado, clique em **Adicionar ativos**:

1. Aba **Apps** → marque seu app → habilite **Gerenciar app**
2. Aba **Contas do WhatsApp** → marque sua conta → habilite **controle total**
3. **Salvar alterações**

> **Na prática pode não ser necessário:** usuário de sistema com **Acesso de Admin** já
> enxerga todas as contas de WhatsApp do portfólio. Neste projeto o token funcionou com
> apenas o app atribuído. Se o envio falhar com erro de permissão, volte aqui e adicione a
> conta do WhatsApp.

### Gerar o token

1. Com o usuário selecionado na lista da **esquerda**, clique em **Gerar token** — no canto
   superior direito do painel da direita, ao lado de *"Anular tokens"*

   > O rótulo é **"Gerar token"**, e não "Gerar novo token". E os botões só aparecem depois de
   > selecionar o usuário na lista: com nenhum selecionado, o painel fica vazio.
2. Escolha o **app** na lista
3. Marque as duas permissões. **A lista tem dezenas de itens** — use o campo de busca e
   digite `whatsapp`:
   - `whatsapp_business_messaging` — enviar mensagens
   - `whatsapp_business_management` — gerenciar templates
4. Em **Expiração do token**, escolha **Nunca**
5. **Gerar token**

📋 **Copie na hora.** A Meta mostra o token **uma única vez**. Se fechar a janela, só resta
gerar outro.

### Conferir se o token permanente funciona

O jeito mais direto é perguntar à própria Meta quando ele expira:

```bash
curl -s "https://graph.facebook.com/v21.0/debug_token?input_token=NOVO_TOKEN&access_token=NOVO_TOKEN"
```

O que você quer ver:

```
"type": "SYSTEM_USER"        <- e nao "USER"
"expires_at": 0              <- zero significa NUNCA
"scopes": [whatsapp_business_management, whatsapp_business_messaging, ...]
```

Se `type` vier como `USER` e `expires_at` tiver uma data, você pegou o token temporário de
novo — ele fica na tela "Etapa 1. Experimente", não aqui. Aí é só substituir o
`WHATSAPP_ACCESS_TOKEN` no `.env`.

## Passo 7 — Criar o template do alerta

Sem isto, o sistema não consegue notificar (ver a explicação no início).

1. Acesse **business.facebook.com/wa/manage/message-templates**
2. **Criar template**
3. Categoria: **Utilidade** (Utility)
   > **Não** escolha Marketing: é mais caro e tem regra mais rígida.
   > Alerta que o próprio usuário pediu se enquadra como Utilidade.
4. Nome: `alerta_preco_voo`
5. Idioma: **Português (BR)**
6. No corpo, cole exatamente:

```
✈️ Encontrei uma passagem dentro do preço que você definiu no seu monitor de voos.

Trecho: {{1}}
Datas da viagem: {{2}}
Detalhes do voo: {{3}}

Preço encontrado: {{4}}
Limite que você configurou: {{5}}

Corra que promoção de passagem costuma durar pouco.
```

> ⚠️ **Duas armadilhas, ambas encontradas na prática:**
>
> **1. Não reduza o texto fixo nem acrescente variáveis.** A Meta recusa com *"a proporção
> entre palavras e parâmetros excede o limite"*. A primeira versão separava origem e destino
> em duas variáveis (6 no total) e foi rejeitada. Foram unidas em `{{1}}`.
>
> **2. Nada de linguagem promocional.** Ao encher o texto para satisfazer a regra acima, a
> primeira tentativa terminava com *"Corra que promoção de passagem costuma durar pouco"* —
> e a Meta **reclassificou o template de Utility para Marketing**, que custa mais e tem
> limites de entrega. Evite "corra", "promoção", "aproveite", "imperdível". Terminar
> explicando **por que** a pessoa recebe a mensagem é o que caracteriza Utility.
>
> **E o pior:** depois de apagar um template, a Meta **bloqueia recriar o mesmo nome com
> categoria diferente**. Foi preciso mudar o nome de `alerta_passagem` para
> `alerta_preco_voo`. Se cair nisso, use um nome novo em vez de esperar.

7. Preencha os exemplos que a Meta pede:

| Variável | Exemplo |
|---|---|
| `{{1}}` | `GRU para LIS` |
| `{{2}}` | `25/09/2026 a 08/10/2026` |
| `{{3}}` | `Air Europa, 1 escala` |
| `{{4}}` | `R$ 5.602,00` |
| `{{5}}` | `R$ 9.000,00` |

8. **Enviar para análise**

⏱️ A aprovação costuma sair em minutos, mas a Meta reserva até 48h.

### Atalho: criar o template por API

Dá para pular o formulário inteiro. Com as credenciais no `.env`:

```bash
curl -X POST "https://graph.facebook.com/v21.0/$WHATSAPP_WABA_ID/message_templates"   -H "Authorization: Bearer $WHATSAPP_ACCESS_TOKEN"   -H "Content-Type: application/json"   -d @docs/template-alerta.json
```

E para acompanhar a aprovação:

```bash
curl -s "https://graph.facebook.com/v21.0/$WHATSAPP_WABA_ID/message_templates?name=alerta_preco_voo"   -H "Authorization: Bearer $WHATSAPP_ACCESS_TOKEN"
```

> **Regras das variáveis:** não podem conter quebra de linha, tabulação, nem mais de 4 espaços
> seguidos. Por isso as datas vão numa linha só, em vez do formato de duas linhas que o
> sistema usa hoje no canal de log.

## Passo 8 — Colocar no projeto

No arquivo `.env` da raiz (que **não** vai para o Git):

```properties
WHATSAPP_PHONE_NUMBER_ID=123456789012345
WHATSAPP_ACCESS_TOKEN=EAAxxxxxxxxxxxxxxxxxx
```

Me avise quando tiver os dois — e diga se o template já foi aprovado.

---

## Conferindo antes de me chamar

Se quiser testar por conta própria, este comando manda o template `hello_world`, que já vem
aprovado:

```bash
curl -X POST "https://graph.facebook.com/v21.0/SEU_PHONE_NUMBER_ID/messages" \
  -H "Authorization: Bearer SEU_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "messaging_product": "whatsapp",
    "to": "5511999998888",
    "type": "template",
    "template": { "name": "hello_world", "language": { "code": "en_US" } }
  }'
```

O número em `to` vai **sem** o `+` e sem espaços.

Resposta esperada:

```json
{"messaging_product":"whatsapp","contacts":[...],"messages":[{"id":"wamid.HBg..."}]}
```

E a mensagem chega no seu WhatsApp em segundos.

### Erros comuns

| Mensagem | O que houve |
|---|---|
| `(#131030) Recipient phone number not in allowed list` | o número não foi verificado no passo 5 |
| `(#190) Access token has expired` | usou o token temporário de 24h; refaça o passo 6 |
| `(#132001) Template name does not exist` | template não aprovado ainda, ou nome/idioma diferente |
| `(#131047) Re-engagement message` | tentou texto livre fora da janela de 24h — é a regra do passo 7 |

---

## O que muda no projeto

O passo 7 tem uma consequência de código: o alerta deixa de ser **um texto** e passa a ser
**um template mais seis parâmetros**. Isso afeta o `AlertMessageFormatter`, que hoje monta uma
string única.

O canal `LOG` continua imprimindo o texto formatado — é bom justamente para conferir como a
mensagem fica antes de gastar envio. Ver a etapa E1.12 em [PLANO-DE-ACAO.md](PLANO-DE-ACAO.md).

## Custos

| Item | Custo |
|---|---|
| Número de teste | R$ 0 |
| Mensagens para os 5 destinatários de teste | R$ 0 |
| Token de sistema | R$ 0 |
| Template Utilidade | R$ 0 para criar |

Em produção, com número próprio, mensagem Utility no Brasil fica em torno de R$ 0,04–0,05.
No volume deste projeto — poucos alertas por mês — é irrelevante. Ver
[D-011](DECISOES.md).


---

# Passo 9 — Número brasileiro próprio (obrigatório para entregar no Brasil)

> 📖 **Versão detalhada, com a preparação do chip:**
> [GUIA-NUMERO-BRASILEIRO.md](GUIA-NUMERO-BRASILEIRO.md)

É a "Etapa 2. Configuração de produção" do painel, que os passos anteriores mandavam evitar.
Com a restrição do [BUG-007](BUGS.md), ela deixou de ser opcional.

## O que você precisa antes

**Um número de telefone brasileiro dedicado**, que:

- ❌ **não pode** ter WhatsApp ativo — nem o comum, nem o Business
- ❌ **não pode** ser o seu número pessoal: ao migrar para a Cloud API, o número **deixa de
  funcionar no aplicativo** do WhatsApp, para sempre
- ✅ pode ser um chip pré-pago barato, um número virtual, ou um fixo que receba chamada
  (a verificação aceita ligação, além de SMS)

> Se o número já tiver WhatsApp, é preciso **excluir a conta** dentro do app antes
> (Configurações → Conta → Excluir minha conta) e esperar alguns minutos.

## Os passos no painel

1. `developers.facebook.com` → seu app → **Casos de uso** → **Personalizar**
2. **Configuração básica** → **Etapa 2. Configuração de produção**
3. **Registre seu número de telefone do WhatsApp**
   - informe o número brasileiro com DDD
   - escolha **SMS** ou **ligação** para receber o código
   - defina um **PIN de 6 dígitos** — anote, ele é pedido em re-registros
4. **Adicione informações de pagamento**
   - cartão de crédito no gerenciador de negócios
   - sem isso, mensagens iniciadas pela empresa não saem
5. Pegue o **novo Phone Number ID** — ele **muda**; o antigo era do número de teste

## Atualizar o projeto

No `.env`:

```properties
WHATSAPP_PHONE_NUMBER_ID=<novo id do numero brasileiro>
WHATSAPP_ACCESS_TOKEN=<o mesmo token permanente, continua valendo>
NOTIFICATION_CHANNEL=WHATSAPP
```

**Nenhuma linha de código muda.** O canal está atrás de uma interface e as credenciais vêm de
configuração — foi para isso que a regra 3 da seção 3 do plano existiu desde o começo.

## O que continua valendo

| Item | Precisa refazer? |
|---|---|
| App e conta comercial | não |
| Token permanente do usuário de sistema | não |
| Template `alerta_preco_voo` | não — pertence à conta, não ao número |
| Lista de destinatários verificados | **não se aplica** — número de produção envia para qualquer um |

## Custo real

| Item | Valor |
|---|---|
| Chip pré-pago ou número virtual | ~R$ 10–30, uma vez |
| Registro na Meta | R$ 0 |
| Mensagem Utility, Brasil | centavos por envio |

Com poucos alertas por mês, o gasto fica na casa de **R$ 1–2 mensais**. A previsão original da
[D-011](DECISOES.md) sobre custo de mensagem continua certa; o que mudou foi a necessidade do
número próprio.

## Verificação de empresa é necessária?

Para começar, **não**. Contas não verificadas enviam para até 250 destinatários distintos por
dia — muito acima do que este projeto precisa. A verificação (Etapa 3) só interessa para
aumentar esse limite.

## Como saber que funcionou

Depois de trocar o `.env`, dispare uma varredura pelo painel e confira o alerta no banco:

```sql
SELECT status, attempts, error_message, provider_message_id FROM alert ORDER BY id DESC LIMIT 3;
```

E, principalmente, **confira o celular**. Se aparecer `SENT` mas nada chegar, volte ao
visualizador de webhooks — foi assim que o 130497 apareceu.
