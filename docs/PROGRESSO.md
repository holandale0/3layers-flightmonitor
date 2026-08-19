
---

### 2026-08-14 — 🐛 [BUG-015](BUGS.md) · O campo que sumia do JSON

Reportado da tela: um monitor CGH → BEL mostrando **"até `undefined` escala"**.

**O relato dizia "o destino apareceu como undefined", e o destino estava certo** — o cabeçalho
mostrava `CGH → BEL`. O `undefined` estava em escalas máximas, uma linha abaixo. Confirmar isso
antes de mexer em qualquer coisa economizou procurar no lugar errado.

**A causa:** `spring.jackson.default-property-inclusion: non_null` fazia a API **omitir** campos
nulos. O tipo do frontend declarava `maxStops: number | null` desde sempre — era a API que
mentia. E em JavaScript campo ausente é `undefined`, com `undefined === null` valendo `false`.

**O sintoma invisível era pior que o visível.** O formulário de edição fazia
`comVolta = form.returnWindowStart !== null`; com `undefined`, isso dava `true`. Abrir para editar
um monitor de somente ida marcava "definir janela de volta", e o `watch` preenchia as datas
sozinho — **salvar converteria o monitor em ida e volta**, sem ninguém pedir. Ninguém tinha
notado porque ninguém tinha editado um monitor de somente ida ainda.

**Corrigido em duas frentes**, e as duas importam: a API passou a mandar `null` explícito
(campo ausente é ambíguo; `null` diz uma coisa só), e o painel passou a normalizar com `?? null`
— porque depender de o servidor ser perfeito é a metade frágil de qualquer correção.

`??` e não `||`: com `||`, `maxStops: 0` — **voo direto** — viraria nulo, que é "sem
preferência". Há teste para isso.

**O guarda foi verificado ao contrário:** voltei a configuração antiga de propósito e os 4 testes
do `ContratoJsonTest` reprovaram, citando o bug. A asserção usa `has(campo)` e não `get(campo)`
— `get` devolveria null nos dois casos e passaria com o bug presente.

**Placar:** core-java **410**, worker-python **104**, frontend-vue **40**.

---

### 2026-08-14 — 📅 Campo de data em dd/mm/aaaa

Pedido do usuário: os campos de data mostravam `mm/dd/yyyy`.

**Verifiquei antes de escolher a solução**, porque a resposta óbvia (`lang="pt-BR"`) já estava no
`index.html` e não funcionava. Montei uma página de teste e fotografei no Chrome em inglês:

| Tentativa | Resultado |
|---|---|
| `lang="pt-BR"` no `<html>` | `mm/dd/yyyy` |
| `lang="pt-BR"` no próprio `<input>` | `mm/dd/yyyy` |
| `showPicker()` disponível? | **sim** |

O formato do `<input type="date">` é do **navegador**, e não da página. Não há atributo que
sobreponha ([D-105](DECISOES.md)). A única saída é um campo próprio — e a terceira linha da
tabela foi o que permitiu não pagar o preço de sempre.

**O calendário não foi perdido.** Trocar por texto puro consertaria o formato quebrando a
usabilidade, principalmente no celular. O botão abre o seletor nativo via `showPicker()`.

#### Um teste que sumiu em silêncio

Ao rodar a suite, o total continuou 53 — e os 11 testes do componente **não apareceram**. O
`vitest.config.ts` não tinha o plugin do Vue: eu o criei assim, com o comentário *"os testes não
precisam do plugin do Vue... o que testamos aqui é lógica pura"*. Era verdade na época e deixou de
ser, e o arquivo `.vue` passou a ser **ignorado na coleta**.

É a pior forma de um teste falhar: parecendo que não existe. Só percebi porque conferi a
aritmética do total — 40 + 13 = 53, e não 64.

#### Um teste meu que estava errado

`data inexistente nao vira valor` esperava que digitar `31/02/2026` **não emitisse nada**. O
código emitia `null`, e o código estava certo: com silêncio, o formulário ficaria com a data
antiga enquanto a tela mostra outra, e salvar guardaria o valor velho achando que mudou. Corrigi a
expectativa, e não o comportamento.

**Placar:** core-java **410**, worker-python **104**, frontend-vue **64**.

---

### 2026-08-14 — 🛡️ Intervalo mínimo de varredura: 10 minutos

Pedido do usuário: impedir frequência de consulta abaixo de 10 minutos. Era 5.

**O motivo não é só educação com as fontes.** Preço de passagem muda em minutos, mas a camada 1
devolve dado **cacheado**, com horas de atraso — varrer de 5 em 5 minutos gasta cota para reler a
mesma resposta. Custo real, benefício imaginário ([D-106](DECISOES.md)).

**Três camadas**, e cada uma faz uma coisa diferente:

| Camada | Papel |
|---|---|
| `min="10"` no formulário | evita a ida ao servidor |
| `@Min(10)` no DTO | devolve mensagem com o campo marcado |
| `CHECK` no banco (V9) | protege contra carga manual, script e bug nosso |

As duas primeiras são conveniência; a terceira é a garantia. Verifiquei as três no sistema
rodando — inclusive tentando o `UPDATE` direto no banco, que foi recusado por
`monitor_intervalo_valido`.

**A regra vale na edição também**, com teste próprio: regra que vale só na criação se contorna
criando com 10 e editando para 1.

**A migration sobe o que estava abaixo.** Testei com um monitor "legado" de 5 minutos inserido
direto no banco: a V9 o levou a 10 e deixou o de 60 intacto. O `UPDATE` vem **antes** do CHECK —
na ordem inversa, a migration quebraria no meio do deploy em qualquer instalação que já tivesse
um monitor abaixo do novo mínimo.

**Uma brecha registrada:** a busca manual continua sem trava. É uma pessoa clicando, uma de cada
vez, e o risco de bloqueio vem da repetição desassistida — mas fica escrito para ser decisão, e
não esquecimento.

**Placar:** core-java **414**, worker-python **104**, frontend-vue **64**.

---

### 2026-08-16 — 🐛 [BUG-016](BUGS.md) · O monitor de só ida comprando passagem de ida e volta

Reportado da tela: um monitor GRU → BEL configurado como **somente ida** mostrando observações com
data de volta.

**O filtro era assimétrico.** Com janela de volta, conferia; sem janela de volta, **não conferia
nada**. "Somente ida" é expresso como *ausência*, e o código leu ausência como "tanto faz".

**O dano não era cosmético.** Contra a API real, GRU → BEL em dezembro: ida e volta R$ 1.674, só
ida **R$ 1.004**. Um monitor de só ida com teto de R$ 1.100 nunca teria alertado — o sistema
estava **cego para as oportunidades que existia para achar**.

#### Uma hipótese errada no meio da investigação

Consultei a API, vi `return_date: None` em todas as entradas, e concluí que a data de volta era
**inventada pelo nosso código**. Estava conferindo a chave errada — o endpoint usa `return_at`.
Com a chave certa, as 5 entradas tinham volta.

Fica registrado porque a hipótese mais dramática ("o código inventa dados") era falsa, e só não
virou uma correção errada porque foi testada antes de virar código.

#### Filtrar não resolveria

O `v1/prices/calendar` **ignora `one_way`** — testei com `true`, com `1` e sem o parâmetro. Nas
três, tudo volta com `return_at`. Descartar as ofertas com volta deixaria o monitor de só ida sem
preço nenhum: dado errado viraria dado nenhum.

A correção é **perguntar certo** ([D-107](DECISOES.md)): `v2/prices/latest?one_way=true` para só
ida. Cobre menos datas (2 contra 5) — e dois preços certos valem mais que cinco de outro produto.

#### Um segundo defeito no mesmo mapeamento

`arrival_at` recebia `return_at`: o horário de **partida da volta** era gravado como **chegada da
ida**. O endpoint não devolve chegada; o campo agora fica nulo, que é a verdade.

#### Os testes existentes codificavam o bug

O helper `pedido()` montava requisição **sem** janela de volta e alimentava com dados de ida e
volta — exercitando o calendário por um caminho de só ida. Oito testes quebraram com a correção,
e estavam certos sobre o comportamento e errados sobre a pergunta. O helper ganhou janela de volta,
e nasceu o `test_so_ida.py` com sete testes novos.

#### Verificado contra a fonte real

```
SO IDA:        ida=2026-12-19 volta=None preco=1004 chegada=None
               ida=2026-12-20 volta=None preco=1064 chegada=None
IDA E VOLTA:   ida=2026-12-09 volta=2026-12-14 preco=1674
```

**⚠️ Fica pendente:** 147 observações de 4 monitores de só ida têm preço de ida e volta gravado.
Misturar dois produtos na mesma série é pior que qualquer um sozinho — a mediana ficaria no meio,
e a primeira observação de só ida seria acusada como queda de 40%. É o [RISCO-009](BUGS.md)
acontecendo. Aguardando decisão sobre apagar.

**Placar:** core-java **414**, worker-python **111**, frontend-vue **64**.

---

### 2026-08-16 — 🧹 Histórico de preços zerado

A pedido do usuário, depois do [BUG-016](BUGS.md): apagar os dados coletados de **todos** os
monitores — inclusive os que estavam corretos — e preservar a configuração.

| Apagado | Preservado |
|---|---|
| `price_observation` (201) | `monitor` (5) |
| `search_run` (153) | `recipient` (3) |
| `alert` (5) | `monitor_recipient` (5) |
| | `monitor_avoided_airline`, `whatsapp_config` |

**O `alert` entrou na limpeza por um motivo que não é óbvio:** o anti-spam lê essa tabela. Manter
os cinco alertas antigos silenciaria por 12 horas o primeiro alerta válido depois do reset — um
sistema "zerado" que não avisa é pior que um sistema com histórico sujo.

Também zerei `monitor.last_searched_at` e adiantei `next_search_at`: sem isso, o painel mostraria
"última busca" apontando para uma varredura que não existe mais.

**Backup antes** (48 KB, `pg_dump --data-only` das três tabelas), porque a operação é
irreversível e custava nada.

**Verificado com dado novo.** A primeira varredura do GRU → BEL, somente ida:

```
ida         volta         preco     chegada
2026-12-19  (sem volta)   1004.00   (sem chegada)
2026-12-20  (sem volta)   1064.00   (sem chegada)
```

Sem alerta, corretamente: R$ 1.004 está acima do teto de R$ 500 configurado. Antes da correção,
este mesmo monitor teria gravado R$ 1.674 com volta em 14/12.

**Continua em aberto:** a estatística é por **rota** ([D-016](DECISOES.md)), e uma rota pode
agora ter observações de só ida e de ida e volta misturadas — a consulta filtra por origem,
destino, período e confirmação, e nada mais. Antes do BUG-016 isso não existia, porque tudo era
ida e volta. Aguardando decisão.

---

### 2026-08-16 — ⏱️ Duração do voo na tabela — e uma companhia que era agência

Pedido do usuário: mostrar a duração total do voo.

**Meio caminho já existia.** A coluna `duration_minutes` está no banco desde a V1, o
`ObservationResponse` já a expõe, e o tipo do frontend já a declara — a camada 2 sempre a
gravou. Só a camada 1 perdia o dado no meio do caminho. **Nenhuma migration foi necessária.**

O endpoint de só ida informa `duration` em minutos; o calendário de ida e volta não informa. Onde
a fonte não diz, o campo fica nulo ([D-109](DECISOES.md)).

**A coluna pagou por si no primeiro dado real:**

```
19/12/2026   R$ 1.383,00   1 escala    7h05
20/12/2026   R$ 1.306,00   2 escalas  16h30
```

Setenta e sete reais separando **sete horas** de viagem — exatamente o que o preço sozinho
escondia.

#### E olhando a tela, achei outra coisa

A coluna **Companhia** mostrava `Kiwi.com` e `Mytrip.com`. **Não são companhias aéreas, são
agências** — e o erro era meu, da correção do [BUG-016](BUGS.md) de ontem: o campo `gate` da
Travelpayouts foi parar em `airline`.

É o pior tipo de dado errado: plausível o bastante para ninguém desconfiar. Quem lesse escolheria
voo pensando em companhia aérea.

Agora fica nulo ([D-108](DECISOES.md)) — este endpoint não informa a companhia, e dizer isso é
melhor que preencher com outra coisa. Os dois endpoints informam metades diferentes:

| | Companhia | Duração |
|---|---|---|
| `v1/prices/calendar` (ida e volta) | ✅ | ❌ |
| `v2/prices/latest` (só ida) | ❌ | ✅ |

**Dois testes existentes quebraram** ao acrescentar o componente no `WorkerFlightOffer` —
construíam o record posicionalmente. Ajustados, e ganharam uma duração plausível em vez de mais
um `null`.

**Placar:** core-java **414**, worker-python **114**, frontend-vue **69**.

---

### 2026-08-16 — 🏪 A agência volta — em campo próprio, e na mesma coluna da tela

O usuário questionou a decisão anterior: *"ao invés de simplesmente deixar de mostrar a agência,
não seria melhor renomear o campo? Se estiver vazio, não haverá referência para eu ir buscar a
passagem e comprar."*

**Estava certo.** Eu tinha corrigido a mentira ("Companhia: Kiwi.com") esvaziando o campo, e com
isso tirei a informação que permite **agir**. Um monitor que encontra a oferta e não diz onde
comprá-la resolve metade do problema.

**Mas renomear a coluna e guardar as duas coisas juntas quebraria uma regra**, e não só a
estética — verifiquei antes de responder: `Preferencias.companhiaEvitada()` compara `airline` com
a lista de companhias que o monitor evita. Com "Kiwi.com" ali, quem pediu *"evitar GOL"* passaria
a comparar GOL com Kiwi.com, e a preferência pararia de funcionar **em silêncio**.

A saída atende os dois lados ([D-110](DECISOES.md)): **separadas no dado, juntas na tela.**

```
Companhia / Agência
Kiwi.com    agência
Mytrip.com  agência
LATAM       companhia
```

Uma coluna — porque quem lê quer responder "onde compro isso?" — com o rótulo dizendo qual das
duas é. O código precisa distinguir; o leitor, não.

**Migration V10**, aditiva. A companhia tem precedência quando as duas existem: quem opera o voo
informa mais que quem intermediou a venda.

**Dois testes posicionais quebraram de novo** ao acrescentar o componente no `WorkerFlightOffer` —
pela segunda vez no mesmo dia. Um record com onze componentes construído por posição é frágil por
natureza; anotado como incomodo, não como bug.

**Placar:** core-java **414**, worker-python **116**, frontend-vue **74**.

---

### 2026-08-18 — ✅ E4.4 concluída · Produção na própria máquina, publicada por túnel

Escolhas do usuário: rodar na **máquina dele**, painel **acessível pela internet com senha**, e
**webhook da Meta configurado junto**.

As três juntas têm uma tensão: máquina doméstica publicada na internet exige URL estável, e IP
residencial muda. Pior: boa parte dos provedores brasileiros usa **CGNAT**, que impede
redirecionamento de porta mesmo com IP fixo.

**Túnel Cloudflare resolve os dois** ([D-111](DECISOES.md)): a conexão sai de dentro para fora,
nada fica aberto no roteador, e o TLS termina na Cloudflare — o webhook exige HTTPS e não há
certificado para emitir aqui dentro. Como o usuário já tem domínio próprio, a URL fica estável,
que é o que a Meta precisa para registrar o webhook uma vez só.

#### O que muda de desenvolvimento para produção

| | Desenvolvimento | Produção |
|---|---|---|
| Postgres e RabbitMQ | portas publicadas | **só na rede interna** |
| Worker | porta publicada | **só na rede interna** |
| Painel | aberto, em `0.0.0.0` | **com senha**, em `127.0.0.1` + túnel |
| Canário | desligado | **ligado** |
| Memória do core | sem limite | 768 MB |

Sobreposição, e não um segundo compose: duplicar o arquivo inteiro garante que as duas cópias
divirjam.

#### Duas coisas que só apareceram rodando

**`ports: []` não remove porta.** O Compose **anexa** listas entre arquivos, então a lista vazia
não apagava nada e o banco continuava publicado. Só percebi conferindo o `config` — o `up` não
reclama. Resolvido com `!reset []`, que existe exatamente para isso.

**A senha quebrou o healthcheck.** O painel virou `unhealthy` com o nginx perfeitamente no ar: o
healthcheck fazia `wget` na raiz, e a raiz passou a exigir credencial. Um healthcheck que exige
credencial mede a credencial, e não a saúde. Nasceu o `/healthz`, sem senha, que responde `ok`
sem tocar no core nem no banco.

#### Verificado com a pilha de produção de verdade

```
core       Up (healthy)     8081/tcp                 <- sem porta no host
postgres   Up (healthy)     5432/tcp                 <- sem porta no host
worker     Up (healthy)     8001/tcp                 <- sem porta no host
painel     Up (healthy)     127.0.0.1:8090->80/tcp   <- so no laco local

/            -> 401      /healthz -> 200
/api/monitors-> 401      / com senha -> 200
```

O túnel foi testado com token falso e reiniciou em laço, como esperado — o que confirma que ele
**depende** do token e não sobe silenciosamente sem publicar nada.

#### O que fica dito, e não escondido

O sistema só varre enquanto a máquina estiver ligada. Um monitor de 6 em 6 horas perde a
madrugada com o PC desligado — e ele existe justamente para vigiar quando ninguém está olhando.
O histórico não se perde: o próximo horário fica no banco, e ao religar o scheduler encontra os
vencidos. Migrar para uma VPS depois é o mesmo compose e um `.env` diferente.

**Falta você:** criar o túnel na Cloudflare e pôr duas linhas no `.env`. Passo a passo em
[GUIA-DEPLOY.md](GUIA-DEPLOY.md).

**Placar:** core-java **414**, worker-python **116**, frontend-vue **74**.
