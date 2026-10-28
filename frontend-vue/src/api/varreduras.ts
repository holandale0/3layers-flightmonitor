import { getJson } from '@/api/http'
import type { Varredura } from '@/model/varredura'

/** As varreduras recentes de um monitor: `/monitors/{id}/runs`. */

export function listarVarreduras(id: number, limite = 50): Promise<Varredura[]> {
  return getJson<Varredura[]>(`/monitors/${id}/runs?limit=${limite}`)
}
