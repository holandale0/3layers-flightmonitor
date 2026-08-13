# Diagramas

Quatro diagramas do sistema, gerados com [archify](https://github.com/tt-a1i/archify).

| Diagrama | Tipo | Responde |
|---|---|---|
| [arquitetura](arquitetura.html) | `architecture` | Quais serviços existem e quem fala com quem |
| [coleta](coleta.html) | `sequence` | Como um preço vira alerta, da varredura à entrega |
| [alerta](alerta.html) | `lifecycle` | O que o sistema sabe sobre a entrega, e quando |
| [analise](analise.html) | `dataflow` | Como o histórico vira nota, anomalia e tendência |

Cada um existe em dois formatos:

- **`.html`** — o artefato bom. Interativo: rastreia rotas, tem visões guiadas com
  explicação, tema claro/escuro e exportação. **Abra este.** O GitHub não o renderiza:
  baixe o arquivo, ou clone o repositório e abra no navegador.
- **`.svg`** — o retrato estático, que é o que aparece no [README](../README.md). O GitHub
  renderiza SVG, e é assim que o projeto tem figura na página inicial.

## Regerar

Os `.json` são a fonte; o resto é derivado.

```bash
# 1. o archify (é um skill de agente; aqui usamos só a CLI)
git clone --depth 1 https://github.com/tt-a1i/archify.git /tmp/archify

# 2. valide e entregue cada diagrama
cd /tmp/archify/archify
node bin/archify.mjs validate architecture <repo>/diagrams/arquitetura.architecture.json --quality showcase --json
node bin/archify.mjs deliver  architecture <repo>/diagrams/arquitetura.architecture.json <repo>/diagrams/arquitetura.html --quality showcase

# 3. extraia os SVG estáticos para o README
cd <repo> && python scripts/extrai_svg_dos_diagramas.py
```

O perfil é **`showcase`**, que exige `ok: true` com **9 verificações** e zero avisos. Ele é
exigente de propósito: reprova rótulo em cima de rota, rota atravessando nó, segmento curto
demais e corredor ambíguo. Nenhum dos quatro passou de primeira.

## O que a validação ensinou

**Os diagnósticos apontam o sintoma, e às vezes a causa é outra.** O ciclo de vida do alerta
gerou **mais de 80 erros** — todos consequência de **um** detalhe: faltava uma lane com o id
`main`, que é o trilho de fases. Sem ela, todos os estados colapsaram na mesma faixa e passaram
a colidir entre si. Corrigido o id, sobraram 9.

**Rótulo é o que mais colide.** O próprio guia do archify pede rótulos esparsos, e ele tem
razão: metade dos erros sumiu ao remover rótulos que só repetiam o nome do nó de destino
(`delivered` numa seta que chega em `DELIVERED`).

**Quando duas correções de geometria seguidas falham, o problema é estrutural.** No fluxo de
dados, `channelX` não resolveu um corredor disputado nas duas tentativas. A saída foi mudar o
desenho: `SEM_DADOS` deixou de ser um nó de destino e virou o que a decisão **recebe** quando
não há base — o que, além de validar, é mais fiel ao que o código faz.

## Por que o SVG não é só um recorte do HTML

O `extrai_svg_dos_diagramas.py` resolve três coisas que um recorte ingênuo não resolveria:

1. **O CSS mora na página**, não no SVG — e vai embutido, podado para o que as classes
   presentes alcançam (de ~177 KB para o necessário).
2. **`data-detail-anchor` é atributo sem valor:** válido em HTML, **inválido em XML**. O GitHub
   renderiza SVG com parser XML, então sem normalizar isso a imagem não aparece — e o sintoma é
   um espaço em branco, sem erro.
3. **O SVG não tem fundo:** quem pinta é o `<body>`. Sem um retângulo próprio, o diagrama sai
   transparente e fica ilegível em metade dos temas.
