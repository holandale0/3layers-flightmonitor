
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
