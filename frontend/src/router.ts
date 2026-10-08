import { createRouter, createWebHistory } from 'vue-router';
import DashboardView from './views/DashboardView.vue';
import ClubsView from './views/ClubsView.vue';
import ActivitiesView from './views/ActivitiesView.vue';
import KnowledgeView from './views/KnowledgeView.vue';
import ChatView from './views/ChatView.vue';
import RoadmapView from './views/RoadmapView.vue';

export default createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: DashboardView },
    { path: '/clubs', component: ClubsView },
    { path: '/activities', component: ActivitiesView },
    { path: '/knowledge', component: KnowledgeView },
    { path: '/chat', component: ChatView },
    { path: '/roadmap', component: RoadmapView },
    { path: '/:pathMatch(.*)*', redirect: '/' }
  ]
});
