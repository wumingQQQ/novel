import { createRouter, createWebHistory } from 'vue-router'
import RoleHallPage from '@/views/RoleHallPage.vue'
import RolePreviewPage from '@/views/RolePreviewPage.vue'
import MyRolesPage from '@/views/MyRolesPage.vue'
import MyEvaluationsPage from '@/views/MyEvaluationsPage.vue'
import EvaluationWorkspacePage from '@/views/EvaluationWorkspacePage.vue'
import ChatPage from '@/views/ChatPage.vue'

export default createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'role-hall', component: RoleHallPage },
    { path: '/roles/:id', name: 'role-preview', component: RolePreviewPage, props: true },
    { path: '/my-roles', name: 'my-roles', component: MyRolesPage },
    { path: '/my-evaluations', name: 'my-evaluations', component: MyEvaluationsPage },
    { path: '/my-evaluations/:characterId', name: 'evaluation-workspace', component: EvaluationWorkspacePage, props: true },
    { path: '/chat/:sessionId', name: 'chat', component: ChatPage, props: true },
  ],
})
