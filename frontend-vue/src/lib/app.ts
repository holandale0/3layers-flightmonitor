/**
 * Ponte com o aplicativo Android (mobile-android/), quando o painel roda
 * dentro dele — D-113.
 *
 * O painel e o MESMO no navegador e no app. Esta e a unica parte que sabe da
 * diferenca, e ela so existe para o que o navegador nao responde: o token de
 * push do Firebase e o nome do aparelho. No navegador, `emAplicativo()` e
 * falso e nada aqui e chamado.
 *
 * O contrato espelha PonteDoApp.kt.
 */

interface PonteNativa {
  versao(): string
  pushDisponivel(): boolean
  nomeDoAparelho(): string
  ativarPush(destinatario: number): void
}

interface RespostaDoPush {
  token?: string
  erro?: string
}

declare global {
  interface Window {
    FlightMonitorApp?: PonteNativa
  }
}

const EVENTO = 'flightmonitor:push'

/**
 * O app demora o que a pessoa demorar no dialogo de permissao do Android, e
 * depois o Firebase. Sem limite, um app que nunca respondesse deixaria o botao
 * girando para sempre.
 */
const ESPERA_MAXIMA_MS = 120_000

function ponte(): PonteNativa | undefined {
  return typeof window === 'undefined' ? undefined : window.FlightMonitorApp
}

export function emAplicativo(): boolean {
  return ponte() !== undefined
}

/** O app foi gerado com o Firebase? Sem ele, nao ha push para oferecer. */
export function pushDisponivel(): boolean {
  try {
    return ponte()?.pushDisponivel() ?? false
  } catch {
    return false
  }
}

export function nomeDoAparelho(): string {
  return ponte()?.nomeDoAparelho() ?? 'Aparelho'
}

/**
 * Pede ao app a permissao de notificacao e o token do Firebase.
 *
 * A resposta nao volta da chamada: o app a entrega depois, como evento no
 * `window`, porque no meio ha um dialogo do sistema. Daqui para fora, e uma
 * Promise comum.
 */
export function obterTokenDePush(destinatario: number): Promise<string> {
  const nativa = ponte()
  if (!nativa) {
    return Promise.reject(new Error('Disponível só no aplicativo Android'))
  }

  return new Promise<string>((resolve, reject) => {
    const limite = window.setTimeout(() => {
      window.removeEventListener(EVENTO, ouvir)
      reject(new Error('O aplicativo não respondeu. Tente de novo.'))
    }, ESPERA_MAXIMA_MS)

    function ouvir(evento: Event) {
      window.clearTimeout(limite)
      window.removeEventListener(EVENTO, ouvir)
      const resposta = (evento as CustomEvent<RespostaDoPush>).detail ?? {}
      if (resposta.token) {
        resolve(resposta.token)
      } else {
        reject(new Error(resposta.erro ?? 'O aplicativo não devolveu o token.'))
      }
    }

    window.addEventListener(EVENTO, ouvir)
    nativa.ativarPush(destinatario)
  })
}
