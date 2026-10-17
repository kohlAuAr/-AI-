import { renderToString as renderVue } from '@vue/server-renderer';
import { setup } from '@css-render/vue3-ssr';

// Naive UI renders its actual components/styles on the server; no DOM or component stubs.
export function renderToString(app) {
  setup(app);
  return renderVue(app);
}
