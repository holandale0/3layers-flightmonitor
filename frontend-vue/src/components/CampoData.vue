<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { brParaIso, completa, isoParaBr, mascarar } from '@/lib/data'

/**
 * Campo de data em **dd/mm/aaaa**, com valor ISO por dentro.
 *
 * # Por que não `<input type="date">`
 *
 * O nativo mostra a data no formato do **navegador**, e não no da página. Foi
 * verificado com o Chrome em inglês: nem `lang="pt-BR"` no `<html>`, nem no
 * próprio campo, mudam o `mm/dd/yyyy`. Não há atributo que sobreponha isso.
 *
 * Num sistema em português, com datas de viagem, `03/05` significar março ou
 * maio é a diferença entre viajar no outono e no inverno.
 *
 * # O calendário continua aí
 *
 * Trocar por um campo de texto e perder o seletor seria consertar uma coisa
 * quebrando outra — principalmente no celular, onde digitar oito dígitos é bem
 * pior do que tocar num dia. O botão abre o seletor **nativo** via
 * `showPicker()`, num `<input type="date">` que existe só para isso.
 */
const props = defineProps<{
  modelValue: string | null
  required?: boolean
  /** Marca o campo em vermelho quando a API recusou este valor. */
  invalido?: boolean
}>()

const emit = defineEmits<{ 'update:modelValue': [string | null] }>()

const texto = ref(isoParaBr(props.modelValue))
const seletor = ref<HTMLInputElement | null>(null)

/** Digitou algo, ainda não fechou uma data válida. */
const incompleto = computed(() => texto.value.length > 0 && brParaIso(texto.value) === null)

// O valor pode mudar por fora: ao abrir o formulário de edição, ou quando a
// janela de volta é preenchida sozinha ao marcar a opção.
watch(
  () => props.modelValue,
  (iso) => {
    if (brParaIso(texto.value) !== iso) {
      texto.value = isoParaBr(iso)
    }
  },
)

function aoDigitar(evento: Event) {
  const alvo = evento.target as HTMLInputElement
  texto.value = mascarar(alvo.value)
  // O input é controlado: sem isto, apagar no meio da string deixaria o cursor
  // e o valor fora de sincronia.
  alvo.value = texto.value

  if (completa(texto.value)) {
    emit('update:modelValue', brParaIso(texto.value))
  } else if (texto.value === '') {
    emit('update:modelValue', null)
  }
}

function aoSair() {
  // Data pela metade não vira valor. Limpar é melhor que guardar algo que a
  // pessoa não terminou de escrever e vai achar que salvou.
  if (texto.value !== '' && brParaIso(texto.value) === null) {
    texto.value = isoParaBr(props.modelValue)
  }
}

function abrirCalendario() {
  const campo = seletor.value
  if (!campo) return
  campo.value = props.modelValue ?? ''
  campo.showPicker?.()
}

function aoEscolherNoCalendario(evento: Event) {
  const iso = (evento.target as HTMLInputElement).value || null
  texto.value = isoParaBr(iso)
  emit('update:modelValue', iso)
}
</script>

<template>
  <span class="campo-data" :class="{ erro: invalido || incompleto }">
    <input
      :value="texto"
      type="text"
      inputmode="numeric"
      placeholder="dd/mm/aaaa"
      maxlength="10"
      autocomplete="off"
      :required="required"
      @input="aoDigitar"
      @blur="aoSair"
    />

    <button
      type="button"
      class="calendario"
      title="Escolher no calendário"
      aria-label="Escolher no calendário"
      @click="abrirCalendario"
    >
      📅
    </button>

    <!--
      Só existe para hospedar o seletor nativo. Fica fora do fluxo visual, mas
      renderizado: `showPicker()` não abre em elemento com `display: none`.
    -->
    <input
      ref="seletor"
      type="date"
      class="seletor-nativo"
      tabindex="-1"
      aria-hidden="true"
      @change="aoEscolherNoCalendario"
    />
  </span>
</template>

<style scoped>
.campo-data {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.3rem;
}

input[type='text'] {
  flex: 1;
  min-width: 0;
  padding: 0.5rem 0.6rem;
  border: 1px solid var(--borda);
  border-radius: 6px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-variant-numeric: tabular-nums;
}

.campo-data.erro input[type='text'] {
  border-color: var(--falha);
}

.calendario {
  padding: 0.35rem 0.5rem;
  border: 1px solid var(--borda);
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  font-size: 0.95rem;
  line-height: 1;
}

.calendario:hover {
  border-color: var(--texto-suave);
}

.seletor-nativo {
  position: absolute;
  bottom: 0;
  left: 0.6rem;
  width: 1px;
  height: 1px;
  opacity: 0;
  pointer-events: none;
  border: 0;
  padding: 0;
}
</style>
