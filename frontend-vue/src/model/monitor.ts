/** Espelha os DTOs de monitor do core-java. */

export interface RecipientSummary {
  id: number
  name: string
  /** Nulo desde a E4.6: um destinatario pode receber so por e-mail. */
  phoneE164: string | null
  active: boolean
}

export interface Monitor {
  id: number
  label: string | null
  origin: string
  destination: string
  departureWindowStart: string
  departureWindowEnd: string
  returnWindowStart: string | null
  returnWindowEnd: string | null
  minStayDays: number | null
  maxStayDays: number | null
  maxPrice: number
  currency: string
  maxStops: number | null
  passengers: number
  active: boolean
  searchIntervalMinutes: number
  lastSearchedAt: string | null
  nextSearchAt: string | null
  createdAt: string | null
  updatedAt: string | null
  recipients: RecipientSummary[]
}

/**
 * Payload de criacao e atualizacao.
 *
 * Campos opcionais vao como `null` e nao omitidos: o DTO do core aplica os
 * defaults (moeda, passageiros, intervalo) quando recebe nulo.
 */
export interface MonitorRequest {
  label: string | null
  origin: string
  destination: string
  departureWindowStart: string
  departureWindowEnd: string
  returnWindowStart: string | null
  returnWindowEnd: string | null
  minStayDays: number | null
  maxStayDays: number | null
  maxPrice: number | null
  currency: string | null
  maxStops: number | null
  passengers: number | null
  active: boolean
  searchIntervalMinutes: number | null
  recipientIds: number[]
}

/** Resultado de uma varredura disparada pela tela. */
export interface MonitorRunResult {
  busca: {
    monitorId: number
    observacoesGravadas: number
    candidatosAbaixoDoTeto: number
    melhorPreco: number | null
    confirmada: boolean
    camada2Degradada: boolean
    candidatoIlusorio: boolean
    falhou: boolean
    avisos: string[]
  }
  alerta: {
    alertar: boolean
    motivo: string
    detalhe: string
  }
}

export function monitorVazio(): MonitorRequest {
  const hoje = new Date()
  const daquiUmMes = new Date(hoje.getFullYear(), hoje.getMonth() + 1, hoje.getDate())
  const doisMeses = new Date(hoje.getFullYear(), hoje.getMonth() + 2, hoje.getDate())

  return {
    label: '',
    origin: '',
    destination: '',
    departureWindowStart: iso(daquiUmMes),
    departureWindowEnd: iso(doisMeses),
    returnWindowStart: null,
    returnWindowEnd: null,
    minStayDays: null,
    maxStayDays: null,
    maxPrice: null,
    currency: 'BRL',
    maxStops: null,
    passengers: 1,
    active: true,
    searchIntervalMinutes: 360,
    recipientIds: [],
  }
}

/**
 * Converte um monitor no payload de edicao.
 *
 * <b>Normaliza ausente para nulo</b> (`?? null`), e nao copia cru. Foi o
 * BUG-015: a API omitia campos nulos, o valor chegava `undefined`, e toda
 * comparacao com `null` falhava — a tela mostrava "ate undefined escala" e o
 * formulario marcava "janela de volta" num monitor de somente ida, chegando a
 * transformar um no outro ao salvar.
 *
 * A API foi corrigida para mandar nulo explicito. Isto aqui fica assim mesmo:
 * depender de o servidor ser perfeito e a metade fragil de qualquer correcao.
 *
 * `??` e nao `||`: com `||`, `maxStops: 0` — que significa <b>voo direto</b> —
 * viraria nulo, que significa "sem preferencia". Sao coisas opostas.
 */
export function paraRequest(m: Monitor): MonitorRequest {
  return {
    label: m.label ?? null,
    origin: m.origin,
    destination: m.destination,
    departureWindowStart: m.departureWindowStart,
    departureWindowEnd: m.departureWindowEnd,
    returnWindowStart: m.returnWindowStart ?? null,
    returnWindowEnd: m.returnWindowEnd ?? null,
    minStayDays: m.minStayDays ?? null,
    maxStayDays: m.maxStayDays ?? null,
    maxPrice: m.maxPrice,
    currency: m.currency,
    maxStops: m.maxStops ?? null,
    passengers: m.passengers,
    active: m.active,
    searchIntervalMinutes: m.searchIntervalMinutes,
    recipientIds: m.recipients.map((r) => r.id),
  }
}

function iso(d: Date): string {
  return d.toISOString().slice(0, 10)
}
