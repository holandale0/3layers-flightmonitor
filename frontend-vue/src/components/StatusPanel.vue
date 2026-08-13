<script setup lang="ts">
import { onMounted } from 'vue'

import { verificarSaude } from '@/api/saude'
import { useCarregamento } from '@/composables/useCarregamento'

const { dados: health, carregando, erro, executar: verificar } = useCarregamento(verificarSaude)

onMounted(verificar)
</script>

<template>
  <section class="painel">
    <header>
      <h2>Status do sistema</h2>
      <button type="button" :disabled="carregando" @click="verificar">
        {{ carregando ? 'Verificando...' : 'Verificar' }}
      </button>
    </header>

    <p v-if="carregando" class="msg">Consultando a API...</p>

    <p v-else-if="erro" class="msg erro">
      {{ erro }}
      <small>Suba o core-java na porta 8081 e o PostgreSQL com <code>docker compose up -d</code>.</small>
    </p>

    <ul v-else-if="health" class="itens">
      <li>
        <span class="rotulo">core-java</span>
        <span class="valor" :class="health.status === 'UP' ? 'ok' : 'falha'">{{ health.status }}</span>
      </li>
      <li v-for="(comp, nome) in health.components" :key="nome">
        <span class="rotulo">{{ nome }}</span>
        <span class="valor" :class="comp.status === 'UP' ? 'ok' : 'falha'">{{ comp.status }}</span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.painel {
  border: 1px solid var(--borda);
  border-radius: 8px;
  padding: 1.25rem 1.5rem;
  max-width: 32rem;
}

header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 1rem;
}

h2 {
  font-size: 1rem;
  margin: 0;
}

button {
  font: inherit;
  font-size: 0.85rem;
  padding: 0.35rem 0.85rem;
  border: 1px solid var(--borda);
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

button:disabled {
  opacity: 0.5;
  cursor: default;
}

.msg {
  margin: 0;
  color: var(--texto-suave);
}

.msg.erro {
  color: var(--falha);
}

.msg small {
  display: block;
  margin-top: 0.5rem;
  color: var(--texto-suave);
}

.itens {
  list-style: none;
  margin: 0;
  padding: 0;
}

.itens li {
  display: flex;
  justify-content: space-between;
  padding: 0.45rem 0;
  border-bottom: 1px solid var(--borda);
}

.itens li:last-child {
  border-bottom: none;
}

.rotulo {
  font-family: ui-monospace, monospace;
  font-size: 0.9rem;
}

.valor {
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.valor.ok {
  color: var(--ok);
}

.valor.falha {
  color: var(--falha);
}
</style>
