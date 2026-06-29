import { Tracker } from '@giso/tracker-web';
import { APP_KEY, APP_VERSION, TRACK_DEBUG, TRACK_ENDPOINT } from './config';
import { mountDebugPanel } from './debug-panel';
import { renderArticle } from './pages/article';
import { renderFeed } from './pages/feed';
import { onRouteChange, type Route } from './router';
import './styles.css';

let currentPgid = '—';
let teardown: (() => void) | null = null;

const app = document.getElementById('app')!;
const pageRoot = document.createElement('div');
pageRoot.className = 'page-root';
app.appendChild(pageRoot);
app.appendChild(mountDebugPanel(() => currentPgid));

Tracker.init({
  appId: APP_KEY,
  appVersion: APP_VERSION,
  endpoint: TRACK_ENDPOINT,
  channel: 'news-demo',
  debug: TRACK_DEBUG,
});

function show(route: Route) {
  if (teardown) teardown();
  pageRoot.innerHTML = '';
  if (route.name === 'feed') {
    teardown = renderFeed(pageRoot, route.cat ?? 'sports', (p) => {
      currentPgid = p;
    });
  } else {
    teardown = renderArticle(pageRoot, route.aid, (p) => {
      currentPgid = p;
    });
  }
}

onRouteChange(show);
