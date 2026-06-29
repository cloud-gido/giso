export type Route =
  | { name: 'feed'; cat?: string }
  | { name: 'article'; aid: string };

export function parseRoute(): Route {
  const hash = location.hash.replace(/^#\/?/, '') || 'feed';
  const [path, query] = hash.split('?');
  const params = new URLSearchParams(query ?? '');
  if (path.startsWith('article/')) {
    return { name: 'article', aid: decodeURIComponent(path.slice('article/'.length)) };
  }
  return { name: 'feed', cat: params.get('cat') ?? 'sports' };
}

export function navigate(route: Route): void {
  if (route.name === 'feed') {
    location.hash = `#/feed?cat=${route.cat ?? 'sports'}`;
    return;
  }
  location.hash = `#/article/${encodeURIComponent(route.aid)}`;
}

export function onRouteChange(cb: (route: Route) => void): void {
  const handler = () => cb(parseRoute());
  window.addEventListener('hashchange', handler);
  handler();
}
