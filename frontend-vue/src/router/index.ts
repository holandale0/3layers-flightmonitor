import { createRouter, createWebHistory } from 'vue-router'

import MonitoresView from '@/views/MonitoresView.vue'

/**
 * A rota de historico ja existe aqui, mas a tela chega na etapa E1.14.
 * Ate la, o link do cartao leva a um placeholder honesto em vez de 404.
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
    { path: '/status', name: 'status', component: () => import('@/views/StatusView.vue') },
    { path: '/:pathMatch(.*)*', redirect: '/monitores' },
  ],
})

export default router
