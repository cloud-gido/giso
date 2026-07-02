/**
 * ViewTree capture for visual registry (Web SDK v2 plugin).
 * Walk DOM, collect normalized bounds and suggested eids from data-giso-eid / id / class.
 */
export interface ViewBounds {
  x: number;
  y: number;
  w: number;
  h: number;
}

export interface ViewTreeNode {
  tag: string;
  label: string;
  suggested_eid: string;
  bounds: ViewBounds;
  selector: string;
  children?: ViewTreeNode[];
}

function normRect(r: DOMRect, vw: number, vh: number): ViewBounds {
  return {
    x: Math.max(0, r.left / vw),
    y: Math.max(0, r.top / vh),
    w: Math.min(1, r.width / vw),
    h: Math.min(1, r.height / vh),
  };
}

function suggestEid(el: Element): string {
  const g = el.getAttribute('data-giso-eid');
  if (g) return g;
  if (el.id) return el.id.replace(/-/g, '_').toLowerCase();
  const cls = el.className && typeof el.className === 'string'
    ? el.className.split(/\s+/).find((c) => /^[a-z][a-z0-9_-]*$/i.test(c))
    : '';
  if (cls) return cls.replace(/-/g, '_').toLowerCase();
  return el.tagName.toLowerCase();
}

function cssPath(el: Element): string {
  if (el.id) return `#${CSS.escape(el.id)}`;
  const parts: string[] = [];
  let cur: Element | null = el;
  while (cur && cur.nodeType === 1 && parts.length < 5) {
    let sel = cur.tagName.toLowerCase();
    if (cur.id) {
      parts.unshift(`#${CSS.escape(cur.id)}`);
      break;
    }
    const parent: Element | null = cur.parentElement;
    if (parent) {
      const current = cur;
      const siblings = [...parent.children].filter((c) => c.tagName === current.tagName);
      if (siblings.length > 1) sel += `:nth-of-type(${siblings.indexOf(current) + 1})`;
    }
    parts.unshift(sel);
    cur = parent;
  }
  return parts.join(' > ');
}

/** Flat list of visible interactive-ish nodes (buttons, links, inputs, [data-giso-eid]). */
export function captureViewTree(root: Element | Document = document, max = 80): ViewTreeNode[] {
  const doc = root instanceof Document ? root : root.ownerDocument!;
  const vw = doc.documentElement.clientWidth || window.innerWidth;
  const vh = doc.documentElement.clientHeight || window.innerHeight;
  const sel = 'button,a,input,textarea,select,[data-giso-eid],[role="button"]';
  const nodes: ViewTreeNode[] = [];
  (root instanceof Document ? root : root).querySelectorAll(sel).forEach((el) => {
    if (nodes.length >= max) return;
    const r = el.getBoundingClientRect();
    if (r.width < 8 || r.height < 8 || r.bottom < 0 || r.right < 0) return;
    const label = (el.getAttribute('aria-label') || (el as HTMLElement).innerText || '').trim().slice(0, 80);
    nodes.push({
      tag: el.tagName.toLowerCase(),
      label,
      suggested_eid: suggestEid(el),
      bounds: normRect(r, vw, vh),
      selector: cssPath(el),
    });
  });
  return nodes;
}

/** JSON string for admin visual picker import. */
export function exportViewTreeJson(root?: Element | Document): string {
  return JSON.stringify(captureViewTree(root), null, 2);
}
