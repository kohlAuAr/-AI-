import { createRouter, createWebHistory, createMemoryHistory } from 'vue-router';
import DashboardView from './views/DashboardView.vue';
import ClubsView from './views/ClubsView.vue';
import ActivitiesView from './views/ActivitiesView.vue';
import KnowledgeView from './views/KnowledgeView.vue';
import ChatView from './views/ChatView.vue';
import RoadmapView from './views/RoadmapView.vue';
import HomeView from './prototype/HomeView.vue';
import CatalogView from './prototype/CatalogView.vue';
import DetailView from './prototype/DetailView.vue';
import PersonalView from './prototype/PersonalView.vue';
import ManageView from './prototype/ManageView.vue';
import HelpView from './prototype/HelpView.vue';
import LoginView from './prototype/LoginView.vue';
import BusinessPersonalView from './prototype/BusinessPersonalView.vue';
import BusinessManageView from './prototype/BusinessManageView.vue';
import { businessMode } from './prototype/business';

export default createRouter({
  history: import.meta.env.SSR ? createMemoryHistory() : createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView },
    { path: '/clubs', component: CatalogView },
    { path: '/recruitment', component: CatalogView },
    { path: '/clubs/:id', component: DetailView },
    { path: '/activities', component: CatalogView },
    { path: '/activities/:id', component: DetailView },
    { path: '/me', component: businessMode ? BusinessPersonalView : PersonalView },
    { path: '/login', component: LoginView },
    { path: '/messages', component: PersonalView },
    { path: '/manage', component: businessMode ? BusinessManageView : ManageView },
    { path: '/manage/:section(recruitment|members|activities|finance)', component: businessMode ? BusinessManageView : ManageView },
    { path: '/assistant', component: HelpView },
    { path: '/guide', component: HelpView },
    { path: '/system', component: DashboardView },
    { path: '/system/clubs', component: ClubsView },
    { path: '/system/activities', component: ActivitiesView },
    { path: '/system/knowledge', component: KnowledgeView },
    { path: '/system/chat', component: ChatView },
    { path: '/system/roadmap', component: RoadmapView },
    { path: '/knowledge', redirect: '/system/knowledge' },
    { path: '/chat', redirect: '/system/chat' },
    { path: '/roadmap', redirect: '/system/roadmap' },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});
