<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { excluirDestinatario, listarDestinatarios } from '@/api/destinatarios'
import { verificarSaude } from '@/api/saude'
import DestinatarioForm from '@/components/DestinatarioForm.vue'
import { mensagemDeErro, useCarregamento } from '@/composables/useCarregamento'
import { instante } from '@/lib/formato'
import type { CanalDeNotificacao } from '@/model/health'
import type { Recipient } from '@/model/recipient'
import { alcancavelPor, contatosDe } from '@/model/recipient'

const { dados, carregando, erro, executar: carregar } = useCarregamento(() =>
  listarDestinatarios(),
)

const destinatarios = computed<Recipient[]>(() => dados.value ?? [])
const ativos = computed(() => destinatarios.value.filter((d) => d.active).length)

const editorAberto = ref(false)
const emEdicao = ref<Recipient | null>(null)

/**
 * O canal de notificacao ativo, lido do Actuator.
 *
 * Serve para uma coisa so, mas que nenhuma outra tela consegue mostrar: avisar
 * que alguem cadastrado NAO vai ser alcancado. Um destinatario so com e-mail,
 * com o sistema em WHATSAPP, e um alerta que falha em silencio — o envio vira
 * falha permanente e a pessoa nunca sabe que perdeu a passagem.
 */
const canal = ref<CanalDeNotificacao | null>(null)

const inalcancaveis = computed(() => {
  const ativo = canal.value
  return ativo === null
    ? []
    : destinatarios.value.filter((d) => d.active && !alcancavelPor(d, ativo))
})

onMounted(async () => {
  await carregar()
  try {
    const saude = await verificarSaude()
    canal.value = saude.components?.notificacao?.details?.canal ?? null
  } catch {
    // Sem o canal, a tela funciona igual — so nao mostra o aviso. Nao vale
    // travar o cadastro por causa de um indicador.
    canal.value = null
  }
})

function novo() {
  emEdicao.value = null
  editorAberto.value = true
}

function editar(d: Recipient) {
  emEdicao.value = d
  editorAberto.value = true
}

function aoSalvar() {
  editorAberto.value = false
  emEdicao.value = null
  carregar()
}

async function excluir(d: Recipient) {
  if (
    !confirm(
      `Excluir o destinatário "${d.name}"?\n\n` +
        'Ele sai dos monitores em que está. Os alertas já enviados são preservados no histórico.',
    )
  ) {
    return
  }
  try {
    await excluirDestinatario(d.id)
    await carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e, 'Erro ao excluir')
  }
}
</script>

<template>
  <section>
    <header class="topo">
      <div>
        <h1>Destinatários</h1>
        <p class="subtitulo">
          {{ destinatarios.length }} cadastrado(s), {{ ativos }} ativo(s)
          <template v-if="canal"> · canal ativo: <code>{{ canal }}</code></template>
        </p>
      </div>
      <button v-if="!editorAberto" type="button" @click="novo">Novo destinatário</button>
    </header>

    <div v-if="editorAberto" class="cartao">
      <DestinatarioForm
        :destinatario="emEdicao"
        @salvo="aoSalvar"
        @cancelar="editorAberto = false"
      />
    </div>

    <p v-if="erro" class="erro">{{ erro }}</p>

    <div v-if="inalcancaveis.length && canal" class="alerta-canal">
      <strong>{{ inalcancaveis.length }} destinatário(s) ativo(s) não serão avisados.</strong>
      O canal do sistema é <code>{{ canal }}</code>, e
      {{ inalcancaveis.map((d) => d.name).join(', ') }}
      não {{ inalcancaveis.length === 1 ? 'tem' : 'têm' }}
      {{ canal === 'EMAIL' ? 'e-mail' : 'telefone' }} cadastrado.
    </div>

    <p v-if="carregando" class="msg">Carregando...</p>

    <p v-else-if="destinatarios.length === 0 && !editorAberto" class="msg">
      Nenhum destinatário cadastrado. Sem pelo menos um, os monitores funcionam mas não avisam
      ninguém.
    </p>

    <ul v-else class="lista">
      <li
        v-for="d in destinatarios"
        :key="d.id"
        class="cartao"
        :class="{ inativo: !d.active }"
      >
        <div class="cabecalho">
          <div>
            <strong class="nome">{{ d.name }}</strong>
            <span v-if="!d.active" class="etiqueta">inativo</span>
            <span
              v-else-if="canal && !alcancavelPor(d, canal)"
              class="etiqueta perigo"
              :title="`O canal ativo é ${canal} e falta esse contato`"
            >
              não recebe
            </span>
          </div>
          <code class="contatos">{{ contatosDe(d) }}</code>
        </div>

        <p class="rodape">Cadastrado em {{ instante(d.createdAt) }}</p>

        <div class="acoes">
          <button type="button" class="secundario" @click="editar(d)">Editar</button>
          <button type="button" class="perigo" @click="excluir(d)">Excluir</button>
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
  flex-wrap: wrap;
}

.nome {
  font-size: 1.05rem;
}

.contatos {
  color: var(--texto-suave);
  font-size: 0.85rem;
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

.etiqueta.perigo {
  border-color: var(--falha);
  color: var(--falha);
}

.alerta-canal {
  border: 1px solid var(--falha);
  border-radius: 8px;
  padding: 0.7rem 1rem;
  margin-bottom: 1rem;
  font-size: 0.85rem;
  color: var(--texto-suave);
}

.alerta-canal strong {
  color: var(--falha);
  display: block;
  margin-bottom: 0.2rem;
}

.rodape {
  margin: 0.6rem 0 0;
  font-size: 0.78rem;
  color: var(--texto-suave);
}

.acoes {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.8rem;
}

.msg {
  color: var(--texto-suave);
}

.erro {
  color: var(--falha);
}
</style>
