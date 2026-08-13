<script setup lang="ts">
import { computed, ref } from 'vue'

import { data, dataCurta, dinheiro as formatarDinheiro } from '@/lib/formato'
import type { MenorPorData } from '@/model/observacao'

const props = defineProps<{
  dados: MenorPorData[]
  teto: number
  moeda: string
}>()

// Coordenadas do desenho. O SVG usa viewBox, entao estas unidades sao
// proporcionais e o grafico acompanha a largura disponivel.
const L = { w: 760, h: 300, esq: 64, dir: 16, topo: 20, base: 44 }

const areaW = L.w - L.esq - L.dir
const areaH = L.h - L.topo - L.base

const hover = ref<number | null>(null)

const maxValor = computed(() => {
  const precos = props.dados.map((d) => d.preco)
  // O teto entra na escala para a linha de referencia nunca sair do quadro.
  return Math.max(...precos, props.teto) * 1.08
})

/** Ate 24px de espessura, com 2px de respiro entre barras vizinhas. */
const larguraBarra = computed(() => {
  const faixa = areaW / Math.max(props.dados.length, 1)
  return Math.min(24, Math.max(3, faixa - 2))
})

function x(i: number) {
  const faixa = areaW / Math.max(props.dados.length, 1)
  return L.esq + i * faixa + (faixa - larguraBarra.value) / 2
}

function alturaBarra(preco: number) {
  return Math.max(2, (preco / maxValor.value) * areaH)
}

function y(preco: number) {
  return L.topo + areaH - alturaBarra(preco)
}

const yTeto = computed(() => L.topo + areaH - (props.teto / maxValor.value) * areaH)

/** Quatro linhas de grade, incluindo a base. */
const grades = computed(() => {
  const passos = 4
  return Array.from({ length: passos + 1 }, (_, i) => {
    const valor = (maxValor.value / passos) * i
    return { valor, y: L.topo + areaH - (valor / maxValor.value) * areaH }
  })
})

const maisBarato = computed(() => {
  if (props.dados.length === 0) return null
  return props.dados.reduce((a, b) => (b.preco < a.preco ? b : a))
})

// O grafico arredonda para o real cheio: centavos no rotulo do eixo so poluem.
const dinheiro = (v: number) => formatarDinheiro(v, props.moeda, true)
const indiceMaisBarato = computed(() =>
  maisBarato.value ? props.dados.indexOf(maisBarato.value) : -1,
)

const cabemNoTeto = computed(() => props.dados.filter((d) => d.preco <= props.teto).length)

function cabe(d: MenorPorData) {
  return d.preco <= props.teto
}

/** Rotula no maximo ~10 datas no eixo, para os textos nao colidirem. */
const passoRotulo = computed(() => Math.max(1, Math.ceil(props.dados.length / 10)))
</script>

<template>
  <figure class="grafico">
    <figcaption>
      <strong>Menor preço por data de partida</strong>
      <span class="legenda">
        <span class="chave"><i class="marca cabe"></i>cabe no teto ({{ cabemNoTeto }})</span>
        <span class="chave"><i class="marca acima"></i>acima do teto</span>
        <span class="chave"><i class="marca linha-teto"></i>seu limite</span>
      </span>
    </figcaption>

    <div class="tela">
      <svg :viewBox="`0 0 ${L.w} ${L.h}`" role="img" preserveAspectRatio="xMidYMid meet">
        <title>
          Menor preço observado para cada data de partida, comparado ao limite de
          {{ dinheiro(teto) }}
        </title>

        <!-- Grade recessiva: 1px, sólida, um passo fora da superfície -->
        <g class="grade">
          <line
            v-for="g in grades"
            :key="g.valor"
            :x1="L.esq"
            :x2="L.w - L.dir"
            :y1="g.y"
            :y2="g.y"
          />
          <text v-for="g in grades" :key="`t${g.valor}`" :x="L.esq - 10" :y="g.y + 4">
            {{ dinheiro(g.valor) }}
          </text>
        </g>

        <!-- Barras: 4px arredondado no topo, quadrado na base -->
        <g>
          <path
            v-for="(d, i) in dados"
            :key="d.data"
            :class="['barra', cabe(d) ? 'cabe' : 'acima', { apagada: hover !== null && hover !== i }]"
            :d="`M ${x(i)} ${L.topo + areaH}
                 L ${x(i)} ${y(d.preco) + 4}
                 Q ${x(i)} ${y(d.preco)} ${x(i) + 4} ${y(d.preco)}
                 L ${x(i) + larguraBarra - 4} ${y(d.preco)}
                 Q ${x(i) + larguraBarra} ${y(d.preco)} ${x(i) + larguraBarra} ${y(d.preco) + 4}
                 L ${x(i) + larguraBarra} ${L.topo + areaH} Z`"
            @mouseenter="hover = i"
            @mouseleave="hover = null"
          />
        </g>

        <!-- Linha do teto: referência, não série -->
        <g class="teto">
          <line :x1="L.esq" :x2="L.w - L.dir" :y1="yTeto" :y2="yTeto" />
          <text :x="L.w - L.dir" :y="yTeto - 6" text-anchor="end">
            limite {{ dinheiro(teto) }}
          </text>
        </g>

        <!-- Rótulo direto só no extremo: o mais barato -->
        <g v-if="maisBarato" class="destaque">
          <text
            :x="x(indiceMaisBarato) + larguraBarra / 2"
            :y="y(maisBarato.preco) - 8"
            text-anchor="middle"
          >
            {{ dinheiro(maisBarato.preco) }}
          </text>
        </g>

        <!-- Eixo de datas, ralo o bastante para não colidir -->
        <g class="eixo-x">
          <text
            v-for="(d, i) in dados"
            v-show="i % passoRotulo === 0"
            :key="d.data"
            :x="x(i) + larguraBarra / 2"
            :y="L.topo + areaH + 18"
            text-anchor="middle"
          >
            {{ dataCurta(d.data) }}
          </text>
        </g>
      </svg>

      <div v-if="hover !== null" class="dica">
        <strong>{{ data(dados[hover].data) }}</strong>
        <span>{{ dinheiro(dados[hover].preco) }}</span>
        <small>
          {{ dados[hover].observacoes }} observação(ões) ·
          {{ dados[hover].confirmado ? 'confirmado' : 'não confirmado' }} ·
          {{ cabe(dados[hover]) ? 'cabe no teto' : 'acima do teto' }}
        </small>
      </div>
    </div>
  </figure>
</template>

<style scoped>
/*
 * Paleta validada com scripts/validate_palette.js:
 *   light  #2a78d6 / #93928a — CVD ΔE 16.8, contraste >= 3:1 nos dois
 *   dark   #3987e5 / #7a7973 — CVD ΔE 17.5, contraste >= 3:1 nos dois
 * O cinza e de-enfase, nao uma serie: as checagens de banda de luminancia e
 * croma do validador nao se aplicam a ele.
 */
.grafico {
  --serie: #2a78d6;
  --de-enfase: #93928a;
  --grade: #e6e5e0;
  margin: 0;
}

@media (prefers-color-scheme: dark) {
  .grafico {
    --serie: #3987e5;
    --de-enfase: #7a7973;
    --grade: #2c3038;
  }
}

figcaption {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
}

.legenda {
  display: flex;
  gap: 0.9rem;
  flex-wrap: wrap;
  font-size: 0.78rem;
  color: var(--texto-suave);
}

.chave {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

.marca {
  width: 10px;
  height: 10px;
  border-radius: 2px;
  display: inline-block;
}

.marca.cabe {
  background: var(--serie);
}

.marca.acima {
  background: var(--de-enfase);
}

.marca.linha-teto {
  height: 2px;
  border-radius: 0;
  background: repeating-linear-gradient(
    90deg,
    var(--texto-suave) 0 4px,
    transparent 4px 7px
  );
}

.tela {
  position: relative;
}

svg {
  width: 100%;
  height: auto;
  display: block;
  overflow: visible;
}

.grade line {
  stroke: var(--grade);
  stroke-width: 1;
}

.grade text {
  fill: var(--texto-suave);
  font-size: 11px;
  text-anchor: end;
}

.barra {
  cursor: pointer;
  transition: opacity 0.12s;
}

.barra.cabe {
  fill: var(--serie);
}

.barra.acima {
  fill: var(--de-enfase);
}

.barra.apagada {
  opacity: 0.45;
}

.teto line {
  stroke: var(--texto-suave);
  stroke-width: 2;
  stroke-dasharray: 5 4;
}

.teto text {
  fill: var(--texto-suave);
  font-size: 11px;
}

.destaque text {
  fill: var(--texto);
  font-size: 12px;
  font-weight: 600;
}

.eixo-x text {
  fill: var(--texto-suave);
  font-size: 11px;
}

.dica {
  position: absolute;
  top: 0;
  right: 0;
  background: var(--fundo);
  border: 1px solid var(--borda);
  border-radius: 6px;
  padding: 0.5rem 0.7rem;
  font-size: 0.82rem;
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  pointer-events: none;
  box-shadow: 0 2px 8px rgb(0 0 0 / 0.08);
}

.dica small {
  color: var(--texto-suave);
  font-size: 0.75rem;
}
</style>
