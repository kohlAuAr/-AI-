import { createRouter, createWebHistory, createMemoryHistory } from 'vue-router';
import DashboardView from './views/DashboardView.vue';
import ClubsView from './views/ClubsView.vue';
import ActivitiesView from './views/ActivitiesView.vue';
import KnowledgeView from './views/KnowledgeView.vue';
import ChatView from './views/ChatView.vue';
import RoadmapView from './views/RoadmapView.vue';
import CommunityHomeView from './community/CommunityHomeView.vue';
import CatalogView from './prototype/CatalogView.vue';
import CommunityClubsView from './community/CommunityClubsView.vue';
import CommunityActivitiesView from './community/CommunityActivitiesView.vue';
import CommunityActivityDetail from './community/CommunityActivityDetail.vue';
import CommunityManageActivities from './community/CommunityManageActivities.vue';
import CommunityMessagesView from './community/CommunityMessagesView.vue';
import DetailView from './prototype/DetailView.vue';
import PersonalView from './prototype/PersonalView.vue';
import ManageView from './prototype/ManageView.vue';
import HelpView from './prototype/HelpView.vue';
import LoginView from './prototype/LoginView.vue';
import CommunityPersonalView from './community/CommunityPersonalView.vue';
import CommunityAssistantView from './community/CommunityAssistantView.vue';
import BusinessManageView from './prototype/BusinessManageView.vue';
import { businessMode } from './prototype/business';

export default createRouter({
  history: import.meta.env.SSR ? createMemoryHistory() : createWebHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: CommunityHomeView },
    { path: '/clubs', component: CommunityClubsView },
    { path: '/recruitment', component: CommunityClubsView },
    { path: '/clubs/:id', component: DetailView },
    { path: '/activities', component: businessMode ? CommunityActivitiesView : CatalogView },
    { path: '/activities/:id', component: businessMode ? CommunityActivityDetail : DetailView },
    { path: '/me', component: CommunityPersonalView },
    { path: '/login', component: LoginView },
    { path: '/messages', component: businessMode ? CommunityMessagesView : PersonalView },
    { path: '/manage', component: businessMode ? BusinessManageView : ManageView },
    { path: '/manage/activities', component: businessMode ? CommunityManageActivities : ManageView },
    { path: '/manage/:section(recruitment|members|activities|finance)', component: businessMode ? BusinessManageView : ManageView },
    { path: '/assistant', component: CommunityAssistantView },
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
