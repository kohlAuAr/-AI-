import { createRouter, createWebHashHistory } from 'vue-router';
import HomeView from './prototype/HomeView.vue';
import CatalogView from './prototype/CatalogView.vue';
import DetailView from './prototype/DetailView.vue';
import PersonalView from './prototype/PersonalView.vue';
import ManageView from './prototype/ManageView.vue';
import HelpView from './prototype/HelpView.vue';

// Hash routes also survive reload on a static-only host without server routing.
export default createRouter({
  history: createWebHashHistory(),
  scrollBehavior: () => ({ top: 0 }),
  routes: [
    { path: '/', component: HomeView },
    { path: '/clubs', component: CatalogView },
    { path: '/clubs/:id', component: DetailView },
    { path: '/recruitment', component: CatalogView },
    { path: '/activities', component: CatalogView },
    { path: '/activities/:id', component: DetailView },
    { path: '/me', component: PersonalView },
    { path: '/messages', component: PersonalView },
    { path: '/manage', component: ManageView },
    { path: '/manage/:section(recruitment|members|activities|finance)', component: ManageView },
    { path: '/assistant', component: HelpView },
    { path: '/guide', component: HelpView },
    { path: '/:pathMatch(.*)*', redirect: '/guide' }
  ]
});
