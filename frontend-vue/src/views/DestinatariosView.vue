<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { removerAparelho, testarAparelho } from '@/api/aparelhos'
import { excluirDestinatario, listarDestinatarios, registrarAparelho } from '@/api/destinatarios'
import { verificarSaude } from '@/api/saude'
import DestinatarioForm from '@/components/DestinatarioForm.vue'
import { mensagemDeErro, useCarregamento } from '@/composables/useCarregamento'
import { emAplicativo, nomeDoAparelho, obterTokenDePush, pushDisponivel } from '@/lib/app'
import { instante } from '@/lib/formato'
import type { CanalDeNotificacao } from '@/model/health'
import type { PushDevice, Recipient } from '@/model/recipient'
import { alcancavelPor, contatoDoCanal, contatosDe, inalcancavel } from '@/model/recipient'

const { dados, carregando, erro, executar: carregar } = useCarregamento(() =>
  listarDestinatarios(),
)

const destinatarios = computed<Recipient[]>(() => dados.value ?? [])
const ativos = computed(() => destinatarios.value.filter((d) => d.active).length)

const editorAberto = ref(false)
const emEdicao = ref<Recipient | null>(null)

/**
 * Os canais de notificacao ativos, lidos do Actuator.
 *
 * Servem para uma coisa so, mas que nenhuma outra tela consegue mostrar: avisar
 * que alguem cadastrado NAO vai ser alcancado. Com varios canais (D-113), o que
 * importa e quem nao e alcancado por NENHUM — esse perde a passagem em silencio.
 */
const canais = ref<CanalDeNotificacao[]>([])

const inalcancaveis = computed(() =>
  destinatarios.value.filter((d) => d.active && inalcancavel(d, canais.value)),
)

/** O botao de push so aparece dentro do app gerado com o Firebase. */
const oferecerPush = emAplicativo() && pushDisponivel()
const pushAtivo = computed(() => canais.value.includes('PUSH'))

/** Qual acao esta em andamento: `registrar-<destinatario>` ou `testar-<aparelho>`. */
const ocupado = ref<string | null>(null)
const aviso = ref<string | null>(null)

onMounted(async () => {
  await carregar()
  try {
    const saude = await verificarSaude()
    canais.value = saude.components?.notificacao?.details?.canais ?? []
  } catch {
    // Sem os canais, a tela funciona igual — so nao mostra o aviso. Nao vale
    // travar o cadastro por causa de um indicador.
    canais.value = []
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

/**
 * "Receber neste aparelho": o app pede a permissao e o token ao Firebase, e o
 * painel registra pela API — o mesmo caminho de qualquer outro cadastro.
 */
async function receberNesteAparelho(d: Recipient) {
  ocupado.value = `registrar-${d.id}`
  aviso.value = null
  erro.value = null
  try {
    const token = await obterTokenDePush(d.id)
    await registrarAparelho(d.id, { token, name: nomeDoAparelho() })
    await carregar()
    aviso.value = pushAtivo.value
      ? `Este aparelho passa a receber os alertas de ${d.name}. Use "Testar" para conferir.`
      : `Aparelho registrado para ${d.name}, mas o canal PUSH não está ativo no servidor: ` +
        'inclua PUSH em NOTIFICATION_CHANNELS para os alertas chegarem aqui.'
  } catch (e) {
    erro.value = mensagemDeErro(e, 'Não foi possível registrar o aparelho')
  } finally {
    ocupado.value = null
  }
}

async function testar(a: PushDevice) {
  ocupado.value = `testar-${a.id}`
  aviso.value = null
  erro.value = null
  try {
    const r = await testarAparelho(a.id)
    if (r.enviado) {
      aviso.value = `Notificação de teste enviada para ${a.name}.`
    } else {
      erro.value = `O teste para ${a.name} falhou: ${r.erro}`
    }
    // O teste pode ter desligado o aparelho, se o Firebase recusou o token.
    await carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e, 'Erro ao testar')
  } finally {
    ocupado.value = null
  }
}

async function remover(a: PushDevice) {
  if (!confirm(`Remover o aparelho "${a.name}"? Ele deixa de receber alertas por push.`)) {
    return
  }
  try {
    await removerAparelho(a.id)
    await carregar()
  } catch (e) {
    erro.value = mensagemDeErro(e, 'Erro ao remover')
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
          <template v-if="canais.length">
            · {{ canais.length === 1 ? 'canal ativo' : 'canais ativos' }}:
            <code v-for="c in canais" :key="c" class="canal">{{ c }}</code>
          </template>
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
    <p v-if="aviso" class="aviso">{{ aviso }}</p>

    <div v-if="inalcancaveis.length" class="alerta-canal">
      <strong>{{ inalcancaveis.length }} destinatário(s) ativo(s) não serão avisados.</strong>
      {{ inalcancaveis.map((d) => d.name).join(', ') }}
      não {{ inalcancaveis.length === 1 ? 'tem' : 'têm' }}
      {{ canais.map(contatoDoCanal).join(' nem ') }} cadastrado.
    </div>

    <p v-if="carregando && !dados" class="msg">Carregando...</p>

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
              v-else-if="inalcancavel(d, canais)"
              class="etiqueta perigo"
              title="Nenhum dos canais ativos tem como chegar a esta pessoa"
            >
              não recebe
            </span>
          </div>
          <code class="contatos">{{ contatosDe(d) }}</code>
        </div>

        <!-- Por onde cada canal ativo chega a esta pessoa. Com um canal so, a
             etiqueta "não recebe" bastava; com varios, faltar um e normal. -->
        <p v-if="d.active && canais.length > 1" class="alcance">
          <span
            v-for="c in canais"
            :key="c"
            :class="alcancavelPor(d, c) ? 'sim' : 'nao'"
            :title="alcancavelPor(d, c) ? `Recebe por ${c}` : `Falta ${contatoDoCanal(c)}`"
          >
            {{ alcancavelPor(d, c) ? '✓' : '✗' }} {{ c }}
          </span>
        </p>

        <ul v-if="d.pushDevices.length" class="aparelhos">
          <li v-for="a in d.pushDevices" :key="a.id" :class="{ desligado: !a.active }">
            <span class="aparelho">
              📱 {{ a.name }}
              <small v-if="!a.active" :title="a.lastError ?? ''">
                · desligado{{ a.lastError ? ': o Firebase recusou o token' : '' }}
              </small>
            </span>
            <span class="acoes-aparelho">
              <button
                v-if="a.active"
                type="button"
                class="secundario"
                :disabled="ocupado !== null"
                @click="testar(a)"
              >
                {{ ocupado === `testar-${a.id}` ? 'Enviando...' : 'Testar' }}
              </button>
              <button type="button" class="perigo" @click="remover(a)">Remover</button>
            </span>
          </li>
        </ul>

        <p class="rodape">Cadastrado em {{ instante(d.createdAt) }}</p>

        <div class="acoes">
          <button type="button" class="secundario" @click="editar(d)">Editar</button>
          <button
            v-if="oferecerPush && d.active"
            type="button"
            class="secundario"
            :disabled="ocupado !== null"
            @click="receberNesteAparelho(d)"
          >
            {{ ocupado === `registrar-${d.id}` ? 'Registrando...' : 'Receber neste aparelho' }}
          </button>
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
  /* No celular o botao desce para baixo do titulo, em vez de espreme-lo. */
  flex-wrap: wrap;
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

.canal + .canal {
  margin-left: 0.3rem;
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
  word-break: break-all;
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

.alcance {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 0.9rem;
  margin: 0.5rem 0 0;
  font-size: 0.78rem;
}

.alcance .sim {
  color: var(--ok);
}

.alcance .nao {
  color: var(--texto-suave);
}

.aparelhos {
  list-style: none;
  margin: 0.7rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.aparelhos li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.6rem;
  flex-wrap: wrap;
  font-size: 0.85rem;
  background: var(--realce);
  border-radius: 6px;
  padding: 0.35rem 0.6rem;
}

.aparelhos li.desligado .aparelho {
  color: var(--texto-suave);
}

.aparelhos small {
  color: var(--falha);
}

.acoes-aparelho {
  display: flex;
  gap: 0.4rem;
}

.acoes-aparelho button {
  font-size: 0.78rem;
  padding: 0.2rem 0.6rem;
}

.rodape {
  margin: 0.6rem 0 0;
  font-size: 0.78rem;
  color: var(--texto-suave);
}

.acoes {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.8rem;
}

/* Os mesmos botoes da tela de monitores: o estilo e scoped em cada view. */
button {
  font: inherit;
  font-size: 0.85rem;
  padding: 0.35rem 0.85rem;
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

.aviso {
  color: var(--ok);
}
</style>
