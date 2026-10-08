import { createApp } from 'vue';
import App from './App.vue';
import router from './public-router';
import './style.css';
import './prototype/prototype.css';

createApp(App).use(router).mount('#app');
