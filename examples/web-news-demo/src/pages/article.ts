import { Elements, Pages, Params, Tracker } from '@giso/tracker-web';
import { findArticle } from '../catalog';
import { ReadTracker } from '../read-tracker';
import { navigate } from '../router';

export function renderArticle(root: HTMLElement, aid: string, onPgid: (pgid: string) => void): () => void {
  const article = findArticle(aid);
  if (!article) {
    root.innerHTML = `<div class="empty">文章不存在 <a href="#/feed">返回资讯流</a></div>`;
    onPgid('—');
    return () => {};
  }

  root.innerHTML = `
    <header class="article-top">
      <button type="button" class="back-btn">← 返回</button>
      <button type="button" class="share-btn top-share">分享</button>
    </header>
    <article class="article-detail">
      <span class="card-cat">${article.newsCat}</span>
      <h1>${article.title}</h1>
      <p class="article-meta">${article.author} · ${article.publishedAt}</p>
      <div class="article-cover" style="--hue:${article.coverHue}"></div>
      <div class="article-content"></div>
      <section class="related">
        <h3>相关推荐</h3>
        <div class="related-list"></div>
      </section>
    </article>
  `;

  const contentEl = root.querySelector('.article-content')!;
  article.body.forEach((p) => {
    const el = document.createElement('p');
    el.textContent = p;
    contentEl.appendChild(el);
  });

  const relatedEl = root.querySelector('.related-list')!;
  article.related.forEach((rel, i) => {
    const item = document.createElement('button');
    item.type = 'button';
    item.className = 'related-item';
    item.textContent = rel.title;
    Tracker.get().bind(item, {
      eid: Elements.RELATED_ARTICLE,
      pos: i + 1,
      params: { [Params.AID]: rel.aid },
    });
    item.addEventListener('click', () => {
      navigate({ name: 'article', aid: rel.aid });
    });
    relatedEl.appendChild(item);
  });

  const topShare = root.querySelector('.top-share')!;
  Tracker.get().bind(topShare, {
    eid: Elements.SHARE_BTN,
    params: { [Params.AID]: article.aid },
  });

  root.querySelector('.back-btn')!.addEventListener('click', () => {
    navigate({ name: 'feed', cat: article.newsCat });
  });

  Tracker.get().enterPage(Pages.NEWS_ARTICLE, {
    [Params.AID]: article.aid,
    [Params.NEWS_CAT]: article.newsCat,
  });
  onPgid(Pages.NEWS_ARTICLE);

  const readTracker = new ReadTracker(article.aid, article.newsCat);

  return () => {
    readTracker.finish();
    Tracker.get().exitPage();
    readTracker.destroy();
  };
}
