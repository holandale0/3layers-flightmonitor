<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { ApiError, type ErrosPorCampo } from '@/api/http'
import { lerConfigWhatsApp, restaurarAmbiente, salvarConfigWhatsApp } from '@/api/configuracao'
import { useCarregamento } from '@/composables/useCarregamento'
import { instante } from '@/lib/formato'
import type { WhatsAppConfigRequest } from '@/model/configuracao'
import { paraEnvio, paraRequest } from '@/model/configuracao'

const { dados: config, carregando, erro, executar: carregar } = useCarregamento(lerConfigWhatsApp)

const form = ref<WhatsAppConfigRequest | null>(null)
const erros = ref<ErrosPorCampo>({})
const salvando = ref(false)
const salvo = ref(false)

const doAmbiente = computed(() => config.value?.origem === 'AMBIENTE')

watch(config, (c) => {
  if (c) form.value = paraRequest(c)
})

onMounted(carregar)

async function salvar() {
  if (!form.value) return
  salvando.value = true
  erros.value = {}
  salvo.value = false

  try {
    config.value = await salvarConfigWhatsApp(paraEnvio(form.value))
    salvo.value = true
  } catch (e) {
    if (e instanceof ApiError) {
      erros.value = e.errors ?? {}
      if (!e.errors || Object.keys(e.errors).length === 0) erro.value = e.message
    } else {
      erro.value = 'Erro inesperado ao salvar'
    }
  } finally {
    salvando.value = false
  }
}

async function voltarAoAmbiente() {
  if (!confirm('Apagar a configuração salva?\n\nVoltam a valer as variáveis do .env.')) return
  salvando.value = true
  try {
    config.value = await restaurarAmbiente()
    salvo.value = false
  } catch (e) {
    erro.value = e instanceof ApiError ? e.message : 'Erro ao restaurar'
  } finally {
    salvando.value = false
  }
}
</script>

<template>
  <section>
    <h1>Configuração</h1>
    <p class="subtitulo">Canal WhatsApp — o que identifica a conta e o template.</p>

    <p v-if="carregando" class="msg">Carregando...</p>
    <p v-if="erro" class="erro-geral">{{ erro }}</p>

    <template v-if="config && form">
      <!-- O estado de prontidão vem primeiro: é a pergunta que a pessoa tem. -->
      <div class="estado" :class="config.pronto ? 'ok' : 'faltando'">
        <strong v-if="config.pronto">Pronto para enviar.</strong>
        <strong v-else>Ainda não dá para enviar.</strong>
        <ul>
          <li :class="{ falta: !config.tokenConfigurado }">
            {{ config.tokenConfigurado ? '✓' : '✗' }} Token no <code>.env</code>
            <span v-if="!config.tokenConfigurado" class="dica">
              — defina <code>WHATSAPP_ACCESS_TOKEN</code>. Ele não é editável aqui, e isso é
              proposital: segredo não mora em banco.
            </span>
          </li>
          <li :class="{ falta: !config.phoneNumberId }">
            {{ config.phoneNumberId ? '✓' : '✗' }} Número remetente
            <span v-if="!config.phoneNumberId" class="dica">— preencha abaixo.</span>
          </li>
        </ul>
      </div>

      <p class="origem">
        <template v-if="doAmbiente">
          Os valores abaixo vêm do <code>.env</code>. Salvar aqui passa a mandar neles.
        </template>
        <template v-else>
          Configurado por esta tela<template v-if="config.updatedAt">
            em {{ instante(config.updatedAt) }}</template>.
        </template>
      </p>

      <form class="formulario" @submit.prevent="salvar">
        <label class="campo">
          <span>Identificador do número remetente</span>
          <input v-model="form.phoneNumberId" type="text" placeholder="1246475018551646" />
          <small v-if="erros.phoneNumberId" class="erro">{{ erros.phoneNumberId }}</small>
          <small v-else class="ajuda">
            É o <code>phone_number_id</code> da Meta — um número longo, <strong>não</strong> o
            telefone. Confundir os dois é o erro mais comum de quem configura pela primeira vez.
          </small>
        </label>

        <label class="campo">
          <span>WABA (conta do WhatsApp Business)</span>
          <input v-model="form.wabaId" type="text" placeholder="1023687696962454" />
          <small v-if="erros.wabaId" class="erro">{{ erros.wabaId }}</small>
          <small v-else class="ajuda">
            A conta dona do template. Template aprovado na conta errada devolve “template não
            encontrado” com o template visível no painel da Meta.
          </small>
        </label>

        <div class="linha">
          <label class="campo">
            <span>Template *</span>
            <input v-model="form.templateName" type="text" required />
            <small v-if="erros.templateName" class="erro">{{ erros.templateName }}</small>
            <small v-else class="ajuda">Minúsculas, dígitos e sublinhado.</small>
          </label>

          <label class="campo">
            <span>Idioma *</span>
            <input v-model="form.templateLanguage" type="text" required />
            <small v-if="erros.templateLanguage" class="erro">{{ erros.templateLanguage }}</small>
            <small v-else class="ajuda">Com sublinhado: <code>pt_BR</code>.</small>
          </label>
        </div>

        <p class="ajuda">
          Campo apagado volta a valer o do <code>.env</code>. As mudanças passam a valer no
          próximo alerta — sem reiniciar nada.
        </p>

        <div class="acoes">
          <button type="submit" :disabled="salvando">
            {{ salvando ? 'Salvando...' : 'Salvar' }}
          </button>
          <button
            v-if="!doAmbiente"
            type="button"
            class="secundario"
            :disabled="salvando"
            @click="voltarAoAmbiente"
          >
            Voltar ao .env
          </button>
          <span v-if="salvo" class="confirmado">Salvo.</span>
        </div>
      </form>
    </template>
  </section>
</template>

<style scoped>
h1 {
  font-size: 1.5rem;
  margin: 0;
}

.subtitulo {
  margin: 0.15rem 0 1.5rem;
  color: var(--texto-suave);
  font-size: 0.85rem;
}

.estado {
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 0.9rem 1.1rem;
  margin-bottom: 1rem;
}

.estado.ok strong {
  color: var(--ok);
}

.estado.faltando strong {
  color: var(--falha);
}

.estado ul {
  list-style: none;
  margin: 0.5rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.85rem;
}

.estado li.falta {
  color: var(--falha);
}

.dica {
  color: var(--texto-suave);
}

.origem {
  font-size: 0.85rem;
  color: var(--texto-suave);
  margin: 0 0 1rem;
}

.formulario {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 1.25rem;
}

.linha {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
}

.linha > .campo {
  flex: 1;
  min-width: 10rem;
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

input {
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--borda);
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
}

.ajuda {
  color: var(--texto-suave);
  font-size: 0.78rem;
  margin: 0;
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
  font-size: 0.85rem;
}

.acoes {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.confirmado {
  color: var(--ok);
  font-size: 0.85rem;
}

.msg {
  color: var(--texto-suave);
}
</style>
