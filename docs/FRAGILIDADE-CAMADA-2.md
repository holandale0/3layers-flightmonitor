# A camada 2 vai quebrar

> Documento dedicado ao componente mais frágil do sistema. Escrito para o dia em
> que o `fast-flights` parar de funcionar — porque ele vai parar.

**Última atualização:** 2026-08-09 (etapa E1.6)

---

## 1. Por que é frágil

A camada 2 confirma preços consultando o Google Flights através da biblioteca
`fast-flights`. **Não existe contrato nessa relação.**

A biblioteca monta um protobuf codificado em base64 na URL do Google Flights e depois faz
parsing do HTML da resposta. Nada disso é API pública. O Google pode mudar o formato a
qualquer momento, sem aviso, sem versionamento e sem período de depreciação. Não há SLA, não
há changelog, não há para quem reclamar.

O próprio README da biblioteca resume a situação no subtítulo: *"Fast, robust Google Flights
scraper (API) for Python. **(Probably)**"*.

### A instabilidade já se manifestou três vezes, antes mesmo de entrarmos em produção

| # | O que aconteceu | Como descobrimos |
|---|---|---|
| 1 | **A API da biblioteca mudou por completo** entre 2.x e 3.x. `FlightData` e `Result`, presentes em praticamente todo tutorial na internet, deixaram de existir | Inspecionando a biblioteca instalada, em vez de seguir documentação |
| 2 | **A anotação de tipo mente.** `airlines` é declarado `list[Airline]`, mas em execução devolve `list[str]` | Campo chegou nulo no primeiro teste ao vivo |
| 3 | **O parser falha parcialmente e em silêncio.** Devolveu `time=[None, 45]` — minuto presente, hora ausente | Campo `departure_at` chegou nulo com o resto correto |

Nenhuma dessas três foi encontrada em documentação. Todas apareceram ao rodar contra dado
real. É exatamente esse o padrão que se deve esperar daqui em diante.

---

## 2. O que perdemos se ela cair

**Pouco. E é assim de propósito.**

| Continua funcionando | Deixa de funcionar |
|---|---|
| Varredura periódica de preços | Companhia aérea no alerta |
| Histórico acumulado da rota | Número de escalas confirmado |
| Estatísticas da Fase 2 | Horários reais de voo |
| Alertas no WhatsApp | Aeroporto exato (fica o da cidade) |
| Detecção de queda de preço | Filtro de falso-positivo de cache |

O prejuízo real é o último item: sem confirmação, voltamos a confiar no preço cacheado da
camada 1. Isso **não é hipotético** — o primeiro teste ao vivo mostrou uma divergência de
**61%**:

```
candidato da camada 1 (Travelpayouts, cacheado):  R$ 3.375
preço real (Google Flights, ao vivo):             R$ 5.438
```

Sem a camada 2, o sistema teria alertado sobre uma passagem 61% mais barata do que a real.
Por isso ela existe. E por isso a queda dela precisa ser **visível**, não silenciosa.

---

## 3. As cinco camadas de proteção

### 3.1 Toda exceção vira `ProviderError`
O adaptador captura `Exception` de forma deliberadamente ampla. Não é preguiça: o modo de
falha mais provável é uma mudança no HTML do Google, que se manifesta como `AttributeError`,
`IndexError` ou `KeyError` vindos das entranhas do parser da biblioteca — exceções que não
temos como enumerar. Deixar qualquer uma escapar derrubaria a varredura inteira por causa de
uma camada opcional.

### 3.2 Import tardio
A biblioteca é importada dentro do método, não no topo do módulo. Se ela quebrar na própria
importação — por incompatibilidade de versão, por exemplo — a falha fica contida na camada 2
em vez de impedir o worker de subir.

### 3.3 A cadeia com fallback
`ConfirmationChain` tenta os providers em ordem. Se um falha, tenta o próximo. Se **todos**
falham, devolve resultado **degradado** em vez de erro.

```
confirmed=True                    -> há voo real, use estes dados
confirmed=False, degraded=False   -> consultamos e NÃO existe voo assim;
                                     o candidato da camada 1 era ilusório
confirmed=False, degraded=True    -> nenhuma fonte respondeu; não sabemos.
                                     O sistema segue vivo, só cego nesta camada
```

Distinguir os três desfechos é o coração do desenho. "Não existe esse voo" e "não consegui
verificar" levam a decisões opostas no core-java.

### 3.4 Chave de desligamento
`FASTFLIGHTS_ENABLED=false` no `.env` desliga a camada 2 inteira. **Quando quebrar em
produção num domingo, é uma variável de ambiente — não um deploy.**

Verificado: com a chave desligada, `/search/confirm` devolve HTTP 200 com `degraded: true`,
e não erro.

### 3.5 A degradação é observável
- `/health` reporta a prontidão de cada camada separadamente
- Cada tentativa vira um `ProviderAttempt` na resposta, com duração e motivo da falha
- Confirmação **parcial** também gera aviso: se a fonte responder mas não trouxer companhia
  ou horário, isso aparece em `warnings`

Esse último ponto importa mais do que parece. A biblioteca não vai necessariamente morrer de
uma vez — ela vai **degradar aos poucos**, perdendo um campo aqui, outro ali. Sem o aviso, os
campos chegariam nulos ao banco e ninguém perceberia até o histórico estar contaminado.

---

## 4. Plano de contingência

Quando a camada 2 quebrar, nesta ordem:

### Passo 1 — Estancar (minutos)
```bash
# no .env
FASTFLIGHTS_ENABLED=false
```
O sistema volta a operar degradado. Alertas continuam saindo, sem detalhe de voo.

### Passo 2 — Diagnosticar (uma hora)
```bash
cd worker-python
.venv/Scripts/python.exe -m pytest tests/test_confirmacao.py -v
```
Os testes usam dublês e continuarão passando — eles provam que a **cadeia** está sã. Para
saber se a **biblioteca** quebrou, é preciso rodar contra a rede:

```bash
curl -X POST http://127.0.0.1:8001/search/confirm \
  -H "Content-Type: application/json" \
  -d '{"origin":"GRU","destination":"LIS","departure_date":"AAAA-MM-DD"}'
```

Olhe o campo `attempts[].error`. Ele diz o tipo da exceção e a mensagem.

### Passo 3 — Tentar atualizar (uma hora)
```bash
.venv/Scripts/python.exe -m pip install --upgrade fast-flights
```
Se a comunidade já corrigiu, resolve. **Atenção:** a API da biblioteca já mudou por completo
entre versões maiores. Se subir de 3.x para 4.x, reinspecione a estrutura antes de confiar —
o adaptador foi escrito por inspeção, não por documentação.

### Passo 4 — Trocar de provider (um dia)
Aqui o Strategy paga a dívida. Implementar uma classe com dois membros:

```python
class NovoProvider:
    name = "serpapi"

    async def confirm(self, req: ConfirmRequest) -> ConfirmedOffer | None:
        ...
```

E registrar em `app/providers/factory.py`. **Nada mais muda** — nem endpoint, nem schema, nem
a cadeia, nem o core-java. É conformidade estrutural via `Protocol`: o provider novo não
precisa herdar de nada.

Alternativas já mapeadas, em ordem de preferência:

| Alternativa | Custo | Observação |
|---|---|---|
| **SerpApi** | 250 buscas/mês grátis, depois US$ 25/mês | Dados estruturados e estáveis do Google Flights. Como a camada 2 só roda quando há candidato, 250/mês pode bastar |
| **Playwright** | Grátis, alto custo de manutenção | Plano C desde a [D-008](DECISOES.md). Lento e frágil, mas sob nosso controle |
| **Duffel** | Pay-as-you-go | Conteúdo real de companhia, mas exige aprovação comercial |

A cadeia aceita vários simultâneos. O desenho previsto para o futuro é
`[fast-flights, serpapi]`: o gratuito primeiro, o pago só quando o gratuito falhar —
mantendo o custo perto de zero no caso normal.

---

## 5. O que NÃO fizemos, e por quê

**Não colocamos retry automático dentro do provider.** Se o Google mudou o formato, tentar
de novo não ajuda — só gasta tempo e aumenta o risco de bloqueio por excesso de requisições.
Retry faz sentido para falha transitória de rede, não para quebra de contrato. A cadeia já
cobre o caso de "esta fonte não serve agora" indo para a próxima.

**Não cacheamos a resposta da camada 2.** O propósito dela é justamente ser o dado ao vivo,
em contraste com o cache da camada 1. Cachear anularia a razão de existir.

**Não fizemos a camada 2 obrigatória para alertar.** Seria a decisão mais "segura" à primeira
vista, mas transformaria um componente frágil em ponto único de falha de todo o sistema. Um
alerta sem detalhe de voo é muito melhor que alerta nenhum.

---

## 6. Referências no código

| Arquivo | Papel |
|---|---|
| `worker-python/app/providers/base.py` | Os dois `Protocol` e por que são dois, não um |
| `worker-python/app/providers/fastflights.py` | Adaptador, com a fragilidade documentada linha a linha |
| `worker-python/app/providers/chain.py` | Fallback e degradação |
| `worker-python/app/providers/factory.py` | Registro dos providers e chave de desligamento |
| `worker-python/tests/test_confirmacao.py` | 21 testes, a maioria de caminho de falha |
