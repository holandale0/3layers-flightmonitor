import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * Configuracao dos testes.
 *
 * Separada da `vite.config.ts` porque os testes nao precisam do proxy para o
 * core-java nem da configuracao de servidor.
 *
 * **O plugin do Vue precisa estar aqui.** A primeira versao nao o incluia, com
 * a justificativa de que so haveria teste de logica pura. Quando chegou o
 * primeiro teste de componente (`CampoData`), o Vitest nao conseguiu ler o
 * `.vue` — e o arquivo foi simplesmente **ignorado na coleta**, sem quebrar o
 * total. Onze testes desapareceram em silencio, que e a pior forma de um teste
 * falhar: parecendo que nao existe.
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    include: ['src/**/*.spec.ts'],
    // jsdom por causa dos testes de componente (CampoData): eles montam Vue e
    // precisam de DOM. Os testes puros nao se importam com o ambiente.
    environment: 'jsdom',
  },
})
