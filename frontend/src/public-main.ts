import { createApp } from 'vue';
import PrototypeShell from './prototype/PrototypeShell.vue';
import router from './public-router';
import './style.css';
import './prototype/prototype.css';

createApp(PrototypeShell).use(router).mount('#app');
