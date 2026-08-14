
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
