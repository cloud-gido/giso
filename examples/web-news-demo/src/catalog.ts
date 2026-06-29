export interface NewsArticle {
  aid: string;
  title: string;
  summary: string;
  newsCat: string;
  author: string;
  publishedAt: string;
  readMin: number;
  coverHue: number;
  body: string[];
  related: { aid: string; title: string; newsCat: string }[];
}

const bodies = (n: number) =>
  Array.from({ length: n }, (_, i) =>
    `第 ${i + 1} 段：本文演示资讯阅读埋点。滚动页面会累计 read_pct，离开详情页时上报 news_read（read_dur + read_pct），可在管理台实时联调查看校验结果。`);

export const NEWS_CATEGORIES = [
  { id: 'sports', label: '体育' },
  { id: 'tech', label: '科技' },
  { id: 'finance', label: '财经' },
] as const;

export const ARTICLES: NewsArticle[] = [
  {
    aid: 'news-1001',
    title: '主队加时绝杀晋级，球迷现场沸腾',
    summary: '季后赛关键一战，主队凭借末节 12-0 攻势完成逆转。',
    newsCat: 'sports',
    author: '体育前线',
    publishedAt: '2026-06-16 09:00',
    readMin: 4,
    coverHue: 210,
    body: bodies(8),
    related: [
      { aid: 'news-1004', title: '伤病报告更新：核心后卫出战成疑', newsCat: 'sports' },
      { aid: 'news-2001', title: '端侧大模型推理延迟再降 30%', newsCat: 'tech' },
    ],
  },
  {
    aid: 'news-1002',
    title: '转会窗首日：豪门官宣中场加盟',
    summary: '俱乐部官方宣布签下国脚级中场，转会费创队史纪录。',
    newsCat: 'sports',
    author: '转会速递',
    publishedAt: '2026-06-16 08:30',
    readMin: 3,
    coverHue: 195,
    body: bodies(6),
    related: [
      { aid: 'news-1001', title: '主队加时绝杀晋级，球迷现场沸腾', newsCat: 'sports' },
    ],
  },
  {
    aid: 'news-2001',
    title: '端侧大模型推理延迟再降 30%',
    summary: '新量化方案在旗舰芯片上实现接近云端的交互体验。',
    newsCat: 'tech',
    author: '科技观察',
    publishedAt: '2026-06-16 07:45',
    readMin: 5,
    coverHue: 265,
    body: bodies(10),
    related: [
      { aid: 'news-2002', title: '开源埋点方案如何做到 Schema 驱动', newsCat: 'tech' },
      { aid: 'news-3001', title: '央行公开市场操作净投放 500 亿', newsCat: 'finance' },
    ],
  },
  {
    aid: 'news-2002',
    title: '开源埋点方案如何做到 Schema 驱动',
    summary: '注册表 + 网关校验 + 双端 SDK 收敛，降低埋点漂移风险。',
    newsCat: 'tech',
    author: 'GISO 团队',
    publishedAt: '2026-06-16 07:10',
    readMin: 6,
    coverHue: 250,
    body: bodies(12),
    related: [
      { aid: 'news-2001', title: '端侧大模型推理延迟再降 30%', newsCat: 'tech' },
    ],
  },
  {
    aid: 'news-3001',
    title: '央行公开市场操作净投放 500 亿',
    summary: '流动性保持合理充裕，短端利率窄幅波动。',
    newsCat: 'finance',
    author: '财经早报',
    publishedAt: '2026-06-16 06:50',
    readMin: 3,
    coverHue: 45,
    body: bodies(7),
    related: [
      { aid: 'news-3002', title: '消费板块回暖，北向资金连续三日净流入', newsCat: 'finance' },
    ],
  },
  {
    aid: 'news-3002',
    title: '消费板块回暖，北向资金连续三日净流入',
    summary: '必选消费与出行链领涨，市场关注中报业绩验证。',
    newsCat: 'finance',
    author: '市场速递',
    publishedAt: '2026-06-16 06:20',
    readMin: 4,
    coverHue: 35,
    body: bodies(8),
    related: [
      { aid: 'news-3001', title: '央行公开市场操作净投放 500 亿', newsCat: 'finance' },
    ],
  },
];

export function articlesByCat(cat: string): NewsArticle[] {
  return ARTICLES.filter((a) => a.newsCat === cat);
}

export function findArticle(aid: string): NewsArticle | undefined {
  return ARTICLES.find((a) => a.aid === aid);
}
