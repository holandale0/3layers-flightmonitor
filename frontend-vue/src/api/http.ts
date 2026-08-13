/**
 * Cliente HTTP do core-java.
 *
 * Em desenvolvimento, `/api` e `/actuator` sao redirecionados pelo proxy do
 * Vite para http://localhost:8081, sem reescrita de caminho — por isso nao ha
 * necessidade de CORS no Spring Boot.
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api'

/** Erros de validacao por campo, como a API devolve em RFC 7807. */
export type ErrosPorCampo = Record<string, string>

export class ApiError extends Error {
  readonly status?: number
  /** Preenchido quando a API devolve 400 com detalhe por campo. */
  readonly errors?: ErrosPorCampo

  constructor(message: string, status?: number, errors?: ErrosPorCampo) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errors = errors
  }
}

/** Resposta de erro no padrao RFC 7807 devolvida pelo core-java. */
interface ProblemDetail {
  title?: string
  detail?: string
  status?: number
  errors?: ErrosPorCampo
}

async function pedir<T>(url: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(url, {
      ...init,
      headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
    })
  } catch {
    // Falha de rede: a API provavelmente nao esta no ar.
    throw new ApiError('Nao foi possivel conectar a API')
  }

  if (!response.ok) {
    throw await problemaDeErro(response)
  }

  // 204 No Content nao tem corpo para desserializar.
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/**
 * Traduz a resposta de erro da API.
 *
 * O `ApiExceptionHandler` do core devolve ProblemDetail com o mapa `errors`
 * quando a validacao falha. Aproveitar isso e o que permite marcar o campo
 * exato no formulario, em vez de mostrar "deu erro".
 */
async function problemaDeErro(response: Response): Promise<ApiError> {
  let problema: ProblemDetail = {}
  try {
    problema = (await response.json()) as ProblemDetail
  } catch {
    // Resposta sem corpo JSON: fica so o status.
  }

  const mensagem =
    problema.detail ?? problema.title ?? `A API respondeu ${response.status}`

  return new ApiError(mensagem, response.status, problema.errors)
}

/** Recursos de negocio, sob /api. */
export function getJson<T>(path: string): Promise<T> {
  return pedir<T>(`${BASE_URL}${path}`)
}

export function postJson<T>(path: string, corpo: unknown): Promise<T> {
  return pedir<T>(`${BASE_URL}${path}`, {
    method: 'POST',
    body: JSON.stringify(corpo),
  })
}

export function putJson<T>(path: string, corpo: unknown): Promise<T> {
  return pedir<T>(`${BASE_URL}${path}`, {
    method: 'PUT',
    body: JSON.stringify(corpo),
  })
}

export function del(path: string): Promise<void> {
  return pedir<void>(`${BASE_URL}${path}`, { method: 'DELETE' })
}

/** Endpoints do Actuator, que ficam na raiz e nao sob /api. */
export function getActuator<T>(path: string): Promise<T> {
  return pedir<T>(`/actuator${path}`)
}
