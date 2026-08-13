# Flight Monitor — Plano de Ação

> Documento-mestre do projeto. Define escopo, arquitetura e o roteiro etapa por etapa.
> Progresso é registrado em [PROGRESSO.md](PROGRESSO.md). Problemas em [BUGS.md](BUGS.md).
> Decisões e seus porquês em [DECISOES.md](DECISOES.md).
> A fragilidade da camada 2 e seu plano de contingência em
> [FRAGILIDADE-CAMADA-2.md](FRAGILIDADE-CAMADA-2.md).

**Última atualização:** 2026-08-09

---

## 1. O que o sistema faz

Monitora silenciosamente, em background, o preço de passagens aéreas para rotas e janelas
de datas configuradas pelo usuário. Quando encontra uma oferta dentro dos critérios, envia
um alerta no WhatsApp. Só incomoda quando há oportunidade real.

**Exemplo de monitor:**

```
GRU → LIS
Ida:    entre 10/03/2027 e 20/03/2027
Volta:  permanência de 10 a 15 dias
Preço máximo: R$ 3.200
Escalas máximas: 1
```

## 2. Escopo do MVP

### Dentro
- Cadastro de monitores (rota, janelas de data, preço-teto, escalas)
- Cadastro de destinatários (números de WhatsApp)
- Varredura periódica automática
- Histórico de preços observados
- Alerta via WhatsApp Cloud API
- Painel web para gerenciar monitores e ver histórico

### Fora (decisão explícita do usuário)
- Cadastro/autenticação de usuários — o sistema é pessoal, single-tenant
- Notificação por e-mail
- Notificação por Telegram
- Compra/reserva de passagens — o sistema apenas avisa

## 3. Arquitetura

```
┌──────────────────────────────────────┐
│  frontend-vue/  Vue 3 + Vite + TS    │
│  Painel de monitores e histórico     │
└──────────────────┬───────────────────┘
                   │ REST
                   ▼
┌──────────────────────────────────────┐
│  core-java/     Spring Boot 4 / Java 21
│                                      │
│  • CRUD de monitores                 │
│  • CRUD de destinatários             │
│  • Scheduler (@Scheduled)            │
│  • Dono do banco de dados            │
│  • Histórico de preços               │
│  • Regra de alerta + anti-spam       │
│  • NotificationService               │
└────────┬────────────────────┬────────┘
         │ REST               │ HTTPS
         │ POST /search       ▼
         │            ┌───────────────────┐
         │            │ WhatsApp Cloud API│
         │            │  (Meta)           │
         │            └───────────────────┘
         ▼
┌──────────────────────────────────────┐
│  worker-python/   FastAPI + Python   │
│                                      │
│  • Provider Travelpayouts (varredura)│
│  • Provider fast-flights (confirmação)
│  • Normalização de resultados        │
│  • (Fase 2) Análise e score          │
└──────────────────┬───────────────────┘
                   ▼
        Travelpayouts Data API
        Google Flights (protobuf)

┌──────────────────────────────────────┐
│  PostgreSQL (Docker)                 │
│  Acessado APENAS pelo core-java      │
└──────────────────────────────────────┘
```

### Regras arquiteturais invioláveis

1. **O Java é o dono do banco.** O Python nunca acessa o PostgreSQL diretamente. Ele
   recebe uma requisição, busca, devolve JSON. É stateless.
2. **O worker Python é um especialista burro.** Não conhece monitores, não conhece
   usuários, não decide se um preço é bom. Ele só sabe buscar preço de rota + data.
3. **O WhatsApp fica isolado atrás de uma interface.** O motor de monitoramento não
   sabe que WhatsApp existe. Trocar de canal não deve tocar em nada além do adaptador.
4. **Toda fonte de preço é um `PriceProvider` plugável.** Se uma fonte quebrar, o
   sistema degrada, não morre.

### Organização interna: BCE por feature

As quatro regras acima falam de como os **serviços** se relacionam. Dentro de cada
serviço vale o **BCE** (Boundary–Control–Entity), organizado **por feature**, e não
por camada global:

```
core-java/  com.flightmonitor.core
  monitor/  entity/  o que o sistema lembra: @Entity, enums persistidos, repositórios
            control/ a lógica do caso de uso: serviços, regras, schedulers e as PORTAS
            boundary/ as bordas: controllers REST e adaptadores de sistema externo
  alert/    idem     (o adaptador do WhatsApp mora em alert/boundary/whatsapp/)
  search/   idem     (SearchClient é PORTA e mora em control/; REST e AMQP são boundary/)
  stats/    idem
  agent/    idem     (NlpPort em control/, RestNlpClient em boundary/)
  common/   o tratador de exceção e os erros de aplicação

worker-python/  app/
  boundary/  http/ e amqp/    entrada
             gateway/         saída (Travelpayouts, fast-flights, Claude)
  control/   busca/ e nlp/    as portas e as cadeias que decidem quem chamar
  composicao/                 a raiz de composição — o único lugar que conhece os dois lados
  (não há entity: o worker é stateless pela regra 1)

frontend-vue/  src/
  api/         http.ts é o transporte; um módulo por recurso
  model/       o modelo de domínio e suas funções puras
  lib/         apoio puro (formatação) — sem Vue, sem rede
  composables/ estado reativo compartilhado
  components/  views/  router/
  (não há Pinia: nenhuma tela compartilha estado com outra)
```

**A direção das dependências:** `boundary → control → entity`. A entidade não conhece
ninguém; o controle conhece só as **portas** que ele mesmo define; a borda implementa
essas portas e traduz protocolo.

**As camadas são verificadas por teste**, e não por convenção:

| Serviço | Onde | Como |
|---|---|---|
| core-java | `ArquiteturaTest.java` | ArchUnit — 7 regras |
| worker-python | `tests/test_arquitetura.py` | AST dos imports — 5 regras |
| frontend-vue | `src/estrutura.spec.ts` | Vitest sobre os imports — 6 regras |

Quebrar uma camada quebra o build, com o nome da classe e da regra. Sem isso, a
organização em pastas é só convenção — e convenção que ninguém verifica dura até a
primeira pressa.

## 4. Estratégia de coleta de preços — o coração do projeto

Esta é a parte mais frágil e mais importante. A abordagem é em **duas camadas**:

### Camada 1 — Varredura ampla (Travelpayouts Data API)
- Gratuita, requer apenas cadastro e token
- Endpoint `/v1/prices/calendar` devolve o preço mais barato de **cada dia de um mês
  inteiro** em uma única chamada
- Limite de 300 requisições/minuto — folgado para o nosso uso
- Dados são **cacheados** (podem estar defasados algumas horas/dias)

**Papel:** descobrir candidatos e construir o histórico de preços da rota ao longo do tempo.

### Camada 2 — Confirmação pontual (fast-flights / Google Flights)
- Biblioteca Python que decodifica o protobuf da URL do Google Flights
- Gratuita, sem chave de API, sem navegador
- Traz dados reais: companhia, escalas, horários, duração
- **Frágil por natureza** — depende do formato interno do Google

**Papel:** quando a camada 1 aponta um candidato abaixo do preço-teto, confirmar se o
preço é real antes de disparar o alerta.

### Fluxo

```
Scheduler dispara monitor
        ↓
Travelpayouts: varre o mês inteiro   ← barato, amplo
        ↓
Algum dia < preço_teto?
   ├── Não → grava histórico, fim
   └── Sim ↓
        fast-flights: confirma essa data  ← caro, preciso
              ↓
        Confirmado e ainda < teto?
           ├── Não → grava, marca falso-positivo
           └── Sim → grava + ALERTA WhatsApp
```

### Por que não Playwright
O plano original previa automação de navegador. Foi descartado: alto custo de manutenção,
lento, quebra a cada mudança de layout, e a camada 1 já resolve a varredura de datas de
forma incomparavelmente mais barata. Playwright fica como plano C, se as duas camadas caírem.

## 5. Canal de notificação

**WhatsApp Cloud API com número de teste da Meta.**

> 🔴 **Revisto em 2026-08-11:** o número de teste da Meta **não entrega no Brasil**. Ela
> bloqueia mensagens entre países envolvendo BR e ID, e o número de teste é sempre americano.
> Ver [BUG-007](BUGS.md) e [D-052](DECISOES.md). O canal exige **número brasileiro próprio**.

- Exige chip ou número virtual brasileiro dedicado, e cartão cadastrado
- Sem limite de destinatários
- Custo por mensagem Utility na casa de centavos — R$ 1–2 por mês no volume deste projeto
- Passo a passo no [GUIA-WHATSAPP.md](GUIA-WHATSAPP.md), passo 9

O código é escrito para que a migração para número de produção seja apenas troca de
credenciais (`phoneNumberId` + `accessToken`), sem alteração de lógica.

> **Nota:** a dúvida original "qual número enviará as notificações?" está respondida — é um
> número da Meta, não o seu número pessoal. Ver [DECISOES.md](DECISOES.md#d-004).

## 6. Modelo de dados (preliminar)

| Tabela | Papel |
|---|---|
| `monitor` | rota, janelas de ida/volta, permanência, preço-teto, escalas, passageiros, ativo, intervalo de busca |
| `recipient` | nome, telefone E.164, ativo |
| `monitor_recipient` | vínculo N:N — quem recebe alerta de qual monitor |
| `price_observation` | monitor, data ida, data volta, preço, moeda, cia, escalas, fonte, observado_em |
| `alert` | monitor, observação que disparou, canal, status, enviado_em, payload |
| `search_run` | execução de varredura: início, fim, status, nº de observações, erro |

`price_observation` é a tabela mais importante do sistema — é dela que sai toda a
inteligência da Fase 2.

## 7. Roteiro

Cada etapa tem entregável verificável. Nenhuma etapa começa antes da anterior estar
marcada como concluída em [PROGRESSO.md](PROGRESSO.md).

---

### FASE 0 — Fundação

| ID | Etapa | Entregável / critério de pronto |
|---|---|---|
| **E0.1** | Docker Compose com PostgreSQL | `docker compose up -d` sobe o banco; conexão validada |
| **E0.2** | Esqueleto Spring Boot em `core-java/` | `mvn spring-boot:run` sobe; `GET /actuator/health` responde `UP` |
| **E0.3** | Esqueleto FastAPI em `worker-python/` | `uvicorn` sobe; `GET /health` responde 200 |
| **E0.4** | Esqueleto Vue 3 + Vite + TS em `frontend-vue/` | `npm run dev` sobe; página inicial renderiza |
| **E0.5** | Git init + `.gitignore` + primeiro commit | Repositório limpo, sem segredos versionados |

---

### FASE 1 — Engine (encontrar preço, guardar, avisar)

| ID | Etapa | Entregável / critério de pronto |
|---|---|---|
| **E1.1** | Schema do banco via Flyway | Migrations aplicadas; tabelas da seção 6 criadas |
| **E1.2** | Entidades JPA + repositórios | Testes de repositório passando |
| **E1.3** | API REST de monitores (CRUD) | CRUD completo testado; validação de payload |
| **E1.4** | API REST de destinatários (CRUD) | CRUD completo; validação de telefone E.164 |
| **E1.5** | Provider Travelpayouts no worker | `POST /search/calendar` devolve preços de um mês real |
| **E1.6** | Provider fast-flights no worker | `POST /search/confirm` devolve detalhes de um voo real |
| **E1.7** | Contrato Java ↔ Python (cliente REST) | Java consome o worker; erro e timeout tratados |
| **E1.8** | Persistência de `price_observation` | Uma busca real grava histórico no banco |
| **E1.9** | Scheduler de varredura | Monitores ativos são varridos automaticamente no intervalo |
| **E1.10** | Regra de alerta + anti-spam | Não re-alerta a mesma oferta; só re-alerta se cair X% ou passar N horas |
| **E1.11** | `NotificationService` + adaptador *log* | Alerta aparece no log com a mensagem formatada |
| **E1.12** | Adaptador WhatsApp Cloud API | Mensagem real chega no celular cadastrado. **Envia template aprovado, não texto livre** — ver [GUIA-WHATSAPP.md](GUIA-WHATSAPP.md) |
| **E1.13** | Painel Vue: listar/criar/editar monitores | CRUD funcionando pela tela |
| **E1.14** | Painel Vue: histórico de preços do monitor | Gráfico/tabela de preços ao longo do tempo |
| **E1.15** | **E2E do motor** (worker falso via WireMock) | Um teste cobre monitor → scheduler → busca → observação → regra → alerta |
| **E1.16** | **E2E entre serviços** (providers falsos) | Java real + worker real + Postgres real trocam dados sem stub entre eles |
| **E1.17** | **Webhook de status do WhatsApp** | ✅ `SENT` significa entregue de verdade; `ACCEPTED` é o novo estado de “a Meta aceitou”; falha vira `error_message`. Ver [GUIA-WEBHOOK.md](GUIA-WEBHOOK.md) |

> **E1.15 e E1.16 dependem apenas da E1.12**, não da interface. Podem ser antecipadas
> para logo depois do adaptador de WhatsApp, testando o motor antes de existir tela.

**Marco:** ao fim da Fase 1 o sistema funciona 24h por dia e avisa no WhatsApp.

---

### FASE 2 — Inteligência

| ID | Etapa | Entregável / critério de pronto |
|---|---|---|
| **E2.1** | Estatísticas de rota | ✅ mínimo, quartis, mediana, média, máximo e desvio, por rota e por mês de partida. **Ficou no core-java, não no worker** — ver [D-059](DECISOES.md) |
| **E2.2** | Detecção de anomalia de preço | ✅ cinco graus (SEM_DADOS a RECORDE) pela regra de Tukey sobre quartis, com frase pronta em português. Ver [D-062](DECISOES.md) |
| **E2.3** | Flight Score | ✅ nota 0-100 com os quatro aspectos decompostos, pesos configuráveis e cobertura explícita. Aspecto sem dado não vira zero — [D-064](DECISOES.md) |
| **E2.4** | Alerta enriquecido | ✅ comparação histórica e nota na mensagem, cada uma só se sustentar. Template segue com 5 parâmetros — [D-069](DECISOES.md) |
| **E2.5** | Tendência de preço | ✅ subindo / caindo / estável por Theil-Sen sobre a série diária, em % por semana, com a série na resposta. [D-070](DECISOES.md) |
| **E2.6** | Preferências do monitor | ✅ evitar companhia, preferir voo direto e pesos do score por monitor. **Bagagem e aeroporto alternativo ficaram fora** — [D-075](DECISOES.md) |

---

### FASE 3 — Agente

| ID | Etapa | Entregável / critério de pronto |
|---|---|---|
| **E3.1** | Endpoint de linguagem natural | ✅ `POST /api/agent/interpret` — texto livre vira intenção estruturada, com o que faltou dito em português. Cadeia LLM → regras, [D-077](DECISOES.md) |
| **E3.2** | Criação de monitor por conversa | ✅ `POST /api/agent/monitors` — cria, recusa incompleto com 422 e duplicata com 409, e diz tudo que assumiu. [D-080](DECISOES.md) |
| **E3.3** | Recomendação em linguagem natural | ✅ veredito + razões com lado + parágrafo em português, compondo as quatro análises da Fase 2. **Sem LLM** — [D-084](DECISOES.md) |

---

### FASE 4 — Evolução de infraestrutura

| ID | Etapa | Entregável / critério de pronto |
|---|---|---|
| **E4.1** | RabbitMQ substituindo REST síncrono | ✅ adaptador AMQP atrás da mesma porta `SearchClient`, escolhido por configuração. Os 11 testes do E2E entre serviços rodam nos **dois** transportes. [D-087](DECISOES.md) |
| **E4.2** | Dockerização completa dos 3 serviços | ✅ `docker compose up` sobe os 5 containers. Banco `flightmon_test` separado fecha o [RISCO-008](BUGS.md). Ciclo completo validado em container, nos **dois** transportes |
| **E4.3** | Observabilidade | Logs estruturados, métricas de busca, taxa de falha por provider |
| **E4.4** | Deploy | Ambiente rodando de forma contínua |
| **E4.5** | **Canário ao vivo** das fontes externas | Rotina agendada consulta as APIs reais e avisa quando o formato mudar |
| **E4.6** | **Canal de e-mail** | ✅ terceira implementação de `NotificationChannel`, escolhida por `NOTIFICATION_CHANNEL=EMAIL`. Migration V7 aditiva, WhatsApp intocado. Testado contra SMTP falso, sem mandar e-mail de verdade. [D-097](DECISOES.md) · [D-098](DECISOES.md) · [GUIA-EMAIL.md](GUIA-EMAIL.md) |

> **Sobre a E4.6 e o escopo original.** A seção 2 diz "sem e-mail", e isso era uma decisão
> deliberada: um canal a mais é uma superfície a mais. O que mudou não foi a preferência, e sim o
> fato de o WhatsApp ter se mostrado **bloqueável por terceiro** — o template está em análise na
> Meta desde a E1.12, e enquanto estiver, o sistema encontra passagem barata e não consegue
> avisar ninguém. O e-mail entra como saída de emergência com dono conhecido, não como
> preferência de canal.

---

## 8. Riscos conhecidos

| Risco | Impacto | Mitigação |
|---|---|---|
| `fast-flights` quebrar (Google muda o protobuf) | Alto | Camada 1 continua funcionando; alerta degradado sem detalhes de voo |
| Travelpayouts mudar termos ou exigir tráfego afiliado | Alto | Providers são plugáveis; SerpApi (250 buscas/mês grátis) como reserva |
| Preços cacheados gerarem falso-positivo | Médio | Camada 2 confirma antes de alertar |
| Bloqueio por excesso de requisições | Médio | Rate limit próprio, cache local, intervalo mínimo entre varreduras |
| Python 3.14 muito novo para as libs | Médio | Fixar Python 3.12 em venv se houver incompatibilidade |
| Limite de 5 destinatários no número de teste | Baixo | Suficiente para uso pessoal; migração prevista |

## 9. Estratégia de testes

Até a etapa E1.6 a estratégia era implícita. Esta seção a torna explícita e registra o que
**não** era coberto.

### O que já existia

| Nível | Onde | O que cobre |
|---|---|---|
| Unitário | ambos | funções puras: quebra de janela em meses, parsing de horário, divisão ida/volta |
| Integração — banco | `core-java` | entidades, constraints, cascatas e triggers contra o **PostgreSQL real** ([D-020](DECISOES.md)) |
| Integração — API | `core-java` | MockMvc do controller ao banco, com validação e códigos HTTP |
| Integração — provider | `worker-python` | HTTP mockado com `MockTransport`, imitando o formato real das fontes |
| Verificação manual | — | `curl` ao vivo a cada etapa |

### As quatro lacunas que isso deixava

1. **Nenhum teste cruzava a fronteira Java ↔ Python.** Cada lado era testado com o outro
   ausente ou simulado. Erro de contrato — nome de campo, formato de data, tratamento de
   nulo — passaria pelos dois lados sem ser detectado.
2. **Nenhum teste exercitava o fluxo de negócio completo.** Existem peças testadas, mas nada
   verificava "monitor cadastrado leva a alerta enviado".
3. **A verificação manual não é repetível.** Todo `curl` que rodei precisaria ser refeito à
   mão a cada mudança. Não protege contra regressão.
4. **Nada detecta mudança nas fontes externas.** Os testes usam HTTP mockado, então
   continuariam verdes mesmo se o Google mudasse tudo — e o primeiro sinal seria a ausência
   de alertas, semanas depois.

### Os níveis adotados

**Nível 1 — E2E do motor** (etapa E1.15) — ✅ **implementado** em
`core-java/src/test/java/com/flightmonitor/core/MotorE2ETest.java`, 10 testes
Spring Boot completo, PostgreSQL real, worker substituído por WireMock. Um teste percorre:
monitor cadastrado → scheduler dispara → busca → grava observação → aplica regra de alerta →
alerta criado com a mensagem certa. É o produto inteiro em um teste, e roda dentro do
`mvn test` sem exigir Python nem rede.

**Nível 2 — E2E entre serviços** (etapa E1.16) — ✅ **implementado** em
`core-java/src/test/java/com/flightmonitor/core/E2EServicosTest.java`, 11 testes,
disparado por `python scripts/e2e_servicos.py`
Java real, worker Python real e PostgreSQL real, **sem stub entre eles**. Só as fontes
externas são falsas, através de providers falsos registrados pela `factory.py` quando
`USE_FAKE_PROVIDERS=true`. Fecha a lacuna 1: valida o contrato de verdade entre as duas
linguagens. Roda por script, não no `mvn test`.

Os cenários são selecionados por códigos IATA reservados no destino (`ZZA` a `ZZE`) — o
core-java não ganha nenhum modo de teste. Ver [D-055](DECISOES.md).

**Nível 4 — Canário ao vivo** (etapa E4.5)
Consulta as APIs reais em uma rotina agendada, fora do CI. Não valida lógica de negócio —
valida que as fontes ainda respondem no formato esperado. É a única defesa contra o
[RISCO-002](BUGS.md): descobrir que o Google mudou o formato **antes** de você deixar de
receber alertas.

Deliberadamente **fora do CI**: depende de rede, consome cota e é intrinsecamente instável.
Um canário vermelho é informação; um canário no CI seria ruído que treina a equipe a ignorar
falha.

### O que ficou de fora, e por quê

**E2E de navegador (Playwright).** Avaliado e não adotado nesta rodada. Seria o nível de
maior custo de manutenção — quebra a cada ajuste de layout — e a interface deste projeto tem
poucas telas. Pode ser reavaliado depois da E1.14.

### Como os testes tomam o lugar das fontes externas

O desenho da E1.6 já deixou as costuras prontas. Nenhum código de produção precisa de
`if (teste)`:

| Dependência externa | Substituta no teste | De onde veio |
|---|---|---|
| Worker Python (visto pelo Java) | WireMock | E1.15 |
| Travelpayouts e Google Flights | providers falsos via `factory.py` | Strategy da [D-026](DECISOES.md) |
| WhatsApp Cloud API | adaptador `LOG` do `NotificationService` | E1.11, e o enum `AlertChannel.LOG` já existe desde a E1.1 |
| PostgreSQL | **nenhuma** — usamos o banco real | [D-020](DECISOES.md) |

## 10. Stack definitiva

| Camada | Tecnologia |
|---|---|
| Core / API | Java 21 + Spring Boot 4.1.0 (Spring Framework 7) — porta **8081** |
| Build Java | Maven 3.8.6 |
| Migrations | Flyway |
| Banco | PostgreSQL 17 (Docker) — host na porta **5433**, pois a 5432 é usada por um PostgreSQL nativo do Windows |
| Worker | Python + FastAPI |
| Coleta | Travelpayouts Data API + fast-flights |
| Front-end | Vue 3 + Vite + TypeScript |
| Notificação | WhatsApp Cloud API (número de teste Meta) |
| Integração | REST no MVP → RabbitMQ na Fase 4 |
| Container | Docker / Docker Compose |
