import { del, getJson, postJson, putJson } from '@/api/http'
import type { Monitor, MonitorRequest, MonitorRunResult } from '@/model/monitor'

/**
 * O recurso `/monitors` do core-java.
 *
 * Um modulo por recurso, e nao um `api.ts` unico: este arquivo ja tinha juntado
 * monitores, destinatarios e observacoes, e a Fase 2 e a Fase 3 ainda vao
 * acrescentar estatisticas, recomendacao e agente. O transporte comum mora em
 * `@/api/http`; aqui fica so o vocabulario deste recurso.
 */

export function listarMonitores(apenasAtivos = false): Promise<Monitor[]> {
  return getJson<Monitor[]>(`/monitors${apenasAtivos ? '?active=true' : ''}`)
}

export function buscarMonitor(id: number): Promise<Monitor> {
  return getJson<Monitor>(`/monitors/${id}`)
}

export function criarMonitor(req: MonitorRequest): Promise<Monitor> {
  return postJson<Monitor>('/monitors', req)
}

export function atualizarMonitor(id: number, req: MonitorRequest): Promise<Monitor> {
  return putJson<Monitor>(`/monitors/${id}`, req)
}

export function excluirMonitor(id: number): Promise<void> {
  return del(`/monitors/${id}`)
}

/** Dispara a varredura agora, sem esperar o scheduler. */
export function varrerAgora(id: number): Promise<MonitorRunResult> {
  return postJson<MonitorRunResult>(`/monitors/${id}/search`, {})
}
