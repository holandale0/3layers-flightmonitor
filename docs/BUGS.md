# Bugs e Problemas

> Registro de problemas encontrados durante desenvolvimento e testes.
> Um problema por entrada. Não apagar entradas resolvidas — o histórico é o valor.

## Como registrar

```markdown
### BUG-000 · Título curto do problema
**Status:** 🔴 aberto · 🟡 em investigação · ✅ resolvido · ⚪ não reproduz · 🔵 contornado
**Encontrado em:** AAAA-MM-DD · Etapa E0.0 · Componente: core-java / worker-python / frontend-vue / infra
**Severidade:** crítica / alta / média / baixa

**Sintoma:** o que se observou.

**Reprodução:** passos para reproduzir.

**Causa raiz:** o que estava realmente errado.

**Solução:** o que foi feito.
```

**Severidade:**
- **crítica** — sistema não funciona, alerta não é enviado, dado é perdido
- **alta** — funcionalidade principal quebrada, mas há contorno
- **média** — funcionalidade secundária ou degradação
- **baixa** — cosmético, log ruidoso, inconveniência

---

## Abertos

- [BUG-009](#bug-009--template-aprovado-mas-em-outra-conta-que-não-a-do-número-remetente) — template aprovado na WABA errada; nenhum alerta automático sai até ser recriado

---

## Resolvidos

### BUG-012 · A suíte esgotou as conexões do PostgreSQL
**Status:** ✅ **RESOLVIDO em 2026-08-12**
**Encontrado em:** 2026-08-12 · Etapa E3.2 · Componente: core-java (testes)
**Severidade:** alta — a suíte parou de rodar por inteiro

**Sintoma:** 11 testes de classes que não tinham sido tocadas passaram a errar de uma vez:

```
FATAL: sorry, too many clients already
Unable to obtain connection from database
```

**Causa raiz: cada configuração distinta cria um contexto Spring próprio.** A suíte foi
acumulando classes com `@DynamicPropertySource` e `@TestPropertySource` — `MotorE2ETest`,
`E2EServicosTest`, `WhatsAppWebhookTest`, `AgentApiTest`, `AgentCriacaoTest` — e o Spring mantém
**todos** os contextos em cache até o fim da execução, cada um com seu pool de 10 conexões.

Sete contextos passam de 70 conexões; somando a aplicação rodando em desenvolvimento e o limite
padrão de 100 do PostgreSQL, a suíte bateu no teto.

**Por que apareceu agora:** foi crescendo em silêncio. Cada etapa que adicionou uma classe de
teste com configuração própria consumiu mais 10, e a E3.2 foi a que passou do limite. O erro
aponta para o banco e não tem relação nenhuma com o teste que falha — o tipo de sintoma que
manda o desenvolvedor investigar o lugar errado.

**Solução:** pool de **2** conexões no `application.properties` de teste, com timeout de 5s. Os
testes rodam em uma thread só; dez conexões por contexto sempre foram desperdício.

**O que NÃO foi feito:** aumentar o `max_connections` do PostgreSQL. Isso esconderia o
desperdício e adiaria o mesmo problema para a próxima dezena de contextos.

**Vale saber, para a próxima:** o número de contextos é o que cresce, e ele cresce com o
projeto. Cada `@TestPropertySource` novo é um contexto novo — reaproveitar a mesma configuração
entre classes de teste mantém a suíte rápida **e** econômica.

---

### BUG-011 · Coleção LAZY na varredura — o BUG-006 de novo, do outro lado
**Status:** ✅ **RESOLVIDO em 2026-08-12** — pego pelo E2E antes de sair do commit
**Encontrado em:** 2026-08-12 · Etapa E2.6 · Componente: core-java
**Severidade:** crítica — derrubaria **toda** varredura de monitor com preferência

**Sintoma:** ao adicionar o filtro de companhias evitadas, 9 dos 13 testes do `MotorE2ETest`
quebraram de uma vez:

```
LazyInitializationException: Cannot lazily initialize collection of role
'com.flightmonitor.core.monitor.Monitor.avoidedAirlines' with key '2572' (no session)
```

**Causa raiz:** a varredura roda **fora de transação**, de propósito — a chamada HTTP ao worker
pode levar dezenas de segundos, e prender conexão do pool durante isso a esgotaria
([D-034](DECISOES.md)). O monitor chega desanexado ao filtro, e ler uma coleção `LAZY` ali
estoura.

**É o BUG-006 outra vez, do lado da busca.** Lá era o telefone do destinatário, lido pelo canal
de entrega fora de transação; aqui é a lista de companhias, lida pelo filtro de candidatos. A
mesma armadilha, em dois lugares que não se parecem — porque a causa não é o campo, é a regra
arquitetural de não segurar transação durante chamada externa.

**Solução, em duas camadas:**

1. `findByIdComPreferencias`, com `left join fetch`, traz a coleção enquanto ainda há sessão —
   o mesmo padrão do `findByIdParaEntrega` criado para o BUG-006;
2. o filtro **não confia** em sessão aberta: se a leitura falhar, ele registra aviso e segue com
   lista vazia. Cobre quem chamar `varrer(Monitor)` com entidade desanexada — um teste, ou um
   caminho novo escrito daqui a seis meses sem lembrar da restrição. **Falhar a varredura
   inteira por causa do filtro de preferência seria trocar uma oportunidade real por uma
   conveniência.**

**O que fez a diferença:** o `MotorE2ETest` da E1.15. Os testes unitários do filtro passavam
todos — eles montam a entidade em memória, onde coleção nenhuma é lazy. Só o teste que percorre
o fluxo inteiro, com o monitor vindo do banco e a varredura fora de transação, reproduz a
condição. É exatamente o argumento que justificou aquela etapa.

---

### ~~BUG-016 · Monitor de somente ida recebia preço de ida e volta~~ — ✅ FECHADO
**Status:** ✅ **código corrigido em 2026-08-16** — o provider passou a perguntar pelo produto certo
**Encontrado em:** 2026-08-16 · reportado pelo usuário · Componente: worker-python (camada 1)
**Severidade:** **alta** — dado errado no histórico, e o sistema cego para as oportunidades que
deveria achar

**Sintoma relatado:** um monitor GRU → BEL configurado como **somente ida** exibia observações com
a coluna *Volta* preenchida (14/12/2026).

**Causa:** o filtro da camada 1 era **assimétrico**:

```python
if req.return_from is not None:      # tem janela de volta -> confere
    ...
# sem janela de volta -> NAO CONFERE NADA
```

"Somente ida" é expresso como **ausência** de janela de volta, e o código lia essa ausência como
*"tanto faz"* em vez de *"não pode ter volta"*.

**O dano não era cosmético.** Testado contra a API real em 16/08/2026, GRU → BEL em dezembro:

| Consulta | Preço |
|---|---|
| ida e volta (o que era gravado) | R$ 1.674 |
| **só ida** (o que devia ser gravado) | **R$ 1.004** |

Um monitor de só ida com teto de R$ 1.100 **nunca teria alertado**, porque comparava R$ 1.674 com
o teto. O sistema estava cego para as oportunidades que existia para achar.

**A investigação teve uma hipótese errada no meio.** Consultei a API e vi `return_date: None` em
todas as entradas, e cheguei a concluir que a data de volta era *inventada pelo nosso código*.
Estava conferindo a chave errada: o endpoint usa **`return_at`**, e não `return_date`. Com a chave
certa, todas as 5 entradas tinham volta. Vale registrar: a hipótese mais dramática ("o código
inventa dados") era falsa, e só não virou correção errada porque foi testada.

**Por que não bastou filtrar:** o `v1/prices/calendar` **ignora `one_way`**. Foi testado com
`one_way=true`, `one_way=1` e sem o parâmetro — nas três, todas as entradas voltam com `return_at`.
Filtrar deixaria o monitor de só ida **sem nenhum preço**, trocando dado errado por dado nenhum.

**Correção:** o provider escolhe o endpoint conforme a pergunta — `v2/prices/latest` com
`one_way=true` para somente ida, `v1/prices/calendar` para ida e volta ([D-107](DECISOES.md)). E o
filtro de só ida descarta oferta com volta mesmo assim: o bug nasceu de confiar numa promessa da
fonte sem conferir.

**Um segundo defeito no mesmo lugar:** `arrival_at` recebia `return_at` — ou seja, o horário de
**partida da volta** era gravado como **chegada da ida**. O endpoint não devolve horário de
chegada; agora o campo fica nulo, que é a verdade.

**Os testes existentes codificavam o bug.** O helper `pedido()` montava requisição **sem** janela
de volta e alimentava com dados de ida e volta — exercitando o calendário por um caminho de só
ida. O comportamento que eles descreviam continua válido; errada era a pergunta que chegava até
ele. O helper ganhou janela de volta, e nasceu o `test_so_ida.py`.

**✅ Os dados gravados foram zerados** (16/08/2026, a pedido do usuário). Apagados
`price_observation` (201), `search_run` (153) e `alert` (5); preservados monitores, destinatários,
vínculos e preferências. Os monitores voltaram a `last_searched_at = NULL`.

O `alert` entrou na limpeza por um motivo prático: o anti-spam lê essa tabela, e alertas antigos
silenciariam por 12h o primeiro alerta válido depois do reset.

**Verificado com dado novo:** a primeira varredura do GRU → BEL gravou R$ 1.004 e R$ 1.064,
**sem data de volta e sem horário de chegada inventado** — e corretamente sem alerta, porque
R$ 1.004 está acima do teto de R$ 500.

---

### ~~BUG-015 · Campo nulo sumia do JSON e virava `undefined` no painel~~ — ✅ FECHADO
**Status:** ✅ **fechado em 2026-08-14** — a API passou a enviar `null` explícito, e o painel
ficou defensivo
**Encontrado em:** 2026-08-14 · reportado pelo usuário · Componente: core-java (serialização) + frontend
**Severidade:** média no sintoma visível, **alta no invisível**

**Sintoma relatado:** um monitor CGH → BEL exibia **"até `undefined` escala"** no cartão.

**O relato dizia "o destino apareceu como undefined", e o destino estava correto** — o cabeçalho
mostrava `CGH → BEL`. O `undefined` estava no campo de **escalas máximas**, uma linha abaixo.
Vale registrar porque procurar o bug no lugar apontado teria custado tempo: o valor errado
raramente está no campo que parece.

**Causa:** `spring.jackson.default-property-inclusion: non_null` fazia a API **omitir** as
propriedades nulas. Um monitor sem limite de escalas chegava ao painel **sem a chave**
`maxStops`:

```json
{ "origin": "CGH", "destination": "BEL", "maxPrice": 700 }
```

O tipo do frontend sempre declarou `maxStops: number | null` — **era a API que mentia**. Em
JavaScript campo ausente é `undefined`, e `undefined === null` é `false`:

```js
function escalas(max: number | null) {
  if (max === null) return 'escalas livres'   // undefined nao entra aqui
  return `ate ${max} escala`                  // "ate undefined escala"
}
```

**O sintoma invisível era pior.** `paraRequest` copiava os campos crus para o formulário de
edição, e o `MonitorForm` fazia `comVolta = form.returnWindowStart !== null`. Com `undefined`,
isso dava **`true`**: abrir para editar um monitor de somente ida marcava "definir janela de
volta", e o `watch` preenchia as datas sozinho. **Editar e salvar converteria o monitor em ida e
volta**, sem ninguém pedir.

**Correção, em duas frentes:**

1. **A causa raiz** — `default-property-inclusion: always`. Campo ausente é ambíguo (não
   existe? não se aplica? esqueceram?); `null` explícito diz uma coisa só. É o mesmo princípio
   que o projeto aplica em toda parte — `SEM_DADOS ≠ NORMAL`, nulo ≠ zero — agora no formato do
   fio. O ganho de bytes nunca pagou o custo de o cliente adivinhar.
2. **O painel ficou defensivo assim mesmo** — `?? null` em `paraRequest` e `== null` em
   `escalas`. Depender de o servidor ser perfeito é a metade frágil de qualquer correção.

`??` e não `||`: com `||`, `maxStops: 0` — que significa **voo direto** — viraria nulo, que
significa "sem preferência". São coisas opostas, e há teste para isso.

**Verificado:** o `ContratoJsonTest` reprova com a configuração antiga (4 de 4), citando o bug na
mensagem. A asserção usa `has(campo)` e não `get(campo)` — `get` devolveria null nos dois casos e
passaria com o bug presente.

**A lição:** o tipo do cliente e o formato do servidor são um contrato, e nada estava verificando
que as duas pontas concordavam. Agora estão.

---

### ~~BUG-014 · A camada 2 inteira estava comentada no requirements.txt~~ — ✅ FECHADO
**Status:** ✅ **fechado em 2026-08-13** — declarada, e guardada por teste
**Encontrado em:** 2026-08-13 · Etapa E4.2 · Componente: worker-python (dependências)
**Existiu de:** E1.6 até E4.2 — **seis semanas**
**Severidade:** crítica — o sistema para de alertar, em silêncio

**Sintoma:** com o worker em container, toda busca voltava
`confirmada=false, camada2Degradada=true`, e o sistema — corretamente — se recusava a alertar com
preço não confirmado. Nenhum erro. Nenhum log vermelho. O monitor simplesmente **deixava de
avisar**.

**Causa:** `fast-flights==3.0.2` estava **comentada** no `requirements.txt`:

```
# Camada 2 da coleta — descomentar na etapa E1.6
# fast-flights==3.0.2
```

O comentário dizia "descomentar na etapa E1.6". A E1.6 foi feita, a biblioteca foi instalada **na
venv local**, e a linha nunca foi descomentada.

**Por que ninguém percebeu por seis semanas:** na máquina de quem desenvolve funcionava. O arquivo
que descreve o ambiente mentia, e nada comparava os dois. Qualquer pessoa clonando o repositório e
rodando `pip install -r requirements.txt` teria um worker sem camada 2.

**Como apareceu:** ao subir o worker em container — o primeiro ambiente construído **só** a partir
do que está declarado. É exatamente para isso que a dockerização serve, e ela cobrou na primeira
tentativa.

**O modo de falha é o que assusta.** Não houve `ImportError` no boot: a cadeia de providers trata
provider ausente como degradação, que é o comportamento certo para uma fonte frágil
([RISCO-002](BUGS.md)). A degradação projetada para "o Google mudou o formato" cobriu também
"a biblioteca não existe" — e as duas são muito diferentes.

**Correção:** linha descomentada, e criado o `tests/test_dependencias.py`, que percorre os imports
do `app/` por AST e exige que cada pacote de terceiro esteja declarado.

**O guarda achou um segundo caso na primeira execução:** `anyio` é importado direto pelo gateway da
camada 2 e nunca foi declarado — vinha de carona pelo FastAPI. Funcionava por acidente, e quebraria
no dia em que o Starlette trocasse de biblioteca.

---

### ~~BUG-013 · Bloco `email:` aninhado sob `management:` no application.yml~~ — ✅ FECHADO
**Status:** ✅ **fechado em 2026-08-13** — bloco movido para sob `flightmonitor:` e guardado por teste
**Encontrado em:** 2026-08-13 · Etapa E4.6 · Componente: core-java (configuração)
**Severidade:** crítica — **nenhum** e-mail sairia, e a suíte ficava verde

**Sintoma:** com `NOTIFICATION_CHANNEL=EMAIL` e `MAIL_FROM` preenchido no `.env`, a aplicação
subia dizendo:

```
WARN EmailNotificationChannel : canal EMAIL sem remetente configurado: defina MAIL_FROM no .env
INFO NotificationService      : canais disponiveis: [WHATSAPP, EMAIL, LOG]; ativo: EMAIL
```

Todo envio seria recusado com **falha permanente** — sem retentativa, e sem chegar ao SMTP.

**Causa:** ao acrescentar o bloco `email:` no `application.yml`, ancorei a inserção no texto
`logging:` sem verificar o que vinha **antes** dele. Entre `flightmonitor:` e `logging:` existem
`server:` e `management:`. O bloco, com dois espaços de indentação, caiu sob `management:`:

```yaml
management:
  endpoints: ...
  endpoint: ...
  email:                        # <- aqui
    remetente: ${MAIL_FROM:}
```

A propriedade virou `management.email.remetente`. O YAML é válido, o Spring não reclama de chave
desconhecida, e `EmailProperties` liga com nulos.

**Por que a suíte não pegou:** `EmailNotificationChannelTest` constrói `EmailProperties` na mão,
para testar o canal sem subir o Spring. Isso é ótimo para comportamento e **cego para
configuração** — nunca passa pelo YAML.

**É o [BUG-008](BUGS.md) de novo, no mesmo formato:** configuração silenciosamente errada, testes
verdes, e a falha só aparecendo no primeiro uso real. Lá era um `application.yml` apontando para
um template do WhatsApp que não existia mais.

**Correção:** bloco movido para sob `flightmonitor:`, logo após `whatsapp:`, e criado o
`ConfiguracaoDoEmailTest` — o análogo do `TemplateDoWhatsAppTest` que nasceu do BUG-008. Ele lê a
configuração **como o Spring a carrega**, e não como o arquivo parece.

**Verificado:** desfiz a correção de propósito e o teste reprovou, com a mensagem apontando a
causa (*"o bloco email: provavelmente está aninhado na chave errada"*). Só então restaurei.

**A lição que se repete:** teste que constrói a configuração na mão não verifica configuração.
Todo bloco novo de `application.yml` precisa de alguém que o leia pelo `Environment`.

---

### BUG-010 · Execução de varredura terminava "antes" de começar — dois relógios diferentes
**Status:** ✅ **RESOLVIDO em 2026-08-12**
**Encontrado em:** 2026-08-12 · Etapa E2.2 (apareceu por acaso) · Componente: core-java
**Severidade:** alta — derrubaria varreduras rápidas em produção, de forma intermitente

**Sintoma:** o `PersistenciaTest.cicloDeVidaDaExecucao` falhou de repente, sem nenhuma mudança
relacionada a ele:

```
ERROR: new row for relation "search_run" violates check constraint "search_run_fim_apos_inicio"
Failing row contains (863, 1792, TRAVELPAYOUTS, SUCCESS,
                      2026-08-12 15:37:20.689554-03,   <- started_at
                      2026-08-12 15:37:20.689276-03,   <- finished_at
                      30, null)
```

O `finished_at` ficou **278 microssegundos antes** do `started_at`.

**Causa raiz: os dois campos vinham de relógios diferentes.**

| Campo | Origem |
|---|---|
| `started_at` | `clock_timestamp()` do PostgreSQL, via `DEFAULT` — relógio do **container** |
| `finished_at` | `Instant.now()` no Java — relógio do **host** |

Basta o relógio do container estar alguns microssegundos adiantado para uma execução rápida
terminar "antes" de começar. O próprio payload da falha é a prova da direção do desvio: o
carimbo do banco saiu depois do carimbo da JVM, com a operação inteira acontecendo no meio.

**Por que só apareceu agora:** o teste não faz trabalho real, então o intervalo entre abrir e
concluir a execução é praticamente zero. Em varredura de verdade, a chamada HTTP consome
centenas de milissegundos e esconde o desvio — mas não sempre. O caminho *"a camada 2 consultou
e o voo não existe"* também fecha em poucos milissegundos.

**Consequência em produção:** `concluirExecucao` faz `saveAndFlush`, então a violação viraria
`DataIntegrityViolationException` dentro do `PriceSearchService` e **abortaria a varredura** —
de forma intermitente, dependendo do relógio. O tipo de falha que se atribui a "coisa do
ambiente" e se perde tempo perseguindo.

**Solução:** `started_at` passou a ser preenchido pelo **Java**, no construtor do `SearchRun`.
Os dois carimbos passam a vir do mesmo relógio, e a diferença entre eles deixa de depender de
sincronia entre máquinas.

O `DEFAULT clock_timestamp()` continua no schema, para uma linha inserida fora da aplicação. A
lição do [BUG-002](BUGS.md) segue respeitada: continua sendo um instante de evento, lido no
momento em que o evento acontece, e não o início da transação.

**O que NÃO foi feito, e por quê:** afrouxar o `CHECK` com uma tolerância, como a migração V4
fez para o webhook. Lá a tolerância é inevitável — o timestamp vem da Meta, com granularidade de
segundos, e não há como alinhar relógios com um terceiro. Aqui os dois carimbos são nossos, e
alinhá-los é a correção de verdade. Afrouxar seria remover o guarda que encontrou o defeito.

---

### ~~BUG-009 · Template aprovado, mas em outra conta que não a do número remetente~~ — ✅ FECHADO
**Status:** ✅ **fechado em 2026-08-13** — template `alerta_preco_voo` aprovado na WABA de produção
e validado com envio real (alerta `1728`, `wamid.HBgNNTUxMTk1MDU3NzI4Mh…`, entrega confirmada
pelo usuário)
**Encontrado em:** 2026-08-12 · Etapa E1.12 · Componente: externo (Meta) + configuração
**Severidade:** crítica — nenhum alerta automático sai enquanto durar

**Sintoma:** o primeiro alerta automático de verdade falhou com
`HTTP 404, código 132001 — template não encontrado`, **apesar** de o template
`alerta_preco_voo` constar como `APPROVED`, `pt_BR`, na Graph API.

**Reprodução:** com `NOTIFICATION_CHANNEL=WHATSAPP`, disparar
`POST /api/monitors/{id}/search` num monitor com oportunidade confirmada. A mesma chamada
reproduzida com `curl` devolve a mensagem completa, que a nossa camada resumia:

```
"(#132001) Template name does not exist in the translation"
details: "template name (alerta_preco_voo) does not exist in pt_BR"
```

**Causa raiz: template pertence a uma conta (WABA), não ao app nem ao negócio.**

| Ativo | Conta |
|---|---|
| Templates, incluindo `alerta_preco_voo` | WABA `<WABA_TESTE>` — *"Test WhatsApp Business Account"* |
| Número de teste `<NUMERO_DE_TESTE_META>` | a **mesma** WABA |
| Número brasileiro `<NUMERO_REMETENTE>` (o que usamos) | uma WABA **diferente**, criada no registro de produção |

Consultando `GET /{WABA}/phone_numbers`, a conta que tem os templates contém **só o número de
teste**. O número brasileiro está `CONNECTED` e `account_mode: LIVE`, mas em outra conta — e
para ele o template simplesmente não existe.

**O que NÃO é explicado por isto — e eu errei ao dizer que era.** Cheguei a escrever aqui que
a recusa do `hello_world` no número novo tinha a mesma causa. **Verifiquei, e está errado.** O
`hello_world` existe na WABA de produção, `APPROVED` em `en_US`, e mesmo assim a Meta recusa:

```
(#131058) Hello World templates can only be sent from the Public Test Numbers
```

O diagnóstico original estava certo: template de exemplo é restrito a número de teste, por
regra própria, independente de WABA. São dois bloqueios distintos que apareceram juntos.

**Por que nenhum teste pegou:** o `TemplateDoWhatsAppTest` (criado horas antes, para o
[BUG-008](BUGS.md)) confere nome, idioma e quantidade de parâmetros contra o arquivo
versionado. Nada disso estava errado. A relação "este template pertence à conta deste número"
só existe no servidor da Meta — **nenhum teste offline poderia detectar**. É exatamente o tipo
de falha que justifica o canário ao vivo da E4.5.

**Solução — em andamento:**
1. ✅ **WABA de produção identificada:** `<WABA_PRODUCAO>`. Veio do `asset_id` na URL do
   Gerenciador do WhatsApp; confirmada por `GET /{WABA}/phone_numbers`, que devolve o
   `<NUMERO_REMETENTE>` e mais nenhum;
2. ✅ **`alerta_preco_voo` recriado nessa conta** a partir do
   [template-alerta.json](template-alerta.json) versionado, sem edição, via
   `POST /{WABA}/message_templates`. Resposta: `id <TEMPLATE_ID>`, `PENDING`, `UTILITY` —
   a categoria foi aceita de primeira desta vez;
3. ✅ `WHATSAPP_WABA_ID` atualizado no `.env`;
4. ⏳ aguardando aprovação. Quando sair, `NOTIFICATION_CHANNEL` volta a `WHATSAPP` e o alerta
   automático é disparado de novo — nada no código muda.

**Mitigação imediata:** `NOTIFICATION_CHANNEL` voltou a `LOG`. Deixá-lo em `WHATSAPP` faria
todo alerta ir a `FAILED` na primeira tentativa — é falha permanente ([D-050](DECISOES.md)),
não há retentativa.

**Melhoria aplicada:** a mensagem de erro do código 132001 passou a citar a causa menos óbvia
primeiro — *"confira se ele foi aprovado na mesma conta (WABA) do remetente"*. A mensagem
antiga mandava conferir nome e idioma, que estavam certos, e apontava para o lugar errado.

---

### BUG-008 · O `application.yml` apontava para um template do WhatsApp que não existe mais
**Status:** ✅ **RESOLVIDO em 2026-08-12** — encontrado antes de causar dano
**Encontrado em:** 2026-08-12 · Etapa E1.12 · Componente: core-java
**Severidade:** crítica — teria feito **todo** alerta falhar

**Sintoma:** nenhum, e é justamente esse o problema. A suíte inteira passava, 139 testes verdes,
com a aplicação configurada para enviar um template que a Meta não conhece.

**Como apareceu:** o template `alerta_preco_voo` foi aprovado. Antes de trocar o
`NOTIFICATION_CHANNEL` para `WHATSAPP`, conferi qual nome a aplicação realmente enviaria — e o
`application.yml` dizia `alerta_passagem`, o nome **antigo**, o que a Meta classificou como
MARKETING e que foi apagado.

**Causa raiz:** duas fontes de verdade para o mesmo valor. O nome novo foi corrigido em
`WhatsAppProperties`, mas ali ele é apenas o **default de campo vazio**:

```java
templateName = vazio(templateName) ? "alerta_preco_voo" : templateName;
```

O `application.yml` fornecia um valor, então o default nunca entrava em ação. E nenhum teste
percebeu porque o `WhatsAppChannelTest` monta `WhatsAppProperties` **a mão**, com o nome certo
escrito no próprio teste — ele validava a intenção do teste, não a configuração da aplicação.

**Consequência que teria acontecido:** ao trocar o canal, cada alerta receberia
`132001 — template não encontrado`. Como é falha **permanente** (D-050), nem retentativa
haveria: `FAILED` na primeira tentativa, silenciosamente, para todos.

**Solução:**
1. `application.yml` passou a `${WHATSAPP_TEMPLATE_NAME:alerta_preco_voo}`, com o motivo escrito
   ao lado;
2. novo `TemplateDoWhatsAppTest` amarra a configuração **carregada pelo Spring** ao template
   versionado em `docs/template-alerta.json`: mesmo nome, mesmo idioma, mesma quantidade de
   parâmetros, e nenhum parâmetro violando as regras da Meta.

**Verificado que o teste pega o erro**, e não só que ele passa:

```
mvn test -Dtest=TemplateDoWhatsAppTest -DargLine="-Dflightmonitor.whatsapp.template-name=alerta_passagem"
  expected: "alerta_preco_voo"
   but was: "alerta_passagem"
```

**Lição:** teste que constrói o objeto de configuração a mão não testa a configuração. Sempre
que um valor existir em dois lugares — YAML e default de código —, algum teste tem que ler o
valor **como a aplicação lê**.

---

### BUG-007 · Mensagens aceitas pela Meta nunca chegavam — restrição por país
**Status:** ✅ **RESOLVIDO e confirmado em 2026-08-12** — entrega BR→BR funcionando
**Encontrado em:** 2026-08-11 · Etapa E1.12 · Componente: WhatsApp / externo
**Severidade:** crítica

**Sintoma:** quatro mensagens (duas `hello_world`, uma com token permanente, uma de texto
livre) foram aceitas pela Graph API com `wamid` e **nenhuma chegou** ao destinatário.

**Diagnóstico — o que foi eliminado, em ordem:**

| Hipótese | Como foi descartada |
|---|---|
| Número fora da lista de permitidos | teste diferencial: número aleatório deu `131030`, o do usuário passou |
| Credenciais insuficientes | token permanente `SYSTEM_USER` envia normalmente |
| Remetente desconectado | `status: CONNECTED`, `platform_type: CLOUD_API` |
| Entrega ao destinatário não funciona | o código de verificação da Meta chegou pelo WhatsApp |
| Restrição de template | texto livre na janela de 24h falhou igual |
| Canal inverso quebrado | mensagem do usuário chegou ao número de teste |

**Causa raiz**, revelada pelo visualizador de webhooks do painel:

```json
"status": "failed",
"errors": [{ "code": 130497,
  "title": "Business account is restricted from messaging users in this country." }]
```

Desde 15/09, a Meta **bloqueia mensagens entre países envolvendo Brasil e Indonésia**:
empresas fora do Brasil não podem mandar para usuários no Brasil, e empresas no Brasil só
podem mandar para usuários no Brasil.

O número de teste da Meta é **americano** (+1 555). O destinatário está no **Brasil**. É
exatamente o caso bloqueado — e a documentação indica que a restrição **persiste mesmo após
completar o caminho de escalonamento**.

**Por que `wamid` enganou:** a Graph API aceita a mensagem na fila e só aplica a restrição na
entrega. `accepted` + `wamid` não são comprovante de nada.

**Solução:** número brasileiro próprio, tornando o envio BR→BR. Ver
[GUIA-NUMERO-BRASILEIRO.md](GUIA-NUMERO-BRASILEIRO.md). Nenhuma linha de código muda — só
credenciais.

**Confirmado em 2026-08-12.** Com o número `<NUMERO_REMETENTE>`
(`status: CONNECTED`, `platform_type: CLOUD_API`), a mensagem **chegou ao destinatário**.
Mesma conta, mesmo token, mesmo destinatário, mesmo formato de mensagem — só mudou o país do
remetente. Diagnóstico confirmado por experimento controlado.

**Pendências do chip que valem registrar:**
- O código de verificação por SMS **não chegou** em duas tentativas. A linha havia sido
  ativada minutos antes, e SMS internacional de código curto é filtrado por operadora
  brasileira. Resolveu-se sozinho depois que a linha terminou de provisionar
- **Não houve etapa de PIN**: o painel fez o registro na Cloud API automaticamente. O PIN só
  é necessário quando isso não acontece — e mesmo aí, é um valor que se *define*, não que se
  recupera

**Lição de método:** sem o webhook de status estaríamos até hoje adivinhando. Seis hipóteses
plausíveis foram descartadas por experimento antes de o payload dar a resposta em uma linha.
O visualizador embutido no painel evitou montar túnel e endpoint público.

---

### BUG-006 · Canal de entrega estourava LazyInitializationException
**Status:** ✅ resolvido
**Encontrado em:** 2026-08-10 · Etapa E1.11 · Componente: core-java
**Severidade:** crítica

**Sintoma:** alertas do canal `LOG` ficavam eternamente em `PENDING`. O despachante os
reivindicava, tentava entregar, e eles voltavam para a fila — em laço.

**Reprodução:** criar um alerta com canal `LOG` e chamar `despacharPendentes()`.

**Causa raiz:** o alerta era carregado com `findById` dentro de uma transação curta, que
commitava antes do envio — porque o envio acontece **fora** de transação, por causa da
[D-034](DECISOES.md). A entidade chegava ao canal **desanexada**, e o canal lê
`alerta.getRecipient().getPhoneE164()`, que é uma associação LAZY. Resultado:
`LazyInitializationException`.

Como o despachante captura exceção do canal e a classifica como falha **transitória**, o
alerta voltava a `PENDING` e era retentado para sempre.

**Por que era crítico:** o canal WhatsApp da etapa E1.12 leria exatamente o mesmo campo — o
telefone do destinatário. O bug atingiria **todos** os alertas em produção, e o sintoma seria
"o sistema não me avisa" com o banco cheio de pendências silenciosas.

**Por que quase passou:** o teste do canal falso passava, porque o dublê só lia `getId()` —
um campo já carregado. Só o teste do canal **real** expôs o problema.

**Solução:** `findByIdParaEntrega` com `join fetch` de destinatário, monitor e observação.
Tudo que o canal precisa ler vem carregado antes de a transação fechar.

**Lição:** dublê de teste que não exercita os mesmos acessos do objeto real dá falsa
segurança. O teste do canal de log — o "menos importante" — foi o que pegou o defeito.

---

### BUG-005 · Varredura manual encontrava oportunidade e nunca alertava
**Status:** ✅ resolvido
**Encontrado em:** 2026-08-09 · Etapa E1.10 · Componente: core-java
**Severidade:** alta

**Sintoma:** `POST /api/monitors/{id}/search` devolvia `confirmada: true` com preço abaixo do
teto, mas nenhum alerta era criado e nada aparecia no log.

**Reprodução:** criar monitor com destinatário e disparar a varredura pelo endpoint. Pelo
scheduler, o mesmo monitor alertava normalmente.

**Causa raiz:** dois caminhos para a mesma operação. O endpoint chamava
`PriceSearchService.varrer()` direto; o scheduler chamava varredura **e** avaliação de alerta.
Ao ligar o `AlertService` na etapa E1.10, só o caminho do scheduler foi atualizado.

**Por que os testes não pegaram:** cada caminho tinha teste próprio e ambos passavam — o do
scheduler cobria o alerta, o do endpoint cobria só a varredura. Nenhum teste comparava os dois
comportamentos entre si. Apareceu no teste manual ao vivo.

**Solução:** `SearchCycleService.processarMonitor(id)` virou o **único** ponto de entrada,
usado pelo scheduler e pelo controller. O endpoint agora devolve `MonitorRunResult`, com a
busca e a decisão de alerta juntas.

**Lição:** quando dois caminhos deveriam fazer a mesma coisa, o jeito de garantir não é
lembrar de atualizar os dois — é não ter dois.

---

### BUG-004 · Cliente HTTP do Java negociava HTTP/2 contra um servidor HTTP/1.1
**Status:** ✅ resolvido
**Encontrado em:** 2026-08-09 · Etapa E1.7 · Componente: core-java
**Severidade:** alta

**Sintoma:** 11 dos 15 testes de contrato falhavam com erros de I/O aparentemente aleatórios:

```
I/O error on POST request: EOF reached while reading
I/O error on POST request: Received RST_STREAM: Stream cancelled
I/O error on POST request: null
```

O padrão inconsistente das mensagens sugeria problema de rede ou de teste — não de código.

**Reprodução:** qualquer POST do `RestClient` construído sobre `HttpClient.newBuilder()`
contra o worker (ou contra o WireMock).

**Causa raiz:** o `HttpClient` da JDK **negocia HTTP/2 por padrão**. O stack trace entregou a
pista — `jdk.internal.net.http.Http2Connection`. O worker roda em uvicorn, que fala apenas
HTTP/1.1, e o WireMock também. A negociação falhava, ora cortando a conexão com `RST_STREAM`,
ora resultando em EOF.

**Por que era grave e não apenas um problema de teste:** o mesmo cliente é usado em produção
contra o mesmo uvicorn. Cada requisição pagaria uma negociação fadada a falhar, e o
comportamento seria **intermitente** — o pior tipo de defeito, porque funcionaria em teste
manual e falharia sob carga.

**Solução:** declarar a versão explicitamente no `WorkerClientConfig`:

```java
HttpClient.newBuilder()
        .connectTimeout(props.connectTimeout())
        .version(HttpClient.Version.HTTP_1_1)   // uvicorn nao fala HTTP/2
        .build();
```

**Lição:** o padrão de uma biblioteca não é necessariamente o padrão do seu ambiente. Ao
montar cliente HTTP à mão, declarar a versão do protocolo em vez de herdar o default.

---

### BUG-003 · Processo do Vite sobrevive ao encerramento da tarefa
**Status:** 🔵 contornado
**Encontrado em:** 2026-08-09 · Etapa E1.3 · Componente: infra / ambiente
**Severidade:** média

**Sintoma:** ao subir o Vite para validar o novo proxy, ele falhou com
`Port 5173 is already in use` — mas a porta respondia HTTP 200 normalmente.

**Causa raiz:** o `npm run dev` da etapa E0.4 havia sido encerrado horas antes, porém apenas
o processo pai foi morto. O `node` filho continuou vivo, servindo a **configuração antiga**
do proxy (a versão com reescrita de caminho).

**Por que era perigoso:** sem o `strictPort: true`, o Vite teria subido em 5174 em silêncio,
e o teste do proxy novo teria sido feito contra o servidor antigo — validando uma
configuração que não era a do código. O teste passaria e a conclusão estaria errada.

**Solução:** matar o processo `node` órfão pelo PID e reiniciar. Ao encerrar serviços, passou
a ser rotina verificar a porta com `Get-NetTCPConnection`, e não confiar apenas no
encerramento da tarefa.

**Lição:** o `strictPort: true` escolhido na E0.4 pagou por si mesmo. Falhar alto é melhor
que se adaptar em silêncio.

---

### BUG-002 · `observed_at` idêntico para observações da mesma transação
**Status:** ✅ resolvido
**Encontrado em:** 2026-08-09 · Etapa E1.2 · Componente: core-java / banco
**Severidade:** alta

**Sintoma:** o teste `ultimoPrecoDaData` falhou. Duas observações de preço gravadas para a
mesma data devolviam a mais antiga como sendo a mais recente.

**Reprodução:** gravar duas `price_observation` para o mesmo `(monitor, departure_date,
return_date)` dentro de uma transação e consultar ordenando por `observed_at DESC`.

**Causa raiz:** `observed_at` usava `DEFAULT now()`. No PostgreSQL, `now()` é sinônimo de
`transaction_timestamp()` — devolve o horário de **início da transação**, não o horário
atual. Todas as linhas gravadas na mesma transação recebiam o mesmo instante, e o
`ORDER BY observed_at DESC` ficava indefinido.

**Por que isso era grave:** o anti-spam da etapa E1.10 depende exatamente dessa pergunta —
"qual foi o último preço visto para esta data?". E o cenário não é raro: numa mesma
varredura, a camada 1 (Travelpayouts) e a camada 2 (fast-flights) gravam observações para
a mesma data, na mesma transação. O mesmo valia para `alert.created_at` quando vários
destinatários recebem de uma vez. O sistema teria alertado errado, ou deixado de alertar,
de forma intermitente e difícil de reproduzir.

**Solução:** migration `V2__event_timestamps_clock.sql` trocando o default para
`clock_timestamp()` nos timestamps de **evento** (`price_observation.observed_at`,
`alert.created_at`, `search_run.started_at`). Os timestamps de **auditoria**
(`created_at`/`updated_at` de `monitor` e `recipient`) seguem com `now()`, onde o instante
lógico da transação é a semântica correta. Somado a isso, as consultas de ordenação ganharam
desempate por `id`, garantindo ordem determinística mesmo em empate de instante.

**Lição:** `now()` no PostgreSQL não é "agora". Para registrar *quando algo aconteceu*, o
correto é `clock_timestamp()`. Já havíamos esbarrado nessa pegadinha na E1.1, quando ela
pareceu ser só um defeito de teste — aqui ela se mostrou um defeito real de projeto.

---

### BUG-001 · Caracteres acentuados corrompidos no log do worker
**Status:** ✅ resolvido
**Encontrado em:** 2026-08-09 · Etapa E0.3 · Componente: worker-python
**Severidade:** baixa

**Sintoma:** o travessão da mensagem de startup saía como `�` no console:

```
WARNING [app.main] TRAVELPAYOUTS_TOKEN nao configurado � camada 1 ...
```

**Reprodução:** subir o worker com `uvicorn app.main:app` no PowerShell/Git Bash do Windows
e observar a mensagem de warning quando o `TRAVELPAYOUTS_TOKEN` está vazio.

**Causa raiz:** o console do Windows usa a codepage cp1252 por padrão. O handler de log do
Python escreve na saída padrão usando essa codepage, e caracteres fora dela viram `?`/`�`.
Não é um problema do arquivo-fonte, que está em UTF-8.

**Solução:** manter **mensagens de log em ASCII puro**. Acentos continuam permitidos em
docstrings, comentários e respostas de API — só as strings que vão para o logger é que
precisam ser ASCII. Comentário explicativo deixado em `app/main.py`.

**Lição:** vale valer para o core-java também, quando começarmos a logar mensagens de alerta.

---

## Riscos em observação

Não são bugs, mas coisas que podem virar bug. Monitorar durante o desenvolvimento.

### ~~RISCO-001 · Python 3.14 pode ser novo demais para as bibliotecas~~ — ✅ DESCARTADO
**Componente:** worker-python
**Detectado em:** 2026-08-09 · **Descartado em:** 2026-08-09 (etapa E0.3)

O receio era que bibliotecas com extensão nativa não tivessem wheel para o Python 3.14.4.
**Não se confirmou.** Todas as dependências instalaram com wheel pronta, sem compilar:

| Pacote nativo | Wheel |
|---|---|
| `pydantic-core` 2.46.4 | `cp314-cp314-win_amd64` |
| `httptools` 0.8.0 | `cp314-cp314-win_amd64` |
| `watchfiles` 1.2.0 | `cp314-cp314-win_amd64` |
| `websockets` 17.0.1 | `cp314-cp314-win_amd64` |
| `pyyaml` 6.0.3 | `cp314-cp314-win_amd64` |

O `fast-flights` 3.0.2 (etapa E1.6) também foi checado por antecipação com
`pip install --dry-run` e resolve normalmente: `primp` e `protobuf` usam wheels abi3
(compatíveis com 3.14) e `selectolax` tem wheel `cp314`.

**Conclusão:** o venv fica no Python 3.14.4. Não é preciso rebaixar para 3.12.

### ~~RISCO-006 · Travelpayouts normaliza aeroporto para código de cidade~~ — ✅ TRATADO (E1.5)
**Resolvido em:** 2026-08-09 · Gravamos sempre o código pedido; ver [D-023](DECISOES.md).
O worker devolve `provider_origin` e emite aviso quando os códigos divergem.

**Componente:** worker-python / core-java
**Detectado em:** 2026-08-09 (validação do token, antes da E1.5)

Pedimos `origin=GRU` e a API devolveu `"origin":"SAO"` — o código **da cidade** de São Paulo,
não o do aeroporto de Guarulhos. Ou seja, a resposta pode misturar GRU, CGH e VCP.

**Por que importa:** o histórico é indexado por rota (`origin`/`destination`), conforme
[D-016](DECISOES.md). Se gravarmos ora `GRU` ora `SAO`, a mesma rota vira duas na estatística
e a média histórica da Fase 2 fica errada.

**A decidir na E1.5:** gravar o código **pedido** (o do monitor) ou o **devolvido**? A
inclinação é gravar o pedido, mantendo coerência com o monitor, e guardar o devolvido como
informação complementar — mas isso levanta a questão de o preço poder ser de outro aeroporto
da mesma região, o que aliás o próprio usuário citou como desejável ("aceitar sair de
Viracopos se economizar mais de R$ 500").

**Verificar em:** E1.5

### ~~RISCO-007 · O parâmetro de mês não é respeitado pela Travelpayouts~~ — ✅ TRATADO (E1.5)
**Resolvido em:** 2026-08-09 · Confirmado em produção: 30 ofertas retornadas, zero dentro da
janela. O worker filtra toda data contra a janela pedida; ver [D-024](DECISOES.md).

**Componente:** worker-python
**Detectado em:** 2026-08-09 (validação do token, antes da E1.5)

Consultamos `depart_date=2027-03` e a resposta trouxe datas de **agosto e setembro de 2026**.
A API devolve o que tem em cache, ignorando o mês pedido quando não há dados para ele.

**Por que importa:** sem filtrar do nosso lado, o sistema gravaria observações fora da janela
do monitor e poderia alertar sobre uma data que o usuário não pediu.

**Mitigação prevista:** filtrar as datas retornadas contra a janela do monitor **no worker**,
antes de devolver ao core. Nunca confiar que o provider respeitou o filtro.

**Bônus encontrado:** a resposta traz `expires_at`, indicando até quando o preço cacheado
vale. É um insumo direto contra o [RISCO-003](#risco-003--preços-cacheados-da-travelpayouts-podem-gerar-falso-positivo)
(falso-positivo por cache) — dá para descartar preço vencido antes mesmo de chamar a camada 2.

### ~~RISCO-005 · Starlette vai exigir `httpx2` no TestClient~~ — ✅ RESOLVIDO (E1.5)
**Resolvido em:** 2026-08-09 · Migrado para `httpx2` antes de escrever o cliente da
Travelpayouts, evitando reescrita. O `httpx` foi desinstalado. Ver [D-025](DECISOES.md).

**Componente:** worker-python
**Detectado em:** 2026-08-09 (etapa E0.3)

Os testes emitem `StarletteDeprecationWarning: Using httpx with starlette.testclient is
deprecated; install httpx2 instead`. Não quebra nada hoje, mas o `httpx` também é a
biblioteca escolhida para o cliente HTTP da Travelpayouts na etapa E1.5.

**A decidir:** se migramos para `httpx2` de uma vez ou mantemos `httpx` e trocamos só o
TestClient. Avaliar antes de escrever o cliente HTTP, para não escrever duas vezes.

**Verificar em:** E1.5

### RISCO-002 · fast-flights depende do formato interno do Google Flights — ⚠️ MITIGADO, NAO ELIMINADO
**Atualizado em:** 2026-08-09 (etapa E1.6)

**O risco continua vivo e sempre estara** — não há contrato com o Google. O que mudou é que
o sistema agora sobrevive à quebra: cadeia com fallback, degradação explícita, chave de
desligamento por variável de ambiente e observabilidade da falha.

**A instabilidade já se manifestou três vezes antes de irmos a produção:** a API da
biblioteca mudou por completo entre 2.x e 3.x; a anotação `list[Airline]` devolve `list[str]`
em execução; e o parser retornou `time=[None, 45]`, com a hora ausente. Nenhuma delas estava
documentada.

**Plano de contingência completo em [FRAGILIDADE-CAMADA-2.md](FRAGILIDADE-CAMADA-2.md).**

**Componente:** worker-python
**Detectado em:** 2026-08-09 (pesquisa de fontes)

A biblioteca decodifica protobuf da URL do Google Flights. Não é uma API contratada — o
Google pode mudar o formato sem aviso e quebrar a coleta. O próprio README da biblioteca
relativiza a confiabilidade.

**Mitigação prevista:** a camada Travelpayouts continua funcionando sozinha; o alerta sai
degradado, sem detalhes de voo. Testes devem cobrir o caminho de falha do provider.

**Verificar em:** E1.6

### RISCO-003 · Preços cacheados da Travelpayouts podem gerar falso-positivo
**Componente:** worker-python / core-java
**Detectado em:** 2026-08-09 (pesquisa de fontes)

A Travelpayouts serve dados cacheados. Um preço "abaixo do teto" pode já não existir mais
no momento da consulta. Alertar sem confirmar geraria mensagens inúteis e perda de confiança
no sistema.

**Mitigação prevista:** confirmação obrigatória pela camada 2 antes de disparar alerta.
Registrar falsos-positivos em `price_observation` para medir a taxa.

**Verificar em:** E1.10

### RISCO-009 · A tendência pode confundir "preço subiu" com "mudou o que estamos olhando"
**Componente:** core-java (E2.5)
**Detectado em:** 2026-08-12 · durante o desenho, antes de causar erro

A série temporal agrupa observações por **dia de observação** e tira a mediana do dia. Mas
preços de datas de partida diferentes não são comparáveis: uma viagem em julho e outra em
novembro são produtos distintos.

Se a mistura de datas observadas mudar ao longo do tempo, a série pode registrar um degrau que
não é movimento de preço — é troca do que está sendo medido.

**O que atenua, e não é sorte:** o desenho do sistema. Cada monitor tem **janela de partida
fixa**, e o scheduler varre a mesma janela a cada ciclo. A mistura de datas observadas é,
portanto, aproximadamente constante de um dia para o outro.

**O que sobra:** a janela encolhe conforme as datas passam, e datas próximas da partida tendem a
ser mais caras. Numa série de 30 dias o efeito é pequeno; numa de seis meses seria relevante — e
é parte da razão de a janela da tendência ser mais curta que a das estatísticas.

**Mitigação possível, não implementada:** calcular a tendência **por data de partida** e agregar
depois, ou restringir a série às datas presentes em todos os dias. Custa uma consulta mais
complexa e só compensa quando houver histórico suficiente para o refinamento importar.

**Verificar em:** quando a primeira rota acumular alguns meses de histórico. Sinal de alerta:
tendência forte que não aparece ao olhar os preços de uma única data de partida.

---

### ~~RISCO-008 · A suíte de testes apaga os dados de desenvolvimento~~ — ✅ ELIMINADO (E4.2)
**Componente:** core-java (testes)
**Detectado em:** 2026-08-12 · Etapa E1.16

Os testes de integração usam o **mesmo PostgreSQL** do desenvolvimento ([D-020](DECISOES.md)) e
limpam as tabelas em `@BeforeEach`/`@AfterEach`. Rodar `mvn test` apaga monitores,
destinatários, observações e alertas que você tenha cadastrado à mão.

Apareceu de forma concreta hoje: depois de rodar a suíte, `GET /api/monitors` devolveu lista
vazia, e o teste de entrega precisou recriar monitor e destinatário do zero.

**Reincidiu em 2026-08-13, e desta vez custou informação.** Rodei a suíte duas vezes para
conferir uma contagem de testes, e ela levou junto o destinatário e o monitor da E1.12. O
`alert` era o **único** registro do número de destino usado no teste anterior — a Graph API não
devolve histórico de conversas, e o número não estava em nenhum documento. A etapa parou até o
usuário informá-lo de novo.

**Por que ainda não é bug:** o banco é local e descartável, e usar o banco real é o que dá
valor aos testes de constraint e trigger. A alternativa — banco separado só para teste —
custa configuração e some com o ganho de testar contra o schema que realmente roda.

**Mas a classificação está no limite.** "Descartável" valia enquanto o dado era recriável por
quem apagou. Um dado que só existia ali, e que precisou de outra pessoa para voltar, não era
descartável — era um dado de verdade guardado num lugar que se comporta como rascunho.

**Mitigação prevista:** um banco `flightmon_test` no mesmo container, com o `application.properties`
de teste apontando para ele. Barato, e elimina a surpresa.

**✅ Resolvido na E4.2, em 2026-08-13.** Existe agora o banco `flightmon_test`, criado por
`docker/postgres/init/01-banco-de-teste.sql` e apontado pelo `application.properties` de teste.

**O que NÃO mudou:** continua PostgreSQL de verdade, mesmo container, mesmo Flyway, mesmas
migrations. O valor dos testes de constraint e trigger ([D-020](DECISOES.md)) está inteiro — o que
mudou é só em qual banco eles fazem a bagunça.

**Verificado na prática:** um destinatário foi criado no banco de desenvolvimento, a suíte inteira
(375 testes) rodou por cima, e ele **sobreviveu** — pela primeira vez.

---

### RISCO-004 · Rate limit e bloqueio por excesso de requisições
**Componente:** worker-python
**Detectado em:** 2026-08-09

Travelpayouts permite 300 req/min, mas o Google Flights não publica limite. Varreduras
frequentes demais podem levar a bloqueio de IP.

**Mitigação prevista:** rate limit próprio no worker, cache local de resultados recentes,
intervalo mínimo entre varreduras do mesmo monitor.

**Verificar em:** E1.9
