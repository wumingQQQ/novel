import { createRouter, createWebHistory } from 'vue-router'
import RoleHallPage from '@/views/RoleHallPage.vue'
import RolePreviewPage from '@/views/RolePreviewPage.vue'
import MyRolesPage from '@/views/MyRolesPage.vue'
import MyEvaluationsPage from '@/views/MyEvaluationsPage.vue'
import EvaluationWorkspacePage from '@/views/EvaluationWorkspacePage.vue'
import ChatPage from '@/views/ChatPage.vue'
import CreationPage from '@/views/CreationPage.vue'
import CreationJobPage from '@/views/CreationJobPage.vue'
import NovelDetailPage from '@/views/NovelDetailPage.vue'
import MyJobsPage from '@/views/MyJobsPage.vue'
import MyChatsPage from '@/views/MyChatsPage.vue'

export default createRouter({
  history: createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', name: 'role-hall', component: RoleHallPage },
    { path: '/creation', name: 'creation', component: CreationPage },
    { path: '/my-jobs', name: 'my-jobs', component: MyJobsPage },
    { path: '/my-chats', name: 'my-chats', component: MyChatsPage },
    { path: '/creation/novels/:id', name: 'novel-detail', component: NovelDetailPage, props: true },
    { path: '/creation/jobs/:jobId', name: 'creation-job', component: CreationJobPage, props: true },
    { path: '/roles/:id', name: 'role-preview', component: RolePreviewPage, props: true },
    { path: '/my-roles', name: 'my-roles', component: MyRolesPage },
    { path: '/my-evaluations', name: 'my-evaluations', component: MyEvaluationsPage },
    { path: '/my-evaluations/:requestId', name: 'evaluation-workspace', component: EvaluationWorkspacePage, props: true },
    { path: '/chat/:sessionId', name: 'chat', component: ChatPage, props: true },
  ],
})
