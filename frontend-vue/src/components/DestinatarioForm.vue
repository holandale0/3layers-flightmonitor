<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiError, type ErrosPorCampo } from '@/api/http'
import { atualizarDestinatario, criarDestinatario } from '@/api/destinatarios'
import type { Recipient, RecipientRequest } from '@/model/recipient'
import { destinatarioVazio, paraEnvio, paraRequest } from '@/model/recipient'

const props = defineProps<{ destinatario: Recipient | null }>()
const emit = defineEmits<{ salvo: [Recipient]; cancelar: [] }>()

const form = ref<RecipientRequest>(destinatarioVazio())
const erros = ref<ErrosPorCampo>({})
const erroGeral = ref<string | null>(null)
const salvando = ref(false)

const editando = computed(() => props.destinatario !== null)

// A mesma regra do backend, so que aqui e' para AVISAR, nao para garantir: a
// API recusa de qualquer jeito com o CHECK do banco por tras. Repetir aqui
// evita uma ida ao servidor para descobrir algo obvio.
const semContato = computed(
  () => !form.value.phoneE164?.trim() && !form.value.email?.trim(),
)

watch(
  () => props.destinatario,
  (d) => {
    form.value = d ? paraRequest(d) : destinatarioVazio()
    erros.value = {}
    erroGeral.value = null
  },
  { immediate: true },
)

async function salvar() {
  salvando.value = true
  erros.value = {}
  erroGeral.value = null

  try {
    const corpo = paraEnvio(form.value)
    const salvo = props.destinatario
      ? await atualizarDestinatario(props.destinatario.id, corpo)
      : await criarDestinatario(corpo)
    emit('salvo', salvo)
  } catch (e) {
    if (e instanceof ApiError) {
      // A API devolve os erros campo a campo (RFC 7807) — inclusive o "informe
      // ao menos um contato", ancorado em phoneE164.
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
    <h2>{{ editando ? 'Editar destinatário' : 'Novo destinatário' }}</h2>

    <p v-if="erroGeral" class="erro-geral">{{ erroGeral }}</p>

    <label class="campo">
      <span>Nome *</span>
      <input v-model="form.name" type="text" maxlength="120" placeholder="Maria" required />
      <small v-if="erros.name" class="erro">{{ erros.name }}</small>
    </label>

    <fieldset>
      <legend>Como avisar</legend>
      <p class="ajuda">
        Preencha <strong>ao menos um</strong>. O canal ativo do sistema decide qual é usado —
        quem só tem e-mail não recebe por WhatsApp, e vice-versa.
      </p>

      <label class="campo">
        <span>WhatsApp</span>
        <input
          v-model="form.phoneE164"
          type="tel"
          placeholder="+5511999998888"
          autocomplete="tel"
        />
        <small v-if="erros.phoneE164" class="erro">{{ erros.phoneE164 }}</small>
        <small v-else class="ajuda">
          Formato E.164, com o código do país. Espaços, hífens e parênteses são removidos
          sozinhos; o <code>+</code> não é adivinhado.
        </small>
      </label>

      <label class="campo">
        <span>E-mail</span>
        <input
          v-model="form.email"
          type="email"
          maxlength="254"
          placeholder="voce@exemplo.com"
          autocomplete="email"
        />
        <small v-if="erros.email" class="erro">{{ erros.email }}</small>
      </label>

      <p v-if="semContato" class="aviso">
        Sem telefone e sem e-mail, esta pessoa não pode ser avisada de nada.
      </p>
    </fieldset>

    <label class="opcao">
      <input v-model="form.active" type="checkbox" />
      <span>Destinatário ativo</span>
    </label>
    <small class="ajuda">
      Inativo continua vinculado aos monitores e no histórico de alertas — só para de receber.
    </small>

    <div class="acoes">
      <button type="submit" :disabled="salvando || semContato">
        {{ salvando ? 'Salvando...' : 'Salvar' }}
      </button>
      <button type="button" class="secundario" @click="emit('cancelar')">Cancelar</button>
    </div>
  </form>
</template>

<style scoped>
.formulario {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
}

h2 {
  font-size: 1.05rem;
  margin: 0;
}

fieldset {
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 0.9rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  margin: 0;
}

legend {
  font-size: 0.85rem;
  color: var(--texto-suave);
  padding: 0 0.4rem;
}

.campo {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.campo > span {
  font-size: 0.8rem;
  color: var(--texto-suave);
}

input[type='text'],
input[type='tel'],
input[type='email'] {
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--borda);
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
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
  color: var(--falha);
  border: 1px solid var(--falha);
  border-radius: 6px;
  padding: 0.5rem 0.75rem;
  margin: 0;
  font-size: 0.85rem;
}

.aviso {
  color: var(--texto-suave);
  font-size: 0.8rem;
  margin: 0;
}

.ajuda {
  color: var(--texto-suave);
  font-size: 0.78rem;
  margin: 0;
}

.acoes {
  display: flex;
  gap: 0.5rem;
}
</style>
