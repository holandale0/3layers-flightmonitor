<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
</script>

<template>
  <div class="app">
    <header class="barra">
      <span class="marca">✈️ Flight Monitor</span>
      <nav>
        <RouterLink to="/monitores">Monitores</RouterLink>
        <RouterLink to="/destinatarios">Destinatários</RouterLink>
        <RouterLink to="/status">Status</RouterLink>
      </nav>
    </header>

    <main>
      <RouterView />
    </main>
  </div>
</template>

<style scoped>
.barra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1.5rem;
  border-bottom: 1px solid var(--borda);
  flex-wrap: wrap;
}

.marca {
  font-weight: 600;
}

nav {
  display: flex;
  gap: 0.4rem;
}

nav a {
  color: var(--texto-suave);
  text-decoration: none;
  font-size: 0.9rem;
  padding: 0.3rem 0.7rem;
  border-radius: 6px;
}

nav a:hover {
  background: var(--realce);
}

nav a.router-link-active {
  color: var(--texto);
  background: var(--realce);
}

main {
  padding: 2rem 1.5rem 4rem;
  max-width: 56rem;
  margin: 0 auto;
}
</style>
