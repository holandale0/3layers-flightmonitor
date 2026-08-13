# Progresso

> Diário de bordo do projeto. Atualizado a cada etapa concluída.
> Roteiro completo em [PLANO-DE-ACAO.md](PLANO-DE-ACAO.md).

## Situação atual

**Fase:** 4 — Infraestrutura
**Etapa em andamento:** E4.3 — observabilidade
**Bloqueio ativo:** nenhum — 🏁 **a Fase 1 fechou** com a E1.12
**Fora do roteiro:** ✅ arquitetura BCE nos três módulos, com as camadas verificadas por teste
**Canal ativo:** `EMAIL` — validado com envio real, inclusive de dentro do container
**Como subir tudo:** `docker compose up -d` · painel em http://localhost:8090
**Pendente de túnel público:** `DELIVERED`/`READ` da E1.16 — o webhook precisa de URL pública
**Última atualização:** 2026-08-13

## Painel de etapas

### Fase 0 — Fundação
| ID | Etapa | Status |
|---|---|---|
| E0.1 | Docker Compose com PostgreSQL | ✅ concluída |
| E0.2 | Esqueleto Spring Boot | ✅ concluída |
| E0.3 | Esqueleto FastAPI | ✅ concluída |
| E0.4 | Esqueleto Vue 3 + Vite | ✅ concluída |
| E0.5 | Git init + .gitignore | ✅ concluída |

### Fase 1 — Engine
| ID | Etapa | Status |
|---|---|---|
| E1.1 | Schema do banco (Flyway) | ✅ concluída |
| E1.2 | Entidades JPA + repositórios | ✅ concluída |
| E1.3 | API REST de monitores | ✅ concluída |
| E1.4 | API REST de destinatários | ✅ concluída |
| E1.5 | Provider Travelpayouts | ✅ concluída |
| E1.6 | Provider fast-flights | ✅ concluída |
| E1.7 | Contrato Java ↔ Python | ✅ concluída |
| E1.8 | Persistência de observações | ✅ concluída |
| E1.9 | Scheduler de varredura | ✅ concluída |
| E1.10 | Regra de alerta + anti-spam | ✅ concluída |
| E1.11 | NotificationService (log) | ✅ concluída |
| E1.12 | Adaptador WhatsApp | ✅ concluída |
| E1.13 | Painel Vue: monitores | ✅ concluída |
| E1.14 | Painel Vue: histórico | ✅ concluída |
| E1.15 | **E2E do motor** (WireMock) | ✅ concluída |
| E1.16 | **E2E entre serviços** | ✅ concluída |
| E1.17 | **Webhook de status do WhatsApp** | ✅ concluída |

### Fase 2 — Inteligência
| ID | Etapa | Status |
|---|---|---|
| E2.1 | Estatísticas de rota | ✅ concluída |
| E2.2 | Detecção de anomalia | ✅ concluída |
| E2.3 | Flight Score | ✅ concluída |
| E2.4 | Alerta enriquecido | ✅ concluída |
| E2.5 | Tendência de preço | ✅ concluída |
| E2.6 | Preferências do monitor | ✅ concluída |

### Fase 3 — Agente
| ID | Etapa | Status |
|---|---|---|
| E3.1 | Endpoint de linguagem natural | ✅ concluída |
| E3.2 | Criação de monitor por conversa | ✅ concluída |
| E3.3 | Recomendação em linguagem natural | ✅ concluída |

### Fase 4 — Infraestrutura
| ID | Etapa | Status |
|---|---|---|
| E4.1 | RabbitMQ | ✅ concluída |
| E4.2 | Dockerização completa | ✅ concluída |
| E4.3 | Observabilidade | ⬜ pendente |
| E4.4 | Deploy | ⬜ pendente |
| E4.5 | **Canário ao vivo** das fontes | ⬜ pendente |
| E4.6 | **Canal de e-mail** | ✅ concluída (falta só a senha de app para o envio real) |

### Fora do roteiro
| Item | Status |
|---|---|
| Arquitetura BCE nos três módulos, verificada por teste | ✅ concluída |
| Limpeza de dados pessoais + repositório recomeçado | ✅ concluída |
| Painel: cadastro de destinatários | ✅ concluída |

**Legenda:** ⬜ pendente · 🟡 em andamento · ✅ concluída · 🔴 bloqueada · 📋 decidida, não implementada

---

## Diário

### 2026-08-09 — Planejamento inicial

**Feito:**
- Analisada a discussão original que originou o projeto
- Levantado o ambiente local: Java 21.0.10 (Temurin), Maven 3.8.6, Python 3.14.4,
  Node 22.23.1, Docker 29.1.3, Git 2.45.1
- Pesquisadas as fontes de preço disponíveis em 2026
- **Descoberta crítica:** o Amadeus Self-Service foi descontinuado em 17/07/2026 —
  a rota "API oficial gratuita" não existe mais
- Definida a estratégia de coleta em duas camadas (Travelpayouts + fast-flights)
- Confirmado que o WhatsApp Cloud API tem número de teste gratuito — dispensa
  comprar chip dedicado no MVP
- Decidido o front-end: Vue 3 + Vite + TypeScript
- Criados `PLANO-DE-ACAO.md`, `DECISOES.md`, `PROGRESSO.md` e `BUGS.md`

**Decisões registradas:** D-008 a D-012 em [DECISOES.md](DECISOES.md)

**Próximo passo:** E0.1 — subir o PostgreSQL via Docker Compose.

---

### 2026-08-09 — ✅ E0.1 concluída · PostgreSQL via Docker Compose

**Entregue:**
- `docker-compose.yml` com PostgreSQL 17.10 (alpine), volume nomeado `flightmon-postgres-data`,
  healthcheck via `pg_isready` e `restart: unless-stopped`
- `.env` com senha aleatória de 24 caracteres (fora do Git)
- `.env.example` versionado, já com os campos de Travelpayouts e WhatsApp reservados
- `.gitignore` na raiz protegendo `.env`, `target/`, `node_modules/`, `.venv/`

**Achado de ambiente — conflito de porta:**
A porta 5432 já estava ocupada por um **PostgreSQL 15 nativo instalado no Windows**
(`C:\Program Files\PostgreSQL\15`). O container foi mapeado para **5433** no host para os
dois coexistirem sem conflito. Toda string de conexão do projeto usa `localhost:5433`.

**Testes executados — todos passaram:**

| Teste | Resultado |
|---|---|
| Container atinge `healthy` | ✅ em ~40s |
| Encoding do servidor | ✅ UTF8 |
| DDL + INSERT + SELECT + DROP dentro do container | ✅ |
| Conexão autenticada do **host** via `localhost:5433` | ✅ banco e usuário corretos |
| Dados sobrevivem a `docker compose restart` | ✅ volume persiste |

**Observação:** o cliente `psql` do host é da versão 15 e o servidor é 17. Funcionou
normalmente para os testes, mas o acesso oficial do projeto será via driver JDBC do
Spring Boot, não pelo cliente nativo.

**Comandos úteis:**
```bash
docker compose up -d       # sobe o banco
docker compose ps          # status e saúde
docker compose logs -f postgres
docker compose down        # para (mantém os dados)
docker compose down -v     # para e APAGA os dados
```

**Próximo passo:** E0.2 — esqueleto do Spring Boot em `core-java/`.

---

### 2026-08-09 — ✅ E0.2 concluída · Esqueleto Spring Boot

**Entregue:** projeto Maven em `core-java/`, gerado pelo Spring Initializr.

- Grupo `com.flightmonitor`, artefato `flight-monitor-core`, pacote `com.flightmonitor.core`
- **Spring Boot 4.1.0** (Spring Framework 7) sobre Java 21
- Dependências: WebMVC, Data JPA, Flyway, PostgreSQL, Actuator, Validation, Lombok
- `application.yml` substituindo o `.properties` padrão
- Pasta `src/main/resources/db/migration/` pronta para o Flyway (etapa E1.1)
- POM limpo: removidos os blocos vazios de `licenses`, `developers`, `scm` e `url`

**Mudança em relação ao plano — Spring Boot 4, não 3:**
O Initializr não oferece mais a linha 3.x. Adotado o 4.1.0. Isso muda nomes de artefatos:
`spring-boot-starter-webmvc` (não `-web`), starter próprio de Flyway, e starters de teste
granulares por módulo. Registrado em [D-013](DECISOES.md#d-013--spring-boot-410-em-vez-de-spring-boot-3-).

**Segundo conflito de porta:**
A 8080 estava ocupada por um processo `AgentService`. A API foi fixada em **8081**.
O mapa de portas do projeto está em [D-014](DECISOES.md#d-014--portas-do-projeto-).

**Credenciais sem duplicação:**
O `application.yml` importa o `.env` da raiz com
`spring.config.import: optional:file:../.env[.properties]`. O mesmo arquivo alimenta o
docker-compose e a aplicação — a senha do banco existe em um lugar só.
Registrado em [D-015](DECISOES.md#d-015--o-env-da-raiz-é-a-única-fonte-de-credenciais-).

**Testes executados — todos passaram:**

| Teste | Resultado |
|---|---|
| `mvn clean verify` | ✅ BUILD SUCCESS em 34s |
| Teste de contexto (`@SpringBootTest`) | ✅ 1 teste, 0 falhas |
| Flyway conectou no banco real durante o teste | ✅ criou `flyway_schema_history` no PostgreSQL |
| App sobe standalone | ✅ Tomcat na 8081, startup em 14,3s |
| `GET /actuator/health` | ✅ `UP` — **com `db: UP`** |
| `GET /actuator/info` e `/actuator/metrics` | ✅ HTTP 200 |
| Rota inexistente | ✅ HTTP 404 |

O teste que mais importa é o `db: UP` somado à criação da `flyway_schema_history`: prova
que o `.env` foi lido, o pool Hikari conectou e o Flyway inicializou contra o container
da E0.1. A fundação Java↔banco está de pé de ponta a ponta.

**Comandos úteis:**
```bash
cd core-java
mvn clean verify                              # build + testes
mvn spring-boot:run                           # sobe em modo dev
java -jar target/flight-monitor-core-0.0.1-SNAPSHOT.jar
curl http://localhost:8081/actuator/health
```

**Próximo passo:** E0.3 — esqueleto do FastAPI em `worker-python/`.
Atenção ao RISCO-001 (Python 3.14 pode não ter wheels prontas).

---

### 2026-08-09 — ✅ E0.3 concluída · Esqueleto FastAPI

**Entregue:** worker em `worker-python/`, com venv próprio no Python 3.14.4.

```
worker-python/
├── app/
│   ├── config.py           # pydantic-settings lendo o .env da raiz
│   ├── main.py             # FastAPI + lifespan
│   ├── routers/health.py   # GET /health
│   └── providers/          # camadas 1 e 2 da coleta (E1.5 / E1.6)
├── tests/test_health.py
├── requirements.txt
├── requirements-dev.txt
└── pyproject.toml          # config do pytest e do ruff
```

- FastAPI 0.141.1, Uvicorn 0.52.1, Pydantic 2.13.4, httpx 0.28.1
- Porta **8001**
- O `/health` já reporta a prontidão de cada fonte de preço, e não só um "ok" genérico —
  serve de diagnóstico quando um provider cair

**RISCO-001 descartado.** Era a maior incógnita da etapa. Todas as libs nativas têm wheel
`cp314` pronta — `pydantic-core`, `httptools`, `watchfiles`, `websockets`, `pyyaml`. Nada
compilou. Checado por antecipação também o `fast-flights` 3.0.2 da etapa E1.6, com
`pip install --dry-run`: resolve normalmente. **O venv fica no 3.14.4, sem rebaixar para 3.12.**

**Credenciais:** o `config.py` lê o mesmo `.env` da raiz usado pelo docker-compose e pelo
core-java, com `extra="ignore"` para conviver com as chaves de Postgres e WhatsApp.
Comprovado em teste: token gravado no `.env` foi lido corretamente pelo worker.

**Testes executados — todos passaram:**

| Teste | Resultado |
|---|---|
| `ruff check` | ✅ All checks passed |
| `pytest` | ✅ 4 testes, 0 falhas |
| `pytest -W error::DeprecationWarning` | ✅ passou — nenhuma depreciação no nosso código |
| Worker sobe via uvicorn na 8001 | ✅ startup em ~1s |
| `GET /health` | ✅ `UP`, com prontidão dos providers |
| `GET /docs` e `/openapi.json` | ✅ HTTP 200 |
| Rota inexistente | ✅ HTTP 404 |
| Leitura do `.env` da raiz | ✅ token de teste lido corretamente |

**Correções feitas durante a etapa:**
1. `@app.on_event("startup")` estava **deprecado** no FastAPI atual — migrado para
   `lifespan` com `asynccontextmanager`. Não deixamos código depreciado num esqueleto novo.
2. **BUG-001** — acento corrompido no log do Windows. Corrigido, ver [BUGS.md](BUGS.md).

**Novo risco registrado:** RISCO-005 — o Starlette avisa que o TestClient vai exigir
`httpx2`. Como o `httpx` também será o cliente da Travelpayouts, decidir antes da E1.5.

**Comandos úteis:**
```bash
cd worker-python
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/python.exe -m pytest
.venv/Scripts/python.exe -m ruff check .
.venv/Scripts/python.exe -m uvicorn app.main:app --reload --port 8001
curl http://127.0.0.1:8001/health
```

**Próximo passo:** E0.4 — esqueleto Vue 3 + Vite + TypeScript em `frontend-vue/` (porta 5173).

---

### 2026-08-09 — ✅ E0.4 concluída · Esqueleto Vue 3 + Vite

> A pasta foi renomeada pelo usuário de `front-end/` para **`frontend-vue/`**.
> Todas as referências na documentação foram atualizadas.

**Entregue:**

```
frontend-vue/
├── src/
│   ├── api/client.ts              # cliente HTTP + ApiError
│   ├── types/health.ts            # tipagem da resposta do actuator
│   ├── components/StatusPanel.vue # painel de status do sistema
│   ├── App.vue
│   └── style.css                  # tokens de cor, com suporte a tema escuro
├── vite.config.ts                 # porta 5173 + proxy /api -> :8081
└── tsconfig.app.json              # alias "@" -> ./src
```

- Vue 3.5.40, Vite 8.2.1, TypeScript 6.0
- Porta **5173** com `strictPort: true` — falha alto em vez de trocar de porta em silêncio,
  o que evita o tipo de confusão que já tivemos com as portas 5432 e 8080
- Removidos os arquivos de demonstração do template (`HelloWorld.vue`, assets do Vite)

**CORS não será necessário em desenvolvimento.** O proxy do Vite encaminha `/api` para
`http://localhost:8081`, então o navegador vê tudo como mesma origem. Isso simplifica o
Spring Boot — nenhuma configuração de CORS até termos deploy em domínios separados.

**Arquitetura respeitada:** o proxy expõe **apenas** o core-java. O worker-python não é
alcançável pelo navegador, conforme a regra 2 da seção 3 do plano.

**Testes executados — todos passaram:**

| Teste | Resultado |
|---|---|
| `npm install` | ✅ 48 pacotes, 0 vulnerabilidades |
| `npm run build` (vue-tsc + vite build) | ✅ build em 1,67s, 0 erros de tipo |
| Vite dev server sobe na 5173 | ✅ ready em 1.176ms |
| HTML servido com título e `#app` corretos | ✅ |
| Módulos `.ts` e `.vue` carregam | ✅ HTTP 200 em todos |
| **Proxy `/api/actuator/health` → core-java** | ✅ `UP` com `db: UP` |

O último teste é o marco da Fase 0: a requisição saiu do front na 5173, atravessou o proxy
do Vite, chegou ao Spring Boot na 8081, que consultou o PostgreSQL na 5433 e devolveu
`db: UP`. **A cadeia completa front → API → banco está fechada.**

**Correções feitas durante a etapa:**
1. `baseUrl` no tsconfig — **deprecado no TypeScript 6**, quebrava o build. Removido; o
   `paths` já resolve relativo ao próprio tsconfig desde o TS 5.
2. `ApiError` usava *parameter property* no construtor, proibido pelo `erasableSyntaxOnly`
   que o template do Vite ativa. Reescrito com campo declarado no corpo da classe.

Nenhum dos dois virou bug em `BUGS.md`: foram erros de compilação pegos e corrigidos dentro
da própria etapa, sem chegar a rodar.

**Achado de ambiente:** apareceu uma pasta `.github/modernize/java-upgrade/` que não foi
criada por nós — é a extensão *GitHub Copilot App Modernization for Java* do VS Code, que
reagiu ao projeto Maven. É benigna e traz um `.gitignore` próprio com `**/*`, então não
entra no commit da E0.5.

**Comandos úteis:**
```bash
cd frontend-vue
npm install
npm run dev      # http://localhost:5173
npm run build    # type-check + build de producao
```

**Próximo passo:** E0.5 — `git init`, revisar o `.gitignore` e primeiro commit.

---

### 2026-08-09 — ✅ E0.5 concluída · Repositório inicializado

**Entregue:**
- `git init -b main` — branch `main` (o `init.defaultBranch` global estava em `master`)
- `README.md` na raiz: arquitetura, estratégia de coleta, como rodar e roadmap
- `.gitattributes` na raiz normalizando fim de linha
- Commit inicial `9e8594c` com **48 arquivos**

**Fim de linha normalizado.** O `git add` disparou 41 avisos de `LF will be replaced by
CRLF`. O `.gitattributes` do Initializr cobria só o `core-java`. Criado um na raiz com
`* text=auto eol=lf`, mantendo CRLF apenas para `.cmd`, `.bat` e `.ps1`. Num projeto que
roda em container Linux isso não é cosmético: **script shell com CRLF quebra dentro do
container**, com erro obscuro do tipo `bad interpreter`.

**Auditoria de segredos antes do commit:**

| Verificação | Resultado |
|---|---|
| `.env` ignorado | ✅ pela regra `*.env` |
| `.env.example` rastreável | ✅ preservado pela negação `!.env.example` |
| `core-java/target/` ignorado | ✅ |
| `worker-python/.venv/` ignorado | ✅ |
| `frontend-vue/node_modules/` e `dist/` ignorados | ✅ |
| `.github/modernize/` ignorado | ✅ pelo `.gitignore` próprio da extensão |
| Busca da senha real do banco no conteúdo staged | ✅ nenhuma ocorrência |
| Busca por padrões `password=`, `token=`, `api_key=` | ✅ nenhuma ocorrência |
| `git ls-files .env` | ✅ não rastreado |

> **Nota de método:** a primeira verificação de `.gitignore` deu um falso positivo. Eu havia
> testado se o `git check-ignore -v` produzia saída, mas ele também imprime regras de
> **negação** — então `.env.example` apareceu como "ignorado" quando na verdade estava
> corretamente rastreável. O certo é usar o **código de saída** (`git check-ignore -q`).
> Refeito e confirmado.

**Comandos úteis:**
```bash
git log --oneline
git check-ignore -q .env && echo "protegido"
```

---

## 🏁 Fase 0 concluída

Os três componentes existem, sobem e conversam entre si. A cadeia
**navegador → Vite → Spring Boot → PostgreSQL** foi validada de ponta a ponta.

| Componente | Porta | Validação |
|---|---|---|
| PostgreSQL 17 (Docker) | 5433 | dados sobrevivem a restart |
| core-java (Spring Boot 4.1) | 8081 | `/actuator/health` com `db: UP` |
| worker-python (FastAPI) | 8001 | `/health` + 4 testes passando |
| frontend-vue (Vue 3 + Vite) | 5173 | build sem erro de tipo + proxy funcionando |

**Placar:** 5 de 32 etapas · 1 bug encontrado e resolvido · 1 risco descartado (RISCO-001)
· 1 risco novo em observação (RISCO-005)

**Próximo passo:** E1.1 — schema do banco via Flyway, criando as tabelas da seção 6 do
plano. É a etapa que dá forma ao domínio do sistema.

---

### 2026-08-09 — ✅ E1.1 concluída · Schema do banco via Flyway

**Entregue:** `core-java/src/main/resources/db/migration/V1__initial_schema.sql` — 6 tabelas,
8 índices, 26 constraints CHECK, 2 triggers.

| Tabela | Colunas | Papel |
|---|---|---|
| `monitor` | 20 | critérios de busca vigiados |
| `recipient` | 6 | quem recebe alerta no WhatsApp |
| `monitor_recipient` | 2 | vínculo N:N |
| `search_run` | 8 | execuções de varredura, para medir falha por fonte |
| `price_observation` | 17 | **cada preço visto — a tabela mais importante** |
| `alert` | 11 | um envio por destinatário, com resultado da entrega |

**Decisão de projeto — o histórico pertence à rota, não ao monitor.**
`price_observation` carrega `origin`/`destination` denormalizados e a FK para `monitor` é
`ON DELETE SET NULL`. Meses de aprendizado sobre GRU→LIS não podem sumir porque alguém
apagou um monitor, e dois monitores da mesma rota devem somar observações em vez de manter
históricos separados. Registrado em [D-016](DECISOES.md).

**Outras escolhas:**
- Dinheiro em `numeric(10,2)`, nunca `float` — centavo perdido em histórico de preço
  contamina toda a estatística da Fase 2
- Datas de voo em `date` e instantes em `timestamptz`; horários de voo em `timestamp`
  **sem** fuso, porque horário de partida é local do aeroporto
- Índice parcial `idx_monitor_proxima_busca ... WHERE active` — o scheduler só pergunta
  por monitores ativos
- CHECKs semânticos: IATA em maiúsculas, origem ≠ destino, janela de volta preenchida aos
  pares, permanência mínima ≤ máxima, telefone em E.164

**Testes executados — 26 asserções, todas passaram.**
Suíte guardada em `core-java/src/test/resources/db/teste_schema.sql`, rodando em transação
com ROLLBACK ao final.

| Grupo | Verificações |
|---|---|
| `monitor` | aceita válido; rejeita IATA minúsculo, origem=destino, janela invertida, janela de volta pela metade, preço zero, zero passageiros, permanência invertida, intervalo < 5 min |
| `recipient` | aceita E.164 válido; rejeita telefone com pontuação, sem `+`, e duplicado |
| Integridade | rejeita vínculo para monitor inexistente; cascata remove vínculo órfão |
| Trigger | `updated_at` sobrepõe o valor enviado pela aplicação |
| `search_run` | rejeita fonte desconhecida e status inválido |
| `price_observation` | rejeita preço negativo e volta antes da ida |
| `alert` | rejeita canal não suportado |
| **Sobrevivência do histórico** | **apagar o monitor mantém as observações, com `monitor_id` nulo** |

**Dois problemas encontrados durante os testes:**

1. **Falso negativo na captura de erro.** O telefone `'+55 11 99999-8888'` era rejeitado por
   estouro de `varchar(16)` (SQLSTATE 22001), não por CHECK — e o helper do teste só
   capturava violações de integridade. A rejeição estava certa; o teste é que não a
   reconhecia. Helper ampliado para `string_data_right_truncation`.

2. **`now()` não é o horário atual.** O teste do trigger falhou porque `now()` no PostgreSQL
   devolve o horário de **início da transação**. Como INSERT e UPDATE rodavam na mesma
   transação, o `updated_at` não mudava — e isso está correto. O teste é que media a
   propriedade errada; foi reescrito para verificar o que de fato importa: que o trigger
   **sobrepõe** o valor enviado pela aplicação, tornando `updated_at` não-forjável.
   Nuance registrada em [D-017](DECISOES.md).

Nenhum dos dois foi para `BUGS.md` — foram defeitos do teste, não do schema.

**Comandos úteis:**
```bash
# aplicar migrations (acontece no startup)
cd core-java && mvn spring-boot:run

# rodar a suite de constraints
psql -h localhost -p 5433 -U flightmon -d flightmon \
     -f core-java/src/test/resources/db/teste_schema.sql
```

**Próximo passo:** E1.2 — entidades JPA e repositórios espelhando este schema. O
`ddl-auto: validate` vai conferir se as entidades batem exatamente com as tabelas.

---

### 2026-08-09 — ✅ E1.2 concluída · Entidades JPA e repositórios

**Entregue:** 5 entidades, 4 enums e 5 repositórios, organizados **por feature** e não em um
pacote `domain` monolítico — a E1.3 e a E1.4 vão adicionar services e controllers ao lado.

```
com.flightmonitor.core
├── monitor/    Monitor, MonitorRepository
├── recipient/  Recipient, RecipientRepository
├── search/     SearchRun, PriceObservation, PriceSource, SearchStatus, + repositórios
└── alert/      Alert, AlertChannel, AlertStatus, AlertRepository
```

**Consultas já preparadas para as próximas etapas:**

| Consulta | Serve a |
|---|---|
| `findByActiveTrueAndNextSearchAtLessThanEqual...` | scheduler (E1.9), usa o índice parcial |
| `findByIdComDestinatarios` (join fetch) | envio de alertas sem N+1 (E1.12) |
| `findFirstBy...DepartureDateAndReturnDate...` | anti-spam (E1.10) |
| `menorPrecoDaRota` / `precoMedioDaRota` | estatísticas da Fase 2 |
| `countBySourceAndStatusAndStartedAtAfter` | taxa de falha por provider |

As duas consultas de estatística filtram por **rota**, não por monitor — coerente com
[D-016](DECISOES.md).

**🐛 BUG-002 — o achado mais importante desta etapa.**
O teste do "último preço visto" falhou. Causa: `observed_at` usava `DEFAULT now()`, e no
PostgreSQL `now()` é o horário de **início da transação**. Observações gravadas na mesma
varredura recebiam instantes idênticos, deixando o `ORDER BY` indefinido.

Isso teria quebrado o anti-spam da E1.10 de forma intermitente — o cenário é rotineiro, já
que camada 1 e camada 2 gravam para a mesma data na mesma transação. Corrigido pela migration
`V2__event_timestamps_clock.sql`, distinguindo timestamp de **evento** (`clock_timestamp()`)
de timestamp de **auditoria** (`now()`), conforme [D-018](DECISOES.md). Consultas ordenadas
ganharam desempate por `id`.

Vale notar: essa mesma pegadinha apareceu na E1.1 e pareceu ser só um defeito de teste.
Aqui ela se revelou um defeito real de projeto.

**Correção de contexto de persistência.** O teste de exclusão do monitor quebrava com
`TransientPropertyValueException`: o `ON DELETE SET NULL` acontece no banco, e o Hibernate
não sabe disso. É preciso limpar o contexto antes de apagar.

**Descoberta do Spring Boot 4.** As anotações de teste mudaram de pacote:
`org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest` agora é
`org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`, e `AutoConfigureTestDatabase`
está em `org.springframework.boot.jdbc.test.autoconfigure`. O padrão é
`org.springframework.boot.<módulo>.test.autoconfigure`.

**Testes executados — 15 testes, todos passaram.**

| Teste | Verifica |
|---|---|
| Campos gerados pelo banco | `createdAt`/`updatedAt` chegam preenchidos; defaults Java batem com os do banco |
| CHECK de IATA e de rota | exceção cita `monitor_origin_iata` / `monitor_rota_distinta` |
| CHECK de telefone | rejeita fora do E.164 |
| Vínculo N:N | `join fetch` traz os destinatários |
| Consulta do scheduler | devolve só o ativo e vencido, ignorando o futuro e o inativo |
| `registrarBusca` | agenda a próxima varredura pelo intervalo |
| Enum como texto | grava `TRAVELPAYOUTS`, e não ordinal — é o que o CHECK valida |
| **Estatística por rota** | soma observações de **dois monitores diferentes** da mesma rota |
| Último preço da data | devolve a observação mais recente (anti-spam) |
| **Histórico sobrevive ao monitor** | apagar o monitor mantém a observação com `monitor_id` nulo |
| Ciclo de vida de `SearchRun` e `Alert` | RUNNING→SUCCESS, PENDING→SENT com id do provedor |
| Paginação do histórico | ordem estável, contagem correta |

**Rede de segurança comprovada.** Não bastava afirmar que o `ddl-auto: validate` protege
contra divergência entre entidade e schema — introduzi uma coluna inexistente de propósito
e a aplicação recusou-se a subir com
`SchemaManagementException: missing column [coluna_que_nao_existe] in table [recipient]`.
Divergência revertida em seguida, suíte verde.

**Comandos úteis:**
```bash
cd core-java
mvn test                       # exige o container do banco de pe
mvn test -Dtest=PersistenciaTest
```

**Próximo passo:** E1.3 — API REST de monitores (CRUD com validação de payload).

---

### 2026-08-09 — ✅ E1.3 concluída · API REST de monitores

**Entregue:** CRUD completo em `/api/monitors`.

| Verbo | Rota | Resposta |
|---|---|---|
| POST | `/api/monitors` | 201 + `Location` |
| GET | `/api/monitors?active=true` | 200, filtro opcional |
| GET | `/api/monitors/{id}` | 200 / 404 |
| PUT | `/api/monitors/{id}` | 200 / 404 |
| DELETE | `/api/monitors/{id}` | 204 / 404 |

```
monitor/
├── MonitorController.java
├── MonitorService.java
└── dto/  MonitorRequest, MonitorResponse, RecipientSummary,
         JanelasCoerentes + JanelasCoerentesValidator
common/
├── ApiExceptionHandler.java   (RFC 7807)
├── NotFoundException.java
└── ConflitoException.java
```

**Erros no padrão RFC 7807.** O `ApiExceptionHandler` devolve `ProblemDetail`, com os erros
de validação detalhados campo a campo:

```json
{
  "status": 400,
  "title": "Payload invalido",
  "type": "urn:flightmonitor:payload-invalido",
  "detail": "Um ou mais campos estao invalidos",
  "errors": {
    "origin": "deve ser um codigo IATA: 3 letras maiusculas",
    "maxPrice": "deve ser maior que zero",
    "departureWindowStart": "nao faz sentido monitorar uma data que ja passou",
    "departureWindowEnd": "deve ser igual ou posterior a departureWindowStart"
  }
}
```

**Validador de coerência entre campos.** As regras que envolvem mais de um campo (fim depois
do início, janela de volta aos pares, permanência mínima ≤ máxima) viraram uma constraint
de classe `@JanelasCoerentes`, que aponta **o campo específico** em vez de dar um erro
genérico de objeto.

**Conveniências no DTO:** IATA em minúsculas é normalizado para maiúsculas, e moeda,
passageiros, `active` e intervalo ganham default no construtor compacto, espelhando os
DEFAULT do banco.

**Nova descoberta do Spring Boot 4 — Jackson 3.** O pacote mudou de `com.fasterxml.jackson`
para `tools.jackson`. Somado às mudanças de artefato e de pacote de teste já registradas,
reforça a [D-013](DECISOES.md): conferir a fonte antes de copiar exemplo de Spring Boot 3.

**⚠️ Revisão da E0.4 — proxy do Vite.** O proxy removia o prefixo `/api`, o que exigiria
chamar `/api/api/monitors`. Corrigido para encaminhar sem reescrita, com entrada separada
para `/actuator`. Agora o caminho no navegador é idêntico ao do servidor. Ver
[D-021](DECISOES.md). O cliente do front ganhou `getJson` e `getActuator`.

**🐛 BUG-003 — processo órfão do Vite.** Ao subir o Vite para validar o proxy novo, ele
falhou com `Port 5173 is already in use`, embora a porta respondesse 200. O `node` da etapa
E0.4 nunca havia morrido: o encerramento matou só o processo pai, e o filho seguia servindo
a **configuração antiga**. Sem o `strictPort: true`, o Vite teria subido na 5174 em silêncio
e eu teria validado o proxy velho, concluindo errado. Detalhes em [BUGS.md](BUGS.md).

**Testes executados — 31 no total, todos passaram** (16 novos de API + 15 anteriores).

| Teste | Verifica |
|---|---|
| POST cria | 201, `Location`, defaults aplicados |
| Normalização de IATA | `gru` vira `GRU` |
| Payload vazio | 400 com erro por campo obrigatório |
| IATA malformado | 400 explicando o formato |
| Preço zerado | 400 |
| Data no passado | 400 |
| Janela invertida | 400 apontando `departureWindowEnd` |
| Janela de volta pela metade | 400 apontando `returnWindowEnd` |
| Permanência invertida | 400 apontando `maxStayDays` |
| Origem = destino | 409 |
| GET por id | 200 e 404 |
| Filtro `?active=true` | devolve só ativos |
| PUT | atualiza campos; 404 em id inexistente |
| DELETE | 204, depois 404 nas chamadas seguintes |
| Vínculo de destinatários | vincula existente; 404 em id inexistente |

**Verificação manual ponta a ponta pelo proxy do Vite:** `POST /api/monitors` na porta 5173
devolveu 201 com o `gru` normalizado, e o payload inválido devolveu o `ProblemDetail`
completo. O monitor de teste foi removido e o banco conferido: zero linhas em todas as
tabelas.

**Próximo passo:** E1.4 — API REST de destinatários, com validação de telefone E.164 e
tratamento de telefone duplicado (409).

---

### 2026-08-09 — ✅ E1.4 concluída · API REST de destinatários

**Entregue:** CRUD completo em `/api/recipients`, reaproveitando a estrutura de service, DTO
e `ApiExceptionHandler` estabelecida na E1.3.

**Normalização de telefone.** O DTO remove espaços, hífens, parênteses e pontos antes de
validar — `+55 (11) 91111-3333` vira `+5511911113333`. Mas o `+` do E.164 **não é inventado**:
se faltar o código do país, o cadastro é recusado com explicação. Adivinhar o país seria pior
do que reclamar, porque geraria um número silenciosamente errado que só falharia na hora de
enviar o alerta.

Efeito colateral desejável: a detecção de duplicidade passa a funcionar mesmo com formatações
diferentes do mesmo número.

**Duplicidade tratada na aplicação, não no banco.** O service consulta antes de inserir e
devolve **409 com o telefone no detalhe**, em vez de deixar a `UNIQUE` do banco estourar um
500. No update, só é conflito se o telefone pertencer a *outro* destinatário — manter o
próprio número ao editar o nome funciona normalmente.

**Testes executados — 43 no total, todos passaram** (12 novos + 31 anteriores).

| Teste | Verifica |
|---|---|
| POST cria | 201, `Location`, `active` default |
| Normalização | `+55 (11) 91111-3333` → `+5511911113333` |
| Telefone sem `+` | 400 explicando o E.164 |
| Nome em branco | 400 |
| Telefone duplicado | **409**, não 500 |
| Duplicado com outra formatação | 409 |
| GET por id | 200 e 404 |
| PUT mantendo o próprio telefone | 200, sem falso conflito |
| PUT com telefone alheio | 409 |
| Desativar | `active: false` some do filtro `?active=true` |
| DELETE | 204 e depois 404 |
| **Exclusão não derruba o monitor** | vínculo some por cascata, monitor permanece |

**Reincidência conhecida.** O último teste falhou com
`TransientPropertyValueException` — mesma causa da E1.2: a cascata de `monitor_recipient`
acontece **no banco**, e o Hibernate não sabe disso. Em produção cada requisição tem seu
próprio contexto de persistência, mas o teste roda tudo numa transação só. Resolvido com um
helper `simularNovaRequisicao()` que dá `flush` e `clear`, reproduzindo o isolamento real.

Não foi para `BUGS.md`: é característica conhecida da combinação ORM + cascata no banco, já
documentada. Vale como padrão para os próximos testes.

**🔑 Token da Travelpayouts configurado.** Gravado no `.env` (ignorado pelo Git) e **validado
com chamada real** à API, que respondeu com preços de verdade. Isso antecipou dois problemas
que teriam custado tempo na E1.5:

- **[RISCO-006]** pedimos `origin=GRU` e a API devolveu `origin=SAO` — código da **cidade**,
  não do aeroporto. Como o histórico é indexado por rota, gravar ora `GRU` ora `SAO`
  transformaria uma rota em duas e corromperia a média da Fase 2.
- **[RISCO-007]** pedimos `depart_date=2027-03` e vieram datas de **agosto e setembro de
  2026**. A API devolve o que tem em cache e ignora o mês pedido. O worker precisa filtrar
  contra a janela do monitor, nunca confiando que o provider respeitou o filtro.

**Achado positivo:** a resposta traz `expires_at`, indicando até quando o preço cacheado vale.
É munição direta contra o RISCO-003 (falso-positivo por cache) — dá para descartar preço
vencido antes mesmo de acionar a camada 2.

**Próximo passo:** E1.5 — provider Travelpayouts no worker Python, resolvendo os riscos 006
e 007 e decidindo a pendência P-1 (moeda).

---

### 2026-08-09 — ✅ E1.5 concluída · Camada 1 buscando preço de verdade

**Esta é a etapa em que o sistema deixou de ser esqueleto.** Ele agora consulta preços reais.

**Entregue:**

```
worker-python/app/
├── schemas.py                    # contrato core <-> worker
├── providers/travelpayouts.py    # camada 1
└── routers/search.py             # POST /search/calendar
```

**Janela que cruza a virada do mês.** A API aceita só um mês por chamada, mas uma janela real
pode ir de 28/03 a 05/04. O provider quebra a janela em meses e junta os resultados —
inclusive na virada do ano. Coberto por três testes.

**Os dois riscos foram fechados, e o teste ao vivo mostrou o quanto importavam:**

| Consulta real | Recebidas | Dentro da janela |
|---|---|---|
| GRU→LIS, março/2027 | 30 | **0** |
| GRU→LIS, setembro/2026 | 30 | 8 |

Na primeira, **a API devolveu 30 ofertas e nenhuma era da janela pedida** — todas de outros
meses, vindas do cache. Sem o filtro do [RISCO-007](BUGS.md), teríamos gravado 30 observações
falsas e alertado sobre datas que o usuário nunca pediu. Não era risco teórico: aconteceu na
primeira chamada real.

Na segunda, dado real atravessou corretamente, filtrado e ordenado por preço:

```
2026-09-25 -> 2026-10-08   R$ 3375   DT voo 748   1 escala
      partida local: 2026-09-25T18:05:00   expira: 2026-08-09T20:14:40Z
```

**[RISCO-006] resolvido por decisão de projeto.** Pedimos `GRU`, a API responde `SAO` (código
de cidade). Gravamos sempre o **código pedido**, para o histórico da rota não se partir em
dois. A imprecisão fica registrada pelo campo `source`, sem precisar de coluna nova, e a
resposta traz `provider_origin` mais um aviso explícito. Ver [D-023](DECISOES.md).

**[RISCO-005] resolvido antes de virar retrabalho.** Migramos para `httpx2` **antes** de
escrever o cliente, aproveitando que ele ainda não existia. A superfície de API é
equivalente, o aviso de depreciação do TestClient sumiu, e o `httpx` foi desinstalado para
não manter dois clientes HTTP. Ver [D-025](DECISOES.md).

**Diagnóstico embutido na resposta.** Os campos `returned`, `kept` e `warnings` não são
enfeite: permitem distinguir "a janela não tem oferta" de "a fonte está fora do ar" sem abrir
log. Foi assim que a diferença entre as duas consultas acima ficou evidente na hora.

**Resiliência a dado corrompido.** Um dia com preço inválido ou data malformada é descartado
individualmente, sem invalidar o mês inteiro. Coberto por teste.

**Testes executados — 21 no worker, todos passaram** (17 novos + 4 anteriores), sem tocar a
rede: o HTTP é mockado com `httpx2.MockTransport`, reproduzindo o formato real da API.

| Grupo | Verificações |
|---|---|
| Quebra de janela em meses | mês único, virada de mês, virada de ano |
| Caminho feliz | oferta mantida, campos mapeados, `source` correto |
| RISCO-006 | grava `GRU` mesmo recebendo `SAO`, com aviso |
| RISCO-007 | descarta datas fora da janela pedida |
| RISCO-003 | descarta preço com `expires_at` vencido |
| Filtros | escalas e janela de volta |
| Ordenação | menor preço primeiro |
| Fuso | partida sem fuso, hora local preservada |
| Falhas | token ausente, HTTP 500, recusa da API, timeout |
| Robustez | registro corrompido não derruba o mês |

**Pendência P-1 (moeda) resolvida na prática:** a API aceita `currency=BRL` e devolve valores
em reais, então não há conversão a fazer. A moeda pedida é a moeda gravada.

**Lição do BUG-003 aplicada:** ao encerrar o worker, conferi a porta 8001 com
`Get-NetTCPConnection` em vez de confiar no encerramento da tarefa.

**Próximo passo:** E1.6 — camada 2 com `fast-flights`, confirmando o candidato com companhia,
escalas e horários reais antes de qualquer alerta.

---

### 2026-08-09 — ✅ E1.6 concluída · Camada 2 com Strategy, fallback e degradação

**Sobre a sugestão do Strategy.** A intuição estava certa, mas o alvo precisou ser ajustado:
o padrão **não** se aplica entre as camadas — elas fazem coisas diferentes, e uma interface
única criaria simetria falsa. Ele se aplica **dentro** de cada camada, onde `fast-flights`,
SerpApi e Playwright são genuinamente intercambiáveis. Ver [D-026](DECISOES.md).

E Strategy sozinho não bastava: ele resolve "trocar de implementação", não "a implementação
quebrou em produção num domingo". Somamos uma cadeia com fallback e degradação explícita
([D-027](DECISOES.md)).

**Entregue:**

```
worker-python/app/providers/
├── base.py         # dois Protocol, e a justificativa de serem dois
├── fastflights.py  # adaptador, fragilidade documentada linha a linha
├── chain.py        # fallback + degradação + medição de divergência
└── factory.py      # registro dos providers + chave de desligamento
```

| Endpoint | Papel |
|---|---|
| `POST /search/confirm` | confirma um candidato com dados reais de voo |

**A camada 2 se pagou na primeira execução real.** Confirmando um candidato de setembro:

```
candidato da camada 1 (cacheado):  R$ 3.375
preço real (Google Flights):       R$ 5.438
aviso: preco real 61% acima do candidato da camada 1
```

**61% de divergência.** Sem a camada 2, o sistema teria alertado sobre uma passagem que
custa quase o dobro do anunciado. O [RISCO-003](BUGS.md) deixou de ser teórico.

**A fragilidade se manifestou três vezes, antes de produção:**

| # | O que | Como apareceu |
|---|---|---|
| 1 | A API da biblioteca mudou por completo entre 2.x e 3.x — `FlightData` e `Result` não existem mais | Ao inspecionar a biblioteca instalada em vez de seguir tutorial |
| 2 | A anotação promete `list[Airline]`, mas devolve `list[str]` | `airline` chegou nulo no primeiro teste ao vivo |
| 3 | O parser devolveu `time=[None, 45]` — hora ausente, minuto presente | `departure_at` chegou nulo com o resto correto |

Os dois primeiros foram corrigidos. O terceiro é defeito da biblioteca e **não tem correção
nossa** — mas deixou de ser silencioso: agora gera o aviso *"confirmado, mas a fonte nao
trouxe: horario de partida"*. Isso importa porque a biblioteca não vai morrer de uma vez;
vai degradar aos poucos, e sem o aviso os campos chegariam nulos ao banco sem ninguém notar.

**Cinco camadas de proteção, todas verificadas:**

| Proteção | Verificação |
|---|---|
| Toda exceção vira `ProviderError` | teste com `AttributeError` do parser |
| Import tardio da biblioteca | falha de import não impede o worker de subir |
| Cadeia com fallback | teste: 1º falha → 2º confirma |
| **Chave de desligamento** | `FASTFLIGHTS_ENABLED=false` → HTTP 200 com `degraded: true` |
| Degradação observável | `/health`, `attempts[]` com duração e erro, `warnings` |

**Separação de ida e volta.** A biblioteca devolve todas as pernas numa lista única, sem
marcar direção. Sem separar, um GRU→LIS→GRU com conexão apareceria com 3 escalas em vez de 1.

**Testes executados — 42 no worker, todos passaram** (21 novos). A maioria é de **caminho de
falha**, de propósito: o que precisa estar garantido não é que a camada 2 funcione, e sim que
a quebra dela não derrube o sistema.

**📄 Documento dedicado:** [FRAGILIDADE-CAMADA-2.md](FRAGILIDADE-CAMADA-2.md) — por que é
frágil, o que se perde quando cai, as cinco proteções, e um plano de contingência em quatro
passos para o dia em que quebrar.

**Próximo passo:** E1.7 — cliente Java consumindo os dois endpoints do worker, com timeout e
tratamento do estado degradado.

---

### 2026-08-09 — 📋 Roteiro revisado · testes E2E incorporados

Provocado por uma pergunta do usuário: o roteiro cobria testes end-to-end?

**Resposta honesta: não cobria.** Havia 43 testes no Java e 42 no Python, mas todos dentro do
seu próprio serviço. Quatro lacunas ficavam descobertas:

1. Nenhum teste cruzava a fronteira **Java ↔ Python** — cada lado era testado com o outro
   ausente ou simulado, então erro de contrato passaria pelos dois
2. Nenhum teste exercitava o **fluxo de negócio completo** (monitor → alerta)
3. As verificações manuais por `curl` **não são repetíveis** — não protegem contra regressão
4. Nada detectaria **mudança nas fontes externas**: os testes usam HTTP mockado e
   continuariam verdes mesmo se o Google mudasse tudo

**Três etapas novas** (total do roteiro passa de 32 para 35):

| Etapa | Nível | O que faz |
|---|---|---|
| **E1.15** | E2E do motor | Spring completo + Postgres real + worker via WireMock. Um teste cobre monitor → scheduler → busca → observação → regra → alerta. Roda no `mvn test` |
| **E1.16** | E2E entre serviços | Java, Python e Postgres reais, sem stub entre eles. Só as fontes externas são falsas |
| **E4.5** | Canário ao vivo | Rotina agendada contra as APIs reais, **fora do CI**. Única defesa contra o RISCO-002 |

**E2E de navegador foi avaliado e recusado** nesta rodada: maior custo de manutenção e poucas
telas no projeto. Reavaliar depois da E1.14.

**Um efeito colateral bom do desenho anterior:** as costuras para os testes já existiam. O
Strategy da E1.6 permite registrar providers falsos pela `factory.py`, e o `AlertChannel.LOG`
está no schema desde a E1.1. Nenhum código de produção vai precisar de `if (teste)`.

Estratégia completa documentada na seção 9 do [PLANO-DE-ACAO.md](PLANO-DE-ACAO.md) e em
[D-029](DECISOES.md).

**Próximo passo:** E1.7 — cliente Java consumindo os dois endpoints do worker.

---

### 2026-08-09 — ✅ E1.7 concluída · Java e Python conversando

**As duas metades do sistema se falaram pela primeira vez.**

**Entregue:**

```
core-java/.../search/client/
├── SearchClient.java            # porta
├── RestSearchClient.java        # adaptador REST
├── WorkerClientConfig.java      # dois RestClient, um por camada
├── WorkerProperties.java        # @ConfigurationProperties
├── WorkerHealthIndicator.java   # worker aparece no /actuator/health
├── WorkerUnavailableException.java
└── dto/                         # 7 records espelhando o contrato do worker
```

**Porta e adaptador desde já.** A `SearchClient` é interface por dois motivos concretos: a
E1.15 substitui a implementação sem subir servidor HTTP, e a E4.1 troca REST por RabbitMQ
sem tocar em quem consome.

**Contrato de erro assimétrico — de propósito.** É a tradução da [D-028](DECISOES.md) para o
Java, e está documentada na própria interface para ninguém "consertar" achando inconsistente:

| Camada | Worker fora do ar |
|---|---|
| 1 — varredura | **lança** `WorkerUnavailableException`: sem preço não há varredura |
| 2 — confirmação | **degrada**: alerta sem detalhe de voo vale mais que alerta nenhum |

**🐛 BUG-004 — HTTP/2 contra um servidor HTTP/1.1.** Onze dos quinze testes falhavam com
erros de I/O aparentemente aleatórios (`EOF reached`, `RST_STREAM`, `null`). O padrão
inconsistente sugeria problema de teste; o stack trace entregou a verdade —
`jdk.internal.net.http.Http2Connection`.

O `HttpClient` da JDK **negocia HTTP/2 por padrão**, mas o uvicorn só fala HTTP/1.1. Não era
defeito de teste: o mesmo cliente vai a produção contra o mesmo uvicorn, e o sintoma seria
intermitente — funcionaria no teste manual e falharia sob carga. Resolvido declarando
`HttpClient.Version.HTTP_1_1` explicitamente.

**Dois `RestClient`, um por camada.** O timeout de leitura é definido na fábrica de
requisições, não por chamada. Um valor único seria curto demais para a camada 2 (Google ao
vivo, 60s) ou longo demais para detectar a camada 1 travada (API cacheada, 30s).

**Tradução snake_case confinada à fronteira.** `@JsonNaming` por classe nos DTOs de cliente.
Mudar a estratégia global do Jackson leria o worker corretamente **e quebraria a nossa
própria API** no mesmo movimento.

**Achado de configuração:** o IDE apontou que `server.error.include-message` está **deprecado
no Boot 4** — resquício da E0.2. Migrado para `spring.web.error.include-message`. Também
adicionado o `spring-boot-configuration-processor`, que dá autocompletar às chaves
`flightmonitor.*`.

**Testes executados — 58 no Java, todos passaram** (15 novos de contrato).

| Grupo | Verificações |
|---|---|
| Desserialização | snake_case do worker vira record Java; horário de voo sem fuso, `expires_at` com fuso |
| Serialização | o pedido sai em snake_case, com `departure_from`, `max_stops`, `candidate_price` |
| Robustez | listas nulas viram vazias; `vazioAposFiltro()` distingue janela sem oferta de fonte morta |
| Camada 1 falhando | HTTP 502, worker fora do ar e timeout **lançam** |
| Camada 2 falhando | HTTP 500, worker fora do ar e timeout **degradam** |
| Três desfechos | `naoExiste()` distinguido de `degraded()` |

**Falha do meu próprio teste, registrada como lição:** os testes de "worker fora do ar"
paravam e reiniciavam o WireMock. Com porta dinâmica, ele volta em **outra porta** e quebra
todos os testes seguintes. Trocado por um cliente apontando para uma porta sem ninguém
escutando — determinístico e sem perturbar o servidor compartilhado.

**🔗 Contrato validado contra o worker REAL.** O `SearchClientTest` usa WireMock com JSON que
*eu* escrevi — se eu tivesse entendido o formato errado, ele passaria mesmo assim. Criei o
`ContratoRealTest`, desligado por padrão, que fala com o worker de verdade:

```
mvn test -Dtest=ContratoRealTest -Dworker.live=true
```

```
varredura: 60 recebidas, 14 mantidas, provider_origin=SAO
melhor oferta: 2026-09-25 por R$ 3375 (DT)
confirmacao: confirmado=true via=fast-flights
voo: Tap Air Portugal GRU->LIS R$ 5438, 0 escalas
aviso: preco real 81% acima do candidato da camada 1
```

As **60 ofertas recebidas** provam de quebra que a divisão de janela em múltiplos meses da
E1.5 funciona: a janela cruzou a virada e ele consultou os dois meses. Este teste é a semente
da etapa E1.16.

**O core enxerga o worker no `/actuator/health`:**

```json
"worker": { "status": "UP", "details": {
  "service": "flight-monitor-worker",
  "providers": { "travelpayouts": true, "fast_flights": true } } }
```

Sem isso, um worker fora do ar apareceria apenas como ausência de alertas — sintoma
indistinguível de "não houve oportunidade".

**Próximo passo:** E1.8 — persistir as ofertas como `price_observation`, ligando o cliente ao
banco.

---

### 2026-08-09 — ✅ E1.8 concluída · O histórico começou a existir

**As peças viraram um fluxo.** Uma varredura real agora percorre monitor → worker → duas
camadas → banco.

**Entregue:**

```
core-java/.../search/
├── PriceSearchService.java   # orquestra varredura, confirmação e persistência
├── SearchOutcome.java        # resumo que a E1.10 vai consumir
├── SearchProperties.java     # quantos candidatos vão à camada 2
├── SearchController.java     # POST /search e GET /observations
└── ObservationResponse.java
```

| Verbo | Rota | Papel |
|---|---|---|
| POST | `/api/monitors/{id}/search` | dispara varredura — o mesmo caminho que o scheduler usará |
| GET | `/api/monitors/{id}/observations` | histórico de preços |

**Nenhuma transação aberta durante chamada HTTP.** A confirmação leva segundos; manter
transação aberta prenderia uma conexão do pool o tempo todo, e com o scheduler varrendo vários
monitores em paralelo (E1.9) o pool se esgotaria e derrubaria **a API inteira**, que não tem
relação com busca. A persistência acontece em blocos curtos via `TransactionTemplate`.

Vale registrar por que não usei `@Transactional` em método privado: o Spring aplica a anotação
por proxy, e chamada interna não passa pelo proxy — seria **silenciosamente ignorada**. Ver
[D-034](DECISOES.md).

**🎯 A execução real provou o desenho de duas camadas:**

```
60 ofertas da fonte → 48 fora da janela → 12 gravadas → 6 abaixo do teto de R$ 4.000
camada 2 confirmou a mais barata:
   cache anunciava  R$ 3.375
   preço real é     R$ 5.714   (69% acima)
R$ 5.714 > teto  →  SEM oportunidade, nenhum alerta
```

**O sistema recusou-se a alertar sobre um falso-positivo, com dado real.** Sem a camada 2,
você teria recebido um alerta de passagem 69% mais barata do que ela é.

**O histórico guarda a verdade, inclusive quando ela desmente o alerta.** A observação de
R$ 5.714 foi gravada ao lado da de R$ 3.375. Descartá-la faria a estatística da Fase 2
acreditar que a rota é mais barata do que é — enviesada para baixo justamente pelos
falsos-positivos. Ver [D-036](DECISOES.md).

**Confirmação limitada ao candidato mais barato.** Cada confirmação leva 1-2s e arrisca
bloqueio por excesso de requisições; uma varredura pode ter dezenas de candidatos. O alerta
trata da melhor oportunidade, então confirmar a mais barata basta.
Configurável em `flightmonitor.search.max-confirmacoes`.

**Estado do banco após duas varreduras reais:**

| source | confirmed | qtd | menor | maior |
|---|---|---|---|---|
| TRAVELPAYOUTS | false | 24 | 3.375 | 6.136 |
| FAST_FLIGHTS | true | 2 | 5.714 | 5.714 |

As 4 execuções ficaram registradas em `search_run` com status, contagem e duração — a camada 1
levou ~1s, a camada 2 ~2s.

**🐛 Regressão por poluição de teste, corrigida.** O `PriceSearchServiceTest` **não** é
transacional (de propósito: exercita commits reais), então deixava dados no banco e quebrou
3 testes do `PersistenciaTest`, que era transacional e assumia banco limpo. Corrigido nos dois
lados: limpeza em `@AfterEach` na origem, e os testes antigos passaram a usar rotas exclusivas
(`CGH→OPO`, `BEL→MAO`) e a filtrar pelos ids que eles mesmos criaram, em vez de depender de
banco vazio.

**Imprecisão de semântica encontrada pelo teste real.** Eu havia documentado
`candidatoIlusorio` como "o voo não existe". A execução mostrou o voo existindo (Air Europa) e
apenas custando acima do teto. A documentação foi corrigida: a flag significa "o candidato
não se sustentou", cobrindo os dois casos — que, para a decisão de alertar, dão no mesmo.

**Testes executados — 72 no Java, todos passaram** (12 novos).

| Grupo | Verificações |
|---|---|
| Persistência | ofertas viram observações com `source`, `confirmed` e `search_run` corretos |
| D-023 | a rota gravada é a do monitor, não a devolvida pela fonte |
| Camada 2 | só o mais barato é confirmado; confirmação é gravada com companhia e duração |
| Degradação | camada 2 fora do ar mantém a oportunidade e sinaliza incerteza; execução vira PARTIAL |
| Falso-positivo | preço real acima do teto é gravado mas não vira oportunidade |
| Execuções | SUCCESS com contagem certa; worker fora do ar vira FAILED sem gravar nada |
| Acúmulo | duas varreduras geram duas observações, não uma sobrescrita |

O `SearchClient` ser interface (E1.7) tornou o dublê trivial: sem WireMock, sem servidor HTTP.

**Próximo passo:** E1.9 — scheduler varrendo os monitores vencidos automaticamente.

---

### 2026-08-09 — ✅ E1.9 concluída · O sistema passou a trabalhar sozinho

**Entregue:**

```
core-java/.../search/
├── SearchScheduler.java       # so agenda (@Scheduled)
├── SearchCycleService.java    # toda a logica do ciclo
├── SchedulerProperties.java
└── CycleResult.java
```

**A decisão central: reivindicar antes de trabalhar.** O ciclo agenda a próxima busca **no
momento em que pega** o monitor, não ao terminar. A ordem inversa pareceria mais natural e tem
dois defeitos graves:

- **falha vira laço apertado** — o monitor continua vencido e é escolhido no ciclo seguinte,
  e no seguinte, martelando uma fonte que já está com problema;
- **queda do processo trava o monitor** — morrendo no meio, ele fica vencido para sempre.

Reivindicando primeiro, o pior caso vira "esta varredura foi perdida, a próxima acontece no
intervalo normal": um atraso, não uma avalanche. Ver [D-037](DECISOES.md).

**🤖 Verificação ao vivo — o sistema agiu sem ninguém mandar:**

```
22:26:53  scheduler de varredura ativo
          [monitor criado pela API, /search NUNCA foi chamado]
22:26:59  ciclo iniciado com 1 monitor(es)
22:27:02  monitor 340: oportunidade a 5714 (confirmada=true)
22:27:02  ciclo concluido: CycleResult[reivindicados=1, sucesso=1, falha=0, oportunidades=1]
```

E o complemento que prova a reivindicação: com ciclo de 15s e monitor de intervalo 5 min,
**quatro ciclos rodaram e apenas um fez trabalho**. Duas execuções no banco (uma por camada),
nenhuma varredura duplicada, monitor reagendado para dali a 4 minutos.

**`SKIP LOCKED` mesmo com uma instância só.** Hoje não muda nada, mas a Fase 4 prevê deploy em
container: duas instâncias sem trava varreriam o mesmo monitor e gerariam **alerta duplicado
no celular do usuário**. Sem `SKIP LOCKED`, a segunda ficaria bloqueada esperando — trocando
duplicidade por lentidão. Custo de adotar agora: uma anotação. Ver [D-038](DECISOES.md).

**`fixedDelay`, não `fixedRate`.** Com `fixedRate` os ciclos se empilhariam, já que uma
varredura leva dezenas de segundos. E o método do scheduler captura `RuntimeException` porque
exceção que escapa de um `@Scheduled` **cancela o agendamento em definitivo** — o motor
pararia em silêncio.

**Retentativa com freio.** Após falha, o monitor volta à fila antes do intervalo normal, mas
nunca antes de 15 minutos: martelar uma fonte com problema só piora.

**Scheduler desligado nos testes**, senão dispararia durante a suíte consumindo cota real. O
truque que faz funcionar: o arquivo de teste é `application.properties` e o principal é
`application.yml` — nomes diferentes, então o Spring carrega os dois e o `.properties` só tem
precedência no que repetir. Um `application.yml` em `src/test/resources` teria **substituído**
o principal e quebrado a configuração de banco.

**Testes executados — 83 no Java, todos passaram** (11 novos), sem nenhum `Thread.sleep`.

| Grupo | Verificações |
|---|---|
| Seleção | varre só ativos e vencidos; ciclo ocioso não chama o worker; lote respeitado |
| Reivindicação | próxima busca agendada ANTES da varredura; dois ciclos não revarrem o mesmo monitor |
| Falhas | falha não gera laço apertado; retentativa antecipada mas com freio; monitor problemático não derruba os outros do lote |
| Oportunidades | contadas corretamente; preço acima do teto não conta |
| Configuração | o bean do scheduler está ausente nos testes |

**Próximo passo:** E1.10 — regra de alerta e anti-spam. O ciclo já detecta oportunidades e
registra em log; falta decidir **quando vale a pena incomodar**.

---

### 2026-08-09 — ✅ E1.10 concluída · A regra que decide quando incomodar

**Entregue:**

```
core-java/.../alert/
├── AlertService.java           # a decisao
├── AlertDecision.java          # decisao + MOTIVO
├── AlertMessageFormatter.java  # a mensagem do WhatsApp
└── AlertProperties.java        # os numeros que definem o produto
```

**⚠️ A evidência mudou uma decisão anterior.** A [D-028](DECISOES.md) dizia que "alerta sem
detalhe de voo vale mais que alerta nenhum". Três de três medições a contradizem:

| Medição | Cache | Real | Divergência |
|---|---|---|---|
| E1.6 | R$ 3.375 | R$ 5.438 | +61% |
| E1.7 | R$ 3.000 | R$ 5.438 | +81% |
| E1.8 | R$ 3.375 | R$ 5.714 | +69% |

Não é ruído ocasional — é o comportamento normal da fonte cacheada. **Decisão do usuário:
sem confirmação, o sistema se cala** e antecipa a próxima varredura para 15 min. A D-028
continua valendo no que importava: a camada 2 não é dependência dura do *sistema* — a
varredura roda, o histórico cresce. O que se suspende é só o alerta. Ver [D-041](DECISOES.md).

**Anti-spam com duas travas, escolhidas pelo usuário:**

| Trava | Regra |
|---|---|
| Queda mínima | mesma combinação de datas exige queda de **5%** |
| Cooldown | **12h** entre alertas do mesmo monitor, mesmo em datas diferentes |

Um detalhe que importa: alertas `FAILED` **não** contam para o anti-spam. Se a entrega falhou,
o usuário nunca viu a mensagem — deixar isso bloquear um novo alerta faria o sistema silenciar
por causa do próprio defeito.

**A decisão carrega o motivo.** `AlertDecision` tem um enum de `Motivo`, não só um booleano.
Sem ele, "não recebi alerta" seria indistinguível de "não houve oportunidade", "o preço não
caiu o suficiente" e "o sistema está quebrado".

**🐛 BUG-005 — os dois caminhos divergiram em silêncio.** O teste ao vivo revelou que a
varredura manual encontrava oportunidade confirmada e **nunca alertava**. Causa: o endpoint
chamava `PriceSearchService` direto, e só o caminho do scheduler foi atualizado ao ligar o
alerta.

Os testes automatizados não pegaram porque cada caminho tinha o seu e ambos passavam — nenhum
comparava os dois entre si. Corrigido tornando `processarMonitor(id)` o **único** ponto de
entrada. Ver [D-044](DECISOES.md).

**Verificação ao vivo:**

```
1a varredura:  alertou=True   motivo=ALERTADO
               detalhe="primeiro alerta deste monitor; 1 alerta(s) criado(s) a 5714.00"

2a varredura:  alertou=False  motivo=DENTRO_DO_COOLDOWN
               detalhe="ultimo alerta ha menos de 12h"
```

E a mensagem gravada, pronta para a E1.11 enviar:

```
✈️ *Oportunidade encontrada*

GRU → LIS
25/09/2026 → 08/10/2026

Air Europa · 1 escala
*R$ 5.714,00*
Seu limite: R$ 9.000,00  (R$ 3.286,00 abaixo)

_Monitor: Lisboa_
```

**Testes executados — 99 no Java, todos passaram** (16 novos).

| Grupo | Verificações |
|---|---|
| Caminho feliz | primeiro alerta; um por destinatário ativo; inativo não recebe |
| Mensagem | rota, datas, preço, limite, economia, escalas e companhia |
| Recusas | sem oportunidade; camada 2 degradada; sem destinatário |
| Anti-spam | cooldown bloqueia; queda insuficiente bloqueia; queda de 10% re-alerta; preço que subiu nunca re-alerta; datas novas passam |
| Nuances | alerta FAILED não bloqueia; cooldown vale entre datas diferentes |
| Rastreio | o alerta aponta para a observação que o originou |

**Erro meu, registrado como lição:** usei **dois `@BeforeEach`** na mesma classe. O JUnit não
garante ordem entre eles — a limpeza rodou depois do preparo e apagou o monitor recém-criado,
quebrando 15 dos 16 testes. Unificados em um método só.

**Próximo passo:** E1.11 — `NotificationService` com adaptador de log, enviando os alertas
`PENDING`.

---

### 2026-08-10 — ✅ E1.11 concluída · O ciclo se fechou

**Entregue:**

```
core-java/.../alert/
├── NotificationChannel.java      # Strategy: o canal e plugavel
├── LogNotificationChannel.java   # entrega no log
├── NotificationService.java      # despacha, retenta, desiste
├── NotificationScheduler.java    # rede de seguranca
├── DeliveryResult.java           # sucesso / transitorio / permanente
└── DispatchResult.java
```

Mais a migration `V3__alert_attempts.sql`, com o contador de tentativas.

**🎯 O sistema completo funcionou sozinho.** Monitor criado pela API, e mais nada:

```
11:35:38  ciclo iniciado com 1 monitor(es)
11:35:43  monitor 747: ALERTA a 5602 - primeiro alerta deste monitor
11:35:43  despacho concluido: entregues=1, falhas=0, retentar=0
11:35:43  ciclo concluido: oportunidades=1, alertados=1
```

Cinco segundos entre o scheduler acordar e a mensagem estar entregue — varrendo duas camadas
de fonte externa no meio do caminho. No banco: `status=SENT`, `attempts=0`,
`provider_message_id` preenchido.

**Falha transitória e permanente levam a caminhos opostos:**

| Tipo | Exemplo | O que acontece |
|---|---|---|
| Transitória | HTTP 500, timeout | `attempts++`, segue `PENDING` até o limite |
| Permanente | número inválido | `FAILED` imediato, **sem gastar tentativa** |

Retentar um número inválido três vezes só adia o diagnóstico; desistir de um HTTP 500 na
primeira tentativa perderia um alerta por soluço de rede. O contador exigiu migration:
retentativa sem limite vira laço infinito.

**🐛 BUG-006 — o mais grave até agora, e quase passou.** Alertas do canal `LOG` ficavam
eternamente em `PENDING`. Causa: o alerta era carregado numa transação que commitava antes do
envio (o envio é fora de transação, [D-034](DECISOES.md)), chegando **desanexado** ao canal —
que lê `getRecipient().getPhoneE164()`, uma associação LAZY. `LazyInitializationException`,
classificada como falha transitória, retentando para sempre.

**Seria crítico em produção:** o canal WhatsApp da E1.12 lê exatamente o mesmo campo. Todos os
alertas falhariam, e o sintoma seria "o sistema não me avisa" com o banco acumulando pendência
em silêncio.

**Por que quase passou:** o teste do canal falso passava — o dublê só lia `getId()`, um campo
já carregado. Só o teste do canal **real** expôs. Fica a lição: dublê que não exercita os
mesmos acessos do objeto real dá falsa segurança.

**⚠️ Um limite assumido, não escondido.** O `SKIP LOCKED` da reivindicação não basta sozinho:
a trava é liberada no commit e o envio acontece depois. Adicionei um `ReentrantLock` que
impede despacho duplo **nesta instância** — mas ele não cobre duas instâncias. Para isso seria
preciso um estado `SENDING` persistido com recuperação de travados. Fica para a Fase 4, quando
multi-instância for real; resolver agora seria complexidade especulativa. Ver
[D-047](DECISOES.md).

**Testes executados — 111 no Java, todos passaram** (12 novos).

| Grupo | Verificações |
|---|---|
| Entrega | pendente vira SENT com `provider_message_id`; canal de log entrega sem credencial |
| Idempotência | alerta já enviado não é reenviado; sem pendentes não chama o canal |
| Falha permanente | vai direto a FAILED sem gastar tentativa |
| Falha transitória | mantém PENDING, conta tentativa, desiste no limite |
| Recuperação | retentativa bem-sucedida apaga o erro anterior |
| Robustez | canal que lança exceção não derruba o despacho |
| Lote | vários entregues juntos; falha isolada não bloqueia os outros |

**Um teste antigo precisou mudar:** o `alertaRastreiaAObservacao` da E1.10 esperava canal
`WHATSAPP` fixo. Agora o alerta grava o canal **configurado** — trocar o canal depois não pode
reescrever como um alerta antigo foi entregue.

**Próximo passo:** E1.12 — adaptador WhatsApp Cloud API. O `NotificationChannel` já existe;
é implementar a interface e trocar uma linha de configuração.

---

### 2026-08-10 — 🟡 E1.12 · Adaptador WhatsApp pronto, aguardando a Meta

**Situação:** o código está completo e testado. Falta apenas a Meta liberar o número de teste.

**O bloqueio não é nosso.** Ao tentar reivindicar o número de teste no painel, a Meta
respondeu:

```
⛔ Tente daqui a algum tempo ou entre em contato com o suporte para resolver o problema.
   Nenhum número de telefone disponível para este app.
```

Decisão: implementar contra uma Graph API falsa em vez de esperar. Quando o número liberar,
são duas linhas no `.env` e `NOTIFICATION_CHANNEL=WHATSAPP` — nenhum código novo.

**Uma armadilha do painel, evitada.** A trilha *"Etapa 2. Configuração de produção"* pede
registrar número próprio e **cadastrar informações de pagamento** — exatamente o que a
[D-011](DECISOES.md) descartou. O caminho certo é *"Etapa 1. Experimente"*. Registrado no
[GUIA-WHATSAPP.md](GUIA-WHATSAPP.md) para não cairmos nisso de novo.

**⚠️ Confirmado: o alerta é template, não texto livre.** A Meta só aceita template aprovado em
mensagem iniciada pela empresa; texto livre vale apenas dentro da janela de 24h depois de o
destinatário escrever — e um monitor que avisa de madrugada nunca está nessa janela.

Consequência no código: o `AlertMessageFormatter` passou a produzir **dois formatos** da mesma
oferta — texto corrido para o canal `LOG`, e seis parâmetros para o WhatsApp. Os parâmetros
respeitam as regras da Meta: nenhum vazio, sem quebra de linha, sem espaços repetidos. Violar
qualquer uma faz a API recusar a mensagem **inteira**. Ver [D-049](DECISOES.md).

**Erros classificados por código, não por status HTTP.** O status engana: a Meta devolve
**HTTP 400** tanto para "número não verificado" (retentar nunca vai funcionar) quanto para
casos que passam.

| Código | Situação | Classificação |
|---|---|---|
| 131030 | número não verificado | permanente |
| 190 | token inválido ou expirado | permanente |
| 132001 | template não existe | permanente |
| 131047 | texto livre fora da janela | permanente |
| 130429 | limite de requisições | **transitória** |
| 131000 | erro interno da Meta | **transitória** |

Cada erro permanente carrega explicação em português — quem lê o `error_message` no banco
descobre o que fazer sem procurar código na documentação da Meta.

**Testes executados — 129 no Java, todos passaram** (18 novos), sem tocar a rede.

| Grupo | Verificações |
|---|---|
| Envio | devolve o `wamid`; manda `type: template`, não texto livre |
| Formato | telefone sem `+`; token no `Authorization`; 6 parâmetros na ordem |
| Erros permanentes | número não verificado, token expirado, template inexistente, fora da janela, 4xx desconhecido |
| Erros transitórios | limite de taxa, erro interno, 5xx sem corpo, timeout |
| Honestidade | HTTP 200 sem `wamid` **não** vira sucesso inventado |
| Configuração | sem credenciais recusa apontando o guia; alerta sem monitor recusa |

**Verificação de configuração ao vivo:** subindo com `canal=WHATSAPP` e sem credenciais, a
aplicação sobe normalmente e avisa o que falta, em vez de quebrar de forma obscura:

```
WARN  WhatsApp sem credenciais: defina WHATSAPP_PHONE_NUMBER_ID e WHATSAPP_ACCESS_TOKEN
INFO  canais de notificacao disponiveis: [WHATSAPP, LOG]; ativo: WHATSAPP
```

**O que falta para fechar a etapa:**

1. Meta liberar o número de teste (fora do nosso controle)
2. Criar e aprovar o template `alerta_passagem` — passo 7 do guia
3. Preencher o `.env` e trocar `NOTIFICATION_CHANNEL=WHATSAPP`
4. Um envio real chegando no celular

**Próximo passo:** E1.13 — painel Vue de monitores, enquanto a Meta não libera.

---

### 2026-08-11 — ✅ E1.13 concluída · Painel de monitores

**Entregue:**

```
frontend-vue/src/
├── router/index.ts          # rotas
├── views/
│   ├── MonitoresView.vue    # lista, ações e resultado de varredura
│   ├── HistoricoView.vue    # placeholder honesto até a E1.14
│   └── StatusView.vue
├── components/MonitorForm.vue
├── api/monitores.ts
└── types/monitor.ts, recipient.ts
```

**`vue-router` adicionado agora, não depois.** A E1.14 traz histórico por monitor, que é
naturalmente uma segunda rota. Adicionar depois significaria reestruturar o `App.vue`.

**A rota de histórico já existe, apontando para um placeholder que diz a verdade** — "esta
tela chega na E1.14, mas os dados já estão sendo gravados". Melhor que link quebrado ou 404.

**Os erros por campo da API viraram erros por campo na tela.** O `ApiExceptionHandler` da E1.3
devolve `ProblemDetail` com o mapa `errors`; o cliente HTTP passou a extrair esse mapa, e o
formulário marca o campo exato. Aquele trabalho na E1.3 de apontar *qual* campo está errado
só teve valor agora — sem ele, a tela mostraria "deu erro" e o usuário teria que adivinhar.

**Volta e permanência são mutuamente exclusivas na interface.** O banco aceita as duas
preenchidas, mas na prática uma exclui a outra: ou "volto entre tais datas", ou "fico de 10 a
15 dias". Marcar uma desmarca a outra, evitando um estado que confundiria sem ser inválido.

**"Buscar agora" na tela.** Dispara `POST /monitors/{id}/search` e mostra o resultado inline:
observações gravadas, candidatos abaixo do teto, melhor preço, **a decisão de alerta com o
motivo**, e os avisos da varredura. É a etapa E1.10 ficando visível — dá para ver "sem alerta:
DENTRO_DO_COOLDOWN" em vez de só não receber nada.

**Monitor sem destinatário é sinalizado em vermelho** — "não vai notificar ninguém". É um
estado válido e silencioso, exatamente o tipo de coisa que passa despercebida até o dia em que
a passagem barata aparece.

**Testes executados:**

| Teste | Resultado |
|---|---|
| `npm run build` (vue-tsc + vite) | ✅ 0 erros de tipo |
| Rotas `/`, `/monitores`, `/status`, `/monitores/1` | ✅ HTTP 200 |
| `GET /api/monitors` pelo proxy do Vite | ✅ 2 monitores, com destinatários |
| Monitor ativo e pausado | ✅ renderizados distintamente |

**Próximo passo:** E1.14 — gráfico e tabela do histórico de preços por monitor.

---

### 2026-08-11 — ✅ E1.14 concluída · Histórico de preços

**Entregue:** `HistoricoView.vue` com indicadores, gráfico e tabela, mais
`GraficoPrecos.vue` (SVG inline, sem biblioteca de chart).

**A forma foi escolhida antes da cor.** A pergunta que o usuário faz olhando esta tela é
"qual data está mais barata e cabe no meu orçamento" — isso é comparação de magnitude com um
limiar, não série temporal. Então: **barras de menor preço por data de partida**, com o teto
como linha de referência.

**Gráfico de ênfase, não categórico.** Uma cor para o que cabe no teto, cinza para o resto.
Categórico seria errado aqui: as datas não são identidades a distinguir, e colorir cada uma
enterraria justamente o dado que importa.

**Paleta validada por script, não no olho:**

| Modo | Série | De-ênfase | CVD ΔE | Contraste |
|---|---|---|---|---|
| claro | `#2a78d6` | `#93928a` | 16.8 | ambos ≥ 3:1 |
| escuro | `#3987e5` | `#7a7973` | 17.5 | ambos ≥ 3:1 |

A primeira tentativa de cinza (`#c2c1ba`) passava na separação mas ficava em 1.81:1 contra a
superfície — visualmente fraco demais. Escurecido até passar.

**Detalhes de marca que não são enfeite:**
- barras com no máximo 24px e 2px de respiro entre vizinhas — a separação é feita pelo vazio,
  não por contorno
- topo arredondado em 4px, base quadrada na linha zero
- grade de 1px sólida e recessiva; **nunca tracejada**
- **rótulo direto só no extremo** — o mais barato. Número em cima de toda barra vira ruído e
  ninguém lê
- eixo de datas rotulado a cada N, calculado pela quantidade de barras, para os textos não
  colidirem

**Tooltip por barra** com data, preço, quantidade de observações e se está confirmado.

**A tabela não é redundante.** Ela é o *relief* exigido quando alguma cor fica abaixo de 3:1,
e é onde o histórico completo vive: a mesma data aparece várias vezes conforme o preço muda —
que é exatamente a matéria-prima da Fase 2. O gráfico resume; a tabela guarda.

**Indicadores no topo** (menor preço, médio, total, confirmadas) porque número de manchete se
lê direto, sem precisar interpretar gráfico.

**Verificação com dado real:** 14 observações → 7 datas distintas → 7 barras, 6 dentro do teto
de R$ 6.000 e 1 acima, faixa de R$ 3.375 a R$ 6.136. As duas cores aparecem, a linha do teto
corta o gráfico, e o rótulo do mais barato tem onde caber.

**Próximo passo:** E1.15 — E2E do motor com WireMock.

---

### 2026-08-12 — ✅ BUG-007 fechado · Entrega no WhatsApp funcionando

**A mensagem chegou.** Com o número brasileiro `<NUMERO_REMETENTE>` no lugar do número de teste
americano, a entrega funcionou de primeira.

O experimento foi controlado: mesma conta, mesmo token permanente, mesmo destinatário, mesmo
formato de mensagem. **Só mudou o país do remetente.** Isso confirma o diagnóstico do
[BUG-007](BUGS.md) sem margem para dúvida.

**Percalços do caminho, registrados no guia:**

| Percalço | Desfecho |
|---|---|
| Código de verificação não chegava por SMS | A linha tinha sido ativada minutos antes; resolveu ao terminar de provisionar |
| "Não me lembro de cadastrar PIN" | Não havia PIN a lembrar — o painel registrou na Cloud API sozinho |
| `hello_world` recusado no número novo | Templates de exemplo só valem em número de teste público |

**O que ainda falta para a E1.12 fechar de verdade:** o alerta automático sai como **template**,
e o `alerta_preco_voo` continua `PENDING` há mais de um dia. O teste de hoje usou texto livre
dentro da janela de 24h — prova o canal, mas não o fluxo automático.

Por isso o `NOTIFICATION_CHANNEL` **segue em `LOG`**: trocar para `WHATSAPP` agora faria os
alertas falharem com `132001 template não existe`, e o anti-spam registraria tentativas
inúteis.

**A D-053 ganhou uma comprovação a mais:** o `wamid` desta mensagem entregue é indistinguível
dos `wamid` das quatro que falharam. Reforça por que a E1.17 (webhook de status) precisa
existir.

---

### 2026-08-12 — ✅ E1.15 concluída · O produto inteiro em um teste

**O que passou a existir:** `MotorE2ETest`, 10 testes que percorrem o caminho completo —
**monitor cadastrado → ciclo dispara → camada 1 → camada 2 → observação gravada → regra de
alerta → alerta entregue**. Suíte do core-java: **129 → 139 testes**, todos verdes.

**A peça substituída é uma só: o worker Python, trocado por WireMock.** Spring completo,
PostgreSQL real, transações reais, o `RestSearchClient` de produção falando HTTP de verdade.

**Por que não um dublê de `SearchClient`** (o que o `SearchCycleServiceTest` já faz): um dublê
pularia serialização snake_case, negociação de versão do HTTP e timeout — e foi exatamente aí
que nasceu o [BUG-004](BUGS.md). O JSON dos stubs foi copiado do formato **real** devolvido
pelo worker nas etapas E1.5 e E1.6. Registrado em [D-054](DECISOES.md).

**O canal `LOG` como dublê de entrega não é conveniência.** Ele lê o telefone do destinatário
*fora de transação* — a condição precisa do [BUG-006](BUGS.md), que deixava todo alerta preso
em `PENDING`. Um canal falso que ignora a entidade não pegaria a regressão.

**Trava de segurança:** o teste fixa `flightmonitor.notification.canal=LOG` via
`@DynamicPropertySource`. O `application.yml` importa o `.env` da raiz; no dia em que ele
trouxer `NOTIFICATION_CHANNEL=WHATSAPP`, sem essa trava a suíte mandaria mensagem de verdade,
cobrada, a cada `mvn test`.

**O que cada teste protege:**

| Teste | O que quebraria sem ele |
|---|---|
| `doMonitorAoAlerta` | a frase que resume o produto — alerta `SENT`, com `provider_message_id`, apontando para a observação **confirmada** e para o destinatário certo |
| `oPedidoQueSaiRespeitaOMonitor` | a janela do monitor chegar torta ao worker, ou em camelCase |
| `precoRealAcimaDoTetoNaoAlerta` | alerta falso a partir de preço de cache — o caso medido de R$ 3.375 anunciados contra R$ 5.714 reais |
| `camada2ForaDoArNaoAlerta` | o sistema chutar em vez de ficar em silêncio ([D-041](DECISOES.md)) |
| `camada2ForaDoArAntecipaRetentativa` | perder a oportunidade por indisponibilidade passageira, ou martelar fonte com problema |
| `semDestinatarioNaoGeraAlerta` | varredura inútil quando não há para quem contar — e o histórico deixar de ser formado |
| `cooldownImpedeORealerta` | mensagem repetida do mesmo bom preço |
| `aposCooldownExigeQuedaRelevante` | alertar por 1% de oscilação; e não alertar por queda de 14% |
| `workerForaDoArNaoInventaAlerta` | falha virar silêncio, e monitor entrar em laço apertado |
| `semCandidatoNaoChamaCamada2` | gastar consulta ao vivo ao Google sem candidato nenhum |

**Um detalhe que o teste revelou:** `processarMonitor()` chamado direto — o caminho do endpoint
manual — **não reagenda** um monitor que já está vencido. É correto: quem é dono do calendário
é a reivindicação do ciclo, não o pedido manual. A primeira versão do teste afirmava o
contrário e falhou; virou dois testes, um para cada caminho, com o motivo escrito no código.

**Assertivas escolhidas com cuidado:** o preço do cache (3.480) e o preço confirmado (3.720)
são diferentes de propósito, e o teste exige que a mensagem contenha 3.720 **e não contenha**
3.480. "Alertou" e "alertou com o número certo" são coisas diferentes.

**Estado do template do WhatsApp:** `alerta_preco_voo` segue `PENDING` na Meta desde 10/08 —
mais de dois dias. `NOTIFICATION_CHANNEL` permanece em `LOG`.

**Próximo passo:** E1.16 — E2E entre serviços, com o worker Python real e providers falsos.

---

### 2026-08-12 — 🟢 Template aprovado · e um defeito crítico apareceu junto

**O `alerta_preco_voo` foi aprovado pela Meta** — `APPROVED`, `UTILITY`, `pt_BR`, 5 parâmetros
posicionais. O corpo aprovado bate byte a byte com `docs/template-alerta.json`, e os 5
parâmetros correspondem exatamente ao que `parametrosDoTemplate()` produz.

**Antes de trocar o canal, conferi qual nome a aplicação enviaria de verdade. Estava errado.**

O `application.yml` dizia `template-name: alerta_passagem` — o nome **antigo**, aquele que a
Meta classificou como MARKETING e que foi apagado. O nome certo existia só como *default de
campo vazio* em `WhatsAppProperties`, e o YAML fornecia um valor, então o default nunca entrava.

Nenhum dos 139 testes reclamava: o `WhatsAppChannelTest` monta as propriedades **a mão**, com o
nome certo escrito no próprio teste. Ele validava a intenção do teste, não a configuração da
aplicação. Registrado como [BUG-008](BUGS.md).

Ter trocado `NOTIFICATION_CHANNEL` para `WHATSAPP` hoje faria **todo** alerta receber
`132001 — template não encontrado`. Como é falha permanente ([D-050](DECISOES.md)), não haveria
nem retentativa: `FAILED` de primeira, para todos, em silêncio.

**Correção em duas partes:**
1. `application.yml` → `${WHATSAPP_TEMPLATE_NAME:alerta_preco_voo}`, com o motivo ao lado;
2. `TemplateDoWhatsAppTest` amarra a configuração **carregada pelo Spring** ao template
   versionado: mesmo nome, mesmo idioma, mesma quantidade de parâmetros numerados sem buraco, e
   nenhum parâmetro violando as regras da Meta (vazio, quebra de linha, espaço não-quebrável).

**O teste foi verificado ao contrário** — forçado a `alerta_passagem`, ele falha com
`expected: "alerta_preco_voo" but was: "alerta_passagem"`. Guarda que não sabe falhar não é
guarda.

Suíte do core-java: **139 → 142 testes**, todos verdes.

**O que falta para a E1.12 fechar:** trocar `NOTIFICATION_CHANNEL` para `WHATSAPP` e ver um
alerta **automático** — gerado pelo ciclo, entregue como template — chegar no celular. É a
única coisa que ainda não foi provada de ponta a ponta; o teste de ontem usou texto livre
dentro da janela de 24h, que prova o canal e não o fluxo. Como isso envia mensagem real e
cobrada, fica para quando você mandar.

**Próximo passo:** E1.16 — E2E entre serviços.

---

### 2026-08-12 — 🔴 Primeiro alerta automático real · falhou, e o motivo é externo

**Autorizado pelo Leonardo, liguei o canal WhatsApp e disparei um alerta de verdade.** O motor
fez tudo certo:

```
POST /api/monitors/1149/search
  observações gravadas: 3 · candidatos abaixo do teto: 2
  melhor preço: R$ 5.123,00 · confirmada: true · camada 2 degradada: false
  alerta: ALERTADO — "primeiro alerta deste monitor; 1 alerta(s) criado(s) a 5123.00"
```

Camada 1 varreu, camada 2 confirmou ao vivo, a regra decidiu alertar, o alerta foi criado e
despachado. **Todo o motor funcionou.** A entrega é que não:

```
alert 317 · FAILED · WHATSAPP · HTTP 404, código 132001 — template não encontrado
```

**A causa não era o nome do template.** Reproduzido com `curl`, o erro completo dizia o que a
nossa camada resumia demais:

```
"(#132001) Template name does not exist in the translation"
details: "template name (alerta_preco_voo) does not exist in pt_BR"
```

**Template pertence a uma conta (WABA), não ao app.** E consultando
`GET /{WABA}/phone_numbers`, a conta que guarda todos os nossos templates —
*"Test WhatsApp Business Account"* — contém **só o número de teste americano**. O número
brasileiro que usamos como remetente está `CONNECTED`, `account_mode: LIVE`, porém **em outra
conta**. Para ele, `alerta_preco_voo` nunca existiu. Registrado como [BUG-009](BUGS.md).

**Sobre o `hello_world`:** cheguei a atribuir a recusa dele à mesma causa. Verifiquei depois e
não é — ele existe na WABA de produção, `APPROVED`, e mesmo assim a Meta responde
`(#131058) Hello World templates can only be sent from the Public Test Numbers`. O diagnóstico
original valia; são dois bloqueios distintos que apareceram juntos.

**O que nenhum teste poderia ter pego:** o [BUG-008](BUGS.md) foi corrigido horas antes, e o
`TemplateDoWhatsAppTest` confere nome, idioma e quantidade de parâmetros contra o arquivo
versionado. Estava tudo certo. A relação *"este template pertence à conta deste número"* só
existe no servidor da Meta. É o argumento mais concreto que apareceu até agora a favor do
canário ao vivo da E4.5.

**Estado deixado para trás:**
- `NOTIFICATION_CHANNEL` de volta em `LOG` — mantê-lo em `WHATSAPP` mandaria todo alerta para
  `FAILED` de primeira, sem retentativa, por ser falha permanente
- core reiniciado, canal ativo `LOG`, confirmado no log de arranque
- a mensagem do erro 132001 passou a citar a WABA **primeiro**, antes de nome e idioma — a
  mensagem antiga mandava conferir justamente o que estava certo
- o monitor `1149` ("Teste de entrega E1.12", GRU→LIS, teto R$ 9.000) ficou no banco, com as 3
  observações da varredura real. Serve de dado para a Fase 2

**Custo real do teste:** R$ 0. A Meta recusou antes de entregar; não há cobrança por mensagem
rejeitada.

**Falta uma informação que só o painel tem:** o ID da WABA de produção, a que contém o número
brasileiro. Com ele, o template é recriado por API a partir do JSON versionado, e aí é esperar
aprovação de novo.

---

### 2026-08-12 — ✅ BUG-009 diagnosticado e destravado · template recriado na conta certa

**O ID que faltava estava na URL do painel.** No Gerenciador do WhatsApp, com o número
brasileiro selecionado, o endereço traz `asset_id=<WABA_PRODUCAO>` — é a WABA de produção.
Confirmado por `GET /{WABA}/phone_numbers`: ela contém o `<NUMERO_REMETENTE>` e mais nenhum.

**Template recriado nessa conta**, submetendo o
[template-alerta.json](template-alerta.json) versionado **sem nenhuma edição**:

```
POST /<WABA_PRODUCAO>/message_templates
  → id <TEMPLATE_ID> · PENDING · UTILITY
```

A categoria saiu `UTILITY` de primeira. Da primeira vez a Meta reclassificou para MARKETING por
causa de uma frase promocional que eu tinha escrito no corpo; o texto atual, sem gatilho
promocional, passou direto na categorização.

`WHATSAPP_WABA_ID` atualizado no `.env` para a conta de produção.

**Uma correção minha, do mesmo dia:** escrevi que a recusa do `hello_world` no número novo tinha
a mesma causa da BUG-009. Testei e não tem. O `hello_world` existe na WABA de produção,
`APPROVED` em `en_US`, e a Meta recusa mesmo assim:

```
(#131058) Hello World templates can only be sent from the Public Test Numbers
```

O diagnóstico original valia — template de exemplo é restrito a número de teste por regra
própria. Eram dois bloqueios distintos que apareceram juntos, e eu juntei os dois numa
explicação só. Custo desta verificação: R$ 0, recusada antes de entregar.

**O que falta:** aprovação. Quando sair, `NOTIFICATION_CHANNEL` volta a `WHATSAPP` e o alerta
automático é disparado de novo. **Nada no código muda** — o nome, o idioma e os 5 parâmetros já
são os certos, e o `TemplateDoWhatsAppTest` guarda isso.

---

### 2026-08-12 — ✅ E1.16 concluída · Java e Python conversando sem intermediário

**A lacuna que faltava.** O `MotorE2ETest` da E1.15 cobre o motor inteiro — mas com o worker
substituído por WireMock, e **o JSON daquele WireMock fui eu que escrevi**. Se eu tivesse
entendido o contrato errado, os dois lados passariam: o Java validando a minha suposição, e o
Python validando a mesma suposição pelo outro lado.

Agora os dois processos são reais e falam HTTP de verdade. Só as **fontes externas** são
falsas.

```
python scripts/e2e_servicos.py
  [e2e] subindo worker FALSO na porta 8002
  [e2e] rodando E2EServicosTest contra o worker falso
  Tests run: 11, Failures: 0, Errors: 0
  [e2e] OK — Java e Python fecharam o contrato sem stub entre eles
```

**Cenário escolhido pela rota, não por um modo de teste.** O core-java não pôde ganhar nenhum
parâmetro novo — seria código de produção existindo por causa de teste. Mas ele já manda o
destino, então códigos IATA da faixa `ZZ*` (que a IATA não atribui) selecionam o desfecho do
lado do worker. Do lado do core é apenas outra rota. Ver [D-055](DECISOES.md).

| Destino | O que o worker faz | O que o teste prova |
|---|---|---|
| qualquer | camada 1 devolve 3.480 e 4.900; camada 2 confirma 3.720 | ciclo completo, e o alerta carrega o preço **confirmado**, não o do cache |
| `ZZA` | camada 2 confirma 9.990 | candidato ilusório: o preço real estoura o teto e o alerta não sai |
| `ZZB` | camada 2 levanta `ProviderError` | degradação: silêncio, execução `PARTIAL` |
| `ZZC` | camada 2 devolve `None` | "consultei e não existe" ≠ "não consegui consultar" |
| `ZZD` | camada 1 levanta erro | 502 → busca `FAILED`, nenhum alerta inventado |
| `ZZE` | camada 1 responde com `returned=30, kept=0` | janela vazia ≠ fonte morta |

**O teste mais importante é o `contratoCampoACampo`.** Cada asserção nele corresponde a um jeito
diferente de o contrato quebrar em silêncio: `snake_case`, `Decimal` do Python virando
`BigDecimal`, `departure_at` como horário **local** e não instante (se virasse instante em
algum ponto, o horário andaria), inteiro chegando sem truncar, e a rota gravada sendo a
**pedida** e não a devolvida pela fonte.

**Três defesas contra o pior desfecho possível — o teste rodar contra o worker de verdade:**

1. o script usa a porta **8002**, e aborta se já houver algo escutando lá;
2. o worker registra aviso em log toda vez que monta uma camada falsa;
3. o teste `estaFalandoComOWorkerFalso` afirma os três preços exatos. Preço real de GRU→LIS
   nunca cairia em `3480.00`, `4900.00` e `3720.00`. Se ele falhar, tudo o mais que a classe
   afirma perdeu o sentido.

**`USE_FAKE_PROVIDERS` não entra no `.env`, nem no `.env.example`.** Só o script a define. Um
`true` esquecido faria o sistema inventar preços em silêncio — varreduras "bem-sucedidas",
observações gravadas, alertas enviados, tudo falso e sem um sinal de erro. É a pior categoria
de falha: a que se parece com sucesso. Ver [D-056](DECISOES.md).

**Duas correções que a etapa cobrou de tabela:**

- o `Protocol` `CalendarProvider` declarava um método `scan()` que **ninguém implementava** — o
  Travelpayouts tem `buscar()`. O contrato existia só no papel. Agora declara o método real, e
  o `TravelpayoutsProvider` tem `name`, como o Protocol sempre exigiu;
- o roteador instanciava a fonte da camada 1 diretamente, sem passar pela fábrica. O Strategy
  da camada 1 existia como função que ninguém chamava. Agora passa — e é o que torna a troca
  possível sem o roteador saber que existe teste.

**Placar:** core-java **142 → 153 testes** (11 pulados no build padrão, por dependerem de
processo externo); worker-python **42 → 58**.

**Também registrado:** [RISCO-008](BUGS.md) — a suíte de testes apaga os dados de
desenvolvimento, porque usa o mesmo banco. Apareceu hoje de forma concreta: depois de rodar a
suíte, a lista de monitores voltou vazia. Mitigação prevista para a E4.2.

**Próximo passo:** E1.17 — webhook de status do WhatsApp.

---

### 2026-08-12 — ✅ E1.17 concluída · `SENT` finalmente significa entregue

**Este é o teste que faltava existir no dia do BUG-007.** Aquelas quatro mensagens tinham a
explicação no webhook — código `130497` — e o sistema não tinha onde recebê-lo. Agora tem, e o
`falhaDeEntregaVeioComOMotivoDoBug007` reproduz aquele payload exato.

**O estado ganhou o degrau que faltava:**

| Estado | O que significa |
|---|---|
| `PENDING` | criado, ainda não despachado |
| `ACCEPTED` | a Meta recebeu e devolveu `wamid`. **Não sabemos se chegou** |
| `SENT` | o webhook confirmou a entrega no aparelho |
| `FAILED` | não foi entregue, e `error_message` diz por quê |

**Quem decide entre `ACCEPTED` e `SENT` é o canal**, não o despachante — é o canal que sabe como
a própria entrega se confirma. O `LOG` entrega de forma síncrona e certa; o WhatsApp depende de
aviso posterior. Um método `confirmacaoAssincrona()` com padrão `false` resolve, e mantém a
[D-053](DECISOES.md) sem transformar todo canal em refém de webhook. Ver [D-057](DECISOES.md).

**Consequência que vale dizer em voz alta:** com o webhook desligado, alertas do WhatsApp param
em `ACCEPTED` e não avançam. É desconfortável de propósito — é exatamente o que sabemos.

**Segurança levada a sério, porque este endpoint é público.** A Meta precisa alcançá-lo, então
ele fica exposto. Sem conferir a assinatura, qualquer um que descubra a URL marcaria alertas
como entregues — ou como falhos, apagando justamente o sinal que o webhook existe para capturar.

- HMAC-SHA256 do corpo com o segredo do app, comparado em **tempo constante**
  (`MessageDigest.isEqual`, não `Arrays.equals` — a diferença de tempo permitiria descobrir a
  assinatura byte a byte)
- o corpo entra como `String` crua: deixar o Spring desserializar e re-serializar produziria
  outro texto, e a assinatura nunca bateria
- sem segredo configurado, aceita **com aviso em log a cada requisição**. O incômodo é
  proposital

**Responder 200 até para o que não entendemos** ([D-058](DECISOES.md)): corpo ilegível, `wamid`
desconhecido e exceção nossa devolvem `200`. A Meta desativa a assinatura depois de falhas
repetidas, e ficar sem webhook custa mais do que perder um lote. Assinatura inválida é o único
`401` — ali não há assinatura da Meta a preservar.

**Três coisas que o formato da Meta impõe, e que os testes fixam:**

1. **ordem não é garantida** — `read` pode chegar antes de `delivered`, e como leitura implica
   entrega, um `read` isolado também confirma;
2. **repetição é esperada** — o horário registrado é o da **primeira** confirmação, não o da
   repetição;
3. **timestamp em segundos, como texto** — interpretar como milissegundos jogaria a data para
   1970, e o novo `CHECK` de coerência recusaria a linha.

**A migração V4 trouxe um `CHECK` com tolerância de um minuto**, e a tolerância não é preguiça:
o webhook manda segundos inteiros e o nosso `sent_at` tem microssegundos. Uma mensagem aceita às
`10:00:00.500` e entregue no mesmo segundo chega como `10:00:00` — anterior ao envio. Comparação
exata rejeitaria entregas legítimas por causa de meio segundo.

**O índice único cobrou um dublê:** o canal falso do `NotificationServiceTest` devolvia sempre
`"falso:1"`, e o índice recusou o segundo alerta do lote. O índice está certo — dois alertas com
o mesmo `provider_message_id` tornariam o webhook ambíguo, e a confirmação de um marcaria o
outro. O dublê é que produzia algo que nenhum provedor real produz.

**Também mapeados:** os códigos que **só aparecem no webhook**, invisíveis para quem olha a
resposta do envio — `130497` (restrição de país, o BUG-007), `131026` (destinatário
inalcançável) e `131049` (Meta optou por não entregar).

**Passo a passo de como ligar:** [GUIA-WEBHOOK.md](GUIA-WEBHOOK.md), incluindo túnel para
desenvolvimento e o que fazer quando a verificação falha.

**Placar:** core-java **153 → 174 testes**, todos verdes.

**Próximo passo:** pausa. Na volta, fechar a E1.12 — o template `alerta_preco_voo` está
`PENDING` na WABA de produção desde hoje, e é o último bloqueio da Fase 1.

---

### 2026-08-12 — ✅ E2.1 concluída · O que é "normal" para uma rota

Primeira etapa da Fase 2. O sistema já sabia se um preço cabia no teto; agora sabe se ele é
**bom** — ou melhor, tem os números para responder isso na E2.2.

**Entregue:** `GET /api/stats/routes/{origem}/{destino}` e `/api/stats/monitors/{id}`, com corte
mensal em `/months`. Mínimo, quartis, mediana, média, máximo, desvio padrão e coeficiente de
variação, numa única passada de SQL.

**Mudei o roteiro, e vale explicar.** O plano dizia "estatísticas de rota **no worker**".
Ficaram no Java. O próprio plano já dizia isso em outro lugar — duas das quatro regras
invioláveis apontam para cá:

> **Regra 1.** O Java é o dono do banco. O Python nunca acessa o PostgreSQL diretamente.
> **Regra 2.** O worker é um especialista burro; **não decide se um preço é bom**.

Para o worker calcular a mediana, ou ele acessaria o banco — violando a regra 1 — ou o Java
despejaria milhares de linhas por HTTP a cada consulta. E definir o que é "normal" é o primeiro
passo de decidir se um preço é bom, que a regra 2 tira do worker. Registrado em
[D-059](DECISOES.md). O Python continua dono da coleta; se a fase chegar a regressão ou
sazonalidade, aquilo vai para lá.

**Por que não só a média.** Preço de passagem tem cauda longa: uns poucos valores altíssimos
puxam a média e fazem um preço medíocre parecer bom. Um dos testes fixa isso com números
redondos — quatro preços entre 3.000 e 3.300 mais um de 20.000:

| | valor |
|---|---|
| média | R$ 6.520 — faria R$ 4.000 parecer barato |
| **mediana** | **R$ 3.200** — não se move |

**A armadilha que quase ninguém veria.** As duas camadas produzem números que **não são
comparáveis**: medimos o cache subestimando o preço real em 61%, 69% e 81%. Misturá-los cria um
"normal" que não existe — e como o cache puxa a média para baixo, um preço real legítimo passa
a parecer *acima* da média. Uma detecção de anomalia alimentada pela mistura ficaria **calada
exatamente quando deveria falar**.

Por isso toda estatística declara a fonte: `TODAS` ou `CONFIRMADAS`, e a etiqueta vai na
resposta, não só no pedido. Ver [D-060](DECISOES.md).

**Poucas amostras não somem, ficam marcadas** ([D-061](DECISOES.md)). Abaixo de 8 observações o
resultado sai com `confiavel: false`. Esconder faria o painel dizer "sem dados" havendo dados;
devolver sem ressalva faria a E2.2 disparar sobre três pontos. E com uma amostra só, o desvio
padrão é **nulo**, não zero: zero afirmaria "rota estável", que é uma conclusão, não um dado.

**Mês de partida, não de observação.** O corte mensal responde "quando sai mais barato viajar".
Agrupar pelo instante da observação produziria um gráfico sobre o nosso próprio horário de
varredura, que não interessa a ninguém.

**Placar:** core-java **174 → 200 testes**, todos verdes.

**Próximo passo:** E2.2 — detecção de anomalia, que consome exatamente estes números.

---

### 2026-08-12 — ✅ E2.2 concluída · O sistema passou a saber o que é preço bom

A E2.1 respondeu "o que é normal nesta rota". Esta responde a pergunta que interessa: **"este
preço aqui é bom?"** — com uma frase pronta para a mensagem da E2.4.

**Cinco graus, excludentes, do mais forte para o mais fraco:**

| Grau | Quando |
|---|---|
| `RECORDE` | menor preço da janela |
| `EXCELENTE` | abaixo do limite estatístico de valor atípico |
| `BOM` | entre os 25% mais baratos já observados |
| `NORMAL` | dentro da faixa comum |
| `SEM_DADOS` | não há histórico suficiente para dizer |

**Regra de Tukey, e não z-score** ([D-062](DECISOES.md)). A escolha óbvia seria "quantos desvios
padrão abaixo da média", e seria errada: z-score pressupõe distribuição simétrica, e preço de
passagem tem cauda longa para cima. Essa cauda infla o desvio padrão, e o detector passa a
exigir uma queda enorme para reagir — **justamente na rota onde uma passagem cara distorceu a
amostra**.

O limite de Tukey — `p25 - 1,5 × (p75 - p25)` — nasce dos quartis, então nada disso o desloca.
Um teste fixa exatamente isso: mesma rota, máximo saltando de R$ 6.000 para R$ 40.000, e o
limite continua idêntico.

**E ele se adapta à rota, de graça.** Outro teste: o mesmo preço de R$ 3.050 é *atípico* numa
rota que quase não varia e *normal* numa que varia muito. Um limiar percentual fixo — "15%
abaixo da média" — não conseguiria distinguir os dois casos.

**"Não sei" nunca vira "normal"** ([D-063](DECISOES.md)). `NORMAL` afirma "eu medi e não tem
nada de especial"; `SEM_DADOS` diz "eu não medi". Colapsar os dois faria a E2.4 escrever "preço
dentro do normal" sobre uma rota nunca medida.

**A explicação é escrita para gente, não para estatístico.** Um teste proíbe as palavras
*quartil*, *desvio*, *percentil* e *Tukey* no texto gerado. O que sai é:

> `17.1% abaixo da mediana de R$ 3.500, entre os 25% mais baratos nos ultimos 90 dias`

E outro teste garante que nenhum valor monetário carregue o espaço não-quebrável do `pt-BR` —
ele viajaria até o parâmetro do template do WhatsApp, onde a Meta recusa.

**Uma limitação que os testes cobraram e que ficou documentada:** quando o limite de Tukey cai
abaixo do mínimo já observado, o grau `EXCELENTE` fica inalcançável — todo preço abaixo do
limite também é recorde. Descobri isso porque quatro testes falharam de primeira, e a resposta
certa não era mexer no código: uma rota cujo menor preço já visto está dentro da faixa comum
nunca teve um extremo, então o próximo preço muito baixo é corretamente **recorde**, e não
"parecido com os extremos de antes".

**O veredito carrega a referência que o produziu.** Sem isso, um alerta estranho no histórico
seria impossível de depurar — não daria para saber contra o que ele foi comparado.

**Achado de tabela: [BUG-010](BUGS.md).** Um teste que não tinha nada a ver com esta etapa
começou a falhar. `search_run` guardava `started_at` vindo do relógio do **container** (via
`DEFAULT clock_timestamp()`) e `finished_at` vindo do relógio do **host** (`Instant.now()`). Uma
execução rápida terminava 278 µs *antes* de começar, violando o `CHECK`. Em produção isso
abortaria a varredura de forma intermitente. Corrigido alinhando os dois ao mesmo relógio — e
**não** afrouxando o `CHECK`, que foi justamente quem encontrou o defeito.

**Placar:** core-java **200 → 221 testes**, todos verdes.

**Próximo passo:** E2.3 — Flight Score, que combina preço, escalas, duração e horário numa nota.

---

### 2026-08-12 — ✅ E2.3 concluída · Nota de 0 a 100, e o que ela não esconde

Primeira etapa em que o sistema emite juízo sobre o **voo**, e não só sobre o preço. Quatro
aspectos, pesos configuráveis, e a nota sempre acompanhada da decomposição.

| Aspecto | Peso | Como é pontuado |
|---|---|---|
| Preço | 50 | posição nos quantis da própria rota |
| Escalas | 20 | degraus fixos; a maior queda é do direto para a primeira escala |
| Duração | 20 | comparada à **melhor já vista naquela rota** |
| Horário | 10 | faixa da partida — manhã, tarde, noite, madrugada |

**A regra que governa tudo aqui: nulo não é zero** ([D-064](DECISOES.md)). Observações da camada
1 não trazem duração nem horário. A saída preguiçosa seria pontuar zero nesses aspectos, e ela é
ativamente nociva: toda oferta ainda não confirmada apareceria com nota baixa, e o sistema
passaria a **preferir voos por terem mais dados, não por serem melhores**. Viés de medição
virando recomendação.

Aqui o aspecto sem dado sai com nota nula, o peso dele sai da conta, e a nota é renormalizada
sobre o que sobrou. Um teste fixa a consequência: a mesma oferta pontua 100 com quatro aspectos
e 100 com dois — **e a `cobertura` diz que não são a mesma coisa**, 1,00 contra 0,70.

**Toda referência é da própria rota** ([D-065](DECISOES.md)). R$ 3.000 é caro para São
Paulo–Rio e barato para São Paulo–Tóquio; dez horas é ótimo para Lisboa e absurdo para Curitiba.
Um teste mostra as duas pontas: **a mesma duração de dez horas** vale 100 numa rota cujo melhor
é dez horas, e zero numa cujo melhor é uma hora.

**O preço é interpolado entre quantis, não entre mínimo e máximo.** Um único preço absurdo no
histórico comprimiria a escala inteira e faria quase tudo parecer bom — a mesma armadilha que
levou à regra de Tukey na E2.2. Teste com máximo saltando para R$ 90.000: a nota não se move.

**Os pesos são configuração, e a nota vem decomposta** ([D-066](DECISOES.md)). Não existe peso
certo — quanto uma escala vale em relação a duzentos reais é preferência pessoal, e quem escreve
o código não tem autoridade sobre a viagem de quem usa. E uma nota única é inauditável: quando
ela sai 62, ninguém sabe se o problema foi o preço, a escala ou o voo de madrugada.

O preço pesar mais que os outros três somados também não é acidente: o sistema existe para achar
passagem barata, e uma nota que premiasse conforto acima de preço contradiria o produto.

**A explicação nomeia os dois extremos**, e não critica quem não merece:

> `excelente; a favor: menor preco ja visto na rota; contra: 2 escala(s)`

Um voo bom em tudo não recebe crítica inventada — apontar defeito onde não há é ruído.

**Endpoint:** `GET /api/stats/observations/{id}/score`. Pontua uma observação **existente**, e
não um voo hipotético enviado no corpo: a nota nasce de comparação com o histórico, então só faz
sentido para algo que o sistema realmente viu.

**Placar:** core-java **221 → 246 testes**, todos verdes.

**Próximo passo:** E2.4 — alerta enriquecido, que junta a anomalia da E2.2 e o score da E2.3 na
mensagem que chega no WhatsApp.

---

### 2026-08-12 — ✅ E2.4 concluída · A inteligência chegou na mensagem

As três etapas anteriores produziam números que ninguém via. Agora eles chegam onde importa:

```
✈️ *Oportunidade encontrada*

GRU → LIS
10/03/2027 → 22/03/2027

Iberia · 1 escala
*R$ 2.900,00*
Seu limite: R$ 4.000,00  (R$ 1.100,00 abaixo)

📉 17.1% abaixo da mediana de R$ 3.500, entre os 25% mais baratos nos ultimos 90 dias
⭐ Nota do voo: 82/100 — parte de manha

_Monitor: Lisboa_
```

**A regra da etapa: na dúvida, silêncio** ([D-067](DECISOES.md)). Cada informação só entra se
se sustentar sozinha. Um monitor recém-criado recebe **exatamente** a mensagem de antes desta
etapa — e há um teste que compara as duas cadeias caractere a caractere para garantir isso.

Seria uma pena as três etapas anteriores terem sido construídas para admitir ignorância — a E2.1
marcando amostra pequena, a E2.2 devolvendo `SEM_DADOS` em vez de `NORMAL`, a E2.3
renormalizando o que não mede — e jogar tudo fora no último passo, escrevendo "nota preliminar
68" no WhatsApp de alguém. Escrever "não temos histórico suficiente" também está fora: o usuário
quer saber da passagem, não do estado do nosso banco.

**O filtro mora num lugar só**, em `AlertInsights.de(...)`. Um campo que não se sustenta
simplesmente não existe no objeto, então nenhum formatador precisa lembrar de checar.

**O alerta grava o que sabia** ([D-068](DECISOES.md), migração V5). Duas razões, e a segunda
pesa mais: a entrega acontece fora de transação com a entidade desanexada (lição do BUG-006),
**e** o alerta deve registrar o que sabia *quando decidiu*. Um número recalculado meses depois
divergiria do que motivou o alerta, e o histórico passaria a mentir sobre o próprio passado.

**O template continua com cinco parâmetros** ([D-069](DECISOES.md)). Mudar a estrutura exigiria
template novo e nova aprovação da Meta — que já custou duas rodadas neste projeto. O
enriquecimento entra **dentro** dos parâmetros existentes:

```
Detalhes do voo:   Iberia, 1 escala, nota 82/100
Preço encontrado:  R$ 2.900,00 (17.1% abaixo da mediana)
```

**Um detalhe que um teste fixou:** o texto livre **mantém** o espaço não-quebrável do `pt-BR`
entre `R$` e o valor — é o que impede a moeda de ficar órfã numa quebra de linha. Nos parâmetros
do template ele precisa sair, porque a Meta recusa. O primeiro teste falhou justamente por eu
ter escrito espaço comum na assertiva; a resposta certa não era mexer no código, e sim
documentar a diferença.

**Percalço de schema:** declarei a coluna `flight_score` como `smallint` e o campo Java como
`Integer`, e o Hibernate recusou o contexto inteiro — 200 erros de uma vez. Como a V5 já tinha
sido aplicada, corrigi pelo lado Java (`Short`), que é como o projeto já modela o número de
escalas. Mexer numa migração aplicada quebraria o checksum do Flyway.

**Placar:** core-java **246 → 261 testes**, todos verdes.

**Próximo passo:** E2.5 — tendência de preço: "subindo, caindo ou estável" nos últimos N dias.

---

### 2026-08-12 — ✅ E2.5 concluída · A primeira análise que olha o tempo

As etapas E2.1 a E2.3 perguntam *"onde este preço está em relação aos outros"*. Esta pergunta
outra coisa: **"para onde os preços vêm andando"** — e é a que responde à dúvida real de quem
viaja.

**Endpoints:** `GET /api/stats/routes/{o}/{d}/trend` e `/api/stats/monitors/{id}/trend`. A
resposta traz a **série usada**, e não só a conclusão: é dela que sai o gráfico do painel, e sem
ela um resultado estranho seria impossível de conferir.

**Theil-Sen, e não mínimos quadrados** ([D-070](DECISOES.md)). A regressão clássica minimiza o
**quadrado** dos erros, então um único dia atípico inclina a reta inteira — e numa série de dez
pontos, que é o que este projeto terá por muito tempo, um ponto pesa 10% da conclusão.

Theil-Sen tira a **mediana das inclinações entre todos os pares**, e tolera ~29% de pontos
corrompidos. Dois testes fixam isso:

| Cenário | Resultado |
|---|---|
| Alta clara com uma promoção de R$ 900 no meio | continua `SUBINDO` |
| Queda com **dois** dias de R$ 9.000 | continua `CAINDO` |

É a terceira vez que a mesma escolha aparece: mediana em vez de média na E2.1, Tukey em vez de
z-score na E2.2, Theil-Sen agora. Preço de passagem tem cauda longa, e todo estimador sensível a
extremo erra na mesma direção.

**Porcentagem por semana, não reais por dia** ([D-071](DECISOES.md)). R$ 20/dia é ruído numa
rota de R$ 8.000 e movimento forte numa de R$ 600. Um teste mostra duas rotas em patamares de
R$ 600 e R$ 6.000, caindo proporcionalmente igual, devolvendo **exatamente o mesmo número**. E a
semana é a unidade em que uma pessoa pensa ao decidir se espera mais um pouco.

**Dias, e não observações.** O que sustenta uma tendência são dias distintos com dado — cem
preços coletados no mesmo dia não dizem nada sobre movimento no tempo. A resposta traz os dois
números separados, e é `diasComDados` que governa a confiança.

**Buracos na série são o caso normal**, não exceção: a varredura não roda todo dia. Como
Theil-Sen trabalha com a distância real entre pontos, uma série com saltos de 5, 7 e 8 dias
produz a mesma inclinação de uma diária — há teste com essa aritmética escrita por extenso.

**Não dá conselho de compra** ([D-072](DECISOES.md)). O texto diz *"o preço vem caindo cerca de
7% por semana"*; nunca *"espere"* ou *"compre agora"*, e há um teste que proíbe essas palavras.
Tendência recente é indício, não previsão — e a primeira vez que um conselho errasse feio
custaria a confiança em tudo o mais que o sistema diz.

**Registrado como [RISCO-009](BUGS.md):** a série pode confundir "preço subiu" com "mudou o que
estamos olhando", se a mistura de datas de partida observadas variar. O desenho do sistema
atenua — janela de partida fixa por monitor, varrida igual a cada ciclo —, e é parte da razão de
a janela da tendência ser mais curta (30 dias) que a das estatísticas (90).

**Placar:** core-java **261 → 279 testes**, todos verdes.

**Próximo passo:** E2.6 — preferências do monitor, que fecha a Fase 2.

---

### 2026-08-12 — ✅ E2.6 concluída · A Fase 2 fechou

Até aqui o sistema tratava toda viagem igual. Agora cada monitor tem opinião própria.

**O que entrou:**

| Preferência | O que faz |
|---|---|
| `avoidedAirlines` | a companhia continua no histórico, mas não vira candidata a alerta |
| `prefereVooDireto` | endurece a curva de escalas na nota — de 65 para 30 numa conexão |
| `pesoPreco/Escalas/Duracao/Horario` | sobrescrevem os pesos globais do Flight Score, campo a campo |

**Dois dos quatro itens do roteiro ficaram fora, e por motivos diferentes**
([D-075](DECISOES.md)):

- **Bagagem — falta o dado, não o código.** Nenhuma das fontes devolve franquia de bagagem.
  Guardar a preferência assim criaria um campo que *parece funcionar e não filtra nada* — a pior
  espécie de funcionalidade, porque o usuário confia nela;
- **Aeroporto alternativo — cabe, mas não aqui.** Aceitar GRU ou VCP no mesmo monitor exige a
  varredura abrir em vários pares de rota, e aí aparece a pergunta difícil: a observação de
  VCP→LIS pertence a qual histórico? Mexe na D-016 e merece etapa própria.

**Casar o nome da companhia foi a parte que mais podia falhar em silêncio.** A camada 1 devolve
`IB`, a camada 2 devolve `Iberia` — a mesma empresa, escrita de dois jeitos. Comparação literal
deixaria metade das ofertas passar, e a preferência funcionaria **às vezes**, dependendo de qual
camada encontrou a oferta. O pior tipo de comportamento, porque parece aleatório.

A regra final tem duas partes, e as duas nasceram de teste falhando:

| Regra | Exemplo | Nasceu de |
|---|---|---|
| prefixo de palavra inteira | `Azul` → `Azul Linhas Aereas` | teste que falhou — a primeira versão não cobria o caso mais natural de todos |
| prefixo curto, até 3 letras | `IB` → `Iberia` | o caso do código IATA |

E um limite ficou documentado em teste próprio: **`TP` não alcança `Tap Air Portugal`**, porque
textualmente não há relação. Resolver exigiria uma tabela de códigos IATA, que este projeto não
tem motivo para carregar.

**Preferência não é exigência** ([D-073](DECISOES.md)). A oferta de companhia evitada continua
gravada: o histórico pertence à **rota**, não ao gosto de quem monitora. Se a Iberia opera
metade da rota, tirá-la da amostra produziria uma mediana que não existe em lugar nenhum, e todo
preço passaria a parecer caro.

**Zero e nulo significam coisas diferentes** nos pesos, e o código trata os dois: `null` é "não
escolhi, use o global"; `0` é "este aspecto não me importa", e o aspecto continua sendo avaliado
e mostrado — só deixa de pesar.

### 🐛 [BUG-011](BUGS.md) — o BUG-006 de novo, do outro lado

Ao ligar o filtro, **9 dos 13 testes do `MotorE2ETest` quebraram de uma vez**:

```
LazyInitializationException: Cannot lazily initialize collection of role
'Monitor.avoidedAirlines' (no session)
```

A varredura roda fora de transação de propósito — segurar conexão durante a chamada HTTP
esgotaria o pool (D-034). O monitor chega desanexado ao filtro, e a coleção `LAZY` estoura.

É exatamente o BUG-006, do lado da busca: lá era o telefone do destinatário no canal de entrega,
aqui é a lista de companhias no filtro de candidatos. **A causa não é o campo — é a regra de não
segurar transação durante chamada externa**, que continua cobrando atenção em cada lugar novo.

Corrigido em duas camadas: `findByIdComPreferencias` com `join fetch`, e um guarda no filtro que
segue com lista vazia se a leitura falhar. Falhar a varredura inteira por causa do filtro de
preferência seria trocar uma oportunidade real por uma conveniência.

**O que fez a diferença:** os testes unitários do filtro passavam todos — eles montam a entidade
em memória, onde coleção nenhuma é lazy. Só o E2E, com o monitor vindo do banco e a varredura
fora de transação, reproduz a condição. É o argumento da E1.15 se pagando pela terceira vez.

**Placar:** core-java **279 → 305 testes**; E2E entre serviços verde; worker-python 58.

---

## 🏁 Fase 2 concluída

Seis etapas, e um fio condutor que apareceu sem ter sido planejado: **toda escolha de estimador
foi a robusta**, e sempre pela mesma razão.

| Etapa | Escolha | Descartado | Por quê |
|---|---|---|---|
| E2.1 | mediana e quartis | média | cauda longa desloca a média |
| E2.2 | regra de Tukey | z-score | z-score pressupõe simetria |
| E2.3 | interpolação entre quantis | mínimo a máximo | um preço absurdo comprime a escala |
| E2.5 | Theil-Sen | mínimos quadrados | um dia atípico inclina a reta |

Preço de passagem tem cauda longa, e todo estimador sensível a extremo erra na mesma direção.

O segundo fio é mais importante: **o sistema aprendeu a dizer "não sei"**. `SEM_DADOS` em vez de
`NORMAL`, `confiavel: false` em vez de esconder o número, nota nula em vez de zero, silêncio na
mensagem em vez de "nota preliminar". Um sistema que opina desde a primeira observação está
chutando — e alarme falso destrói a confiança mais rápido do que alarme ausente.

**Próximo passo:** Fase 3 — Agente, começando pela E3.1.

---

### 2026-08-12 — ✅ E3.1 concluída · O sistema passou a entender português

```
POST /api/agent/interpret
{"texto": "Quero ir pra Belem em dezembro, ate R$ 1.500, voo direto", "origemPadrao": "GRU"}

{
  "intencao": {
    "origin": "GRU", "destination": "BEL",
    "departureFrom": "2026-12-01", "departureTo": "2026-12-31",
    "maxPrice": 1500.0, "prefereVooDireto": true,
    "provider": "regras", "confianca": 1.0, "avisos": []
  },
  "completo": true,
  "faltando": [],
  "sugestao": "entendi o pedido; confira os campos antes de criar o monitor"
}
```

**Só interpreta — não cria nada.** A criação é da E3.2, e a separação é proposital: quem escreve
um pedido deve poder conferir o que o sistema entendeu **antes** de algo ser gravado. Um monitor
criado a partir de interpretação errada não dá erro — ele vigia a rota errada por meses, em
silêncio.

**A interpretação vive no worker** ([D-076](DECISOES.md)): é tarefa de especialista, recebe texto
e devolve campos, sem tocar no banco. Contraste deliberado com a D-059, onde as estatísticas
ficaram no Java: a regra não é "tudo no Java" nem "tudo no Python" — é **onde o dado está**.

**Cadeia com dois interpretadores** ([D-077](DECISOES.md)), modelo primeiro e regras sempre por
último. As regras nunca saem da lista, e é isso que garante que uma chave revogada vire uma
resposta mais pobre em vez de um erro 500. **Resolve a P-4**, aberta desde o primeiro dia: a
resposta é uma cadeia configurável, com `claude-sonnet-5` como padrão — e o sistema funcionando
sem nenhum modelo.

Hoje ele roda **sem chave nenhuma**, só por regras, e cobre o jeito comum de pedir:

| Pedido | Leitura |
|---|---|
| "de São Paulo para Lisboa em março por até 4 mil, voo direto, uma semana" | GRU→LIS, 01–31/03/2027, R$ 4.000, direto, 7–9 dias |
| "para Buenos Aires saindo de Recife entre 10 e 20 de julho por 2500 reais para 2 pessoas" | REC→EZE, 10–20/07/2027, R$ 2.500, 2 passageiros |
| "quero viajar barato" | confiança 0.2, e três avisos dizendo o que faltou |

**O modelo devolve nome de cidade; a tradução para IATA é nossa** ([D-078](DECISOES.md)).
Modelos erram código de aeroporto com frequência — são milhares, GIG e SDU diferem por uma letra
— enquanto acertam nome de cidade quase sempre. E o erro é caro de um jeito específico: código
errado não dá erro em lugar nenhum.

**Cidade desconhecida vira `null` e aviso, nunca chute.** "Quero ir pra Xanadu em março por
4 mil" devolve datas e preço, destino nulo e `nao consegui identificar: destino`.

**A data de hoje viaja no pedido** ([D-079](DECISOES.md)), em vez de cada processo ler o próprio
relógio. "Em março" depende de quando se pergunta, e os dois serviços podem estar em containers
ou fusos diferentes. Efeito colateral que vale sozinho: os testes fixam `hoje` e param de
depender do calendário — sem isso, o teste de "mês que já passou vai para o ano seguinte"
falharia sozinho num sábado qualquer.

**Um defeito que o teste pegou, e que teria sido invisível:** o regex de preço lia `2500` como
`250`, porque `\d{1,3}` casava com os três primeiros dígitos e parava. Um monitor de R$ 250 para
Lisboa nunca alertaria, e não haveria como descobrir por quê — a interface mostraria o número
errado como se fosse o pedido. Corrigido com um `(?!\d)` no fim, e fixado em teste próprio.

**Duas correções no core que a etapa cobrou:**

- `boolean` primitivo não aceita `null`, e a desserializacão inteira falhava quando o campo vinha
  ausente. Virou `Boolean`, normalizado no construtor;
- `@JsonNaming(SnakeCase)` vazava snake_case para a **resposta** da API — este seria o único
  endpoint fora do padrão, obrigando o painel a lidar com duas convenções. Trocado por
  `@JsonAlias`, que vale só na leitura;
- worker fora do ar virava página de erro do servlet, com pilha no corpo. Agora é **503** com
  ProblemDetail: o defeito não é do usuário, e um 500 o mandaria procurar problema no pedido.

**Placar:** core-java **305 → 314 testes**; worker-python **58 → 87**.

**Próximo passo:** E3.2 — criação de monitor por conversa, que consome esta intenção.

---

### 2026-08-12 — ✅ E3.2 concluída · A frase virou monitor

Verificado ao vivo, com os dois serviços de pé:

```
POST /api/agent/monitors
{"texto": "Quero ir pra Belem em dezembro, ate R$ 1.500, voo direto", "origemPadrao": "GRU"}

201 → monitor 3255: GRU→BEL, 01–31/12/2026, teto R$ 1.500, voo direto
      assumido: destinatario Leonardo (o unico ativo cadastrado), 1 passageiro,
                varredura a cada 6 horas, sem restricao de permanencia,
                escalas permitidas mas penalizadas na nota
```

**Três coisas que este endpoint se recusa a fazer:**

| Recusa | Resposta |
|---|---|
| criar a partir de pedido incompleto | **422** com a interpretação e o que falta |
| assumir em silêncio | tudo que escolheu sozinho vai em `assumido` |
| duplicar | **409** apontando o id do monitor que já existe |

**Nada é assumido em silêncio** ([D-080](DECISOES.md)). Um monitor tem campos que a frase quase
nunca menciona — passageiros, intervalo, permanência, destinatário. Preencher calado é a forma
mais educada de mentir: a pessoa acha que pediu uma coisa e recebeu outra, e só descobre quando
o alerta não chega. É o princípio da Fase 2 — dizer o que não se sabe — aplicado ao que **se
escolheu por conta própria**.

**Com um destinatário ativo só, ele é usado** ([D-081](DECISOES.md)); com vários, nenhum, e
**com aviso**. O sistema é de uso pessoal por desenho, e com um destinatário não existe outra
escolha possível. Com vários, adivinhar quem recebe uma mensagem é pior do que não mandar. E o
aviso importa: monitor sem destinatário busca preço, grava histórico e **nunca avisa ninguém** —
sem a frase, o silêncio pareceria "não achei nada barato".

**Reenviar a mesma frase não cria um segundo monitor** ([D-082](DECISOES.md)). É o acidente mais
provável de um endpoint conversacional: a pessoa não tem certeza se funcionou e manda de novo.
A checagem é por **sobreposição** de janela, não igualdade — *"Lisboa em março"* e *"Lisboa entre
10 e 20 de março"* são o mesmo pedido dito de dois jeitos.

**422 e não 400** ([D-083](DECISOES.md)): a sintaxe do pedido está certa, o conteúdo é que não
basta. E o corpo traz a interpretação parcial, então a interface pergunta só o que falta em vez
de mandar reescrever tudo.

**As preferências da E2.6 atravessam a conversa inteira:** "voo direto" e "sem Iberia" viram
`prefereVooDireto` e `avoidedAirlines` no monitor criado, sem nenhum passo manual.

### 🐛 [BUG-012](BUGS.md) — a suíte esgotou as conexões do banco

Onze testes de classes intocadas erraram de uma vez com `FATAL: sorry, too many clients
already`. Cada configuração distinta cria um **contexto Spring próprio**, o Spring mantém todos
em cache até o fim da execução, e cada um abre um pool de 10 conexões. Sete contextos passam de
70; com a aplicação de desenvolvimento rodando, bateu no limite de 100 do PostgreSQL.

Foi crescendo em silêncio — cada etapa que adicionou uma classe com configuração própria
consumiu mais 10, e a E3.2 foi a que passou do teto. Resolvido com pool de **2** nos testes, que
rodam em uma thread só. **Não** aumentei o `max_connections`: isso esconderia o desperdício e
adiaria o mesmo problema.

**Placar:** core-java **314 → 328 testes**, todos verdes.

**Próximo passo:** E3.3 — recomendação em linguagem natural, a última da Fase 3.

---

### 2026-08-12 — ✅ E3.3 concluída · A Fase 3 fechou

A Fase 2 produziu quatro análises que ninguém lia. Esta etapa junta as quatro numa frase que uma
pessoa entende. Verificado ao vivo, com monitor criado **pela frase** e dez varreduras:

```
GET /api/agent/recommendation/monitors/3601

veredito: VALE_MUITO   confiavel: true

vale muito a pena — menor preco visto nos ultimos 90 dias nesta rota;
voo bom: 12h25, a melhor duracao ja vista na rota (nota 83/100).
Para ponderar: comparado com 10 precos confirmados desta rota.

  [A_FAVOR   ] PRECO      menor preco visto nos ultimos 90 dias nesta rota
  [A_FAVOR   ] VOO        voo bom: 12h25, a melhor duracao ja vista na rota (nota 83/100)
  [A_PONDERAR] HISTORICO  comparado com 10 precos confirmados desta rota
```

**Sem modelo de linguagem, e isso foi decisão e não omissão** ([D-084](DECISOES.md)). A
tentação era mandar tudo para um LLM e pedir um parágrafo bonito. Dois motivos contra: **não há
o que interpretar** — a entrada já são fatos calculados com frase pronta, e o que sobra é
composição, que é determinismo — e **o risco é assimétrico**: o ganho seria fluência, a perda
possível é uma frase inventada como *"melhor momento para comprar"*, que este sistema passou
três fases se recusando a dizer.

O modelo continua fazendo sentido onde há ambiguidade de verdade: interpretar o pedido de quem
escreve, na E3.1.

**O resumo vem com as razões** ([D-085](DECISOES.md)): o parágrafo é para ler, a lista é para
conferir. Recomendação sem a lista seria opinião sem prestação de contas.

**Três lados, e não dois** ([D-086](DECISOES.md)). `A_PONDERAR` existe porque preço em queda
**não piora a oferta** — torna razoável esperar, que é outra coisa. Marcar como "contra"
transformaria constatação em conselho disfarçado. Sem o terceiro lado, toda informação teria que
ser espremida em bom ou ruim, e a que não é nem um nem outro seria distorcida para caber.

**Um teste que vale por muitos:** varre **todas** as combinações de grau de anomalia e direção
de tendência e proíbe as palavras *compre*, *aproveite*, *corra*, *garanta* e *não perca* na
saída. A D-072 deixa de ser intenção e passa a ser propriedade verificada.

### Duas frases ridículas que só apareceram ao olhar a saída real

Os testes passavam. O texto estava errado assim mesmo:

| Antes | Problema | Agora |
|---|---|---|
| `voo bom: menor preco ja visto na rota (nota 83/100)` | a razão sobre o **voo** falava de **preço** — o componente de preço era o mais alto da nota, e a frase repetia o argumento da linha anterior | exclui o preço ao descrever o voo |
| `12h25, contra 12h25 do melhor da rota` | verdadeiro e ridículo: comparava a oferta consigo mesma | `12h25, a melhor duracao ja vista na rota` |

A segunda também afetava a mensagem de alerta da E2.4, que usava a mesma composição. As duas
viraram teste.

**Placar:** core-java **328 → 348 testes**, todos verdes.

---

## 🏁 Fase 3 concluída

Três etapas, e o agente ficou com uma característica que não estava no roteiro: **ele sabe
quando não sabe**, em todos os três pontos.

| Etapa | O que ele se recusa a fazer |
|---|---|
| E3.1 | chutar aeroporto, ou preencher campo que o texto não disse |
| E3.2 | criar monitor incompleto, assumir em silêncio, duplicar |
| E3.3 | opinar sem histórico, dar conselho de compra |

E o LLM ficou exatamente onde ele ajuda — entendendo o pedido — e fora de onde ele arriscaria
inventar. **A E3.1 é a única etapa do projeto inteiro em que um modelo participa**, e mesmo lá
com um interpretador determinístico por baixo, que responde quando ele não está disponível.

**Próximo passo:** Fase 4 — infraestrutura, começando pela E4.1 (RabbitMQ). Continuam abertas a
E1.12 (template em análise na Meta) e o painel Vue, que ainda não mostra nada das Fases 2 e 3.

---

### 2026-08-13 — ✅ E4.1 concluída · O mesmo contrato, agora por fila

```
python scripts/e2e_servicos.py                     -> 11 testes, REST
python scripts/e2e_servicos.py --transporte amqp   -> 11 testes, AMQP
```

**Os mesmos onze testes**, sem nenhum específico para mensageria. Se o comportamento dependesse
do meio, a porta `SearchClient` não estaria cumprindo o papel dela.

**A troca custou zero no motor**, e não por sorte. A `SearchClient` existe como porta desde a
E1.7, e o javadoc dela já citava esta etapa nominalmente:

> *"a migração para RabbitMQ (etapa E4.1) troca o adaptador REST por um de mensageria, sem tocar
> em nada que consome esta interface."*

A [D-006](DECISOES.md), do primeiro dia, prometeu que "a troca fica prevista na arquitetura, não
é retrabalho". Foi exatamente o que aconteceu: `PriceSearchService`, `SearchCycleService` e o
endpoint manual não mudaram uma linha.

**Request/reply, e não "publica e esquece"** ([D-087](DECISOES.md)). A assincronia de verdade
quebraria duas coisas caras: o `processarMonitor` deixaria de ser caminho único — a forma exata
do BUG-005 — e o estado entre pedido e resposta viraria uma máquina de estados distribuída para
um problema que o sistema ainda não tem. O ganho real vem sem isso: broker no meio, fila
absorvendo rajada, worker replicável, e mensagem órfã visível na dead-letter.

**Três defeitos encontrados durante a etapa, todos pelo E2E:**

| Sintoma | Causa | Correção |
|---|---|---|
| 11 testes em timeout de 30s | meu `wait_for(15s)` cancelava uma espera pela topologia que leva até 60s — o worker subia "saudável" e surdo | consumidor sobe em tarefa de segundo plano |
| `not in the trusted packages` | o Python anunciava o nome da classe Java no `__TypeId__` | **tirar** o cabeçalho: quem recebe declara o tipo ([D-088](DECISOES.md)) |
| busca falha virava "sem ofertas" | o AMQP não tinha equivalente do HTTP 502 | cabeçalho `x-fonte-falhou` ([D-090](DECISOES.md)) |

O terceiro é o que mais valeu. Sem ele, uma fonte fora do ar viraria **busca bem-sucedida e
vazia**: o monitor voltaria à fila no intervalo normal em vez de retentar, e o painel diria
"nenhuma oferta" para um serviço caído. A distinção entre "a fonte morreu" e "a janela está
vazia" é a mesma que `returned` e `kept` preservam — e ela quase se perdeu só por trocar de
transporte.

**Uma assertiva ficou melhor por causa disso.** O teste exigia que o erro contivesse `"502"`, e
falhou por AMQP com o comportamento **certo**. Ela estava presa a um detalhe de implementação do
REST; agora exige que o **motivo** sobreviva à viagem, o que é o que realmente importa.

**Quem declara a topologia é o core** ([D-089](DECISOES.md)), e o worker espera a fila existir —
até um minuto. Sem essa espera, a ordem de subida dos containers decidiria se o sistema
funciona, que é a pior forma de dependência: muda de máquina para máquina.

**O padrão continua REST.** O transporte novo só entra com `WORKER_TRANSPORTE=AMQP`; o default é
o que já rodava, e não o recém-chegado.

**Placar:** core-java **348 testes**, worker-python **87**, e o E2E entre serviços verde nos dois
transportes.

**Próximo passo:** E4.2 — dockerização completa dos três serviços.

---

### 2026-08-13 — 🏛️ Arquitetura · Os três módulos ganharam camadas verificadas

Pausa no roteiro, a pedido: aplicar uma arquitetura limpa antes de seguir para a E4.2. Três
frentes, três resultados bem diferentes — e é a diferença que interessa.

#### core-java — BCE por feature

Cada feature (`monitor`, `alert`, `search`, `stats`, `agent`) passou a ter suas próprias
`entity/`, `control/` e `boundary/`. Feature primeiro, camada dentro ([D-091](DECISOES.md)).

O que a estrutura passou a mostrar: mexer no alerta é abrir **uma** pasta. A E4.1 trocou REST por
mensageria tocando só em `search/boundary/client/` — agora isso é visível no diretório, e não só
na memória de quem fez.

**O erro que valeu mais que o acerto.** Coloquei `SearchClient` em `boundary/`, e o ArchUnit
reprovou com **157 violações** de "controle não conhece a borda". A regra estava certa; a pasta é
que estava errada. A **porta** é o vocabulário que o controle usa para pedir — quem *implementa* é
que é borda ([D-092](DECISOES.md)). Movida para `control/client/`, as 157 sumiram de uma vez.

Duas dependências apontavam para fora do centro e foram corrigidas no código, não na regra:

| O que | Por que era errado | Correção |
|---|---|---|
| `Alert.registrarAnalise(AlertInsights)` | a entidade dependia de um record de caso de uso | passa os três valores ([D-094](DECISOES.md)) |
| `AgentService` → `NlpClient` (classe) | o controle dependia de uma classe da borda | nasceu a `NlpPort`; a classe virou `RestNlpClient` |

**E uma regra minha foi removida por estar errada.** "Controller não usa repositório" reprovou 24
dependências; ao olhar uma por uma, era a regra que não se sustentava. Em BCE a boundary é a
fachada do caso de uso — alcançar a entidade é o ponto do estilo. Manter produziria serviços que
só repassam consulta. O que continua valendo — *a borda não decide* — não dá para verificar por
dependência, e virou critério de revisão em vez de teste verde enganoso ([D-093](DECISOES.md)).

#### worker-python — o mesmo desenho, sem `entity`

```
boundary/  http/ e amqp/ (entrada) · gateway/ (saída)
control/   busca/ e nlp/ — as portas e as cadeias
composicao/ a raiz de composição: o único lugar que conhece os dois lados
```

Não há `entity`, **e isso está escrito** ([D-095](DECISOES.md)). Ausência de pasta é ambígua: quem
chega não sabe se foi desenho ou esquecimento. Um teste falha se alguém importar SQLAlchemy,
psycopg, asyncpg, sqlite3 ou pymongo — a regra 1 do plano deixou de ser parágrafo e virou build
quebrado, no dia exato em que alguém fosse violá-la.

A raiz de composição separada existe porque HTTP e AMQP fazem a **mesma** coisa por caminhos
diferentes. Se uma delas instanciasse o Travelpayouts direto, as duas deixariam de concordar — e
o E2E entre serviços só pegaria por sorte, dependendo do transporte ativo naquele dia.

#### frontend-vue — a resposta honesta era "já está no padrão"

O `frontend-vue` já seguia o formato do `create-vue`. Renomear `views/` para `boundary/` deixaria
o projeto pior para qualquer pessoa que conheça Vue, em troca de simetria com um diagrama. BCE
resolve acoplamento entre camadas de negócio, e o frontend não tem negócio — tem tela, chamada e
formatação ([D-096](DECISOES.md)). Pinia ficou de fora pelo mesmo critério: nenhuma tela
compartilha estado com outra.

Só que olhar direito achou **três defeitos reais**, que estrutura nenhuma teria escondido:

| Defeito | O sintoma que ele já produzia |
|---|---|
| `dinheiro`/`data`/`instante` escritas **3 vezes** | o mesmo preço saía "R$ 3.720,00" numa tela e "R$ 3.720" na outra; uma cópia devolvia `—` para nulo, outra quebrava |
| `try/catch/finally` com `ApiError` copiado em **4 lugares** | um `finally` esquecido deixa a tela em "Carregando..." para sempre — e parece lentidão da API |
| `types/` com `monitorVazio()` e `menorPrecoPorData()` | comportamento morando numa pasta que promete tipos |

Viraram `lib/formato.ts` (puro, sem Vue), `composables/useCarregamento.ts` e `model/`. As
diferenças **intencionais** entre as cópias — o gráfico arredonda, as telas não — sobreviveram
como parâmetro, e estão fixadas em teste. E `api/monitores.ts`, que tratava de monitores,
destinatários *e* observações, virou um módulo por recurso.

Um detalhe de comportamento apareceu ao unificar: `carregando` precisa nascer `true`. Com `false`,
entre montar o componente e o `onMounted` disparar havia um quadro sem dado, sem erro e sem
"Carregando..." — a tela piscava vazia.

#### O que ficou verificável

| Serviço | Onde | Regras |
|---|---|---|
| core-java | `ArquiteturaTest.java` (ArchUnit) | 7 |
| worker-python | `tests/test_arquitetura.py` (AST dos imports) | 5 |
| frontend-vue | `src/estrutura.spec.ts` (Vitest) | 6 |

O frontend **não tinha teste nenhum** antes disto. Passou a ter 24 — formatação, estado de
carregamento e as camadas.

**Placar:** core-java **355 testes**, worker-python **92**, frontend-vue **24**, e o E2E entre
serviços verde nos dois transportes.

**Próximo passo:** discutir a estratégia de notificação por e-mail (item 4 do pedido), e então a
E4.2 — dockerização completa.

---

### 2026-08-13 — 📋 Estratégia do canal de e-mail decidida (E4.6, ainda não implementada)

Discussão pedida antes de escrever qualquer código. Três decisões fechadas, registradas em
[D-097](DECISOES.md): **SMTP configurável**, **canal único trocado por configuração** e **coluna
`email` em `recipient`**.

**O que a regra 3 do plano economizou.** Levantei o que o e-mail encosta esperando encontrar
trabalho, e encontrei quase nenhum: `NotificationChannel` já tem a forma certa, e até o detalhe
difícil já estava previsto — `confirmacaoAssincrona()` existe justamente para separar "chegou" de
"o provedor disse que recebeu". E-mail responde `false` ali. O `AlertMessageFormatter` também já
produz texto completo, porque o canal LOG precisava disso.

Sobra: um valor no enum, um CHECK recriado, uma coluna aditiva e a implementação do canal.

**O ponto real é o `recipient`.** `phone_e164` é `NOT NULL UNIQUE` hoje — um destinatário só de
e-mail não cabe no modelo. Vira opcional, com CHECK exigindo **pelo menos um** contato, para não
abrir a porta ao estado sem sentido: alguém que não pode ser alcançado por nada.

**Por que reabrir algo que estava fora do escopo.** A seção 2 do plano diz "sem e-mail", e era
deliberado. O que mudou não foi a preferência: o WhatsApp se mostrou **bloqueável por terceiro**.
O template está em análise na Meta desde a E1.12, e enquanto estiver, o sistema faz todo o
trabalho — varre, confirma, pontua, decide alertar — e não avisa ninguém.

**Uma tentação recusada.** O pixel invisível de rastreamento de abertura daria um `read_at` para
e-mail. Ele falha calado sempre que o cliente bloqueia imagens, que é o padrão em boa parte deles:
o resultado seria `read_at` vazio para e-mails que **foram** lidos. Pior que não ter a informação,
porque parece informação. Alerta por e-mail para em `SENT`, e `SENT` quer dizer "o SMTP aceitou".

**Depende de você:** verificação em duas etapas na conta Google e uma senha de app de 16
caracteres — três minutos, custo zero, sem domínio e sem cadastro. Passo a passo em
[GUIA-EMAIL.md](GUIA-EMAIL.md). O código **não** depende disso para ser escrito nem testado: a
suíte usa SMTP falso. A senha só é necessária para o envio real.

**Próximo passo:** aguardando sua decisão sobre implementar a E4.6 agora ou seguir para a E4.2.

---

### 2026-08-13 — ✅ E1.12 concluída · 🏁 A Fase 1 fechou

O template `alerta_preco_voo` saiu de PENDING. A tela mostrava "Ativo — Qualidade pendente", que
é o rating ainda sem volume, e não um bloqueio — mas foi uma tela que me enganou no
[BUG-009](BUGS.md), então confirmei pela Graph API antes de acreditar: `status=APPROVED`,
`UTILITY`, `pt_BR`.

**Verificado antes de gastar mensagem:**

| O quê | Resultado |
|---|---|
| Status pela API, não pela tela | APPROVED · UTILITY · pt_BR |
| Parâmetros do template vs. `parametrosDoTemplate()` | 5 e 5, mesma ordem |
| `template-name` carregado | `alerta_preco_voo` — guardado pelo teste do [BUG-008](BUGS.md) |
| Canal ativo na subida | `disponíveis: [WHATSAPP, LOG]; ativo: WHATSAPP` |

**O envio real, uma mensagem só.** Varredura em GRU→SSA com teto folgado: 3 observações, 2
candidatos, melhor a **R$ 1.401** confirmado pela camada 2 (LATAM, voo direto). Um alerta por
destinatário, sobre a melhor oferta — nunca um por candidato.

```
alerta 1728 · WHATSAPP · ACCEPTED · attempts=0 · wamid.HBgNNTUxMTk1MDU3NzI4Mh…
despacho concluido: DispatchResult[reivindicados=1, entregues=1, falhas=0, retentar=0]
```

**Chegou** — confirmado pelo usuário no aparelho.

**`ACCEPTED` é o estado certo, e não uma pendência.** O `confirmacaoAssincrona()` do canal declara
que a confirmação vem por webhook, e sem túnel público ele para honestamente em "a Meta aceitou".
É a lição do [BUG-007](BUGS.md) virada código: *aceito* e *entregue* são fatos diferentes, e o
sistema se recusa a confundir os dois mesmo quando confundir daria um `SENT` mais bonito.

**A Fase 2 se calou, e estava certa.** Os parâmetros 3 e 4 saíram sem nota e sem comparação: são 3
observações nesta rota, e não há base para opinar. A mensagem ficou mais pobre e mais honesta.

**O sistema também avisou o que não sabia direito:** que a fonte devolveu o código de cidade `SAO`
no lugar de `GRU` — o preço pode ser de Congonhas ou Viracopos ([RISCO-006](BUGS.md)) — e que
descartou 26 ofertas fora dos critérios.

#### 🔁 [RISCO-008](BUGS.md) reincidiu, e desta vez custou informação

Rodei a suíte duas vezes para conferir uma contagem, e ela apagou o destinatário e o monitor. O
`alert` era o **único** lugar onde existia o número de destino do teste anterior: não estava em
documento nenhum, e a Graph API não devolve histórico de conversas. A etapa parou até o usuário
informar o número de novo.

"Banco descartável" valia enquanto o dado era recriável por quem apagou. Um dado que só existia
ali e precisou de outra pessoa para voltar não era descartável — era dado de verdade num lugar que
se comporta como rascunho. **Promovido a item obrigatório da E4.2:** duas ocorrências em dois
dias, a segunda com perda, é padrão e não azar.

#### O que fica em aberto, e é pequeno

`DELIVERED` e `READ` da E1.16 dependem do webhook receber a chamada da Meta, e isso precisa de URL
pública (ngrok ou equivalente). O código está pronto desde a E1.17 — falta só o túnel, e mais um
envio para percorrer o caminho inteiro.

**Placar:** core-java **355 testes**, worker-python **92**, frontend-vue **24**.

**Próximo passo:** E4.2 — dockerização completa, com a separação do banco de teste junto.

---

### 2026-08-13 — ✅ E4.6 concluída · O sistema deixou de depender de um canal só

Terceira implementação de `NotificationChannel`. **O motor não mudou uma linha** — a regra 3 da
seção 3 do plano ("o WhatsApp fica isolado atrás de uma interface") foi escrita na E1.11 e
cobrada agora, dois meses depois.

#### O que precisou ser feito, e é pouco

| Camada | Mudança |
|---|---|
| Banco | migration **V7**: `EMAIL` no CHECK, coluna `email`, `phone_e164` deixa de ser obrigatório |
| Entidade | `Recipient` ganha `email`; telefone vira opcional |
| DTO | `@ContatoAlcancavel` — "telefone **ou** e-mail", que anotação de campo não expressa |
| Borda | `EmailNotificationChannel` + `EmailProperties` |
| Frontend | o tipo aceita nulo, e a lista mostra o contato que existir |

`AlertMessageFormatter` **não foi tocado**: ele já produzia texto completo desde a E1.11, porque
o canal LOG precisava disso. Aquele "canal de desenvolvimento" acabou pagando duas dívidas.

#### A pergunta do usuário que mudou o desenho

*"Se eu cadastrar meu e-mail como receptor e o emissor for a mesma conta Google, seria como eu
mandando e-mail para mim mesmo?"* Seria — e isso quebra três coisas ([D-098](DECISOES.md)): o
Gmail exibe o remetente como *eu* e **agrupa os alertas numa thread só**; a notificação no celular
é tratada de forma diferente para mensagem que você mesmo mandou; e a senha de app seria da conta
principal.

Virou uma conta dedicada — `flightmonitor.seunome@gmail.com` — e uma consequência de projeto que
eu **não tinha visto**: o **assunto precisa variar**. Mesmo com remetente distinto, o Gmail agrupa
assunto idêntico, e alerta de monitor é repetitivo por natureza. Sem essa pergunta, o defeito só
apareceria no terceiro ou quarto alerta, empilhado numa conversa.

O assunto ficou `✈ GRU → SSA por R$ 1.401,00`, com um teste exigindo menos de 60 caracteres: para
um sistema que existe para avisar, **a tela da notificação é a que mais importa**.

#### Decisões que valem registro

**Falha permanente vs. transitória, escolhida uma a uma.** Senha de app errada é **permanente** —
retentar três vezes só adia o diagnóstico. Servidor fora do ar é **transitória**. Destinatário sem
e-mail cadastrado é **permanente**, e o motivo fica em `error_message`, que é onde alguém vai
procurar ao notar que o alerta não chegou.

**Regex de e-mail deliberadamente frouxa**, no banco e no DTO. Validação estrita de e-mail é
armadilha conhecida: a gramática real da RFC 5322 aceita coisas que quase toda regex rejeita, e o
custo do erro é uma pessoa que não consegue se cadastrar. Basta barrar o obviamente errado.

**O e-mail é guardado em minúsculas.** Sem isso, `Leo@x.com` e `leo@x.com` passariam pelo índice
único como duas pessoas, e a mesma caixa receberia o alerta duas vezes.

**Campo vazio conta como ausente.** Formulário manda `""`, não `null`. Sem tratar, a string vazia
passaria pelo "tem algum contato" e viraria um contato que não alcança ninguém.

**`recipient_tem_algum_contato`** guarda no banco o estado sem sentido: uma pessoa cadastrada para
receber alertas, sem nenhuma forma de recebê-los.

#### Testado sem mandar um único e-mail

10 testes contra **GreenMail**, um SMTP em memória. Diferente do WhatsApp — que exigiu aprovação
de template e mensagens pagas para validar — aqui o caminho inteiro cabe na suíte.

Um deles fixa o que mais importa: `confirmacaoAssincrona()` é **false**. Um `true` aqui deixaria
todo alerta preso em `ACCEPTED` esperando um webhook que nunca viria — o modo de falha que o
[BUG-007](BUGS.md) ensinou a temer.

#### Uma coisa que eu escrevi e desfiz

Cheguei a pôr `contatoPara(canal)` na entidade `Recipient`, com a justificativa de "não espalhar
o switch pelos adaptadores". Ao reler, a justificativa não se sustentava: **não existe switch
espalhado** se cada adaptador lê o próprio campo — o de e-mail quer o e-mail. O método fazia a
entidade importar `AlertChannel` de outra feature em troca de nada. Removido antes de compilar.

**Placar:** core-java **372 testes** (355 + 10 do canal + 7 do destinatário), worker-python **92**,
frontend-vue **24**.

**Falta só você:** a `MAIL_PASSWORD` no `.env`. A linha está lá, em branco, esperando.

**Próximo passo:** E4.2 — dockerização completa, com o banco de teste separado junto.

---

### 2026-08-13 — 🐛 [BUG-013](BUGS.md) · O canal de e-mail subiu mudo, e a suíte não viu

Com a senha de app no `.env` e `NOTIFICATION_CHANNEL=EMAIL`, a aplicação subiu assim:

```
WARN EmailNotificationChannel : canal EMAIL sem remetente configurado: defina MAIL_FROM no .env
INFO NotificationService      : canais disponiveis: [WHATSAPP, EMAIL, LOG]; ativo: EMAIL
```

`MAIL_FROM` estava no `.env`, correto. **O bloco `email:` é que estava na chave errada.**

Ao inserir o bloco no `application.yml`, ancorei no texto `logging:` sem olhar o que vinha antes —
e entre `flightmonitor:` e `logging:` existem `server:` e `management:`. Com dois espaços de
indentação, o bloco caiu sob `management:`. A propriedade virou `management.email.remetente`. YAML
válido, Spring sem reclamar, `EmailProperties` ligado com nulos, e **todo** envio recusado com
falha permanente.

**É o [BUG-008](BUGS.md) de novo, no mesmo formato:** configuração silenciosamente errada, testes
verdes, falha só no primeiro uso real.

**Por que os 10 testes do canal não pegaram:** eles constroem `EmailProperties` na mão, para
testar o canal sem subir o Spring. Ótimo para comportamento, **cego para configuração** — nunca
passam pelo YAML.

Nasceu o `ConfiguracaoDoEmailTest`, análogo do `TemplateDoWhatsAppTest` que veio do BUG-008: lê a
configuração **como o Spring a carrega**. Desfiz a correção de propósito para ver o teste
reprovar — e ele reprovou dizendo *"o bloco email: provavelmente está aninhado na chave errada"*.
Só então restaurei.

**A lição que se repete:** teste que constrói a configuração na mão não verifica configuração.

#### ✅ E o envio real funcionou

```
alerta 1906 · EMAIL · SENT · attempts=0 · <1824794300.0.1786652877929@...>
despacho concluido: DispatchResult[reivindicados=1, entregues=1, falhas=0, retentar=0]
```

`SENT` e não `ACCEPTED` — o oposto do WhatsApp, e correto: `confirmacaoAssincrona()` é false
porque não há webhook que confirme e-mail. `SENT` aqui quer dizer "o SMTP aceitou", e é tudo que
se pode afirmar com honestidade.

**Detalhe divertido:** quem disparou foi o **scheduler**, segundos antes da minha varredura
manual. A minha caiu no anti-spam com *"último alerta há menos de 12h"* — o que por acidente
validou também que o anti-spam funciona entre origens diferentes de disparo.

**Placar:** core-java **375 testes** (372 + 3 do guarda de configuração), worker-python **92**,
frontend-vue **24**.

---

### 2026-08-13 — ✅ E4.2 concluída · `docker compose up` e o sistema inteiro sobe

Cinco containers: `postgres`, `rabbitmq`, `core`, `worker`, `painel`. Imagens em duas etapas —
a que compila não vai para a que executa, então não há Maven, Node nem compilador C no ambiente
de execução. Todos rodam com usuário sem privilégio.

**Validado de ponta a ponta em container**, e não só "subiu": navegador → nginx → core → worker →
Travelpayouts + fast-flights → PostgreSQL → SMTP → **e-mail entregue** (alerta 1966, SENT). Depois
o mesmo ciclo por **AMQP**, com os consumidores conectados nas duas filas.

#### 🏁 [RISCO-008](BUGS.md) eliminado

Existe agora o banco `flightmon_test`. Continua PostgreSQL de verdade, mesmo container, mesmo
Flyway, mesmas migrations — o valor dos testes de constraint e trigger está inteiro. O que mudou é
só **em qual banco eles fazem a bagunça**.

Verificado na prática: criei um destinatário no banco de desenvolvimento, rodei os 375 testes por
cima, e ele **sobreviveu**. Primeira vez.

#### O que a containerização expôs — e era esse o ponto

Container é o primeiro ambiente construído **só** a partir do que está declarado. Quatro coisas
apareceram, e nenhuma teria aparecido de outro jeito.

**1. [BUG-014](BUGS.md) — a camada 2 inteira estava comentada no `requirements.txt`. Por seis
semanas.** `fast-flights` foi instalada na venv local durante a E1.6 e a linha nunca foi
descomentada. Toda busca no container voltava `confirmada=false`, e o sistema — corretamente — se
recusava a alertar. **Sem nenhum erro**: a degradação projetada para "o Google mudou o formato"
([RISCO-002](BUGS.md)) cobriu também "a biblioteca não existe", e as duas são bem diferentes. O
sintoma era o monitor parar de avisar, em silêncio.

Nasceu o `test_dependencias.py`, que percorre os imports por AST e exige declaração. **Ele achou um
segundo caso na primeira execução:** `anyio`, importado direto pelo gateway da camada 2, vinha de
carona pelo FastAPI — funcionava por acidente.

**2. O worker ignorava `WORKER_TRANSPORTE`.** O compose passava a variável para os dois serviços,
mas o worker só conhecia `AMQP_ENABLED`. As filas apareciam declaradas no RabbitMQ com
**`consumers = 0`**, tudo "healthy", e uma busca por fila esperaria para sempre. Duas variáveis
para uma decisão só é convite para ficarem em desacordo — agora qualquer uma das duas liga o
consumidor, com teste fixando isso.

**3. O nginx guardava o IP do core para sempre.** Ele resolve o DNS uma vez, na subida. Recriar o
container do core — que ganha IP novo — deixava o painel em 502 até alguém reiniciar o nginx, com
o core aparecendo `healthy` o tempo todo: o defeito estava em quem chamava. Corrigido com
`resolver 127.0.0.11` **e** destino em variável — o `resolver` sozinho não basta, o nginx só
re-resolve quando o `proxy_pass` aponta para uma variável.

Para provar a correção não bastou recriar o container: o Docker devolveu o mesmo IP, e o teste não
provou nada. Subi um container descartável para **tomar** o IP antigo, o core nasceu em outro, e aí
sim o painel respondeu 200.

**4. `localhost` não é `127.0.0.1` dentro do container.** O healthcheck do painel dizia
"connection refused" com o nginx funcionando: o `wget` do BusyBox tenta `::1` primeiro, e o nginx
escuta em IPv4.

#### Decisões pequenas que valem registro

**Painel na 8090, não na 8080.** A 8080 já estava tomada nesta máquina por um serviço do Windows —
que não é deste projeto e não foi mexido. Mesmo motivo da 5433 no PostgreSQL: porta padrão ocupada
é o normal, não a exceção.

**`depends_on` com `service_healthy`**, e não `service_started`: container de pé não quer dizer
banco aceitando conexão, e o Flyway falharia de forma intermitente na largada.

**O healthcheck do core usa `readiness`, não `health`.** O health geral fica DOWN quando o worker
está fora, e o core continua útil nesse estado — ele só não varre. Container reiniciando por causa
da saúde do vizinho seria pior que o problema.

**Sem testes no build da imagem.** Eles precisam de PostgreSQL e RabbitMQ, que não existem durante
o build. O lugar deles é no `mvn test`, com os serviços de pé.

**Placar:** core-java **375**, worker-python **95**, frontend-vue **24**.

**Próximo passo:** E4.3 — observabilidade.

---

### 2026-08-13 — ✅ Painel de destinatários · E o Actuator ganhou o canal ativo

Faltava a tela mais básica que existe: **cadastrar quem recebe o alerta**. A API existia desde a
E1.4, mas o painel só *listava* — criar destinatário exigia `curl`. Num projeto que vai virar
público, isso é a primeira parede que alguém encontra.

Tela com CRUD completo, formulário com telefone **e** e-mail, e a regra "pelo menos um" repetida
no navegador — para **avisar**, não para garantir: a API recusa de qualquer jeito, com o CHECK do
banco por trás.

#### A tela pediu um indicador que não existia

Queria que ela avisasse quem **não vai ser alcançado**: um destinatário só com e-mail, com o
sistema em `WHATSAPP`, é um alerta que falha em silêncio — vira falha permanente, e a pessoa nunca
sabe que perdeu a passagem.

Escrevi a tela lendo `components.notificacao.canal` do Actuator. **Esse indicador não existia.** Do
jeito que estava, o aviso simplesmente nunca apareceria, e ninguém perceberia — um recurso que é um
no-op silencioso é pior que recurso ausente, porque parece que está lá.

Nasceu o `NotificacaoHealthIndicator`, e ele acabou útil além da tela:

```json
"notificacao": {
  "status": "UP",
  "details": {
    "canal": "EMAIL",
    "disponiveis": ["EMAIL", "LOG", "WHATSAPP"],
    "confirmacaoAssincrona": false
  }
}
```

O `confirmacaoAssincrona` explica no painel por que um alerta para em `ACCEPTED` (WhatsApp, espera
webhook) e outro em `SENT` (e-mail, o SMTP aceitou e acabou).

**`LOG` é `UP`, e não doença.** É o padrão de quem acabou de clonar o projeto; marcar como `DOWN`
faria o `/health` mentir para quem está só experimentando. O que **derruba** o status é canal
configurado sem implementação — aí sim o sistema acha que vai avisar por um caminho que não existe.

#### Verificado em container, com os três casos

| Caso | Resultado |
|---|---|
| Só e-mail, sem telefone | `201` — era impossível antes da E4.6 |
| Só telefone, com espaços e parênteses | `201`, normalizado para `+5511988887777` |
| Sem telefone e sem e-mail | `400`, com a mensagem **no campo** |

**Placar:** core-java **379**, worker-python **95**, frontend-vue **32**.
