<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { ApiError, type ErrosPorCampo } from '@/api/http'
import { listarDestinatarios } from '@/api/destinatarios'
import { atualizarMonitor, criarMonitor } from '@/api/monitores'
import type { Monitor, MonitorRequest } from '@/model/monitor'
import { monitorVazio, paraRequest } from '@/model/monitor'
import type { Recipient } from '@/model/recipient'

const props = defineProps<{ monitor: Monitor | null }>()
const emit = defineEmits<{ salvo: [Monitor]; cancelar: [] }>()

const form = ref<MonitorRequest>(monitorVazio())
const destinatarios = ref<Recipient[]>([])
const erros = ref<ErrosPorCampo>({})
const erroGeral = ref<string | null>(null)
const salvando = ref(false)

/**
 * O contato a mostrar na lista.
 *
 * Desde a E4.6 um destinatario pode ter so telefone, so e-mail, ou os dois — e
 * imprimir `phoneE164` direto mostraria "null" para quem so tem e-mail.
 */
function contato(d: Recipient): string {
  return [d.phoneE164, d.email].filter(Boolean).join(' · ')
}

const editando = computed(() => props.monitor !== null)
const comVolta = ref(false)
const comPermanencia = ref(false)

watch(
  () => props.monitor,
  (m) => {
    form.value = m ? paraRequest(m) : monitorVazio()
    comVolta.value = form.value.returnWindowStart !== null
    comPermanencia.value = form.value.minStayDays !== null
    erros.value = {}
    erroGeral.value = null
  },
  { immediate: true },
)

onMounted(async () => {
  try {
    destinatarios.value = (await listarDestinatarios()).filter((d) => d.active)
  } catch {
    // Sem destinatarios cadastrados o monitor ainda pode ser criado; ele so
    // nao vai notificar ninguem. A tela nao deve travar por isso.
    destinatarios.value = []
  }
})

// Janela de volta e permanencia sao alternativas. O banco aceita as duas
// preenchidas, mas na pratica uma exclui a outra: ou o usuario diz "volto
// entre tais datas", ou diz "fico de 10 a 15 dias".
watch(comVolta, (ativo) => {
  if (!ativo) {
    form.value.returnWindowStart = null
    form.value.returnWindowEnd = null
  } else {
    comPermanencia.value = false
    form.value.returnWindowStart = form.value.departureWindowEnd
    form.value.returnWindowEnd = form.value.departureWindowEnd
  }
})

watch(comPermanencia, (ativo) => {
  if (!ativo) {
    form.value.minStayDays = null
    form.value.maxStayDays = null
  } else {
    comVolta.value = false
    form.value.minStayDays = 7
    form.value.maxStayDays = 14
  }
})

function alternarDestinatario(id: number) {
  const i = form.value.recipientIds.indexOf(id)
  if (i >= 0) {
    form.value.recipientIds.splice(i, 1)
  } else {
    form.value.recipientIds.push(id)
  }
}

async function salvar() {
  salvando.value = true
  erros.value = {}
  erroGeral.value = null

  try {
    const salvo = props.monitor
      ? await atualizarMonitor(props.monitor.id, form.value)
      : await criarMonitor(form.value)
    emit('salvo', salvo)
  } catch (e) {
    if (e instanceof ApiError) {
      // A API devolve os erros campo a campo (RFC 7807). Aproveitamos para
      // marcar o campo exato, em vez de mostrar "deu erro".
      erros.value = e.errors ?? {}
      if (!e.errors || Object.keys(e.errors).length === 0) {
        erroGeral.value = e.message
      }
    } else {
      erroGeral.value = 'Erro inesperado ao salvar'
    }
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <form class="formulario" @submit.prevent="salvar">
    <h2>{{ editando ? 'Editar monitor' : 'Novo monitor' }}</h2>

    <p v-if="erroGeral" class="erro-geral">{{ erroGeral }}</p>

    <label class="campo">
      <span>Apelido</span>
      <input v-model="form.label" type="text" placeholder="Lisboa em setembro" />
      <small v-if="erros.label" class="erro">{{ erros.label }}</small>
    </label>

    <div class="linha">
      <label class="campo">
        <span>Origem *</span>
        <input
          v-model="form.origin"
          type="text"
          maxlength="3"
          placeholder="GRU"
          class="iata"
          required
        />
        <small v-if="erros.origin" class="erro">{{ erros.origin }}</small>
      </label>

      <label class="campo">
        <span>Destino *</span>
        <input
          v-model="form.destination"
          type="text"
          maxlength="3"
          placeholder="LIS"
          class="iata"
          required
        />
        <small v-if="erros.destination" class="erro">{{ erros.destination }}</small>
      </label>
    </div>

    <fieldset>
      <legend>Janela de ida *</legend>
      <div class="linha">
        <label class="campo">
          <span>De</span>
          <input v-model="form.departureWindowStart" type="date" required />
          <small v-if="erros.departureWindowStart" class="erro">
            {{ erros.departureWindowStart }}
          </small>
        </label>
        <label class="campo">
          <span>Até</span>
          <input v-model="form.departureWindowEnd" type="date" required />
          <small v-if="erros.departureWindowEnd" class="erro">
            {{ erros.departureWindowEnd }}
          </small>
        </label>
      </div>
      <small class="ajuda">O sistema varre todos os dias entre estas duas datas.</small>
    </fieldset>

    <fieldset>
      <legend>Volta</legend>
      <label class="opcao">
        <input v-model="comVolta" type="checkbox" />
        <span>Definir janela de volta</span>
      </label>
      <div v-if="comVolta" class="linha">
        <label class="campo">
          <span>De</span>
          <input v-model="form.returnWindowStart" type="date" />
          <small v-if="erros.returnWindowStart" class="erro">{{ erros.returnWindowStart }}</small>
        </label>
        <label class="campo">
          <span>Até</span>
          <input v-model="form.returnWindowEnd" type="date" />
          <small v-if="erros.returnWindowEnd" class="erro">{{ erros.returnWindowEnd }}</small>
        </label>
      </div>

      <label class="opcao">
        <input v-model="comPermanencia" type="checkbox" />
        <span>Ou definir permanência em dias</span>
      </label>
      <div v-if="comPermanencia" class="linha">
        <label class="campo">
          <span>Mínimo</span>
          <input v-model.number="form.minStayDays" type="number" min="1" />
          <small v-if="erros.minStayDays" class="erro">{{ erros.minStayDays }}</small>
        </label>
        <label class="campo">
          <span>Máximo</span>
          <input v-model.number="form.maxStayDays" type="number" min="1" />
          <small v-if="erros.maxStayDays" class="erro">{{ erros.maxStayDays }}</small>
        </label>
      </div>
      <small v-if="!comVolta && !comPermanencia" class="ajuda">
        Sem nenhum dos dois, o monitor busca somente ida.
      </small>
    </fieldset>

    <div class="linha">
      <label class="campo">
        <span>Preço máximo *</span>
        <input v-model.number="form.maxPrice" type="number" step="0.01" min="0.01" required />
        <small v-if="erros.maxPrice" class="erro">{{ erros.maxPrice }}</small>
      </label>

      <label class="campo">
        <span>Escalas máximas</span>
        <select v-model.number="form.maxStops">
          <option :value="null">qualquer</option>
          <option :value="0">voo direto</option>
          <option :value="1">até 1</option>
          <option :value="2">até 2</option>
        </select>
        <small v-if="erros.maxStops" class="erro">{{ erros.maxStops }}</small>
      </label>

      <label class="campo">
        <span>Passageiros</span>
        <input v-model.number="form.passengers" type="number" min="1" max="9" />
        <small v-if="erros.passengers" class="erro">{{ erros.passengers }}</small>
      </label>
    </div>

    <label class="campo">
      <span>Intervalo entre buscas (minutos)</span>
      <input v-model.number="form.searchIntervalMinutes" type="number" min="5" />
      <small v-if="erros.searchIntervalMinutes" class="erro">
        {{ erros.searchIntervalMinutes }}
      </small>
      <small v-else class="ajuda">Mínimo de 5 minutos, para não abusar das fontes de preço.</small>
    </label>

    <fieldset>
      <legend>Quem recebe o alerta</legend>
      <p v-if="destinatarios.length === 0" class="ajuda">
        Nenhum destinatário ativo cadastrado. O monitor funciona, mas não vai notificar ninguém.
      </p>
      <label v-for="d in destinatarios" :key="d.id" class="opcao">
        <input
          type="checkbox"
          :checked="form.recipientIds.includes(d.id)"
          @change="alternarDestinatario(d.id)"
        />
        <span>{{ d.name }} <code>{{ contato(d) }}</code></span>
      </label>
    </fieldset>

    <label class="opcao">
      <input v-model="form.active" type="checkbox" />
      <span>Monitor ativo</span>
    </label>

    <div class="acoes">
      <button type="button" class="secundario" @click="emit('cancelar')">Cancelar</button>
      <button type="submit" :disabled="salvando">
        {{ salvando ? 'Salvando...' : 'Salvar' }}
      </button>
    </div>
  </form>
</template>

<style scoped>
.formulario {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

h2 {
  font-size: 1.1rem;
  margin: 0;
}

.linha {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.campo {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  flex: 1;
  min-width: 8rem;
}

.campo > span {
  font-size: 0.8rem;
  color: var(--texto-suave);
}

input,
select {
  font: inherit;
  padding: 0.45rem 0.6rem;
  border: 1px solid var(--borda);
  border-radius: 6px;
  background: var(--fundo);
  color: var(--texto);
}

.iata {
  text-transform: uppercase;
  max-width: 6rem;
}

fieldset {
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 0.85rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

legend {
  font-size: 0.8rem;
  color: var(--texto-suave);
  padding: 0 0.3rem;
}

.opcao {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.9rem;
}

.opcao input {
  width: auto;
}

.erro {
  color: var(--falha);
  font-size: 0.78rem;
}

.erro-geral {
  margin: 0;
  padding: 0.6rem 0.8rem;
  border: 1px solid var(--falha);
  border-radius: 6px;
  color: var(--falha);
  font-size: 0.88rem;
}

.ajuda {
  color: var(--texto-suave);
  font-size: 0.78rem;
}

.acoes {
  display: flex;
  gap: 0.6rem;
  justify-content: flex-end;
}

button {
  font: inherit;
  font-size: 0.9rem;
  padding: 0.45rem 1.1rem;
  border-radius: 6px;
  border: 1px solid transparent;
  background: var(--acento);
  color: white;
  cursor: pointer;
}

button.secundario {
  background: transparent;
  border-color: var(--borda);
  color: inherit;
}

button:disabled {
  opacity: 0.6;
  cursor: default;
}

code {
  font-family: ui-monospace, monospace;
  font-size: 0.85em;
  color: var(--texto-suave);
}
</style>
