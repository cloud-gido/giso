import { Elements, Pages, Params, Tracker } from '@giso/tracker-web';
import { articlesByCat, NEWS_CATEGORIES, type NewsArticle } from '../catalog';
import { navigate } from '../router';

export function renderFeed(root: HTMLElement, cat: string, onPgid: (pgid: string) => void): () => void {
  const recTraceId = `rec-${crypto.randomUUID()}`;
  let currentCat = cat;

  root.innerHTML = `
    <header class="topbar">
      <h1>资讯</h1>
      <p class="subtitle">GISO Web 埋点演示 · news_feed / news_article</p>
    </header>
    <nav class="tabs" role="tablist"></nav>
    <main class="feed-list" aria-label="资讯列表"></main>
  `;

  const tabsEl = root.querySelector('.tabs')!;
  const listEl = root.querySelector('.feed-list')!;

  NEWS_CATEGORIES.forEach((c) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'tab' + (c.id === currentCat ? ' active' : '');
    btn.textContent = c.label;
    btn.dataset.cat = c.id;
    btn.addEventListener('click', () => {
      if (c.id === currentCat) return;
      navigate({ name: 'feed', cat: c.id });
    });
    tabsEl.appendChild(btn);
  });

  function enterFeed() {
    Tracker.get().enterPage(
      Pages.NEWS_FEED,
      { [Params.NEWS_CAT]: currentCat, tab_name: currentCat },
      { rec_trace_id: recTraceId },
    );
    onPgid(Pages.NEWS_FEED);
    renderCards();
  }

  function renderCards() {
    tabsEl.querySelectorAll('.tab').forEach((el) => {
      el.classList.toggle('active', (el as HTMLElement).dataset.cat === currentCat);
    });
    listEl.innerHTML = '';
    const items = articlesByCat(currentCat);
    items.forEach((article, index) => {
      listEl.appendChild(buildCard(article, index + 1, recTraceId));
    });
  }

  enterFeed();

  return () => {
    Tracker.get().exitPage();
    listEl.innerHTML = '';
  };
}

function buildCard(article: NewsArticle, pos: number, recTraceId: string): HTMLElement {
  const card = document.createElement('article');
  card.className = 'article-card';
  card.style.setProperty('--hue', String(article.coverHue));
  card.innerHTML = `
    <div class="card-thumb"></div>
    <div class="card-body">
      <span class="card-cat">${article.newsCat}</span>
      <h2 class="card-title">${article.title}</h2>
      <p class="card-summary">${article.summary}</p>
      <div class="card-meta">${article.author} · ${article.publishedAt} · ${article.readMin} 分钟</div>
      <button type="button" class="share-btn" aria-label="分享">分享</button>
    </div>
  `;

  Tracker.get().bind(card, {
    eid: Elements.ARTICLE_CARD,
    pos,
    params: {
      [Params.AID]: article.aid,
      [Params.NEWS_CAT]: article.newsCat,
      rec_trace_id: recTraceId,
    },
  });

  const shareBtn = card.querySelector('.share-btn')!;
  Tracker.get().bind(shareBtn, {
    eid: Elements.SHARE_BTN,
    params: { [Params.AID]: article.aid },
  });

  card.addEventListener('click', (e) => {
    if ((e.target as HTMLElement).closest('.share-btn')) return;
    navigate({ name: 'article', aid: article.aid });
  });

  return card;
}
