import { createRouter, createWebHistory } from 'vue-router'

import MonitoresView from '@/views/MonitoresView.vue'

/**
 * Carregamento sob demanda em tudo menos a tela inicial: `MonitoresView` e o
 * destino do `/`, entao adiar o carregamento dela so adicionaria um salto.
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/monitores' },
    { path: '/monitores', name: 'monitores', component: MonitoresView },
    {
      path: '/monitores/:id',
      name: 'historico',
      component: () => import('@/views/HistoricoView.vue'),
      props: true,
    },
    {
      path: '/destinatarios',
      name: 'destinatarios',
      component: () => import('@/views/DestinatariosView.vue'),
    },
    { path: '/status', name: 'status', component: () => import('@/views/StatusView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/monitores' },
  ],
})

export default router
