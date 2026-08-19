# Decisões de Arquitetura

> Registro das decisões tomadas, com o contexto e o motivo. Serve para não
> re-litigar escolhas já feitas e para lembrar *por que* algo foi descartado.

**Legenda de status:** ✅ vigente · ⚠️ revisada · ❌ revertida

---

## Parte I — O que veio da discussão original (ChatGPT)

Estas são as conclusões da conversa que originou o projeto, mantidas como base.

### D-001 · Arquitetura híbrida Java + Python ✅
**Decisão:** Java/Spring Boot como núcleo administrativo; Python como especialista em
coleta e análise.

**Por quê:** cada tecnologia no que é mais forte. O Java traz robustez, persistência,
agendamento e API; o Python traz o ecossistema de automação e análise de dados.
Alinhado à experiência prévia do usuário em Java/Spring, PostgreSQL, RabbitMQ, Docker
e Kubernetes — o projeto vira vitrine desse conhecimento.

### D-002 · Não começar com microserviços ✅
**Decisão:** dois serviços (core + worker), não sete.

**Por quê:** dividir em `user-service`, `monitor-service`, `search-service`,
`analysis-service`, `notification-service` seria exagero no primeiro estágio. Complexidade
sem benefício.

### D-003 · Java é o dono do banco ✅
**Decisão:** o Python não acessa o PostgreSQL. Só troca dados via API.

**Por quê:** mantém separação limpa de responsabilidades e evita dois donos do schema.

### D-004 · WhatsApp como único canal, com número dedicado do sistema ✅
**Decisão:** o remetente **não é o número pessoal do usuário**. É um número associado à
WhatsApp Business Platform da Meta.

**Contexto:** essa foi uma dúvida explícita levantada na discussão original. A resposta é
que o sistema tem seu próprio número remetente, e os destinatários cadastrados recebem
como uma conversa vinda desse número.

**Refinado depois** por [D-011](#d-011--número-de-teste-da-meta-no-lugar-de-chip-dedicado-).

### D-005 · Sem cadastro de usuários, sem e-mail, sem Telegram ✅
**Decisão do usuário:** simplificar. O sistema é pessoal. Existe apenas um cadastro de
números de celular que recebem as notificações.

### D-006 · REST no MVP, mensageria depois ✅
**Decisão:** começar com HTTP síncrono entre Java e Python; migrar para RabbitMQ quando
o projeto crescer (Fase 4).

**Por quê:** REST é trivial de debugar e faz o sistema andar rápido. A troca fica prevista
na arquitetura, não é retrabalho.

### D-007 · Faseamento em Engine → Intelligence → Agent ✅
**Decisão:** não começar pela IA.

**Por quê:** a IA é ~20% do valor. Os outros 80% são coletar preços, armazenar histórico,
comparar e notificar. Sem histórico acumulado, não há o que a IA analise. Um "agente de IA"
sem motor por baixo seria só um scraper com LLM acoplado.

---

## Parte II — Riscos que o ChatGPT já havia mitigado corretamente

Registrado para não repetirmos análise já feita.

| # | Risco identificado | Mitigação adotada |
|---|---|---|
| M-1 | Sites de voos não têm API pública gratuita | Aceito como o desafio central; a escolha da fonte virou a primeira decisão do projeto |
| M-2 | Playwright/Selenium quebra a cada mudança de layout | Sinalizado como alto custo de manutenção → levou à [D-008](#d-008--descartar-playwright-como-estratégia-primária-) |
| M-3 | Automação do WhatsApp Web pessoal é frágil e viola termos | Descartado; usar a Cloud API oficial da Meta |
| M-4 | Regras de janela de 24h e templates do WhatsApp | Reconhecido: alertas são mensagens iniciadas pela empresa, exigem template aprovado. Contornado no MVP pela [D-011](#d-011--número-de-teste-da-meta-no-lugar-de-chip-dedicado-) |
| M-5 | Mensagens de serviço podem deixar de ser gratuitas | Não assumir gratuidade permanente; o sistema faz milhares de buscas mas pouquíssimos envios |
| M-6 | Custo real do projeto não é o WhatsApp, é a fonte de preços | Confirmado — orientou toda a estratégia de coleta |

---

## Parte III — Decisões novas (validadas em 2026-08-09)

Estas corrigem ou refinam a discussão original com base em pesquisa atual.

### D-008 · Descartar Playwright como estratégia primária ✅
**Decisão:** não usar automação de navegador no MVP. Fica como plano C.

**Por quê:** o plano original assumia Playwright como o caminho de coleta. Existe uma opção
muito melhor: a Travelpayouts devolve o preço mais barato de **cada dia de um mês inteiro
em uma única requisição HTTP**. Varrer 30 datas com navegador levaria minutos e quebraria
com frequência; a chamada de API leva menos de um segundo e é estável.

### D-009 · ⚠️ Amadeus Self-Service está fora — foi descontinuado
**Fato novo:** o portal Self-Service do Amadeus foi desativado em **17/07/2026**. Não existe
mais tier gratuito para desenvolvedores independentes. Só resta o portal Enterprise, que é
comercial e exige contrato.

**Impacto:** o caminho "API oficial gratuita", que seria o padrão óbvio, deixou de existir
três semanas antes do início deste projeto. Isso torna a estratégia em camadas
([D-010](#d-010--coleta-em-duas-camadas-)) não apenas conveniente, mas necessária.

### D-010 · Coleta em duas camadas ✅
**Decisão:** Travelpayouts para varredura ampla + fast-flights para confirmação pontual.

**Por quê:**
- Travelpayouts é gratuita, tem 300 req/min e cobre um mês por chamada — ideal para
  descobrir candidatos e acumular histórico. Mas os dados são cacheados e não trazem
  detalhes de voo.
- fast-flights lê o protobuf do Google Flights, é gratuita e sem chave, e traz companhia,
  escalas e horários. Mas é frágil por depender de formato interno do Google.
- Combinadas, cobrem a fraqueza uma da outra. Se a camada 2 cair, o sistema continua
  monitorando e alertando, apenas sem detalhes de voo.

**Alternativas descartadas:** SerpApi (só 250 buscas/mês grátis, insuficiente para varredura
de datas, e US$ 25/mês depois); Duffel (voltado a quem realmente vende passagens, exige
aprovação comercial).

### D-011 · Número de teste da Meta no lugar de chip dedicado ✅
**Decisão:** usar o número de teste gratuito da WhatsApp Cloud API no MVP.

**Por quê:** a discussão original recomendava comprar um chip dedicado. Não é necessário
para começar. O número de teste da Meta é gratuito, imediato, e envia para até 5
destinatários verificados por OTP — suficiente para uso pessoal. Elimina chip, Meta Business
verificado, cartão de crédito e aprovação de templates.

**Custo do WhatsApp no MVP: R$ 0,00.**

**Migração futura:** trocar `phoneNumberId` e `accessToken`. Nenhuma lógica muda. Em
produção, mensagens Utility no Brasil custam por volta de R$ 0,04–0,05 cada — irrelevante
no volume deste projeto.

### D-013 · Spring Boot 4.1.0 em vez de Spring Boot 3 ⚠️
**Fato novo:** o Spring Initializr não oferece mais a linha 3.x. As únicas versões disponíveis
são 4.0.7 e 4.1.0 (default). Adotado o **4.1.0** com Spring Framework 7 e Java 21.

**Diferenças que já apareceram na prática:**
- O starter web agora é `spring-boot-starter-webmvc` (era `spring-boot-starter-web`)
- Flyway ganhou starter próprio: `spring-boot-starter-flyway`
- Os starters de teste ficaram granulares — um por módulo
  (`spring-boot-starter-webmvc-test`, `-data-jpa-test`, etc.) em vez de um `-test` único

**Impacto:** tutoriais e respostas de IA treinadas em Spring Boot 3 podem indicar artefatos
que não existem mais. Ao adicionar dependências, conferir o nome no Initializr.

### D-014 · Portas do projeto ✅
**Decisão:** PostgreSQL em **5433**, API em **8081**.

**Por quê:** as portas padrão estavam ocupadas na máquina de desenvolvimento — a 5432 por um
PostgreSQL 15 nativo do Windows e a 8080 por um processo `AgentService`. Fixar portas próprias
evita colisão silenciosa (o pior caso seria o Spring conectar no banco errado sem erro).

| Serviço | Porta |
|---|---|
| PostgreSQL (container) | 5433 |
| core-java | 8081 |
| worker-python | 8001 (reservada) |
| frontend-vue (Vite) | 5173 (reservada) |

### D-015 · O `.env` da raiz é a única fonte de credenciais ✅
**Decisão:** o Spring Boot importa o `.env` da raiz via
`spring.config.import: optional:file:../.env[.properties]`.

**Por quê:** o mesmo arquivo alimenta o `docker-compose.yml` e a aplicação Java. Sem isso,
a senha do banco existiria em dois lugares e sairia de sincronia. O prefixo `optional:` faz
a aplicação subir normalmente em ambientes sem o arquivo (CI, container), caindo nas
variáveis de ambiente ou nos defaults.

### D-012 · Vue 3 + Vite + TypeScript no front-end ✅
**Decisão do usuário:** Vue.js.

**Consequência:** a pasta `frontend-vue/` (renomeada pelo usuário em 2026-08-09, era
`front-end/`) é um projeto independente que consome a API do core-java.

**CORS não é necessário em desenvolvimento:** o Vite faz proxy de `/api` para
`http://localhost:8081`, então o navegador enxerga tudo na mesma origem. A configuração de
CORS no Spring Boot só será necessária se um dia o front for servido de outro domínio em
produção.

---

### D-016 · Histórico de preços pertence à rota, não ao monitor ✅
**Decisão:** `price_observation` guarda `origin` e `destination` **denormalizados**, e a FK
para `monitor` é `ON DELETE SET NULL`.

**Por quê:** o que o sistema aprende sobre GRU→LIS ao longo de meses é o ativo mais valioso
do projeto — é dele que sai toda a inteligência da Fase 2. Se o histórico dependesse do
monitor, apagar um monitor apagaria meses de aprendizado, e dois monitores para a mesma rota
teriam históricos separados que não conversam.

Com a denormalização: a média histórica de uma rota é consultável independente de qualquer
monitor existir, e monitores diferentes para a mesma rota somam observações em vez de dividir.

**Custo aceito:** `origin`/`destination` ficam duplicados entre `monitor` e
`price_observation`. É duplicação deliberada — os dois campos descrevem coisas diferentes
(o critério de busca vs. o fato observado), e o fato não deve mudar se o critério mudar.

**Verificado em teste:** apagar o monitor mantém as observações, com `monitor_id` nulo.

### D-017 · `updated_at` é imposto por trigger, não pela aplicação ✅
**Decisão:** a função `set_updated_at()` sobrescreve `NEW.updated_at` com `now()` em todo
UPDATE, ignorando o valor enviado pela aplicação.

**Por quê:** garante que a coluna reflita a realidade mesmo se alguém atualizar a linha por
fora do Hibernate — por psql, script de manutenção ou migration futura.

**Nuance importante do PostgreSQL:** `now()` devolve o horário de **início da transação**,
não o horário do relógio. Duas alterações na mesma transação recebem o mesmo `updated_at`.
Isso é intencional e desejável (uma transação é um instante lógico), mas engana em teste:
um INSERT seguido de UPDATE na mesma transação **não** muda o `updated_at`. Se algum dia for
preciso o horário real de parede, o correto é `clock_timestamp()`.

### D-018 · Timestamp de evento usa `clock_timestamp()`; de auditoria usa `now()` ✅
**Decisão:** distinguir os dois tipos de instante no banco.

| Tipo | Colunas | Default | Por quê |
|---|---|---|---|
| **Evento** — algo aconteceu | `price_observation.observed_at`, `alert.created_at`, `search_run.started_at` | `clock_timestamp()` | precisa do horário real; várias linhas da mesma transação têm que se distinguir |
| **Auditoria** — quem tocou a linha | `monitor.created_at/updated_at`, `recipient.created_at/updated_at` | `now()` | o instante lógico da transação é a semântica correta |

**Por quê:** `now()` no PostgreSQL é `transaction_timestamp()` — o horário de início da
transação. Isso quebrou o "último preço visto" de forma real, não teórica. Ver
[BUG-002](BUGS.md). Aplicado pela migration `V2__event_timestamps_clock.sql`.

**Complemento:** toda consulta que ordena por instante desempata por `id`, garantindo ordem
determinística mesmo se dois registros compartilharem o microssegundo.

### D-019 · Nunca editar uma migration já aplicada ✅
**Decisão:** a correção do BUG-002 virou `V2`, e não uma edição da `V1`.

**Por quê:** a `V1` já estava registrada no `flyway_schema_history` com seu checksum. Editá-la
faria o Flyway recusar a subir em qualquer ambiente que já a tivesse aplicado. Mesmo sendo o
primeiro dia do projeto, é o hábito que precisa valer desde o começo — o histórico de
migrations é um log append-only.

### D-020 · Testes de persistência rodam contra o PostgreSQL real ✅
**Decisão:** `@DataJpaTest` com `@AutoConfigureTestDatabase(replace = NONE)`, apontando para
o banco do Docker. Sem H2, sem banco em memória.

**Por quê:** o schema depende de recursos específicos do PostgreSQL — CHECK com regex,
trigger, `ON DELETE SET NULL`, colunas IDENTITY, `clock_timestamp()`. Um H2 não reproduz
nada disso, e os testes passariam dando falsa sensação de segurança justamente nas regras
que mais importam. Vários testes desta etapa verificam mensagens de constraint por nome —
só fazem sentido contra o banco de verdade.

**Como fica limpo:** `@DataJpaTest` é transacional e faz rollback ao final de cada teste,
então o banco de desenvolvimento não acumula lixo. Verificado.

**Consequência:** é preciso ter o container de pé para rodar os testes. Aceito por ora;
quando houver CI, avaliar Testcontainers.

### D-021 · ⚠️ Proxy do Vite sem reescrita de caminho
**Revisa a E0.4.** O proxy original mapeava `/api/*` → `/*`, removendo o prefixo. Ao criar
o controller em `/api/monitors`, isso exigiria que o front chamasse `/api/api/monitors`.

**Decisão:** o proxy passa a encaminhar **sem reescrever**, e ganhou uma entrada para
`/actuator`:

```
/api      -> http://localhost:8081/api
/actuator -> http://localhost:8081/actuator
```

**Por quê:** o caminho no navegador passa a ser idêntico ao caminho no servidor. Isso evita
traduzir URL mentalmente ao depurar, faz o log do Spring bater com a aba de rede do
navegador, e elimina surpresa quando o front for publicado em outro domínio.

**Consequência:** o cliente HTTP ganhou duas funções — `getJson` para `/api` e `getActuator`
para os endpoints do Actuator, que ficam na raiz.

### D-022 · Validação duplicada entre API e banco, de propósito ✅
**Decisão:** as mesmas regras existem como Bean Validation no DTO e como CHECK no banco.

**Por quê:** não é redundância acidental, são papéis diferentes. O banco **garante** que
dado inválido nunca entre, venha de onde vier — API, script ou psql. A API **explica** o que
está errado, em qual campo e por quê, devolvendo 400 com detalhe por campo em vez de um 500
com mensagem de constraint do PostgreSQL.

O `ApiExceptionHandler` ainda captura `DataIntegrityViolationException` como última linha de
defesa: devolve 409 em vez de 500 e **registra um WARN**, porque cair ali significa que
faltou uma validação na camada de aplicação.

### D-023 · A rota gravada é a pedida, nunca a devolvida pela fonte ✅
**Decisão:** `price_observation.origin`/`destination` guardam sempre o código **pedido pelo
monitor** (`GRU`), e não o que a Travelpayouts devolve (`SAO`).

**Por quê:** a API normaliza o aeroporto para o código da cidade ([RISCO-006](BUGS.md)).
Como o histórico é indexado por rota ([D-016](#d-016--histórico-de-preços-pertence-à-rota-não-ao-monitor-)),
gravar ora `GRU` ora `SAO` partiria uma rota em duas e corromperia a média da Fase 2.

**Como a imprecisão fica registrada:** o campo `source` já distingue a confiabilidade —
`TRAVELPAYOUTS` é preço em nível de cidade, `FAST_FLIGHTS` é aeroporto confirmado. Não foi
preciso criar coluna nova. A resposta do worker ainda traz `provider_origin` e um aviso
explícito quando os códigos divergem.

**Consequência a explorar:** o agrupamento por cidade é justamente o que viabiliza a
preferência citada na discussão original — "aceitar sair de Viracopos se economizar mais de
R$ 500". Fica para a etapa E2.6.

### D-024 · O worker nunca confia no filtro do provider ✅
**Decisão:** toda oferta é filtrada no worker contra a janela pedida, mesmo que o parâmetro
correspondente tenha sido enviado à API.

**Por quê:** confirmado em chamada real — pedimos `depart_date=2027-03` e vieram 30 ofertas,
**todas** de outros meses ([RISCO-007](BUGS.md)). A API devolve o que tem em cache. Sem
filtro próprio, o sistema gravaria observações de datas que o usuário nunca pediu.

**Complemento:** a resposta expõe `returned` e `kept`. Não é decoração — é o que permite
diagnosticar sem ler log: `returned` alto com `kept` zero indica janela sem oferta;
`returned` zero indica fonte fora do ar.

### D-025 · `httpx2` no lugar de `httpx` ✅
**Decisão:** o worker usa `httpx2` como único cliente HTTP. Resolve o
[RISCO-005](BUGS.md#risco-005--starlette-vai-exigir-httpx2-no-testclient).

**Por quê:** o Starlette deprecou o `httpx` no TestClient e aponta o `httpx2` como sucessor.
Como o cliente da Travelpayouts estava sendo escrito naquele momento, adotar agora evitou
escrever duas vezes. A superfície de API é equivalente (`AsyncClient`, `MockTransport`,
`TimeoutException`, `HTTPStatusError`), então a migração não custou nada.

**Verificado:** o aviso de depreciação sumiu dos testes, e o `httpx` foi desinstalado para
não manter dois clientes HTTP no mesmo processo.

### D-026 · Strategy por camada, e não entre camadas ✅
**Contexto:** o usuário sugeriu aplicar o padrão Strategy às fontes de preço, com a ressalva
de que não tinha certeza da viabilidade.

**Decisão:** aplicar Strategy **dentro** de cada camada, com duas interfaces separadas
(`CalendarProvider` e `ConfirmationProvider`), e não uma interface única para todas as fontes.

**Por quê:** as duas camadas não fazem a mesma coisa.

| Camada 1 (varredura) | Camada 2 (confirmação) |
|---|---|
| 30 datas por chamada | 1 data por chamada |
| barata, roda a cada 6h | cara, só quando há candidato |
| preço de cidade, cacheado | aeroporto, companhia, horário, ao vivo |
| muitas ofertas | uma oferta, ou nenhuma |

Uma interface única criaria simetria falsa — metade dos métodos não faria sentido para
metade das implementações. Mas **dentro** da camada 2, `fast-flights`, SerpApi e Playwright
são genuinamente intercambiáveis: mesma entrada, mesma saída, custo e confiabilidade
diferentes. Esse é exatamente o caso de uso do padrão.

**Implementação:** `typing.Protocol` em vez de classe base abstrata. A conformidade é
estrutural, então um provider novo não precisa herdar de nada nem sequer importar o módulo
base — basta ter `name` e `confirm`.

### D-027 · Strategy sozinho não bastava: cadeia com degradação explícita ✅
**Decisão:** somar ao Strategy uma `ConfirmationChain` com fallback e três desfechos
distintos.

**Por quê:** Strategy resolve "trocar de implementação". Não resolve o que de fato ameaça
este projeto: a implementação escolhida quebrar em produção, sem aviso, num domingo. A cadeia
tenta os providers em ordem e, se todos falharem, devolve **degradado** em vez de erro.

```
confirmed=True                    -> há voo real
confirmed=False, degraded=False   -> consultamos e não existe; candidato era ilusório
confirmed=False, degraded=True    -> ninguém respondeu; não sabemos
```

Distinguir os três é o coração do desenho: "não existe esse voo" e "não consegui verificar"
levam a decisões opostas no core-java.

### D-028 · A camada 2 nunca é obrigatória para alertar ✅
**Decisão:** falha da camada 2 degrada o alerta, não o impede.

**Por quê:** tornar obrigatória seria a decisão aparentemente mais segura, mas transformaria
o componente mais frágil do sistema em ponto único de falha. Um alerta sem detalhe de voo é
muito melhor que alerta nenhum.

**Contrapartida aceita:** sem confirmação, voltamos a confiar no preço cacheado. O primeiro
teste ao vivo mediu essa diferença: candidato de R$ 3.375 contra preço real de R$ 5.438 —
**61% de divergência**. Por isso a degradação precisa ser visível no `/health`, nos
`warnings` e nos `attempts`.

Documentação completa em [FRAGILIDADE-CAMADA-2.md](FRAGILIDADE-CAMADA-2.md).

### D-029 · Estratégia de testes em quatro níveis, sem E2E de navegador ✅
**Contexto:** o usuário perguntou se o roteiro cobria testes end-to-end. Não cobria — havia
testes de unidade e de integração em cada serviço, mas nada cruzava a fronteira Java ↔ Python
nem exercitava o fluxo de negócio completo.

**Decisão:** adotar três níveis novos e recusar um.

| Nível | Etapa | Papel |
|---|---|---|
| E2E do motor | E1.15 | fluxo de negócio inteiro, worker via WireMock, dentro do `mvn test` |
| E2E entre serviços | E1.16 | Java + Python + Postgres reais, só as fontes externas falsas |
| Canário ao vivo | E4.5 | detecta mudança de formato nas fontes externas, **fora do CI** |
| ~~E2E de navegador~~ | — | avaliado e recusado nesta rodada |

**Por que o canário fica fora do CI:** depende de rede, consome cota e é instável por
natureza. Um canário vermelho é informação valiosa; um canário instável dentro do CI vira
ruído, e ruído treina a equipe a ignorar falha.

**Por que recusamos o E2E de navegador:** é o nível de maior custo de manutenção — quebra a
cada ajuste de layout — e a interface deste projeto tem poucas telas. Pode ser reavaliado
depois da E1.14.

**O que torna isso barato:** as costuras já existem. O Strategy da [D-026](#d-026--strategy-por-camada-e-não-entre-camadas-)
permite registrar providers falsos pela `factory.py`, e o `AlertChannel.LOG` está no schema
desde a E1.1. Nenhum código de produção precisa de `if (teste)`.

### D-030 · Contrato de erro assimétrico entre as duas camadas ✅
**Decisão:** a `SearchClient` trata falha de forma **oposta** em cada camada.

| Camada | Worker fora do ar | Por quê |
|---|---|---|
| 1 — varredura | lança `WorkerUnavailableException` | sem preço não há varredura; marcar `search_run` como FAILED e tentar no próximo ciclo |
| 2 — confirmação | devolve `ConfirmResult.degradado(...)` | derrubar a varredura por causa de uma camada opcional seria pior que seguir sem ela |

**Por quê:** é a tradução, para o cliente Java, da [D-028](#d-028--a-camada-2-nunca-é-obrigatória-para-alertar-).
A assimetria é deliberada e está documentada na própria interface, para que ninguém a
"corrija" por parecer inconsistente.

**Consequência para a E1.10:** a regra de alerta precisa distinguir `naoExiste()` de
`degraded()`. O primeiro significa "o candidato era ilusório, não alerte"; o segundo,
"não sabemos, alerte sem detalhe de voo".

### D-031 · Dois `RestClient`, um por camada ✅
**Decisão:** beans separados para varredura e confirmação, com timeouts distintos —
30s e 60s.

**Por quê:** o timeout de leitura é definido na fábrica de requisições, não por chamada. Um
valor único seria curto demais para a camada 2 (que consulta o Google ao vivo) ou longo
demais para detectar a camada 1 travada (que consulta uma API cacheada e rápida).

### D-032 · HTTP/1.1 explícito no cliente do worker ✅
**Decisão:** `HttpClient.Version.HTTP_1_1` declarado, em vez do padrão da JDK.

**Por quê:** o `HttpClient` da JDK negocia HTTP/2 por padrão, mas o uvicorn só fala HTTP/1.1.
Ver [BUG-004](BUGS.md) — causou 11 falhas intermitentes e teria degradado produção de forma
difícil de diagnosticar.

### D-033 · Tradução snake_case confinada aos DTOs de cliente ✅
**Decisão:** `@JsonNaming(SnakeCaseStrategy)` por classe nos DTOs do worker, em vez de mudar
a estratégia global do Jackson.

**Por quê:** o worker fala snake_case (convenção Python) e a nossa API fala camelCase. Mudar
a configuração global do Jackson resolveria a leitura do worker e **quebraria a nossa própria
API** ao mesmo tempo. A tradução pertence à fronteira, não ao sistema inteiro.

### D-034 · Nunca manter transação aberta durante chamada HTTP ✅
**Decisão:** o `PriceSearchService` não tem nenhum método público `@Transactional`. A
persistência acontece em blocos curtos via `TransactionTemplate`, com as chamadas ao worker
**fora** de qualquer transação.

**Por quê:** a confirmação consulta o Google ao vivo e pode levar dezenas de segundos. Uma
transação aberta durante isso prenderia uma conexão do pool o tempo inteiro. Com o pool em
10 conexões e o scheduler varrendo vários monitores em paralelo (etapa E1.9), o pool se
esgotaria e a aplicação inteira travaria — inclusive a API, que não tem nada a ver com busca.

**Por que não `@Transactional` em método privado:** o Spring aplica a anotação por proxy, e
chamada interna não passa pelo proxy. A anotação seria silenciosamente ignorada — o pior tipo
de falha, porque parece correta no código.

### D-035 · Confirmar apenas o candidato mais barato ✅
**Decisão:** por padrão, só o candidato de menor preço é levado à camada 2
(`flightmonitor.search.max-confirmacoes: 1`).

**Por quê:** cada confirmação é uma consulta ao vivo de ~1 a 2 segundos, sujeita a bloqueio
por excesso de requisições. Uma varredura pode gerar dezenas de candidatos abaixo do teto —
confirmar todos levaria minutos e multiplicaria o risco de bloqueio, sem ganho: o alerta trata
da **melhor** oportunidade.

**Configurável** porque, se um candidato não se sustentar, avaliar o segundo mais barato pode
valer a pena. O laço já percorre os candidatos em ordem de preço.

### D-036 · O histórico grava a verdade, inclusive quando ela desmente o alerta ✅
**Decisão:** quando a camada 2 revela que o preço real estourou o teto, a observação
confirmada é gravada mesmo assim — e o alerta não é disparado.

**Por quê:** o valor do histórico é ser fiel. Medido em execução real: a camada 1 anunciava
R$ 3.375 e o preço real era R$ 5.714. Descartar essa observação faria a estatística da Fase 2
acreditar que a rota é mais barata do que é, e a média histórica ficaria enviesada para baixo
justamente pelos falsos-positivos do cache.

**Efeito colateral útil:** com as duas observações no banco (a cacheada e a confirmada), dá
para medir a taxa de erro do cache ao longo do tempo e calibrar a confiança na camada 1.

### D-037 · Reivindicar o monitor antes de varrer, não depois ✅
**Decisão:** o ciclo agenda a próxima busca **no momento em que pega** o monitor, dentro da
mesma transação curta da seleção.

**Por quê:** agendar só ao terminar pareceria mais natural e tem dois defeitos graves:

- **falha vira laço apertado** — se a varredura falhar, o monitor continua vencido e é
  escolhido de novo no ciclo seguinte, e no seguinte, martelando uma fonte que já está com
  problema;
- **queda do processo trava o monitor** — morrendo no meio, ele fica vencido para sempre.

Reivindicando primeiro, o pior caso vira "esta varredura foi perdida, a próxima acontece no
intervalo normal": um atraso, não uma avalanche.

**Verificado:** com o scheduler em ciclo de 15s e um monitor de intervalo 5 min, quatro ciclos
rodaram e **apenas um** fez trabalho.

### D-038 · `SKIP LOCKED` na seleção, mesmo com uma instância só ✅
**Decisão:** `reivindicarVencidos` usa `PESSIMISTIC_WRITE` com
`jakarta.persistence.lock.timeout = -2` (o `SKIP LOCKED` do Hibernate).

**Por quê:** hoje roda uma instância, então não muda nada. Mas a Fase 4 prevê deploy em
container, e duas instâncias sem trava varreriam **o mesmo monitor**, gerando busca duplicada
e — pior — alerta duplicado no WhatsApp do usuário.

Sem o `SKIP LOCKED`, a segunda instância ficaria **bloqueada** esperando a primeira soltar a
trava, trocando duplicidade por lentidão. Com ele, cada uma pega um lote diferente.

O custo de adotar agora é uma anotação; o custo de descobrir isso em produção é alerta
duplicado no celular de alguém.

### D-039 · `fixedDelay` em vez de `fixedRate` ✅
**Decisão:** o `@Scheduled` usa `fixedDelayString`.

**Por quê:** com `fixedRate` o Spring dispara a cada N segundos independentemente de o ciclo
anterior ter terminado. Como uma varredura leva dezenas de segundos, os ciclos se empilhariam
e varreriam os mesmos monitores em paralelo. Com `fixedDelay`, a contagem só começa depois que
o ciclo anterior termina.

**Complemento:** o método do scheduler captura `RuntimeException`. Exceção que escape de um
método `@Scheduled` **cancela o agendamento em definitivo** — o Spring não reagenda uma tarefa
que lançou. O motor pararia em silêncio, e só a ausência de alertas denunciaria.

### D-040 · Scheduler desligado nos testes via `application.properties` ✅
**Decisão:** `src/test/resources/application.properties` com
`flightmonitor.scheduler.enabled=false`.

**Por quê:** sem isso o scheduler dispararia durante a suíte, consumindo cota das fontes reais
e criando dados que quebrariam os testes seguintes.

**Detalhe que faz funcionar:** o arquivo de teste é `.properties` e o principal é `.yml`.
Nomes diferentes, então o Spring carrega **os dois** e o `.properties` só tem precedência nos
valores que repetir — a configuração de banco do YAML continua valendo. Um
`application.yml` em `src/test/resources` teria **substituído** o principal e quebrado tudo.

**Como os testes cobrem o ciclo mesmo assim:** o `SearchScheduler` só agenda; toda a lógica
está no `SearchCycleService`, chamado diretamente. Nenhum `Thread.sleep`, nenhum teste lento
e instável.

### D-041 · ⚠️ Revisa a D-028: sem confirmação, o sistema se cala
**Decisão do usuário:** quando a camada 2 está fora do ar, **não alertar**. Registrar no
histórico e antecipar a próxima varredura.

**O que mudou desde a [D-028](#d-028--a-camada-2-nunca-é-obrigatória-para-alertar-):** a
D-028 dizia que "alerta sem detalhe de voo vale mais que alerta nenhum". Era razoável como
princípio, mas a evidência acumulada a contradiz. Em **três de três** medições, o preço do
cache divergiu do real:

| Medição | Cache | Real | Divergência |
|---|---|---|---|
| E1.6 | R$ 3.375 | R$ 5.438 | +61% |
| E1.8 | R$ 3.375 | R$ 5.714 | +69% |
| E1.7 | R$ 3.000 | R$ 5.438 | +81% |

Não é ruído ocasional: é o comportamento normal da fonte cacheada. Alertar sobre esses preços
produziria quase só alarme falso — e alarme falso treina o usuário a ignorar a notificação.
Um sistema ignorado é pior que um desligado, porque dá falsa sensação de cobertura.

**O que a D-028 continua valendo:** a camada 2 **não** é dependência dura do *sistema*. A
varredura continua rodando, o histórico continua crescendo e as estatísticas da Fase 2
continuam sendo alimentadas. O que se suspende é apenas o **alerta**.

**Mitigação do risco de silêncio:** quando a decisão é `SEM_CONFIRMACAO`, a próxima varredura
é antecipada para o `retryDelay` (15 min) em vez do intervalo normal (6h). A camada 2 costuma
voltar em minutos, e assim não se perde a oportunidade por uma indisponibilidade passageira.

**Reversível por configuração:** `flightmonitor.alert.alertar-sem-confirmacao=true` volta ao
comportamento da D-028, e a mensagem sai marcada como "preço não confirmado".

### D-042 · Anti-spam com duas travas independentes ✅
**Decisão do usuário:** re-alertar só quando **ambas** passarem.

| Trava | Regra | Por quê |
|---|---|---|
| Queda mínima | mesma combinação de datas exige queda de **5%** | preço de passagem oscila várias vezes ao dia; sem isso, cada centavo a menos viraria mensagem |
| Cooldown | **12h** entre alertas do mesmo monitor, mesmo para datas diferentes | evita rajada quando várias datas ficam abaixo do teto de uma vez |

**Contrapartida aceita:** uma oferta melhor em outra data dentro das 12h fica esperando. É o
preço de não virar ruído, e ambos os números são configuráveis.

**Detalhe que importa:** alertas com status `FAILED` **não** contam para o anti-spam. Se a
entrega falhou, o usuário nunca viu a mensagem — deixar esse alerta bloquear um novo faria o
sistema silenciar por causa do próprio defeito.

### D-043 · Decidir e entregar são etapas separadas ✅
**Decisão:** o `AlertService` apenas **cria** alertas com status `PENDING`. O envio é do
`NotificationService` (etapa E1.11).

**Por quê:** permite testar a regra de alerta sem mandar mensagem nenhuma, e reenviar uma
entrega que falhou sem re-decidir se valia a pena alertar. Também deixa o registro honesto:
`PENDING`, `SENT` e `FAILED` são estados distintos e visíveis.

### D-044 · Um único ponto de entrada para processar um monitor ✅
**Decisão:** `SearchCycleService.processarMonitor(id)` é usado tanto pelo scheduler quanto
pelo endpoint manual.

**Por quê:** na primeira versão desta etapa, o endpoint chamava a varredura direto e o
scheduler chamava varredura + alerta. Os dois caminhos divergiram **em silêncio**: uma
varredura manual encontrava oportunidade confirmada e nunca notificava. Só apareceu no teste
ao vivo, porque os testes automatizados exercitavam os dois caminhos separadamente.

**Lição:** quando dois caminhos deveriam fazer a mesma coisa, o jeito de garantir não é
lembrar de atualizar os dois — é não ter dois.

### D-045 · Falha transitória e permanente levam a caminhos opostos ✅
**Decisão:** `DeliveryResult` classifica a falha, e o despachante age conforme.

| Tipo | Exemplos | O que acontece |
|---|---|---|
| **Transitória** | rede instável, HTTP 500 do provedor, timeout | `attempts++`, continua `PENDING` até o limite |
| **Permanente** | número inválido, mensagem recusada por política | `FAILED` de imediato, **sem gastar tentativa** |

**Por quê:** retentar um número inválido três vezes só adia o diagnóstico e gasta cota. E
desistir de um HTTP 500 na primeira tentativa perderia um alerta por um soluço de rede.

**Por que o contador precisou de migration:** sem `attempts`, retentativa vira armadilha — um
canal permanentemente quebrado ficaria em laço infinito. Migration `V3__alert_attempts.sql`.

### D-046 · Entrega imediata, com varredura agendada como rede de segurança ✅
**Decisão:** o alerta é despachado logo após ser criado; um `@Scheduled` a cada 2 min varre
os `PENDING` que sobraram.

**Por quê:** a entrega imediata evita espera sem motivo. A varredura cobre o que ela não
cobre: queda da aplicação entre criar e entregar, retentativa de falha transitória, e canal
que estava fora do ar e voltou. Sem ela, um alerta pendente ficaria parado no banco para
sempre — e o usuário nunca saberia que houve uma oportunidade.

**Ambas chamam o mesmo método.** A lição do [BUG-005](BUGS.md) foi não ter dois caminhos para
a mesma coisa.

### D-047 · ⚠️ Guarda de despacho único, e o limite que ele não cobre
**Decisão:** um `ReentrantLock.tryLock` impede dois despachos simultâneos **nesta instância**.

**Por quê o `SKIP LOCKED` não bastava:** a trava de linha é liberada no commit da
reivindicação, e o envio acontece **depois** disso — fora de transação, por causa da
[D-034](#d-034--nunca-manter-transação-aberta-durante-chamada-http-). Entre commit e envio há
uma janela em que outro despacho pegaria os mesmos alertas. No WhatsApp do usuário isso
apareceria como **notificação repetida**.

`tryLock` e não `lock`: se já há despacho rodando, não há por que esperar — o que sobrar sai
na próxima varredura.

**Limitação assumida:** isto **não** cobre duas instâncias da aplicação. Para isso seria
preciso um estado `SENDING` persistido, com recuperação de alertas travados por queda no meio
do envio. Fica para a Fase 4, quando o deploy em container tornar multi-instância real —
resolver agora seria complexidade especulativa.

### D-048 · O canal fica gravado no alerta, não só na configuração ✅
**Decisão:** `alert.channel` recebe o canal ativo no momento da criação.

**Por quê:** trocar o canal depois não pode reescrever a história de como um alerta antigo foi
entregue. Se o sistema rodou seis meses no `LOG` e migrou para `WHATSAPP`, o histórico precisa
continuar dizendo a verdade sobre cada envio.

### D-049 · ⚠️ O alerta é template, não texto livre
**Fato da plataforma:** a Meta só aceita **template aprovado** em mensagem iniciada pela
empresa. Texto livre vale apenas dentro da janela de 24h depois de o destinatário escrever —
e um monitor que avisa de madrugada nunca está nessa janela.

**Impacto no código:** o `AlertMessageFormatter` passou a produzir **dois formatos** da mesma
oferta:

| Formato | Para quê |
|---|---|
| texto corrido | canal `LOG`, e conferência de como a mensagem fica |
| lista de 6 parâmetros | canal `WHATSAPP` |

**Regras da Meta que os parâmetros respeitam:** nenhum pode ser vazio, conter quebra de linha
ou tabulação, nem ter mais de 4 espaços seguidos. Violar qualquer uma faz a API recusar a
mensagem **inteira** — por isso o campo de detalhes do voo cai para `"sem detalhes do voo"`
em vez de ficar vazio quando a camada 2 não trouxe companhia nem escalas.

**Consequência operacional:** o template precisa ser criado e aprovado na Meta antes do
primeiro alerta real. Passo a passo em [GUIA-WHATSAPP.md](GUIA-WHATSAPP.md).

### D-050 · Classificação dos erros da Meta por código, não por status HTTP ✅
**Decisão:** `MetaErro` mapeia os códigos conhecidos da Graph API para transitório/permanente,
usando o status HTTP apenas como desempate.

**Por quê:** o status sozinho engana. A Meta devolve **HTTP 400** tanto para "número não
verificado" (permanente — retentar nunca vai funcionar) quanto para erros que passam. Sem o
código, ou retentaríamos o que nunca vai dar certo, ou desistiríamos do que passaria.

| Código | Situação | Classificação |
|---|---|---|
| 131030 | número não está na lista de verificados | permanente |
| 190 | token inválido ou expirado | permanente |
| 132001 | template não existe | permanente |
| 131047 | texto livre fora da janela de 24h | permanente |
| 130429 | limite de requisições | **transitória** |
| 131000 | erro interno da Meta | **transitória** |

**Na dúvida, permanente.** Retentar um erro que não vai passar gasta cota e mantém o defeito
escondido atrás de tentativas. Um alerta em `FAILED` com a mensagem da Meta é mais útil que um
alerta pendente para sempre.

**Cada erro permanente carrega explicação em português** — quem lê o `error_message` no banco
descobre o que fazer sem procurar código na documentação da Meta.

### D-051 · HTTP 200 sem `wamid` não é sucesso ✅
**Decisão:** resposta 200 sem id de mensagem é tratada como falha transitória.

**Por quê:** sem o `wamid` não há como rastrear a entrega depois. Marcar como enviado seria
inventar um sucesso que não podemos comprovar — e o histórico de entregas passaria a mentir.

### D-052 · ⚠️ Revoga a D-011: o número de teste não serve para o Brasil
**Fato novo:** a Meta bloqueia mensagens entre países envolvendo Brasil e Indonésia
([BUG-007](BUGS.md)). O número de teste é sempre americano, então **nunca** entregará a um
destinatário brasileiro — independentemente de template, permissão ou verificação.

**O que a [D-011](#d-011--número-de-teste-da-meta-no-lugar-de-chip-dedicado-) errou:** ela
concluiu que o número de teste tornava o WhatsApp gratuito e imediato. Isso era verdade em
2026 para a maioria dos países, e continua sendo — **exceto justamente para o nosso**. A
pesquisa que sustentou a decisão não cobriu restrição geográfica, porque nada indicava que
existisse uma.

**Decisão do usuário:** adotar **número brasileiro próprio**, o caminho da "Etapa 2 —
Configuração de produção" que a D-011 havia descartado.

| | D-011 (revogada) | D-052 (vigente) |
|---|---|---|
| Número | teste da Meta (+1) | próprio, brasileiro |
| Chip | não precisa | precisa, dedicado |
| Pagamento | não precisa | cartão cadastrado |
| Destinatários | até 5 verificados | sem limite |
| Custo | R$ 0 | centavos por alerta |
| **Entrega no Brasil** | **impossível** | funciona |

**O que sobreviveu da D-011:** a arquitetura. O canal está atrás de uma interface, as
credenciais estão em configuração, e o adaptador já está escrito e testado. A migração é
troca de `WHATSAPP_PHONE_NUMBER_ID` e `WHATSAPP_ACCESS_TOKEN` — **nenhuma linha de código**.
O isolamento previsto na regra 3 da seção 3 do plano se pagou.

### D-053 · `wamid` não é comprovante de entrega ✅
**Decisão:** o status `SENT` passa a significar "aceito pela Meta", e não "entregue". A
confirmação real exige webhook.

**Por quê:** a Graph API devolveu `wamid` e `message_status: accepted` para quatro mensagens
que **falharam na entrega**. Tratar isso como sucesso faz o histórico mentir — e no
[BUG-007](BUGS.md) foi exatamente o que escondeu o problema por horas.

**Consequência para o roteiro:** o webhook de status vira etapa própria (E1.17). Com ele,
`SENT` significaria entregue de verdade, e falhas como a 130497 apareceriam em
`alert.error_message` em vez de silêncio.

### D-054 · O E2E do motor troca o worker por HTTP falso, não por um dublê de objeto ✅
**Decisão:** o teste E2E da E1.15 substitui o worker Python por **WireMock**, e não por uma
implementação falsa de `SearchClient`. O canal de entrega usado é o `LOG` real, fixado por
propriedade no próprio teste.

**Por quê:** um dublê de `SearchClient` pularia justamente o que já quebrou neste projeto —
serialização em snake_case, negociação de versão do HTTP ([BUG-004](BUGS.md)) e timeout. E o
canal `LOG` lê o telefone do destinatário **fora de transação**, que é exatamente a condição
do [BUG-006](BUGS.md); um canal falso que ignora a entidade não detectaria a regressão.

**Por que fixar `flightmonitor.notification.canal=LOG` no teste:** o `application.yml` importa
o `.env` da raiz. No dia em que ele trouxer `NOTIFICATION_CHANNEL=WHATSAPP`, a suíte mandaria
mensagem de verdade — e cobrada. A trava fica no teste, não na disciplina de quem edita o
`.env`.

**Consequência:** o `SearchCycleServiceTest` continua existindo com o dublê de objeto. Os dois
níveis se complementam: um cobre a lógica do ciclo em milissegundos, o outro cobre a costura.

### D-055 · O cenário de teste é escolhido pela rota, não por um parâmetro novo ✅
**Decisão:** no E2E entre serviços, o desfecho de cada cenário é selecionado por **códigos IATA
reservados no destino** (`ZZA` a `ZZE`), interpretados pelo worker. O core-java não ganha
nenhum parâmetro, cabeçalho ou modo de teste.

**Por quê:** a regra 3 da seção 3 do [PLANO-DE-ACAO](PLANO-DE-ACAO.md) diz que o motor não pode
conter código que exista por causa de teste. Um campo `modoDeTeste` no comando de busca
violaria isso — e, pior, teria que ser mantido para sempre. O core **já** manda origem e
destino; usar o que já existe custa zero no lado de produção.

A faixa `ZZ*` não é atribuída pela IATA, então nenhum código de cenário pode colidir com um
aeroporto que alguém queira monitorar de verdade.

**Consequência:** o mapa de cenários vive num lugar só, `worker-python/app/providers/fake.py`, e
o teste Java o consulta por comentário, não por acoplamento.

### D-056 · `USE_FAKE_PROVIDERS` só existe como variável de processo, nunca no `.env` ✅
**Decisão:** a chave que troca as fontes reais por falsas é lida do ambiente e **não** é
adicionada ao `.env` nem ao `.env.example`. Só o script `scripts/e2e_servicos.py` a define.

**Por quê:** um `USE_FAKE_PROVIDERS=true` esquecido no `.env` faria o sistema inventar preços em
silêncio — varreduras "bem-sucedidas", observações gravadas, alertas enviados, tudo falso, e
nenhum sinal de erro em lugar nenhum. É a pior categoria de falha: a que parece sucesso.

Por isso também o padrão é `False` no código, o worker registra **aviso em log** toda vez que
uma camada falsa é montada, e o `E2EServicosTest` tem um teste — `estaFalandoComOWorkerFalso` —
cuja única função é falhar caso o teste esteja conversando com o worker real.

### D-057 · `ACCEPTED` e `SENT` são estados diferentes, e o canal decide qual usar ✅
**Decisão:** um envio bem-sucedido vira `ACCEPTED` quando o canal declara
`confirmacaoAssincrona() == true`, e `SENT` quando não. Só o webhook promove `ACCEPTED` a
`SENT`.

**Por quê:** cumpre a [D-053](#d-053--wamid-não-é-comprovante-de-entrega-) sem transformar todo
canal em refém de webhook. O canal `LOG` entrega de forma síncrona e certa — não existe
intermediário que possa desmentir, e forçá-lo a esperar confirmação criaria alertas presos para
sempre. O WhatsApp é o oposto: o `wamid` é número de protocolo.

A pergunta fica **no canal**, não no despachante, porque é o canal que sabe como a própria
entrega se confirma. O padrão do método é `false`: um canal novo é tratado como confiável a
menos que declare o contrário. Errar para "confio" deixa o histórico otimista; errar para o
outro lado deixaria alertas presos em `ACCEPTED` esperando um webhook que nunca vem.

**Consequência visível:** com o webhook desligado, alertas do WhatsApp param em `ACCEPTED`. É
desconfortável de propósito — é exatamente o que sabemos.

### D-058 · O webhook responde 200 até para o que não entende ✅
**Decisão:** corpo ilegível, `wamid` desconhecido e até exceção nossa respondem `200`. O único
`401` é assinatura inválida.

**Por quê:** a Meta retenta o que não recebe `200` e, depois de falhas repetidas, **desativa a
assinatura do webhook**. Perder o webhook custa muito mais do que perder um lote: foi
justamente não ter webhook que escondeu o [BUG-007](BUGS.md) por horas.

`wamid` desconhecido nem é anomalia — a conta pode ter mensagens que este sistema não enviou, e
a Meta notifica todas.

**Assinatura inválida é a exceção** porque aí não existe assinatura da Meta a preservar: ou é
engano de configuração, ou é alguém tentando escrever no nosso banco.

## Parte IV — Fase 2 (Inteligência)

### D-059 · ⚠️ Revisa o roteiro: as estatísticas ficam no Java, não no worker
**O que o roteiro dizia:** *"E2.1 — Estatísticas de rota **no worker**"*.

**O que foi feito:** implementado em `core-java`, no pacote `stats`, com as agregações em SQL.

**Por quê — o próprio plano já dizia isso em outro lugar.** Duas das quatro regras invioláveis
da seção 3 apontam para o Java:

> **Regra 1.** O Java é o dono do banco. O Python nunca acessa o PostgreSQL diretamente.
> **Regra 2.** O worker Python é um especialista burro. (…) **não decide se um preço é bom**.

O histórico vive no banco, e o banco é do Java. Para o worker calcular a mediana, ou ele
acessaria o PostgreSQL — violando a regra 1 — ou o Java teria que despejar milhares de linhas
por HTTP a cada consulta, o que é mais lento e mais frágil do que uma varredura de tabela. E
definir o que é "normal" para uma rota é o primeiro passo de decidir se um preço é bom, que a
regra 2 tira do worker.

Some-se o argumento técnico: `percentile_cont`, `stddev_samp` e os quartis são exatamente o que
o PostgreSQL faz bem, numa passada só sobre um índice. O código Python equivalente seria mais
linhas para fazer pior.

**Precedente que já existia:** `menorPrecoDaRota` e `precoMedioDaRota` estão no
`PriceObservationRepository` desde a E1.8, como agregações SQL. A E2.1 seguiu o caminho que o
projeto já tinha aberto, em vez de criar um segundo.

**O que continua sendo do worker:** coleta. E, se a Fase 2 chegar a algo que o SQL não faça —
regressão, sazonalidade, previsão —, aquilo vai para o Python, onde o ecossistema paga o custo
da fronteira.

### D-060 · Estatística com fonte explícita: cache e preço confirmado não se misturam ✅
**Decisão:** toda estatística declara sobre qual conjunto foi calculada — `TODAS` ou
`CONFIRMADAS` — e a resposta carrega essa etiqueta.

**Por quê:** medimos divergências de 61%, 69% e 81% entre o preço cacheado da camada 1 e o preço
real da camada 2, **sempre na mesma direção**: o cache subestima. Misturar os dois produz uma
média que não descreve nenhum dos dois mundos.

O perigo é específico e silencioso: como o cache puxa a média para baixo, um preço real
legítimo passa a parecer *acima* da média. Uma detecção de anomalia (E2.2) alimentada pela
mistura ficaria **calada exatamente quando deveria falar** — e um sistema de alerta que não
alerta é indistinguível de um sistema desligado.

**Consequência:** `CONFIRMADAS` costuma ter poucas amostras, porque só uma observação por
varredura passa pela camada 2. Por isso a resposta traz `amostras` e `confiavel`: poucos dados
continuam sendo devolvidos, e vêm marcados.

### D-061 · Poucas amostras não escondem o número, mas o marcam ✅
**Decisão:** abaixo de `flightmonitor.stats.minimo-amostras` (padrão 8), as estatísticas são
calculadas e devolvidas normalmente, com `confiavel: false`.

**Por quê:** as duas alternativas são piores. Esconder o número faria o painel mostrar "sem
dados" quando há dados — e o usuário tem direito de ver o pouco que existe. Devolver sem
ressalva faria a E2.2 disparar anomalia sobre três observações, e alarme falso destrói a
confiança no sistema mais rápido do que ausência de alarme.

O `desvioPadrao` segue a mesma lógica levada ao limite: com uma amostra ele é **nulo**, não
zero. Zero afirmaria "esta rota não varia", que é uma conclusão; nulo diz "não dá para saber",
que é o fato.

### D-062 · Anomalia por regra de Tukey, não por z-score ✅
**Decisão:** um preço é atípico quando fica abaixo de `p25 - 1,5 × (p75 - p25)` — o bigode
inferior de um boxplot.

**Por quê:** z-score ("quantos desvios padrão abaixo da média") pressupõe distribuição
simétrica, e preço de passagem não é nem perto disso. A cauda longa para cima infla o desvio
padrão, e o detector passa a exigir uma queda enorme para reagir — **justamente na rota onde
uma passagem cara distorceu a amostra**.

A regra de Tukey nasce dos quartis, então um valor absurdo na amostra não a desloca. Um dos
testes fixa isso: mesma rota, mesmos quartis, máximo saltando de R$ 6.000 para R$ 40.000, e o
limite continua idêntico.

**Efeito colateral bem-vindo:** o limiar se adapta à volatilidade da rota. Numa rota estável,
uma queda pequena já é atípica; numa volátil, a mesma queda é terça-feira. Um limiar percentual
fixo — "15% abaixo da média" — não distingue as duas.

**Limitação conhecida e documentada:** quando o limite cai abaixo do mínimo já observado, o grau
`EXCELENTE` fica inalcançável, porque todo preço abaixo do limite também é recorde. Não é
defeito: uma rota cujo menor preço já visto está dentro da faixa comum nunca teve um extremo,
então o próximo preço muito baixo é corretamente notícia de recorde.

### D-063 · Sem amostra suficiente, o veredito é "não sei" — nunca "normal" ✅
**Decisão:** com menos de `minimo-amostras` observações, a anomalia devolve `SEM_DADOS`, e não
um grau calculado.

**Por quê:** `NORMAL` afirma *"eu medi e este preço não tem nada de especial"*. `SEM_DADOS` diz
*"eu não medi"*. Colapsar os dois faria a E2.4 escrever "preço dentro do normal" sobre uma rota
que nunca foi medida — uma afirmação falsa com cara de análise.

O outro lado é pior ainda: deixar passar transformaria o alerta num gerador de superlativo sobre
três observações. **Alarme falso custa mais que alarme ausente**, porque ensina o usuário a
ignorar a notificação — e um sistema ignorado é pior que um desligado, já que dá falsa sensação
de cobertura.

### D-064 · No Flight Score, aspecto sem dado tem nota nula — nunca zero ✅
**Decisão:** quando a observação não traz duração ou horário, o aspecto sai com nota `null`, o
peso dele **sai da conta**, e a nota final é renormalizada sobre o peso restante.

**Por quê:** pontuar zero pelo que não se sabe é ativamente nocivo. Observações da camada 1 não
têm duração nem horário — então toda oferta ainda não confirmada apareceria com nota baixa, e o
sistema passaria a **preferir voos por terem mais dados, não por serem melhores**. É viés de
medição virando recomendação.

**Consequência:** duas notas iguais podem ter origens diferentes, e isso precisa ficar visível.
Por isso existe a `cobertura` — a fração do peso que pôde ser avaliada. Apresentar "100" com
cobertura 1,0 e "100" com cobertura 0,7 como o mesmo número seria mentira por omissão.

### D-065 · Preço e duração são pontuados contra a própria rota ✅
**Decisão:** nenhum limite absoluto. O preço é posicionado nos quantis da rota; a duração é
comparada à **melhor duração já vista naquela rota**.

**Por quê:** R$ 3.000 é caro para São Paulo–Rio e barato para São Paulo–Tóquio. Dez horas é ótimo
para Lisboa e absurdo para Curitiba. Qualquer constante em reais ou em horas estaria errada para
quase toda rota — e erraria em silêncio.

**Detalhe que evita uma armadilha:** o preço é interpolado **entre quantis**, e não entre mínimo
e máximo. Um único preço absurdo no histórico comprimiria a escala inteira e faria quase tudo
parecer bom. É a mesma razão que levou à regra de Tukey na [D-062](#d-062--anomalia-por-regra-de-tukey-não-por-z-score-).

### D-066 · Os pesos do score são configuração, e a nota vem decomposta ✅
**Decisão:** os quatro pesos ficam em `flightmonitor.score.*`, e o `FlightScore` sempre devolve
os componentes, cada um com nota, peso e motivo.

**Por quê:** não existe peso certo. Quanto uma escala vale em relação a duzentos reais é
preferência pessoal, e quem escreve o código não tem autoridade sobre a viagem de quem usa.
Deixá-los em configuração torna a opinião **visível e ajustável**, em vez de escondida em quatro
números mágicos no meio de um método.

A decomposição resolve o outro problema: uma nota única é inauditável. Quando ela sai 62,
ninguém sabe se foi o preço, a escala ou o voo de madrugada — e sem saber isso o número não
ajuda a decidir nada.

**O preço pesa mais que os outros três somados não é acidente:** o sistema existe para achar
passagem barata. Uma nota que premiasse conforto acima de preço contradiria o próprio produto.

**Ponto de partida da E2.6:** lá os pesos passam a poder ser sobrescritos por monitor — quem vai
a trabalho e quem vai passear não querem a mesma coisa.

### D-067 · O enriquecimento do alerta é opcional campo a campo — na dúvida, silêncio ✅
**Decisão:** a comparação histórica e a nota só entram na mensagem se cada uma se sustentar
sozinha. Um monitor sem histórico recebe exatamente a mensagem de antes da E2.4 — limpa, sem
ressalva e sem número inventado.

**Por quê:** as três etapas anteriores foram construídas para admitir ignorância — a E2.1 marca
estatística com poucas amostras, a E2.2 devolve `SEM_DADOS` em vez de `NORMAL`, a E2.3
renormaliza o que não pode medir. Seria uma pena jogar tudo isso fora no último passo,
escrevendo *"nota preliminar 68"* no WhatsApp de alguém.

Escrever "não temos histórico suficiente" também está fora: o usuário quer saber da passagem,
não do estado do nosso banco.

**Consequência aceita:** o enriquecimento **aparece com o tempo**, conforme a rota acumula
histórico confirmado — e só uma observação por varredura passa pela camada 2. É o preço de não
chutar.

**Onde o filtro mora:** em `AlertInsights.de(...)`, e não espalhado por quem escreve a mensagem.
Um campo que não se sustenta simplesmente não existe no objeto, então nenhum formatador precisa
lembrar de checar.

### D-068 · O alerta grava a análise; não a recalcula na entrega ✅
**Decisão:** `flight_score`, `anomaly_grade` e `anomaly_drop_pct` são colunas de `alert`,
preenchidas quando o alerta é criado (migração V5).

**Por quê — duas razões, e a segunda é a que importa mais:**

1. **Técnica:** a entrega acontece fora de transação, com a entidade desanexada (a lição do
   [BUG-006](BUGS.md)). Recalcular estatística dentro do adaptador do WhatsApp seria ir ao banco
   da camada errada, no momento errado.
2. **De integridade:** o alerta deve registrar o que sabia **quando decidiu**. Um número
   recalculado na entrega — ou pior, ao ler o histórico meses depois — poderia divergir do que
   motivou o alerta, e o registro passaria a mentir sobre o próprio passado.

**Todas as colunas aceitam NULL**, e isso é deliberado: alerta de rota sem histórico não tem
análise, e `NULL` diz exatamente isso. Zero diria "nota zero", que é outra coisa — a mesma
distinção que governa a [D-064](#d-064--no-flight-score-aspecto-sem-dado-tem-nota-nula--nunca-zero-).

### D-069 · O template do WhatsApp continua com cinco parâmetros ✅
**Decisão:** o enriquecimento entra **dentro** dos parâmetros existentes — `{{3}}` ganha a nota,
`{{4}}` ganha a comparação — em vez de virar parâmetros novos.

**Por quê:** mudar a estrutura do template exige criar um template novo e esperar nova aprovação
da Meta. Isso já custou duas rodadas neste projeto: uma por reclassificação para MARKETING
(frase promocional que eu havia escrito no corpo) e outra pela conta errada
([BUG-009](BUGS.md)). Enriquecer o **conteúdo** dos parâmetros não precisa de aprovação nenhuma
— eles são valores de execução.

Os dois escolhidos foram os que o texto fixo do template já introduz de um jeito que aceita
complemento sem virar frase torta:

```
Detalhes do voo: Iberia, 1 escala, nota 82/100
Preço encontrado: R$ 2.900,00 (17.1% abaixo da mediana)
```

**Diferença que ficou fixada em teste:** o texto livre mantém o espaço não-quebrável do `pt-BR`
entre `R$` e o valor — é o que impede a moeda de ficar órfã numa quebra de linha. Nos parâmetros
do template ele precisa sair, porque a Meta recusa.

### D-070 · Tendência por Theil-Sen, não por mínimos quadrados ✅
**Decisão:** a inclinação da série é a **mediana das inclinações entre todos os pares de
pontos**, e não a reta de regressão clássica.

**Por quê:** mínimos quadrados minimiza o **quadrado** dos erros, então um único dia atípico —
promoção relâmpago, erro momentâneo da fonte — inclina a reta inteira. Numa série de dez a
quinze pontos, que é o que este projeto vai ter por muito tempo, um ponto pesa 10% da conclusão.

Theil-Sen tolera cerca de 29% de pontos corrompidos sem mudar de resposta. Dois testes fixam
isso: uma alta clara com um dia de R$ 900 no meio continua sendo alta, e uma queda com **dois**
dias de R$ 9.000 continua sendo queda.

É a terceira vez que a mesma escolha aparece no projeto — mediana em vez de média na
[D-060](#d-060--estatística-com-fonte-explícita-cache-e-preço-confirmado-não-se-misturam-),
Tukey em vez de z-score na [D-062](#d-062--anomalia-por-regra-de-tukey-não-por-z-score-). Preço
de passagem tem cauda longa, e todo estimador sensível a extremo erra na mesma direção.

### D-071 · A variação vira porcentagem por semana ✅
**Decisão:** a inclinação em reais por dia é normalizada pela mediana da série e expressa em
**% por semana**.

**Por quê:** reais por dia não dizem nada sozinhos — R$ 20/dia é ruído numa rota de R$ 8.000 e
movimento forte numa de R$ 600. Um teste fixa a consequência: duas rotas em patamares de R$ 600
e R$ 6.000, caindo proporcionalmente igual, devolvem exatamente o mesmo número.

A semana foi escolhida porque é a unidade em que uma pessoa pensa ao decidir se espera mais um
pouco. "0,4% ao dia" exige conta mental; "3% por semana" não.

### D-072 · A tendência informa o movimento e não dá conselho de compra ✅
**Decisão:** o texto diz *"o preço vem caindo cerca de 7% por semana"*. Nunca *"espere"* ou
*"compre agora"*. Há um teste que proíbe essas palavras na saída.

**Por quê:** tendência recente é indício, não previsão. Preço de passagem responde a fatores que
este sistema não observa — feriado, evento, mudança de malha, promoção de concorrente. Uma queda
de duas semanas pode inverter no dia seguinte.

Transformar indício em conselho seria prometer o que o sistema não sabe, e a primeira vez que o
conselho errasse feio custaria a confiança em tudo o mais que ele diz. Informar o movimento e
deixar a decisão com quem viaja é honesto **e** mais útil.

### D-073 · Preferência não é exigência: o filtro age no candidato, não no histórico ✅
**Decisão:** uma oferta de companhia evitada **continua sendo gravada** no histórico; o que ela
não faz é virar candidata a confirmação e a alerta.

**Por quê:** o histórico pertence à **rota**, não ao gosto de quem monitora
([D-016](#d-016--o-histórico-de-preços-pertence-à-rota-não-ao-monitor-)). A estatística descreve
o mercado — se a Iberia opera metade da rota, tirá-la da amostra produziria uma mediana que não
existe em lugar nenhum, e todo preço passaria a parecer caro.

O mesmo vale para `prefereVooDireto`: ele **endurece a curva** de escalas na nota, e não exclui.
Um voo com conexão muito mais barato ainda pode vencer. Quem quer exclusão de verdade usa
`maxStops`, que é limite rígido enviado à fonte — o voo nem chega a ser buscado.

### D-074 · Os pesos do score passam a ser por monitor, com sobrescrita campo a campo ✅
**Decisão:** cada monitor pode definir `pesoPreco`, `pesoEscalas`, `pesoDuracao` e
`pesoHorario`. Ausente usa o global; **zero é escolha válida**.

**Por quê:** era a promessa deixada em aberto na
[D-066](#d-066--os-pesos-do-score-são-configuração-e-a-nota-vem-decomposta-). Quem viaja a
trabalho quer voo direto de manhã e paga por isso; quem viaja a passeio aceita escala e
madrugada para economizar. A mesma nota não serve para os dois.

**Campo a campo, e não tudo ou nada:** quem só quer dizer *"escala me incomoda muito"* não
deveria ser obrigado a reinventar os outros três pesos — e provavelmente escolheria pior do que
o padrão.

**Zero e nulo significam coisas diferentes**, e o código trata os dois:

| Valor | Significado |
|---|---|
| `null` | "não escolhi" → usa o peso global |
| `0` | "este aspecto não me importa" → sai da conta, e a cobertura reflete isso |

No caminho da configuração, porém, zero significa "esqueci de preencher" e vira o padrão. Os
dois construtores de `ScoreProperties` existem por isso, e o de configuração precisa de
`@ConstructorBinding` — sem ele o Spring não sabe qual usar e falha na subida com "No default
constructor found", que aponta para o lugar errado.

### D-075 · ⚠️ Revisa o roteiro: bagagem e aeroporto alternativo ficam fora da E2.6
**O que o roteiro pedia:** *"voo direto, evitar cia X, bagagem, aeroporto alternativo"*.

**O que foi entregue:** os dois primeiros, mais os pesos do score. Os outros dois ficaram fora,
por motivos diferentes.

**Bagagem — falta o dado, não o código.** Nenhuma das duas fontes devolve franquia de bagagem:
não está no `FlightOffer` da Travelpayouts nem no `ConfirmedOffer` do fast-flights. Guardar a
preferência assim criaria um campo que **parece funcionar e não filtra nada** — a pior espécie
de funcionalidade, porque o usuário confia nela. Implementar exige antes uma fonte que forneça o
dado, e isso é uma decisão de coleta, não de preferência.

**Aeroporto alternativo — cabe, mas não aqui.** Aceitar GRU ou VCP para o mesmo monitor exige
que a varredura abra em **vários pares de rota**, e aí aparece a pergunta difícil: a observação
de VCP→LIS pertence a qual histórico? Se for ao de GRU→LIS, a [D-016](#d-016--o-histórico-de-preços-pertence-à-rota-não-ao-monitor-)
deixa de valer; se for ao próprio, a estatística do monitor se parte em duas. É uma etapa com
desenho próprio, não um campo a mais.

## Parte V — Fase 3 (Agente)

### D-076 · A interpretação de linguagem natural vive no worker Python ✅
**Decisão:** o `POST /nlp/intent` fica no worker; o core expõe `POST /api/agent/interpret`, que
delega e depois aplica as regras de negócio.

**Por quê:** é tarefa de **especialista** — recebe texto, devolve campos. Não toca no banco, não
conhece monitores, não decide se um preço é bom, então as regras 1 e 2 da seção 3 continuam
valendo. E o ecossistema de LLM é do Python.

**A divisão de trabalho é a mesma da coleta:** o worker relata o que leu; o core decide o que
fazer com isso. Por exemplo, "origem igual ao destino" é um problema **do core** — o worker
apenas informa as duas cidades que encontrou.

**Contraste deliberado com a [D-059](#d-059--️-revisa-o-roteiro-as-estatísticas-ficam-no-java-não-no-worker):**
lá as estatísticas ficaram no Java porque dependem do banco, que é do Java. Aqui não há banco
envolvido, e a tarefa é justamente do tipo que o worker existe para fazer. A regra não é "tudo
no Java" nem "tudo no Python" — é **onde o dado está**.

### D-077 · Interpretação em cadeia: modelo primeiro, regras sempre por último ✅
**Decisão:** a cadeia tenta o LLM (quando há chave) e cai para um interpretador **determinístico
por regras**, que nunca sai da lista.

**Por quê — três motivos, e nenhum é economia:**

1. **É a rede de segurança.** Sem chave, com a API fora do ar ou com a cota esgotada, a
   interpretação continua respondendo. Mesma política da camada 2 de coleta: degradar, não
   morrer;
2. **É o teste do resto.** Um teste que depende de LLM é caro, lento e não-determinístico — a
   mesma frase pode virar duas respostas. Com as regras, a mesma entrada dá sempre a mesma
   saída, e o contrato fica coberto de verdade;
3. **Cobre o jeito comum de pedir.** *"Quero ir pra Lisboa em março por até 4 mil"* não precisa
   de modelo nenhum.

**Consequência:** a resposta traz `provider`, dizendo quem interpretou, e um aviso quando a
opção preferida falhou. Degradação silenciosa seria pior do que não degradar.

**Resolve a P-4** — a pergunta era "qual LLM usar". A resposta é: um configurável, com
`claude-sonnet-5` como padrão, e o sistema funcionando sem nenhum.

### D-078 · O modelo devolve nome de cidade; a tradução para IATA é nossa ✅
**Decisão:** nem o LLM nem o interpretador por regras produzem código IATA. Os dois devolvem
**nome de cidade**, e a conversão acontece numa tabela em `app/nlp/aeroportos.py`.

**Por quê:** modelos erram código de aeroporto com frequência — são milhares, e GIG e SDU diferem
por uma letra — enquanto acertam nome de cidade quase sempre. Cada um no que é bom.

E o erro é caro de um jeito específico: um código errado não dá erro em lugar nenhum. O monitor
passa meses vigiando a rota errada, em silêncio, e a única pista é a ausência de alertas.

**Cidade desconhecida vira `null` e aviso, nunca um chute.** Perguntar custa um segundo; chutar
custa meses.

### D-079 · A data de hoje viaja no pedido, em vez de cada processo ler o próprio relógio ✅
**Decisão:** o core envia `hoje` no corpo do `POST /nlp/intent`.

**Por quê:** *"em março"* significa coisas diferentes conforme quando se pergunta, e os dois
processos podem estar em containers ou fusos diferentes. Deixar cada um consultar o próprio
relógio faria a mesma frase produzir datas diferentes — e o pior é que funcionaria quase sempre,
falhando só na virada do dia ou do ano.

**Efeito colateral que vale sozinho:** os testes fixam `hoje` e param de depender do calendário.
Sem isso, o teste de "mês que já passou vai para o ano seguinte" passaria a falhar sozinho em
algum momento futuro — do tipo que se descobre num sábado.

### D-080 · Nada é assumido em silêncio na criação por conversa ✅
**Decisão:** todo valor que o texto não disse e que o monitor precisa ter aparece em
`assumido`, na resposta, com o valor escolhido.

**Por quê:** um monitor tem campos que a frase quase nunca menciona — passageiros, intervalo de
varredura, permanência, destinatário. Preencher em silêncio é a forma mais educada de mentir: a
pessoa acha que pediu uma coisa e recebeu outra, e só descobre quando o alerta não chega, ou
chega errado.

```json
"assumido": [
  "destinatario: Leonardo (o unico ativo cadastrado)",
  "1 passageiro",
  "varredura a cada 6 horas",
  "sem restricao de permanencia",
  "escalas permitidas, mas penalizadas na nota"
]
```

É o mesmo princípio que governou a Fase 2 inteira — dizer o que não se sabe — aplicado ao que
**se escolheu por conta própria**.

### D-081 · Com um destinatário ativo só, ele é usado; com vários, nenhum ✅
**Decisão:** pedido sem `recipientIds` usa o único destinatário ativo, se houver exatamente um.
Com vários, o monitor nasce sem destinatário e **com aviso**.

**Por quê:** o sistema é de uso pessoal por desenho ([D-005](#d-005--sem-cadastro-de-usuários-sem-e-mail-sem-telegram-)),
e com um destinatário só não existe outra escolha possível — perguntar seria burocracia. Com
vários, qualquer escolha seria adivinhação, e adivinhar quem recebe uma mensagem é pior do que
não mandar.

**O aviso importa mais do que parece:** monitor sem destinatário busca preço, grava histórico e
**nunca avisa ninguém**. Sem a frase explícita, o silêncio pareceria "não achei nada barato".

### D-082 · Reenviar a mesma frase não cria um segundo monitor ✅
**Decisão:** monitor ativo com a mesma rota e janela de partida **sobreposta** faz o pedido
falhar com 409, apontando o id do que já existe.

**Por quê:** reenviar é o acidente mais provável de um endpoint conversacional — a pessoa não
tem certeza se funcionou e manda de novo. Dois monitores iguais dobram as buscas nas fontes
externas e os alertas no celular de quem recebe.

**Sobreposição e não igualdade:** *"Lisboa em março"* e *"Lisboa entre 10 e 20 de março"* são o
mesmo pedido dito de dois jeitos. Exigir janelas idênticas deixaria passar justamente o caso
comum.

**Monitor inativo não bloqueia:** desligado, ele não busca nem alerta, então não há duplicação.

### D-083 · Pedido incompleto responde 422, e não 400 ✅
**Decisão:** falta de destino, período ou preço devolve **422 Unprocessable Entity**, com a
interpretação e a lista do que falta no corpo.

**Por quê:** a sintaxe do pedido está correta — o conteúdo é que não basta. Para o painel são
duas coisas diferentes: 400 é erro de programação, 422 é conversa que continua.

E o corpo do 422 traz a interpretação parcial, então a interface pode perguntar **exatamente**
o que falta em vez de mandar a pessoa reescrever tudo.

### D-084 · ⚠️ A recomendação NÃO usa modelo de linguagem
**Decisão:** o texto da E3.3 é composto deterministicamente, em Java, a partir das quatro
análises da Fase 2. Nenhuma chamada a LLM.

**A tentação era óbvia:** mandar anomalia, score e tendência para um modelo e pedir um parágrafo
bonito. Foi avaliado e descartado, por dois motivos:

1. **Não há o que interpretar.** A entrada já é um conjunto de fatos calculados, cada um com
   frase pronta em português produzida pelas E2.2, E2.3 e E2.5. O que sobra é composição — juntar
   na ordem certa, separar o que pesa a favor do que pesa contra. Isso é determinismo, não
   linguagem;
2. **O risco é assimétrico.** O ganho seria fluência. A perda possível é uma frase inventada —
   *"melhor momento para comprar"*, *"promoção por tempo limitado"* — que este sistema passou
   três fases se recusando a dizer. Verificar que um modelo não acrescentou nada é mais trabalho
   do que escrever a composição.

**Onde o modelo continua fazendo sentido:** na E3.1, interpretando o pedido de quem escreve.
Ali existe ambiguidade de verdade — "daqui a uns três meses", "não muito cedo de manhã" — e a
tabela de aeroportos limita o estrago de um erro. Aqui não há ambiguidade, há aritmética.

**Se um dia mudar:** o caminho seria uma camada de *reescrita*, recebendo as razões prontas e
proibida de acrescentar fatos — com os mesmos testes de "não dá conselho" rodando sobre a saída.

### D-085 · O resumo vem com as razões, e não no lugar delas ✅
**Decisão:** `Recomendacao` sempre devolve a lista de `razoes` — aspecto, lado e frase — além do
parágrafo pronto.

**Por quê:** o resumo é para ler; as razões são para conferir. Uma recomendação sem a lista seria
opinião sem prestação de contas, e a primeira vez que ela errasse não haveria como descobrir de
onde saiu.

É a mesma escolha da [D-066](#d-066--os-pesos-do-score-são-configuração-e-a-nota-vem-decomposta-),
onde o Flight Score passou a vir decomposto pelo mesmo motivo: **número único é inauditável**.

### D-086 · Tendência de queda é "a ponderar", nunca "contra" ✅
**Decisão:** as razões têm três lados — `A_FAVOR`, `CONTRA` e `A_PONDERAR` — e a tendência de
queda cai sempre no terceiro.

**Por quê:** um preço em queda **não piora a oferta**. Ele torna razoável esperar, que é outra
coisa. Marcar como "contra" transformaria uma constatação em conselho disfarçado — exatamente o
que a [D-072](#d-072--a-tendência-informa-o-movimento-e-não-dá-conselho-de-compra-) proíbe.

O terceiro lado existe por causa disso: sem ele, toda informação teria que ser espremida em bom
ou ruim, e a que não é nem um nem outro seria distorcida para caber.

## Parte VI — Fase 4 (Infraestrutura)

### D-087 · O AMQP troca o transporte, não a semântica ✅
**Decisão:** o adaptador de mensageria usa **request/reply** — publica o pedido, espera a
resposta na fila temporária, devolve. Do ponto de vista de quem chama, é idêntico ao REST.

**Por quê:** a alternativa era tornar a busca de fato assíncrona — publicar e seguir,
processando a resposta depois. Ela quebraria duas coisas que custaram caro:

1. **O `processarMonitor` deixaria de ser caminho único.** O endpoint manual precisaria de um
   segundo fluxo, que é exatamente a forma do [BUG-005](BUGS.md) — dois caminhos para a mesma
   coisa, divergindo em silêncio;
2. **O estado entre pedido e resposta precisaria viver em algum lugar**, e a varredura viraria
   uma máquina de estados distribuída para resolver um problema que este sistema ainda não tem.

**O que se ganha mesmo sem assincronia:** o broker desacopla os dois processos, a fila absorve
rajada, o worker pode ser replicado sem o core saber, e mensagem que ninguém processa fica
visível na dead-letter em vez de sumir.

**A troca custou zero no motor**, e não por sorte: a `SearchClient` existe como porta desde a
E1.7, com o javadeoc citando esta etapa nominalmente. A [D-006](#d-006--rest-no-mvp-mensageria-depois-)
prometeu que "a troca fica prevista na arquitetura, não é retrabalho" — e foi o que aconteceu.

### D-088 · Quem recebe declara o tipo da resposta ✅
**Decisão:** o adaptador usa `convertSendAndReceiveAsType`/`fromMessage` com o tipo esperado. O
worker **não** envia cabeçalho `__TypeId__`.

**Por quê:** a primeira versão fazia o Python anunciar o nome da classe, e o Spring recusou com
*"not in the trusted packages"*. A correção não foi liberar o pacote — foi **tirar o cabeçalho**.

Fazer o worker anunciar nomes de classe do outro lado é acoplamento na direção errada: o Python
passaria a depender do desenho interno do Java para conseguir responder, e renomear uma classe
Java quebraria o worker.

### D-089 · O core é o dono da topologia; o worker espera ✅
**Decisão:** exchanges, filas e dead-letter são declaradas pelo **core**. O worker apenas
consome, e **espera a fila existir** — até um minuto, tentando a cada três segundos.

**Por quê:** os dois lados poderiam declarar, já que o AMQP torna isso idempotente. Mas com dois
donos, uma divergência de argumento (durable, dead-letter, TTL) vira erro de canal na conexão, e
**o serviço que subir primeiro ganha** — uma corrida que aparece como falha intermitente.

A espera é a consequência necessária: num `docker compose up`, o worker sobe antes do core, e a
fila ainda não existe. Sem ela, **a ordem de subida dos containers decidiria se o sistema
funciona** — a pior forma de dependência, porque muda de máquina para máquina.

### D-090 · A falha da fonte viaja num cabeçalho, e não no corpo ✅
**Decisão:** quando a camada 1 falha, o worker responde com o cabeçalho `x-fonte-falhou` e o
motivo. O adaptador o lê **antes** de converter o corpo e levanta `WorkerUnavailableException`.

**Por quê:** é o equivalente AMQP do HTTP 502, e ele existe porque a primeira versão não tinha.
Sem o cabeçalho, o worker respondia varredura vazia quando a fonte caía, e o core registrava a
busca como **bem-sucedida sem ofertas** — o monitor voltaria à fila no intervalo normal em vez
de retentar, e o painel mostraria "nenhuma oferta" para uma fonte fora do ar.

A distinção entre "a fonte morreu" e "a janela está vazia" é a mesma que `returned` e `kept`
preservam dentro da resposta, e ela não podia se perder só por trocar de transporte.

**Cabeçalho e não campo novo no JSON:** o schema é compartilhado com o transporte REST, onde a
informação já viaja no status HTTP. Metadado de resultado fica em metadado.

### D-091 · BCE por feature, e não por camada ✅
**Decisão:** cada feature do core-java (`monitor`, `alert`, `search`, `stats`, `agent`) tem suas
próprias `entity/`, `control/` e `boundary/`. O worker espelha o mesmo desenho.

**Por quê:** a alternativa — `controllers/`, `services/`, `entities/` na raiz — agrupa por *o que
a classe é*, e não por *do que ela trata*. Mexer no alerta abriria três pastas distantes, e cada
pasta cresceria até virar um índice alfabético de classes sem relação entre si.

Com feature primeiro, o raio de uma mudança fica visível na estrutura: a E4.1 trocou REST por
mensageria e tocou só em `search/boundary/client/`. Quem lê o diretório vê o que o sistema faz,
e não com que framework ele foi escrito.

**Por que BCE e não Hexagonal ou Clean:** BCE tem três camadas, não quatro ou cinco, e os nomes
já são os do UML que a equipe conhece. Camada a mais sem regra a mais é cerimônia — e a
experiência da própria reorganização mostrou o custo disso (ver D-093).

### D-092 · A porta pertence ao controle; o adaptador, à borda ✅
**Decisão:** `SearchClient` e `NlpPort` são interfaces em `control/`. `RestSearchClient`,
`AmqpSearchClient` e `RestNlpClient` são classes em `boundary/`.

**Por quê:** a primeira versão da reorganização colocou `SearchClient` em `boundary/`, e o
ArchUnit reprovou com **157 violações** de "controle não conhece a borda". A regra estava certa e
a pasta é que estava errada: a porta é o vocabulário que o **controle** usa para pedir algo. Quem
implementa é que é borda.

É a inversão de dependência de verdade, e não só de nome: `SearchCycleService` compila sem que
exista nenhum adaptador. Foi isso que permitiu a E4.1 acrescentar AMQP sem tocar no motor.

### D-093 · A regra "controller não usa repositório" foi removida ✅
**Decisão:** o `ArquiteturaTest` **não** proíbe `boundary` de alcançar `entity`.

**Por quê:** a regra existiu, reprovou 24 dependências, e ao olhar uma por uma ficou claro que
era ela que estava errada. Em BCE a boundary é a **fachada do caso de uso** — alcançar a entidade
é o próprio ponto do estilo. Proibir importaria uma regra da Clean Architecture e produziria uma
camada de serviços que só repassa consulta, sem regra nenhuma dentro: mais arquivos, mesma lógica.

**O que continua valendo:** a borda não **decide**. Isso não dá para verificar por dependência —
dá para verificar lendo o controller. Ficou como critério de revisão, e não como teste verde que
daria falsa sensação de garantia.

### D-094 · A entidade recebe valores, e não o objeto de análise ✅
**Decisão:** `Alert.registrarAnalise(Integer nota, GrauDeAnomalia grau, BigDecimal queda)`, no
lugar de `registrarAnalise(AlertInsights)`.

**Por quê:** `AlertInsights` é um record de caso de uso, e com ele na assinatura a entidade passava
a depender de `control` — a seta apontando para fora do centro. O teste de arquitetura pegou.

Entidade é o que o sistema **lembra**; ela não precisa saber quem produziu o número. O enum
`GrauDeAnomalia` desceu junto para `entity/` pelo motivo oposto: ele é **persistido** em
`alert.anomaly_grade`, logo faz parte do vocabulário do que se lembra. Os outros enums de stats
(`FonteDeStats`, `AspectoDoVoo`, `DirecaoDaTendencia`) não são persistidos e ficaram em `control`.

### D-095 · O worker não tem `entity`, e isso está escrito ✅
**Decisão:** o worker tem `boundary/`, `control/` e `composicao/` — nenhuma camada de entidade. Um
teste falha se alguém importar SQLAlchemy, psycopg, asyncpg, sqlite3 ou pymongo.

**Por quê:** ausência de pasta é ambígua: quem chega não sabe se é desenho ou esquecimento. O
worker é stateless pela regra 1 da seção 3 do plano, e o teste transforma a regra do documento em
algo que quebra o build — exatamente no dia em que alguém fosse quebrá-la, que é o dia em que
ninguém se lembraria dela.

**A raiz de composição separada** (`composicao/`) existe porque HTTP e AMQP fazem a *mesma* coisa
por caminhos diferentes. Se uma das entradas instanciasse o Travelpayouts direto, as duas
deixariam de concordar, e o E2E só pegaria por sorte — dependendo de qual transporte estivesse
ativo naquele dia.

### D-096 · O frontend fica no padrão Vue, sem Pinia e sem BCE ✅
**Decisão:** `frontend-vue` mantém `api/ components/ views/ router/` — o formato do `create-vue` —
acrescido de `model/`, `lib/` e `composables/`. Não entra Pinia, e não se aplica BCE aqui.

**Por quê:** o BCE resolve *acoplamento entre camadas de negócio*, e o frontend não tem negócio: ele
tem tela, chamada de API e formatação. Renomear `views/` para `boundary/` deixaria o projeto pior
para qualquer pessoa que conheça Vue, em troca de simetria com um diagrama. Consistência entre
serviços vale quando ela carrega significado; aqui carregaria só a palavra.

Pinia ficou de fora pelo mesmo critério: nenhuma tela compartilha estado com outra. Cada uma
carrega o que precisa. Store aqui seria cerimônia.

**O que mudou de verdade** foram três defeitos reais, e não o formato de pastas:

1. `dinheiro`, `data` e `instante` estavam escritas **três vezes** — e as cópias já tinham
   divergido: uma devolvia `—` para nulo e outra quebrava; o gráfico arredondava e as telas não.
   O mesmo preço saía "R$ 3.720,00" numa tela e "R$ 3.720" na outra. Viraram `lib/formato.ts`,
   com as diferenças **intencionais** como parâmetro.
2. O bloco `try/catch/finally` com `ApiError` estava copiado em quatro lugares. Cópia de
   tratamento de erro é onde o `finally` some, e aí a tela fica em "Carregando..." para sempre —
   o modo de falha mais chato de diagnosticar, porque parece lentidão da API. Virou
   `composables/useCarregamento.ts`.
3. `types/` continha `monitorVazio()`, `paraRequest()` e `menorPrecoPorData()` — comportamento, e
   não tipo. Virou `model/`, que é o nome do que sempre foi.

E `api/monitores.ts` tratava de monitores, destinatários **e** observações; virou um módulo por
recurso, com `http.ts` guardando só o transporte.

### D-097 · O e-mail entra como saída de emergência, e não como canal preferido ✅
**Decisão:** uma terceira implementação de `NotificationChannel`, com três escolhas fechadas:

| Questão | Decidido | Recusado, e por quê |
|---|---|---|
| Transporte | SMTP configurável (`spring-boot-starter-mail`) | serviço transacional (Resend, Brevo): acopla a um fornecedor, exige cadastro e às vezes domínio próprio — a regra de custo zero não pediu isso |
| Convivência | canal único, trocado por `NOTIFICATION_CHANNEL` | os dois ao mesmo tempo: `alert` tem **um** `channel` e **um** `provider_message_id` por linha; mudar isso é outra etapa, não um efeito colateral desta |
| Destinatário | coluna `email` em `recipient` | endereço único no `.env`: um monitor com dois destinatários mandaria um e-mail só, e o `alert.recipient_id` apontaria para alguém sem relação com quem recebeu |

**Por que reabrir um item que estava fora do escopo.** A seção 2 do plano diz "sem e-mail", e era
deliberado. O que mudou não foi a preferência: o WhatsApp se mostrou **bloqueável por terceiro**.
O template está em análise na Meta desde a E1.12, e enquanto estiver, o sistema faz todo o
trabalho — varre, confirma, pontua, decide alertar — e não consegue avisar ninguém. Um canal cujo
funcionamento depende da aprovação de outra empresa não pode ser o único.

**O que a regra 3 economizou.** Nada no motor precisa mudar. `NotificationChannel` já tem a forma
certa, e o detalhe difícil já estava previsto: `confirmacaoAssincrona()` existe para separar
"chegou" de "o provedor disse que recebeu". E-mail responde `false` — e isso é **honesto**, porque
e-mail não sabe dizer se foi lido. Nada de pixel de rastreamento para fabricar um `read_at` que
seria mentira na metade dos casos.

**SMTP e não API do Gmail:** o Gmail vira um conjunto de variáveis de ambiente, e não uma
dependência. Trocar de provedor depois não toca em código.

**`phone_e164` vira opcional, com CHECK exigindo pelo menos um contato.** Coluna aditiva, nenhuma
linha existente quebra, e o CHECK impede o estado sem sentido: um destinatário que não pode ser
alcançado por nada.

**O padrão continua `LOG`.** Nada muda até alguém configurar — mesma disciplina da E4.1, em que
o AMQP entrou sem virar o default.

### D-098 · Quem envia é uma conta do sistema, nunca a conta pessoal ✅
**Decisão:** o `MAIL_USERNAME` é uma conta Google criada só para o projeto. A conta pessoal do
usuário aparece apenas como **destinatário**, cadastrada em `recipient.email`.

**Por quê:** a pergunta surgiu ao ler o guia — "se eu cadastrar meu e-mail como receptor e
configurar o emissor na mesma conta, não seria eu mandando e-mail para mim mesmo?". Seria, e isso
quebra três coisas de uma vez:

1. **O Gmail exibe o remetente como *eu*** e agrupa mensagens de assunto parecido na mesma
   conversa. O quinto alerta apareceria colapsado dentro de uma thread velha — o oposto do que um
   monitor precisa fazer.
2. **A notificação no celular** é tratada de forma diferente para mensagem que você mesmo mandou.
   O comportamento varia com versão e configuração, e é justamente por não dar para afirmar com
   certeza que ele não serve: num sistema cujo único propósito é avisar, "às vezes não notifica"
   já basta para descartar.
3. **A senha de app seria da conta principal.** Com uma conta dedicada, o pior caso de vazamento
   vira "alguém manda alerta de passagem falso", e não "alguém manda e-mail em seu nome".

**Dois contornos que não funcionam,** e ficam registrados para ninguém tentar de novo:

- **Trocar só o `MAIL_FROM`:** o SMTP do Gmail exige que o remetente seja a conta autenticada ou
  um alias verificado em "Enviar e-mail como" — qualquer outro endereço ele reescreve ou recusa.
- **O sufixo `+`** (`voce+voos@gmail.com`): é a mesma conta, logo continua sendo auto-envio.

**Isto não reabre a [D-097](DECISOES.md).** Continua SMTP configurável, custo zero e sem
fornecedor. Muda só qual conta vai no `MAIL_USERNAME` — que é exatamente o tipo de coisa que a
escolha por SMTP configurável tornou barata.

**Consequência de projeto:** o **assunto precisa variar**. Mesmo com remetente distinto, o Gmail
agrupa assunto idêntico. Vai levar rota e preço (`GRU → SSA por R$ 1.401`), o que resolve o
agrupamento e ainda torna o alerta legível na própria notificação, sem abrir o e-mail.

### D-099 · O repositório não carrega identidade de ninguém ✅
**Decisão:** identificadores da Meta, telefones reais e e-mails pessoais saem dos arquivos
versionados e viram placeholders (`<WABA_PRODUCAO>`, `<NUMERO_REMETENTE>`, `voce@gmail.com`).
Em fixture de teste, número obviamente falso (`+5511999990000`).

**Por quê:** surgiu ao considerar publicar o projeto. **Nenhuma credencial havia vazado** — o
`.env` nunca foi commitado, e os únicos "tokens" no histórico são os placeholders do
`.env.example`. Mas havia dado pessoal espalhado:

| O quê | Onde estava |
|---|---|
| WABA ID e phone number ID | `BUGS.md`, `PROGRESSO.md`, `WhatsAppWebhookTest` |
| Número remetente real | `BUGS.md`, `PROGRESSO.md` |
| **Número pessoal do usuário** | fixtures de `MotorE2ETest` e `AgentCriacaoTest`, desde a Fase 1 |
| E-mails pessoais | `GUIA-EMAIL.md` |

WABA ID público não dá acesso a nada sozinho, mas é insumo de engenharia social junto ao suporte
da Meta. O resto é dado pessoal indexável.

**O caso das fixtures é o mais instrutivo:** o número pessoal entrou em teste na Fase 1 porque era
"o número à mão" para escrever um cenário, e ficou. Dado real em fixture não parece vazamento na
hora — parece conveniência.

**Placeholders consistentes, e não apagados:** o BUG-009 é a história de *duas WABAs diferentes*, e
some se as duas virarem `<WABA>`. Ficaram `<WABA_PRODUCAO>` e `<WABA_TESTE>`, e a lição fica
legível.

**O que isto NÃO resolve:** identidade sai do repositório, mas quem clonar ainda precisa das
próprias credenciais. Essa é a [D-100](DECISOES.md).

### D-100 · Vai para o banco o que identifica; fica no ambiente o que autentica ✅
**Decisão:** `phone_number_id`, `waba_id`, `template_name` e `template_language` moram em
`whatsapp_config` e são editáveis por tela. `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_APP_SECRET` e
`WHATSAPP_WEBHOOK_VERIFY_TOKEN` continuam **exclusivamente** no ambiente.

**Por quê:** a pergunta veio de querer publicar o projeto — quem clonar precisa apontar para as
próprias credenciais sem editar arquivo e reiniciar. Mas levar *tudo* para o banco tem três custos
que a metade escolhida não tem:

1. **Segredo em coluna de texto** vai parar em `pg_dump`, em backup e em qualquer log que serialize
   a entidade. Evitar isso exigiria cifra — cuja chave viria do ambiente. Trocaríamos cinco
   segredos no `.env` por um, e ganharíamos um mecanismo de cifra para manter.
2. **Tela que grava credencial torna autenticação obrigatória.** Sem login (decisão de escopo da
   seção 2), qualquer um que alcance o painel poderia ler a configuração, trocá-la, ou mandar
   mensagem na conta do dono — **gastando o dinheiro dele**. Com só o não-secreto, o pior caso é
   alguém apontar o sistema para outro template, e nada é enviado sem o token que ele não tem.
3. **O `GET` pode existir sem medo.** Ele devolve `tokenConfigurado: true|false`, e nunca o valor.
   Endpoint que devolve segredo transforma toda leitura em vazamento.

**Banco primeiro, ambiente depois — e campo a campo.** Quem configurou pela tela espera que a tela
mande. Campo em branco devolve *aquele* valor ao `.env`, e não a configuração inteira: o caso real
é querer trocar só o template, sem redigitar um identificador que ninguém sabe de cor.

**O fallback não é cortesia, é compatibilidade.** Toda instalação anterior a esta etapa tem os
valores no `.env` e nenhuma linha no banco. Sem ele, a E4.7 quebraria o WhatsApp de quem já o
tinha funcionando — preço alto demais por uma tela.

**Lido a cada envio, e não no construtor.** Era esse o incômodo: trocar de template exigia
reiniciar. O custo é uma consulta por chave primária por alerta — e alerta é evento raro, não laço
quente. Cache aqui economizaria microssegundos e traria de volta exatamente o problema que a etapa
veio resolver, disfarçado de otimização.

**Uma linha, e só uma** (`CHECK (id = 1)`). O sistema é pessoal e fala por um número só; várias
linhas criariam a pergunta "qual vale?", que não tem resposta boa.

### D-101 · Mede-se resultado de negócio, e não saúde de processo ✅
**Decisão:** as métricas da E4.3 contam **varreduras, confirmações, decisões e entregas** — e
não CPU, memória ou latência de endpoint.

**Por quê:** o [BUG-014](BUGS.md) definiu o que precisava ser medido. A camada 2 inteira ficou
indisponível por **seis semanas** e nenhuma métrica de infraestrutura teria percebido: CPU,
memória, uptime e latência estavam perfeitos. Não houve erro, não houve log vermelho — a cadeia
de providers trata fonte ausente como degradação, que é o comportamento certo para uma fonte
frágil, e o sintoma foi o sistema **parar de alertar em silêncio**.

O que teria pegado é `camada2.consultas{resultado="confirmou"}` zerado enquanto
`busca.execucoes` continuava subindo. A pergunta que estas métricas respondem é *"o sistema
ainda está fazendo o que promete?"*, que é diferente de *"o sistema está no ar?"*.

**Sem candidato não conta como "não confirmou".** Dia sem promoção é a maioria dos dias; contar
isso afogaria o sinal de verdade no ruído. A camada 2 só é medida quando foi realmente consultada.

**Transitória e permanente são rótulos separados na entrega**, porque mudam o alarme e não só o
código: falha transitória subindo é a Meta instável, e passa; falha permanente subindo é
configuração errada, e não passa sozinha. A primeira dá para dormir; a segunda, não.

**Prometheus exposto sem coletor apontado para ele.** Não é antecipação vazia: é o que faz
observabilidade, no dia do deploy (E4.4), ser configuração de infraestrutura em vez de mudança
de código.

### D-102 · JSON no container, texto na máquina ✅
**Decisão:** `logging.structured.format.console` vem de `${LOG_FORMAT:}` — vazio por padrão, e
`ecs` nos containers via `docker-compose.yml`.

**Por quê:** JSON é ótimo para uma **ferramenta** ler e ruim para uma **pessoa** ler. No
desenvolvimento quem lê é uma pessoa; no container, um coletor. Forçar um formato para os dois
significa escolher qual dos dois vai sofrer.

O formato é **ECS** (Elastic Common Schema) porque ele já traz `service.name`, `service.version`,
`process.thread.name` e `log.logger` com nomes que as ferramentas conhecem — log estruturado com
chave inventada dá o trabalho de estruturar sem a vantagem de ser lido.

### D-103 · O canário verifica **formato**, e não disponibilidade ✅
**Decisão:** a sonda confere campo a campo — presença, tipo e plausibilidade — nas respostas
reais das duas camadas. Não basta a fonte responder 200.

**Por quê:** as **três quebras reais** que este projeto já viu na camada 2 passariam por qualquer
teste de disponibilidade:

| O que aconteceu | O que um ping teria dito |
|---|---|
| a API da biblioteca mudou inteira entre 2.x e 3.x | nada — nem chegou a rodar |
| `list[Airline]` anotado, `list[str]` em execução | 200 OK |
| o parser devolveu `time=[None, 45]`, sem a hora | 200 OK |

Há um teste para cada uma delas, exigindo que a sonda a acuse. Canário que não pega as falhas
conhecidas não protege de nada.

**Rota de sonda fixa e movimentada** (GRU–GIG, 30 dias à frente): rota rara devolveria vazio por
falta de voo, e "vazio" seria confundido com "a fonte quebrou".

**A sonda nunca propaga exceção.** Se lançasse, uma fonte instável derrubaria a rotina que existe
justamente para observar fontes instáveis.

**HTTP 200 mesmo com problema.** O resultado do canário é **dado**, e não erro de requisição.
Usar 5xx faria um proxy no meio do caminho transformar diagnóstico em falha de rede.

### D-104 · O canário avisa por log, métrica e saúde — não pelo canal de alerta ✅
**Decisão:** formato quebrado vira `log.error`, `flightmonitor.canario.saudavel = 0` e registro do
último resultado. **Não** vira mensagem de WhatsApp nem e-mail.

**Por quê:** o template aprovado pela Meta diz *"Encontrei uma passagem dentro do preço que você
definiu"*. Usá-lo para avisar que uma biblioteca mudou de formato seria mentir para quem recebe
e, provavelmente, custar a aprovação do template — a Meta revisa uso, e não só conteúdo.

Transformar o canário numa mensagem exige um **segundo formato**, com aprovação própria. Essa
decisão pertence à E4.4, quando existir um ambiente rodando de forma contínua para receber o
aviso — antes disso seria construir o canal e não ter para onde mandar.

**O gauge tem três valores, e não dois:** `1` saudável, `0` com problema, **`-1` não consultado**.
O `-1` é o que mais importa: sem ele, worker fora do ar apareceria como fonte quebrada, e o
alarme mandaria olhar o lugar errado justamente quando o tempo importa. É o mesmo princípio de
`SEM_DADOS ≠ NORMAL`, aplicado à operação.

**Desligado por padrão** (`CANARIO_ENABLED=false`): ele consulta APIs reais e gasta cota de duas
fontes gratuitas. Quem acabou de clonar o projeto não deve pagar esse custo sem pedir.

**Uma vez por dia.** Formato de API não muda de hora em hora, e cada execução gasta cota. Rodar
de minuto em minuto não antecipa a descoberta em nada útil e aproxima o bloqueio por excesso de
requisições (RISCO-004).

### D-105 · Campo de data próprio, porque o nativo não obedece ✅
**Decisão:** os campos de data do formulário deixam de ser `<input type="date">` e passam a ser um
componente `CampoData` — texto em **dd/mm/aaaa** por fora, ISO por dentro — com o seletor nativo
preservado atrás de um botão.

**Por quê:** o `<input type="date">` exibe a data no formato do **navegador**, e não no da página.
Foi **verificado**, e não suposto: com o Chrome em inglês, nem `lang="pt-BR"` no `<html>` — que
este projeto já tinha — nem `lang="pt-BR"` no próprio campo mudam o `mm/dd/yyyy`. Não existe
atributo que sobreponha isso; o valor no DOM é sempre ISO, e a máscara é do navegador.

Num sistema em português, com datas de viagem, `03/05` significar março ou maio é a diferença
entre viajar no outono e no inverno.

**O calendário continua lá.** Trocar por um campo de texto e perder o seletor seria consertar uma
coisa quebrando outra — pior ainda no celular, onde tocar num dia é muito melhor que digitar oito
dígitos. O botão abre o seletor **nativo** via `showPicker()`, num `<input type="date">` que existe
só para isso e fica fora do fluxo visual (mas **renderizado**: `showPicker()` não abre em elemento
com `display: none`).

**Data impossível limpa o valor, e não o mantém.** `31/02/2026` tem o formato certo e o dia
errado; sem checagem, o `Date` do JavaScript "corrigiria" para 03/03 em silêncio. E emitir `null`
é melhor que não emitir nada: com silêncio, o formulário ficaria com a data antiga enquanto a
tela mostra outra, e salvar guardaria o valor velho achando que mudou.

**A conversão mora em `lib/data.ts`, pura e testada** — é a parte que erra. O componente só liga
os fios.

### D-106 · Intervalo mínimo de varredura sobe de 5 para 10 minutos ✅
**Decisão:** `search_interval_minutes >= 10`, verificado em **três camadas** — CHECK no banco,
`@Min` no DTO e `min` no formulário.

**Por quê:** cinco minutos era generoso demais para o que as fontes suportam. As duas são
gratuitas e **não contratadas**: a Travelpayouts publica limite de 300 req/min, e a camada 2 não
publica limite nenhum — o que não significa que não exista, significa que você descobre qual é
quando for bloqueado ([RISCO-004](BUGS.md)).

**E o ganho de varrer de 5 em 5 minutos é próximo de zero.** Preço de passagem muda em minutos,
mas a camada 1 devolve dado **cacheado**, com horas de atraso. Consultar mais rápido que o cache
atualiza gasta cota para reler a mesma resposta — custo real, benefício imaginário.

**Três camadas, e não uma.** O formulário evita a ida ao servidor; o DTO devolve mensagem com o
campo marcado; o CHECK protege contra carga manual, script e bug nosso. As duas primeiras são
conveniência; a terceira é a garantia.

**A regra vale também na edição**, e há teste para isso: regra que vale só na criação é regra que
se contorna — bastaria criar com 10 e editar para 1.

**A migration sobe quem estava abaixo**, em vez de recusar a subir. O `UPDATE` vem **antes** do
CHECK: na ordem inversa, a migration falharia em qualquer instalação com um monitor abaixo de 10,
no meio do deploy e com o schema pela metade.

**O que esta regra não cobre:** a busca manual (`POST /monitors/{id}/search`) continua sem trava.
Ela é disparada por uma pessoa, uma de cada vez, e cada uma leva alguns segundos — o risco real
de bloqueio vem da repetição **desassistida**, que é justamente o que o intervalo controla.
Registrado aqui para ser decisão, e não esquecimento.

### D-107 · O provider escolhe o endpoint conforme o produto pedido ✅
**Decisão:** monitor de **somente ida** consulta `v2/prices/latest` com `one_way=true`; monitor com
janela de volta consulta `v1/prices/calendar`.

**Por quê:** os dois endpoints respondem perguntas diferentes, e o [BUG-016](BUGS.md) foi fazer a
pergunta errada. Testado contra a API real:

| Endpoint | `one_way` | Resultado |
|---|---|---|
| `v1/prices/calendar` | ignorado (`true`, `1`, ausente) | sempre com `return_at` |
| `v2/prices/latest` | respeitado | preços de só ida, `return_date` vazio |

**Filtrar não resolveria.** Como o calendário só devolve ida e volta, descartar as ofertas com
volta deixaria o monitor de só ida sem preço nenhum — trocar dado errado por dado nenhum é trocar
um problema por outro.

**O custo, assumido:** o endpoint de só ida cobre menos datas (2 contra 5, no teste da rota
GRU → BEL em dezembro). **Dois preços certos valem mais que cinco errados** — e os cinco não
eram imprecisos, eram de outro produto.

**Filtro mesmo assim.** O `v2/prices/latest` promete respeitar `one_way`, e a oferta com volta é
descartada de qualquer forma. O bug nasceu exatamente de confiar numa promessa da fonte sem
conferir; a correção não repete o erro.

### D-108 · Agência não é companhia aérea ✅ *(refinada pela [D-110](DECISOES.md))*
**Decisão:** no caminho de somente ida, `airline` fica **nulo**. O `gate` da Travelpayouts
(`Kiwi.com`, `Mytrip.com`) é a **agência que vende**, e não quem opera o voo — e esse endpoint
não informa a companhia.

**Por quê:** a primeira versão da correção do [BUG-016](BUGS.md) pôs o `gate` no campo `airline`,
e a tela passou a mostrar **"Companhia: Kiwi.com"**. É falso, e do pior tipo: plausível o
suficiente para ninguém desconfiar. Quem lesse escolheria voo pensando em companhia aérea.

Nulo diz que não se sabe, que é a verdade — o mesmo princípio de `SEM_DADOS ≠ NORMAL`.

**O custo, assumido:** o monitor de só ida perde a informação de companhia, que o calendário de
ida e volta traz. Os dois endpoints informam coisas diferentes:

| | Companhia | Duração |
|---|---|---|
| `v1/prices/calendar` (ida e volta) | ✅ | ❌ |
| `v2/prices/latest` (só ida) | ❌ | ✅ |

**Esta parte foi revista no mesmo dia.** Eu havia deixado a agência fora do modelo, argumentando
que "onde comprar" era outra pergunta. O usuário apontou o furo: com o campo vazio, **não há
referência para ir comprar a passagem** — e um monitor que encontra oferta sem dizer onde
comprá-la resolve metade do problema. A agência passou a ser modelada na [D-110](DECISOES.md).

### D-109 · Duração em horas, e travessão quando não se sabe ✅
**Decisão:** a duração total aparece na tabela de observações como `7h05` / `16h30`; ausente vira
`—`.

**Por quê:** ninguém pensa em duração de viagem em minutos, e converter 990 de cabeça é trabalho
que a tela devia poupar.

**Zero e negativo também viram travessão**, e não `0h00`: duração zero não é "voo instantâneo",
é dado quebrado. Mostrar `0h00` afirmaria uma inverdade; o travessão admite que não se sabe.

**A coluna paga por si.** No primeiro dado real da rota CGH → BEL ela já mostrou o que o preço
sozinho escondia: o voo **mais barato** (R$ 1.306) leva **16h30 com 2 escalas**, e o de R$ 1.383
leva **7h05 com 1**. Setenta e sete reais separando sete horas de viagem.

### D-110 · Separadas no dado, juntas na tela ✅
**Decisão:** `price_observation` ganha a coluna `agency` (V10). Na tabela de observações as duas
aparecem numa **única coluna "Companhia / Agência"**, com um rótulo dizendo qual das duas é.

**Por quê:** o usuário apontou que deixar o campo vazio tira a informação que permite **agir** —
sem saber onde comprar, encontrar a oferta não serve para nada. Estava certo.

**Mas juntar as duas numa coluna só no banco quebraria uma regra**, e não só a estética:
`Preferencias.companhiaEvitada()` compara `airline` com a lista de companhias que o monitor
evita. Com "Kiwi.com" ali, quem pediu *"evitar GOL"* passaria a comparar GOL com Kiwi.com — e a
preferência **pararia de funcionar em silêncio**, que é a pior forma de uma regra falhar.

Daí a divisão: **o código precisa distinguir; o leitor não**. Quem olha a tabela quer responder
"onde eu compro isso?", e uma coluna basta. O rótulo (`agência`) evita que o nome seja lido como
companhia — que foi exatamente o erro que a separação veio corrigir.

**A companhia tem precedência** quando as duas existem: quem opera o voo informa mais do que quem
intermediou a venda.

**As fontes sabem metades diferentes**, e por isso as duas colunas são necessárias:

| | Companhia | Agência | Duração |
|---|---|---|---|
| `v1/prices/calendar` (ida e volta) | ✅ | ❌ | ❌ |
| `v2/prices/latest` (só ida) | ❌ | ✅ | ✅ |
| fast-flights (camada 2) | ✅ | ❌ | ✅ |

### D-111 · Túnel em vez de porta aberta, e senha no nginx em vez de login ✅
**Decisão:** o sistema roda na máquina do usuário e é publicado por um **túnel Cloudflare**; o
painel é protegido por **basic auth no nginx**, e não por autenticação na aplicação.

**Por que túnel e não redirecionamento de porta:** IP residencial muda, e boa parte dos provedores
brasileiros usa **CGNAT** — que impede redirecionamento de porta mesmo com IP fixo. O túnel não
depende de nenhum dos dois: a conexão sai de dentro para fora, e **nada** fica aberto no roteador.

De quebra, o TLS termina na Cloudflare: o webhook da Meta exige HTTPS, e assim não há certificado
para emitir nem renovar aqui dentro.

**Por que basic auth e não login de verdade:** o furo a fechar é concreto — hoje quem alcança o
painel edita monitores. Basic auth no proxy que **já existe** resolve isso em minutos, sem
reabrir a decisão de escopo da seção 2 (sem login, sistema pessoal) e sem acrescentar sessão,
tela de login e recuperação de senha para manter, num sistema de **um** usuário.

**A senha vale para a API também**, e não só para a tela: proteger o `/` e deixar o `/api` aberto
ao lado seria teatro.

**Uma única rota fica de fora, e não há escolha:** o webhook da Meta. Quem chama é o servidor
dela, que não tem como responder a basic auth. Ele se protege por outro meio — a assinatura
`X-Hub-Signature-256`, conferida com o app secret (E1.17).

**A senha nunca entra na imagem nem no repositório.** O `.htpasswd` é gerado no arranque do
container, a partir do ambiente. Imagem é artefato compartilhável; senha não.

**O compose de produção recusa subir sem senha** (`${PAINEL_SENHA:?...}`). Painel aberto na
internet por esquecimento é pior que um deploy que falha alto.

**`unless-stopped`, e não `always`:** o usuário escolheu subir quando quiser. `always` traria de
volta um container que ele parou de propósito — desobedecer em silêncio é pior que não
reiniciar.

**O que esta decisão assume, e está escrito no guia:** o sistema só varre enquanto a máquina
estiver ligada. Um monitor de 6 em 6 horas perde a madrugada com o PC desligado — e o sistema
existe justamente para vigiar quando ninguém está olhando. O histórico não se perde: o próximo
horário fica no banco, e ao religar o scheduler encontra os vencidos.

## Decisões pendentes

| # | Questão | Quando decidir |
|---|---|---|
| ~~P-1~~ | ✅ Resolvida na E1.5: a API aceita `currency=BRL` e devolve em reais. Sem conversão. | — |
| P-2 | Intervalo padrão entre varreduras (custo x atualidade) | Etapa E1.9 |
| P-3 | Limiar exato do anti-spam (queda de X% / N horas) | Etapa E1.10 |
| ~~P-4~~ | ✅ Resolvida na E3.1: cadeia configurável, `claude-sonnet-5` por padrão, e o sistema funciona sem nenhum. Ver [D-077](DECISOES.md) | — |
