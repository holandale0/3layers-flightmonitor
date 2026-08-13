import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vitest/config'

/**
 * Configuracao dos testes.
 *
 * Separada da `vite.config.ts` de proposito: os testes nao precisam do plugin
 * do Vue nem do proxy para o core-java. O que testamos aqui e logica pura —
 * formatacao, estado de carregamento e as camadas — e nao renderizacao.
 *
 * O alias `@` e repetido porque e a unica coisa da build que os testes usam.
 */
export default defineConfig({
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    include: ['src/**/*.spec.ts'],
    environment: 'node',
  },
})
