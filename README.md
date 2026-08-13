# Flight Monitor

Monitor silencioso de preços de passagens aéreas. Roda em background, varre janelas de
datas configuradas e envia um alerta no WhatsApp quando encontra uma oferta dentro dos
critérios. Só incomoda quando há oportunidade real.

```
GRU → LIS
Ida:    entre 10/03/2027 e 20/03/2027
Volta:  permanência de 10 a 15 dias
Preço máximo: R$ 3.200
Escalas máximas: 1
```

## Arquitetura

| Componente | Stack | Porta | Papel |
|---|---|---|---|
| `core-java/` | Java 21 + Spring Boot 4.1 | 8081 | API, agendamento, persistência, regras de alerta |
| `worker-python/` | Python 3.14 + FastAPI | 8001 | Busca de preços e análise |
| `frontend-vue/` | Vue 3 + Vite + TypeScript | 5173 | Painel de monitores e histórico |
| PostgreSQL | Docker | 5433 | Histórico de preços |

O Java é o dono do banco — o worker Python nunca acessa o PostgreSQL diretamente. Ele
recebe rota e data, devolve preço, e não sabe nada sobre monitores ou destinatários.

## Coleta de preços

Duas camadas que cobrem a fraqueza uma da outra:

1. **Travelpayouts Data API** — varredura ampla. Devolve o preço mais barato de cada dia
   de um mês inteiro em uma chamada. Gratuita, estável, porém com dados cacheados.
2. **fast-flights** — confirmação pontual via Google Flights. Traz companhia, escalas e
   horários. Gratuita, mas frágil por depender do formato interno do Google.

Se a camada 2 cair, o sistema continua monitorando e alertando, apenas sem detalhes de voo.

## Como rodar

Pré-requisitos: Java 21, Maven, Python 3.12+, Node 20+, Docker.

```bash
# 1. Credenciais
cp .env.example .env        # ajuste a senha do banco

# 2. Banco
docker compose up -d

# 3. API
cd core-java && mvn spring-boot:run

# 4. Worker
cd worker-python
python -m venv .venv
.venv/Scripts/python.exe -m pip install -r requirements-dev.txt
.venv/Scripts/python.exe -m uvicorn app.main:app --reload --port 8001

# 5. Painel
cd frontend-vue && npm install && npm run dev
```

Painel em http://localhost:5173 · API em http://localhost:8081/actuator/health

> As portas fogem do padrão de propósito: 5432 e 8080 costumam estar ocupadas.
> Ver [docs/DECISOES.md](docs/DECISOES.md).

## Documentação

| Arquivo | Conteúdo |
|---|---|
| [docs/PLANO-DE-ACAO.md](docs/PLANO-DE-ACAO.md) | Escopo, arquitetura e roteiro de 32 etapas em 4 fases |
| [docs/PROGRESSO.md](docs/PROGRESSO.md) | Diário de bordo — o que foi feito e como foi testado |
| [docs/DECISOES.md](docs/DECISOES.md) | Decisões de arquitetura e por que alternativas foram descartadas |
| [docs/BUGS.md](docs/BUGS.md) | Bugs encontrados e riscos em observação |
| [docs/FRAGILIDADE-CAMADA-2.md](docs/FRAGILIDADE-CAMADA-2.md) | Por que a camada 2 vai quebrar e o que fazer quando quebrar |
| [docs/GUIA-WHATSAPP.md](docs/GUIA-WHATSAPP.md) | Passo a passo para obter as credenciais do WhatsApp Cloud API |
| [docs/GUIA-NUMERO-BRASILEIRO.md](docs/GUIA-NUMERO-BRASILEIRO.md) | Como preparar e registrar o número que envia os alertas |

## Roadmap

- **Fase 0 — Fundação** · esqueletos e infraestrutura ✅
- **Fase 1 — Engine** · encontrar preço, guardar histórico, alertar no WhatsApp
- **Fase 2 — Inteligência** · médias, anomalias, tendências e Flight Score
- **Fase 3 — Agente** · criar monitores por linguagem natural
- **Fase 4 — Infraestrutura** · RabbitMQ, containers, observabilidade, deploy
