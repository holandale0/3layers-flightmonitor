# Flight Monitor

Monitor silencioso de preços de passagens aéreas. Roda em background, varre janelas de datas
configuradas e avisa quando encontra uma oferta dentro dos seus critérios. Só incomoda quando há
oportunidade real.

```
GRU → LIS
Ida:    entre 10/03/2027 e 20/03/2027
Volta:  permanência de 10 a 15 dias
Preço máximo: R$ 3.200
Escalas máximas: 1
```

Quando um voo cabe nesses critérios, chega uma mensagem — no WhatsApp, no e-mail, ou no log,
conforme o canal configurado.

## Rodando em dois minutos, sem credencial nenhuma

```bash
git clone https://github.com/holandale0/3layers-flightmonitor.git
cd 3layers-flightmonitor
cp .env.example .env
docker compose up -d
```

Painel em **http://localhost:8090**. Sobem cinco containers e todos ficam saudáveis sem nenhuma
chave de API. O canal padrão é `LOG`, que imprime o alerta no console em vez de enviar.

Dá para cadastrar destinatários e monitores e navegar o painel inteiro. **A busca de preço real,
não** — ela precisa de um token gratuito da Travelpayouts, e sem ele o painel diz exatamente isso:

```
falhou: true
avisos: ["o worker respondeu HTTP 503 na varredura:
         TRAVELPAYOUTS_TOKEN nao configurado no worker"]
```

Falhar assim é proposital. O contrário — devolver "nenhuma oferta encontrada" — faria uma fonte
desconfigurada parecer um dia sem promoção.

### Ver o sistema inteiro funcionando, ainda sem credencial

```bash
USE_FAKE_PROVIDERS=true docker compose up -d worker
```

Isso troca as duas camadas de coleta por fontes falsas e deterministas. Crie um monitor GRU → SSA
com teto de R$ 5.000, clique em **Buscar agora**, e acompanhe `docker compose logs -f core`:

```
┌─────────── ALERTA (canal LOG) ───────────
│ para: Maria <maria@exemplo.com>
├──────────────────────────────────────────
✈️ *Oportunidade encontrada*

GRU → SSA
05/10/2026 → 17/10/2026

Iberia · 1 escala
*R$ 3.720,00*
Seu limite: R$ 5.000,00  (R$ 1.280,00 abaixo)
```

O caminho completo — varredura, confirmação, decisão de alerta, formatação, entrega — sem cadastro
em lugar nenhum.

> `USE_FAKE_PROVIDERS` **não** está no `.env.example`, de propósito. Um `true` esquecido ali faria o
> sistema inventar preços em silêncio, e preço inventado que chega como alerta é pior que alerta
> nenhum. Quem liga isso, liga na linha de comando e sabe que ligou.

## Os três níveis de aviso

Escolha por `NOTIFICATION_CHANNEL` no `.env`. Cada nível custa mais preparo que o anterior:

| Canal | O que precisa | Tempo |
|---|---|---|
| `LOG` *(padrão)* | nada | — |
| `EMAIL` | uma conta Gmail dedicada e uma senha de app · [guia](docs/GUIA-EMAIL.md) | ~5 min |
| `WHATSAPP` | conta Meta Business, número registrado e **template aprovado pela Meta** · [guia](docs/GUIA-WHATSAPP.md) | dias |

O WhatsApp demora porque a aprovação do template é da Meta, não nossa — no desenvolvimento deste
projeto levou dias e duas rejeições. **Comece pelo e-mail.** Trocar de canal depois é uma linha no
`.env`: o motor não sabe que WhatsApp existe.

## Arquitetura

| Componente | Stack | Porta | Papel |
|---|---|---|---|
| `core-java/` | Java 21 + Spring Boot 4.1 | 8081 | API, agendamento, persistência, regras de alerta |
| `worker-python/` | Python 3.14 + FastAPI | 8001 | Busca de preços e linguagem natural |
| `frontend-vue/` | Vue 3 + Vite + TypeScript | 8090 | Painel de monitores, histórico e destinatários |
| PostgreSQL | Docker | 5433 | Histórico de preços |
| RabbitMQ | Docker | 5672 · 15672 | Transporte alternativo entre core e worker |

**O Java é o dono do banco.** O worker Python nunca acessa o PostgreSQL — recebe rota e data,
devolve preço, e não sabe o que é um monitor ou um destinatário. Um teste falha se alguém importar
um driver de banco lá dentro.

Cada serviço é organizado em **BCE por feature** (`entity` / `control` / `boundary`), e as camadas
são verificadas por teste: ArchUnit no Java, AST dos imports no Python, Vitest no frontend. Quebrar
uma camada quebra o build.

## Coleta de preços

Duas camadas que cobrem a fraqueza uma da outra:

1. **Travelpayouts Data API** — varredura ampla. Devolve o preço mais barato de cada dia de um mês
   inteiro numa chamada. Gratuita e estável, mas com dados cacheados.
2. **fast-flights** — confirmação pontual via Google Flights. Traz companhia, escalas e horários.
   Gratuita, e frágil por depender do formato interno do Google.

Nada é alertado sem passar pela camada 2. Se ela cair, o sistema **não alerta com preço não
confirmado** — ele diz que não conseguiu confirmar. Ver
[docs/FRAGILIDADE-CAMADA-2.md](docs/FRAGILIDADE-CAMADA-2.md).

## O princípio que atravessa o projeto

**Dizer o que não se sabe.** Sem histórico suficiente, o sistema se cala em vez de opinar: não há
nota, não há comparação com a mediana, não há "tendência preliminar". `SEM_DADOS` é diferente de
`NORMAL`, e nulo é diferente de zero.

É por isso que a análise usa estatística robusta a cauda longa — mediana e quartis, regra de Tukey
para anomalia, Theil–Sen para tendência — em vez de média e desvio padrão, que preço de passagem
distorce.

## Desenvolvimento

Os containers são o caminho normal. Para mexer no código:

```bash
# Só a infraestrutura
docker compose up -d postgres rabbitmq

# API
cd core-java && ./mvnw spring-boot:run

# Worker
cd worker-python
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/python.exe -m uvicorn app.main:app --reload --port 8001

# Painel (modo de desenvolvimento, na 5173)
cd frontend-vue && npm install && npm run dev
```

### Testes

```bash
cd core-java     && ./mvnw test     # 379
cd worker-python && pytest          # 95
cd frontend-vue  && npm test        # 32
```

A suíte do Java usa o banco `flightmon_test`, separado do de desenvolvimento — rodar os testes não
apaga o que você cadastrou. Nenhum teste manda e-mail, mensagem ou requisição a API paga.

> As portas fogem do padrão de propósito: 5432, 8080 e 5173 costumam estar ocupadas.
> Ver [docs/DECISOES.md](docs/DECISOES.md).

## Documentação

Este projeto documenta o **porquê**, e não só o quê. Se algo parecer uma escolha estranha,
provavelmente há um parágrafo explicando o que foi tentado antes.

| Arquivo | Conteúdo |
|---|---|
| [docs/PLANO-DE-ACAO.md](docs/PLANO-DE-ACAO.md) | Escopo, arquitetura e o roteiro etapa a etapa |
| [docs/PROGRESSO.md](docs/PROGRESSO.md) | Diário de bordo — o que foi feito, como foi testado, e o que deu errado |
| [docs/DECISOES.md](docs/DECISOES.md) | 99 decisões e por que as alternativas foram descartadas |
| [docs/BUGS.md](docs/BUGS.md) | Bugs com diagnóstico e lição, e riscos em observação |
| [docs/FRAGILIDADE-CAMADA-2.md](docs/FRAGILIDADE-CAMADA-2.md) | Por que a camada 2 vai quebrar, e o plano para quando quebrar |
| [docs/GUIA-EMAIL.md](docs/GUIA-EMAIL.md) | Canal de e-mail — o caminho mais rápido para receber alertas |
| [docs/GUIA-WHATSAPP.md](docs/GUIA-WHATSAPP.md) | Credenciais do WhatsApp Cloud API |
| [docs/GUIA-NUMERO-BRASILEIRO.md](docs/GUIA-NUMERO-BRASILEIRO.md) | Preparar e registrar o número que envia |
| [docs/GUIA-WEBHOOK.md](docs/GUIA-WEBHOOK.md) | Receber confirmação de entrega e leitura da Meta |

Tudo em **português do Brasil**, inclusive nomes de classe e comentários.

## Estado

| Fase | Situação |
|---|---|
| **0 — Fundação** · esqueletos e infraestrutura | ✅ |
| **1 — Engine** · encontrar preço, guardar histórico, alertar | ✅ |
| **2 — Inteligência** · estatística de rota, anomalia, tendência, Flight Score | ✅ |
| **3 — Agente** · criar monitores e pedir recomendação em linguagem natural | ✅ |
| **4 — Infraestrutura** · mensageria ✅ · containers ✅ · e-mail ✅ · observabilidade, deploy | 🚧 |

## Aviso

Projeto pessoal, de uso próprio. Ele **avisa** sobre preços — não compra, não reserva e não garante
que a oferta ainda vai existir quando você abrir o site da companhia. Preço de passagem muda em
minutos.

As fontes de preço são gratuitas e não oficiais. Respeite os termos de uso delas, e não aumente a
frequência de varredura achando que vai encontrar mais: vai encontrar bloqueio.
