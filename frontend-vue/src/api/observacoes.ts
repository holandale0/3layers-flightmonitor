import { getJson } from '@/api/http'
import type { Observacao } from '@/model/observacao'

/** O historico de precos de um monitor: `/monitors/{id}/observations`. */

export function listarObservacoes(id: number, limite = 500): Promise<Observacao[]> {
  return getJson<Observacao[]>(`/monitors/${id}/observations?limit=${limite}`)
}
