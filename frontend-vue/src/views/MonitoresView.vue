<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { excluirMonitor, listarMonitores, varrerAgora } from '@/api/monitores'
import MonitorForm from '@/components/MonitorForm.vue'
import { mensagemDeErro, useCarregamento } from '@/composables/useCarregamento'
import { data, dinheiro, instante } from '@/lib/formato'
import type { Monitor, MonitorRunResult } from '@/model/monitor'

const { dados, carregando, erro, executar: carregar } = useCarregamento(() => listarMonitores())

const monitores = computed<Monitor[]>(() => dados.value ?? [])

const editorAberto = ref(false)
const emEdicao = ref<Monitor | null>(null)

const varrendo = ref<number | null>(null)
const ultimaVarredura = ref<{ id: number; resultado: MonitorRunResult } | null>(null)

const ativos = computed(() => monitores.value.filter((m) => m.active).length)

onMounted(carregar)

function novo() {
  emEdicao.value = null
  editorAberto.value = true
}

function editar(m: Monitor) {
  emEdicao.value = m
  editorAberto.value = true
}

function aoSalvar() {
  editorAberto.value = false
  emEdicao.value = null
  carregar()
}

async function excluir(m: Monitor) {
  const nome = m.label || `${m.origin} → ${m.destination}`
  if (!confirm(`Excluir o monitor "${nome}"?\n\nO histórico de preços da rota é preservado.`)) {
    return
  }
  try {
    await excluirMonitor(m.id)
    await carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e, 'Erro ao excluir')
  }
}

/** Dispara a varredura sem esperar o scheduler — util para testar um monitor novo. */
async function varrer(m: Monitor) {
  varrendo.value = m.id
  ultimaVarredura.value = null
  erro.value = null
  try {
    ultimaVarredura.value = { id: m.id, resultado: await varrerAgora(m.id) }
    await carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e, 'Erro ao varrer')
  } finally {
    varrendo.value = null
  }
}

function periodo(m: Monitor) {
  const ida = `${data(m.departureWindowStart)} a ${data(m.departureWindowEnd)}`
  if (m.returnWindowStart && m.returnWindowEnd) {
    return `${ida} · volta ${data(m.returnWindowStart)} a ${data(m.returnWindowEnd)}`
  }
  if (m.minStayDays && m.maxStayDays) {
    return `${ida} · ${m.minStayDays} a ${m.maxStayDays} dias`
  }
  return `${ida} · somente ida`
}

function escalas(max: number | null) {
  if (max === null) return 'escalas livres'
  return max === 0 ? 'voo direto' : `até ${max} escala${max > 1 ? 's' : ''}`
}
</script>

<template>
  <section>
    <header class="topo">
      <div>
        <h1>Monitores</h1>
        <p class="subtitulo">
          {{ monitores.length }} cadastrado(s), {{ ativos }} ativo(s)
        </p>
      </div>
      <button v-if="!editorAberto" type="button" @click="novo">Novo monitor</button>
    </header>

    <div v-if="editorAberto" class="cartao">
      <MonitorForm
        :monitor="emEdicao"
        @salvo="aoSalvar"
        @cancelar="editorAberto = false"
      />
    </div>

    <p v-if="erro" class="erro">{{ erro }}</p>

    <p v-if="carregando" class="msg">Carregando...</p>

    <p v-else-if="monitores.length === 0 && !editorAberto" class="msg">
      Nenhum monitor cadastrado. Crie o primeiro para o sistema começar a vigiar preços.
    </p>

    <ul v-else class="lista">
      <li v-for="m in monitores" :key="m.id" class="cartao" :class="{ inativo: !m.active }">
        <div class="cabecalho">
          <div>
            <strong class="rota">{{ m.origin }} → {{ m.destination }}</strong>
            <span v-if="m.label" class="apelido">{{ m.label }}</span>
            <span v-if="!m.active" class="etiqueta">pausado</span>
          </div>
          <strong class="teto">{{ dinheiro(m.maxPrice, m.currency) }}</strong>
        </div>

        <p class="detalhe">{{ periodo(m) }}</p>
        <p class="detalhe">
          {{ escalas(m.maxStops) }} ·
          {{ m.passengers }} passageiro(s) ·
          busca a cada {{ m.searchIntervalMinutes }} min
        </p>

        <p class="detalhe">
          <template v-if="m.recipients.length">
            Avisa: {{ m.recipients.map((r) => r.name).join(', ') }}
          </template>
          <template v-else>
            <span class="alerta-sem-destino">Sem destinatário — não vai notificar ninguém</span>
          </template>
        </p>

        <p class="rodape">
          Última busca: {{ instante(m.lastSearchedAt) }} ·
          Próxima: {{ instante(m.nextSearchAt) }}
        </p>

        <div
          v-if="ultimaVarredura && ultimaVarredura.id === m.id"
          class="resultado"
        >
          <strong>
            {{ ultimaVarredura.resultado.busca.observacoesGravadas }} observações,
            {{ ultimaVarredura.resultado.busca.candidatosAbaixoDoTeto }} abaixo do teto
          </strong>
          <span v-if="ultimaVarredura.resultado.busca.melhorPreco">
            · melhor: {{ dinheiro(ultimaVarredura.resultado.busca.melhorPreco, m.currency) }}
          </span>
          <div class="decisao">
            {{ ultimaVarredura.resultado.alerta.alertar ? '🔔 Alertou' : 'Sem alerta' }} —
            {{ ultimaVarredura.resultado.alerta.detalhe }}
          </div>
          <ul v-if="ultimaVarredura.resultado.busca.avisos.length" class="avisos">
            <li v-for="(a, i) in ultimaVarredura.resultado.busca.avisos" :key="i">{{ a }}</li>
          </ul>
        </div>

        <div class="acoes">
          <button type="button" class="secundario" :disabled="varrendo === m.id" @click="varrer(m)">
            {{ varrendo === m.id ? 'Buscando...' : 'Buscar agora' }}
          </button>
          <RouterLink class="botao-link" :to="`/monitores/${m.id}`">Histórico</RouterLink>
          <button type="button" class="secundario" @click="editar(m)">Editar</button>
          <button type="button" class="perigo" @click="excluir(m)">Excluir</button>
        </div>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.topo {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1.5rem;
}

h1 {
  font-size: 1.5rem;
  margin: 0;
}

.subtitulo {
  margin: 0.15rem 0 0;
  color: var(--texto-suave);
  font-size: 0.85rem;
}

.lista {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.cartao {
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 1rem 1.25rem;
}

.cartao.inativo {
  opacity: 0.65;
}

.cabecalho {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.5rem;
  flex-wrap: wrap;
}

.rota {
  font-family: ui-monospace, monospace;
  font-size: 1.05rem;
}

.apelido {
  margin-left: 0.6rem;
  color: var(--texto-suave);
  font-size: 0.9rem;
}

.etiqueta {
  margin-left: 0.6rem;
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border: 1px solid var(--borda);
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
  color: var(--texto-suave);
}

.teto {
  font-size: 1.05rem;
  color: var(--ok);
}

.detalhe {
  margin: 0.2rem 0;
  font-size: 0.88rem;
  color: var(--texto-suave);
}

.alerta-sem-destino {
  color: var(--falha);
}

.rodape {
  margin: 0.6rem 0 0;
  font-size: 0.78rem;
  color: var(--texto-suave);
}

.resultado {
  margin-top: 0.8rem;
  padding: 0.7rem 0.9rem;
  border-left: 3px solid var(--acento);
  background: var(--realce);
  border-radius: 0 6px 6px 0;
  font-size: 0.85rem;
}

.decisao {
  margin-top: 0.3rem;
}

.avisos {
  margin: 0.4rem 0 0;
  padding-left: 1.1rem;
  color: var(--texto-suave);
  font-size: 0.8rem;
}

.acoes {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.9rem;
  flex-wrap: wrap;
}

button,
.botao-link {
  font: inherit;
  font-size: 0.85rem;
  padding: 0.35rem 0.85rem;
  border-radius: 6px;
  border: 1px solid transparent;
  background: var(--acento);
  color: white;
  cursor: pointer;
  text-decoration: none;
  display: inline-block;
}

button.secundario,
.botao-link {
  background: transparent;
  border-color: var(--borda);
  color: inherit;
}

button.perigo {
  background: transparent;
  border-color: var(--borda);
  color: var(--falha);
}

button:disabled {
  opacity: 0.6;
  cursor: default;
}

.msg {
  color: var(--texto-suave);
}

.erro {
  color: var(--falha);
}
</style>
