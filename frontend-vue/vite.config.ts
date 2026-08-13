import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    strictPort: true, // falha alto em vez de trocar de porta silenciosamente
    proxy: {
      // Sem rewrite: o caminho no navegador e o mesmo do servidor. Isso evita
      // ter que traduzir URLs ao depurar e ao publicar o front separado.
      // O front conversa apenas com o core-java — o worker-python nao e
      // exposto ao navegador. Ver docs/PLANO-DE-ACAO.md secao 3.
      '/api': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
      '/actuator': {
        target: 'http://localhost:8081',
        changeOrigin: true,
      },
    },
  },
})
