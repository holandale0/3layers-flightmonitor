<script setup lang="ts">
import StatusPanel from '@/components/StatusPanel.vue'
</script>

<template>
  <section>
    <h1>Status do sistema</h1>
    <p class="subtitulo">Saúde da API, do banco e das fontes de preço.</p>
    <StatusPanel />
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
</style>
