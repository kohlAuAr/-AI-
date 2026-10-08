import { createRouter, createWebHashHistory } from 'vue-router';
import CommunityHomeView from './community/CommunityHomeView.vue';
import CommunityPersonalView from './community/CommunityPersonalView.vue';
import CommunityAssistantView from './community/CommunityAssistantView.vue';
import CatalogView from './prototype/CatalogView.vue';
import CommunityClubsView from './community/CommunityClubsView.vue';
import DetailView from './prototype/DetailView.vue';
import PersonalView from './prototype/PersonalView.vue';
import ManageView from './prototype/ManageView.vue';
import HelpView from './prototype/HelpView.vue';

// Hash routes also survive reload on a static-only host without server routing.
export default createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: CommunityHomeView },
    { path: '/clubs', component: CommunityClubsView },
    { path: '/clubs/:id', component: DetailView },
    { path: '/recruitment', redirect: to => ({ path: '/clubs', query: { ...to.query, recruiting: 'true' }, hash: to.hash }) },
    { path: '/activities', component: CatalogView },
    { path: '/activities/:id', component: DetailView },
    { path: '/me', component: CommunityPersonalView },
    { path: '/messages', component: PersonalView },
    { path: '/manage', component: ManageView },
    { path: '/manage/:section(recruitment|members|activities|finance)', component: ManageView },
    { path: '/assistant', component: CommunityAssistantView },
    { path: '/guide', component: HelpView },
    { path: '/:pathMatch(.*)*', redirect: '/guide' }
  ]
});
