
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
