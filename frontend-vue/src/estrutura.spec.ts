import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath } from 'node:url'

import { describe, expect, it } from 'vitest'

/**
 * As camadas do frontend, verificadas como teste.
 *
 * Espelha o `ArquiteturaTest` (core-java) e o `test_arquitetura.py` (worker).
 * A pasta sozinha e so convencao — e convencao que ninguem verifica dura ate a
 * primeira pressa.
 *
 * # As camadas aqui
 *
 *     api/          o acesso a rede: `http.ts` e o transporte, um modulo por recurso
 *     model/        o modelo de dominio e suas funcoes puras
 *     lib/          apoio puro (formatacao) — sem Vue, sem rede
 *     composables/  estado reativo compartilhado — pode usar Vue
 *     components/   pedacos de tela reutilizaveis
 *     views/        as telas que o router monta
 *     router/       o mapa de rotas
 *
 * # Por que nao ha Pinia
 *
 * Nao ha estado global compartilhado entre telas: cada tela carrega o que
 * precisa. Uma store aqui seria cerimonia sem beneficio. Se a Fase 5 trouxer
 * algo realmente compartilhado (o usuario, uma preferencia de exibicao), entra
 * `stores/` — e este comentario e que fica errado, o que e facil de perceber.
 */

const SRC = dirname(fileURLToPath(import.meta.url))

function arquivos(pasta: string, extensoes = ['.ts', '.vue']): string[] {
  const raiz = join(SRC, pasta)
  const achados: string[] = []

  const andar = (dir: string) => {
    for (const nome of readdirSync(dir)) {
      const caminho = join(dir, nome)
      if (statSync(caminho).isDirectory()) {
        andar(caminho)
      } else if (extensoes.some((e) => nome.endsWith(e)) && !nome.endsWith('.spec.ts')) {
        achados.push(caminho)
      }
    }
  }

  andar(raiz)
  return achados
}

/** Os modulos `@/...` que este arquivo importa. */
function importa(caminho: string): string[] {
  const texto = readFileSync(caminho, 'utf-8')
  return [...texto.matchAll(/from '([^']+)'/g)].map((m) => m[1])
}

function violacoes(pasta: string, proibido: (modulo: string) => boolean): string[] {
  return arquivos(pasta)
    .flatMap((caminho) =>
      importa(caminho)
        .filter(proibido)
        .map((mod) => `${relative(SRC, caminho).replace(/\\/g, '/')} importa ${mod}`),
    )
    .sort()
}

describe('estrutura do frontend', () => {
  it('lib e puro: nao conhece Vue nem a rede', () => {
    // E o que permite testar formatacao sem montar componente — e o motivo de
    // `formato.spec.ts` rodar em milissegundos, sem jsdom.
    expect(violacoes('lib', (m) => m === 'vue' || m.startsWith('@/api'))).toEqual([])
  })

  it('o modelo nao sabe como os dados chegam', () => {
    // `menorPrecoPorData` e `paraRequest` sao regra sobre a forma dos dados. Se
    // o modelo importasse `api/`, trocar endpoint passaria a mexer na regra.
    expect(violacoes('model', (m) => m.startsWith('@/api') || m === 'vue')).toEqual([])
  })

  it('so o transporte fala com a rede', () => {
    // `fetch` em um componente e o atalho que espalha tratamento de erro e faz
    // uma tela mostrar "deu erro" enquanto outra mostra o texto do ProblemDetail.
    const comFetch = ['components', 'views', 'composables', 'lib', 'model']
      .flatMap((pasta) => arquivos(pasta))
      .filter((caminho) => /\bfetch\s*\(/.test(readFileSync(caminho, 'utf-8')))
      .map((caminho) => relative(SRC, caminho).replace(/\\/g, '/'))

    expect(comFetch).toEqual([])
  })

  it('a tela nao monta URL da API a mao', () => {
    // O caminho do recurso pertence ao modulo de API. Espalhado pelas telas,
    // renomear um endpoint vira busca textual pelo projeto inteiro.
    const comUrl = ['components', 'views']
      .flatMap((pasta) => arquivos(pasta))
      .filter((caminho) => /'\/(?:api|actuator)\//.test(readFileSync(caminho, 'utf-8')))
      .map((caminho) => relative(SRC, caminho).replace(/\\/g, '/'))

    expect(comUrl).toEqual([])
  })

  it('componente nao importa tela', () => {
    // Uma tela usa componentes; o contrario faria o componente so servir
    // naquela tela — e a dependencia daria a volta.
    expect(violacoes('components', (m) => m.startsWith('@/views'))).toEqual([])
  })

  it('cada modulo de api trata de um recurso so', () => {
    // `monitores.ts` ja tinha juntado monitores, destinatarios E observacoes.
    // Um arquivo por recurso mantem o proximo endpoint barato de achar.
    //
    // A primeira versao deste teste comparava a lista de arquivos com uma lista
    // fixa — e reprovava em toda adicao legitima, sem detectar defeito nenhum.
    // Era tarefa, e nao regra. Agora ele olha os CAMINHOS que cada modulo usa:
    // dois recursos diferentes no mesmo arquivo e exatamente o defeito original.
    const porModulo = readdirSync(join(SRC, 'api'))
      .filter((n) => n.endsWith('.ts') && !n.endsWith('.spec.ts') && n !== 'http.ts')
      .map((nome) => {
        // Sem os comentarios: a documentacao destes modulos cita caminhos
        // ("fica fora de /api de proposito") e eles nao sao chamadas. A primeira
        // versao reprovou o `saude.ts` por causa da propria explicacao dele.
        const texto = readFileSync(join(SRC, 'api', nome), 'utf-8')
          .replace(/\/\*[\s\S]*?\*\//g, '')
          .replace(/\/\/.*$/gm, '')

        const raizes = new Set(
          [...texto.matchAll(/['"`]\/([a-z-]+)/g)].map((m) => m[1]),
        )
        return { nome, raizes: [...raizes].sort() }
      })
      .filter((m) => m.raizes.length > 1)

    expect(porModulo).toEqual([])
  })
})
