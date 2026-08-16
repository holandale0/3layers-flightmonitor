<script setup lang="ts">
import { computed, onMounted } from 'vue'

import { buscarMonitor } from '@/api/monitores'
import { listarObservacoes } from '@/api/observacoes'
import GraficoPrecos from '@/components/GraficoPrecos.vue'
import { useCarregamento } from '@/composables/useCarregamento'
import { data, dinheiro, duracao, instante } from '@/lib/formato'
import type { Monitor } from '@/model/monitor'
import type { Observacao } from '@/model/observacao'
import { menorPrecoPorData } from '@/model/observacao'

const props = defineProps<{ id: string }>()

// As duas chamadas sao um so estado de tela: nao existe historico sem monitor,
// e mostrar meia tela enquanto a outra metade carrega so pisca.
const { dados, carregando, erro, executar } = useCarregamento(async () => {
  const id = Number(props.id)
  const [monitor, observacoes] = await Promise.all([buscarMonitor(id), listarObservacoes(id)])
  return { monitor, observacoes }
})

const monitor = computed<Monitor | null>(() => dados.value?.monitor ?? null)
const observacoes = computed<Observacao[]>(() => dados.value?.observacoes ?? [])

const porData = computed(() => menorPrecoPorData(observacoes.value))

const menorPreco = computed(() =>
  observacoes.value.length ? Math.min(...observacoes.value.map((o) => o.price)) : null,
)

const precoMedio = computed(() => {
  if (!observacoes.value.length) return null
  return observacoes.value.reduce((s, o) => s + o.price, 0) / observacoes.value.length
})

const confirmadas = computed(() => observacoes.value.filter((o) => o.confirmed).length)

onMounted(executar)
</script>

<template>
  <section>
    <RouterLink to="/monitores" class="voltar">← Monitores</RouterLink>

    <p v-if="carregando" class="msg">Carregando...</p>
    <p v-else-if="erro" class="erro">{{ erro }}</p>

    <template v-else-if="monitor">
      <header>
        <h1>{{ monitor.origin }} → {{ monitor.destination }}</h1>
        <p class="subtitulo">
          {{ monitor.label || 'sem apelido' }} ·
          teto {{ dinheiro(monitor.maxPrice, monitor.currency) }}
        </p>
      </header>

      <div v-if="observacoes.length === 0" class="vazio">
        <p>Nenhuma observação ainda.</p>
        <p class="msg">
          O histórico começa a existir na primeira varredura. Use <strong>Buscar agora</strong>
          na lista de monitores, ou espere o scheduler.
        </p>
      </div>

      <template v-else>
        <!-- Números de manchete: leitura direta, sem precisar de gráfico -->
        <div class="indicadores">
          <div class="indicador">
            <span class="rotulo">Menor preço visto</span>
            <strong class="valor destaque">{{ dinheiro(menorPreco, monitor.currency) }}</strong>
          </div>
          <div class="indicador">
            <span class="rotulo">Preço médio</span>
            <strong class="valor">{{ dinheiro(precoMedio, monitor.currency) }}</strong>
          </div>
          <div class="indicador">
            <span class="rotulo">Observações</span>
            <strong class="valor">{{ observacoes.length }}</strong>
          </div>
          <div class="indicador">
            <span class="rotulo">Confirmadas</span>
            <strong class="valor">{{ confirmadas }}</strong>
          </div>
        </div>

        <div class="cartao">
          <GraficoPrecos
            :dados="porData"
            :teto="monitor.maxPrice"
            :moeda="monitor.currency"
          />
        </div>

        <h2>Observações</h2>
        <p class="msg ajuda">
          Cada linha é um preço visto numa varredura. A mesma data aparece várias vezes
          conforme o preço muda ao longo do tempo — é disso que sai a estatística da Fase 2.
        </p>

        <div class="tabela-rolagem">
          <table>
            <thead>
              <tr>
                <th>Ida</th>
                <th>Volta</th>
                <th class="num">Preço</th>
                <th>Companhia</th>
                <th class="num">Escalas</th>
                <th class="num">Duração</th>
                <th>Fonte</th>
                <th>Observado em</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="o in observacoes" :key="o.id">
                <td>{{ data(o.departureDate) }}</td>
                <td>{{ data(o.returnDate) }}</td>
                <td class="num" :class="{ cabe: o.price <= monitor.maxPrice }">
                  {{ dinheiro(o.price, o.currency) }}
                </td>
                <td>{{ o.airline || '—' }}</td>
                <td class="num">{{ o.stops ?? '—' }}</td>
                <td class="num">{{ duracao(o.durationMinutes) }}</td>
                <td>
                  <span class="fonte" :class="o.confirmed ? 'confirmada' : ''">
                    {{ o.source === 'FAST_FLIGHTS' ? 'confirmado' : 'cache' }}
                  </span>
                </td>
                <td class="instante">{{ instante(o.observedAt) }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </template>
  </section>
</template>

<style scoped>
.voltar {
  color: var(--texto-suave);
  text-decoration: none;
  font-size: 0.85rem;
}

h1 {
  font-size: 1.5rem;
  margin: 0.5rem 0 0;
  font-family: ui-monospace, monospace;
}

h2 {
  font-size: 1.05rem;
  margin: 2rem 0 0.3rem;
}

.subtitulo {
  margin: 0.15rem 0 1.5rem;
  color: var(--texto-suave);
  font-size: 0.85rem;
}

.indicadores {
  display: flex;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 1.5rem;
}

.indicador {
  flex: 1;
  min-width: 8rem;
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 0.8rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.rotulo {
  font-size: 0.75rem;
  color: var(--texto-suave);
}

.valor {
  font-size: 1.25rem;
}

.valor.destaque {
  color: var(--ok);
}

.cartao {
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 1.25rem;
}

.vazio {
  border: 1px dashed var(--borda);
  border-radius: 8px;
  padding: 2rem;
  text-align: center;
}

.tabela-rolagem {
  overflow-x: auto;
  border: 1px solid var(--borda);
  border-radius: 8px;
}

table {
  width: 100%;
  border-collapse: collapse;
  font-size: 0.85rem;
}

th,
td {
  padding: 0.5rem 0.75rem;
  text-align: left;
  border-bottom: 1px solid var(--borda);
  white-space: nowrap;
}

th {
  font-weight: 600;
  font-size: 0.78rem;
  color: var(--texto-suave);
  background: var(--realce);
}

tbody tr:last-child td {
  border-bottom: none;
}

.num {
  text-align: right;
  font-variant-numeric: tabular-nums;
}

td.cabe {
  color: var(--ok);
  font-weight: 600;
}

.fonte {
  font-size: 0.72rem;
  border: 1px solid var(--borda);
  border-radius: 999px;
  padding: 0.1rem 0.5rem;
  color: var(--texto-suave);
}

.fonte.confirmada {
  border-color: var(--ok);
  color: var(--ok);
}

.instante {
  color: var(--texto-suave);
  font-size: 0.8rem;
}

.msg {
  color: var(--texto-suave);
}

.ajuda {
  font-size: 0.82rem;
  margin: 0 0 0.75rem;
}

.erro {
  color: var(--falha);
}
</style>
