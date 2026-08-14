/**
 * Conversão entre a data que a pessoa digita e a que a API entende.
 *
 * # Por que isto existe
 *
 * O `<input type="date">` nativo mostra a data no formato do **navegador**, e
 * não no da página. Foi verificado: nem `lang="pt-BR"` no `<html>`, nem no
 * próprio campo, mudam o `mm/dd/yyyy` do Chrome em inglês. O valor no DOM é
 * sempre ISO — quem escolhe a máscara é o navegador, e não há atributo que
 * sobreponha isso.
 *
 * Para um sistema em português, com datas de viagem, `03/05` significar março
 * ou maio é a diferença entre viajar no outono e no inverno. Daí um campo
 * próprio: **dd/mm/aaaa por fora, ISO por dentro**.
 *
 * As funções aqui são puras, e é onde mora a parte que erra: a conversão.
 */

const ISO = /^(\d{4})-(\d{2})-(\d{2})$/
const BR = /^(\d{2})\/(\d{2})\/(\d{4})$/

/** `2026-03-15` → `15/03/2026`. Entrada inválida vira string vazia. */
export function isoParaBr(iso: string | null | undefined): string {
  if (!iso) return ''
  const m = ISO.exec(iso)
  if (!m) return ''
  const [, ano, mes, dia] = m
  return `${dia}/${mes}/${ano}`
}

/**
 * `15/03/2026` → `2026-03-15`. Devolve `null` quando não dá para converter.
 *
 * **Rejeita data que não existe.** `31/02/2026` tem o formato certo e o dia
 * errado; sem esta checagem, o `Date` do JavaScript "corrigiria" para 03/03 em
 * silêncio — e a pessoa procuraria voo num dia que não pediu.
 */
export function brParaIso(texto: string | null | undefined): string | null {
  if (!texto) return null
  const m = BR.exec(texto.trim())
  if (!m) return null

  const [, dia, mes, ano] = m
  const d = Number(dia)
  const mm = Number(mes)
  const aaaa = Number(ano)

  if (mm < 1 || mm > 12 || d < 1) return null
  if (d > diasNoMes(mm, aaaa)) return null

  return `${ano}-${mes}-${dia}`
}

/**
 * Insere as barras enquanto se digita, e descarta o que não for dígito.
 *
 * Sem isto, a pessoa teria que digitar as barras — e digitar `1532026` é bem
 * mais rápido do que caçar a tecla de barra no teclado do celular.
 */
export function mascarar(texto: string): string {
  const digitos = texto.replace(/\D/g, '').slice(0, 8)

  if (digitos.length <= 2) return digitos
  if (digitos.length <= 4) return `${digitos.slice(0, 2)}/${digitos.slice(2)}`
  return `${digitos.slice(0, 2)}/${digitos.slice(2, 4)}/${digitos.slice(4)}`
}

/** Está completo o bastante para tentar converter? */
export function completa(texto: string): boolean {
  return BR.test(texto.trim())
}

function diasNoMes(mes: number, ano: number): number {
  // Fevereiro depende do ano; o resto é tabela. A regra de bissexto completa
  // (século que não é múltiplo de 400) importa: 2100 não é bissexto, e uma
  // janela de datas pode alcançar anos assim.
  if (mes === 2) {
    const bissexto = (ano % 4 === 0 && ano % 100 !== 0) || ano % 400 === 0
    return bissexto ? 29 : 28
  }
  return [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31][mes - 1]
}
