/**
 * Formatacao para leitura humana — pt-BR.
 *
 * # Por que isto existe
 *
 * Antes desta reorganizacao, `dinheiro`, `data` e `instante` estavam escritas
 * TRES vezes: na MonitoresView, na HistoricoView e no GraficoPrecos. E as tres
 * copias ja tinham divergido:
 *
 *   - uma devolvia `—` para nulo, outra quebrava;
 *   - o grafico arredondava (`maximumFractionDigits: 0`), as telas nao.
 *
 * O resultado era o mesmo preco aparecendo como "R$ 3.720,00" numa tela e
 * "R$ 3.720" na outra. As diferencas que sao intencionais viraram parametro
 * aqui; as que eram acidente sumiram.
 *
 * Sao funcoes puras, sem Vue: da para testar sem montar componente.
 */

const LOCAL = 'pt-BR'

/**
 * Um valor em dinheiro.
 *
 * Nulo vira travessao, e nao "R$ 0,00": ainda nao ter preco e diferente de o
 * preco ser zero. E a mesma regra do backend — dizer o que nao se sabe.
 *
 * @param moeda so BRL ganha simbolo e separadores locais; qualquer outra sai
 *        como "USD 320", porque formatar dolar com virgula decimal enganaria
 * @param inteiro arredonda para o real cheio — usado no grafico, onde os
 *        centavos so poluiriam o rotulo
 */
export function dinheiro(
  valor: number | null | undefined,
  moeda = 'BRL',
  inteiro = false,
): string {
  if (valor === null || valor === undefined) {
    return '—'
  }
  if (moeda !== 'BRL') {
    return `${moeda} ${inteiro ? Math.round(valor) : valor}`
  }
  return valor.toLocaleString(LOCAL, {
    style: 'currency',
    currency: 'BRL',
    ...(inteiro ? { maximumFractionDigits: 0 } : {}),
  })
}

/**
 * Uma data ISO (`2026-03-15`) em dd/mm/aaaa.
 *
 * O `T00:00:00` no fim nao e decoracao: `new Date('2026-03-15')` e lido como
 * UTC pelo JavaScript, e no fuso do Brasil isso volta um dia — a data de
 * partida apareceria como 14/03. Com a hora explicita, a data e lida como
 * local, que e o que o usuario quis dizer ao escolher no calendario.
 */
export function data(iso: string | null | undefined): string {
  return iso ? new Date(`${iso}T00:00:00`).toLocaleDateString(LOCAL) : '—'
}

/** A mesma data em dd/mm, para o eixo do grafico, onde o ano nao cabe. */
export function dataCurta(iso: string): string {
  const [, mes, dia] = iso.split('-')
  return `${dia}/${mes}`
}

/** Um instante ISO com fuso (`observedAt`, `lastSearchedAt`) em data e hora. */
export function instante(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString(LOCAL) : '—'
}
