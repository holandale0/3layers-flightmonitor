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
        <RouterLink to="/configuracao">Configuração</RouterLink>
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

/*
 * Celular — e o aplicativo Android, que e este mesmo painel num WebView (D-113).
 *
 * Os quatro links nao cabem ao lado da marca numa tela de 360px. Em vez de
 * esconder atras de um menu, que poe um toque a mais em toda navegacao, a barra
 * vira duas linhas e os links dividem a largura igualmente.
 */
@media (max-width: 40rem) {
  .barra {
    padding: 0.6rem 0.75rem;
    gap: 0.4rem;
  }

  nav {
    width: 100%;
    gap: 0.2rem;
  }

  nav a {
    flex: 1;
    text-align: center;
    padding: 0.5rem 0.2rem;
    font-size: 0.82rem;
  }

  main {
    padding: 1.25rem 1rem 3rem;
  }
}
</style>
