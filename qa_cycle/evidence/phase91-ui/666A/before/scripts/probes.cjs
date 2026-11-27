// 666A in-page probes for 01-capture / 03-fee-run / 04-module-off. HELPERS and HELPERS2 are copied
// verbatim from 665F/before/scripts/01-capture.cjs (lines 39-104 and 107-153; that file is not edited)
// — table measure with per-column cut, contrast, card/grid overflow. FEE (new) probes the 666A
// surfaces: the Fee notes | Fee runs links, count pill, tiles (label/value/testid/colour + the tile
// container's width and column count), filter chips, table row heights and line counts of the first
// cell (K-72), debtor / unbilled / run-item tables, wizard step badges, empty states and the main
// content box.
const HELPERS = (TABLIST) => {
  window.__TABLIST = TABLIST;
  window.__v = (e) => { const b = e.getBoundingClientRect(); const cs = getComputedStyle(e); return b.width > 0 && b.height > 0 && cs.visibility !== "hidden" && cs.display !== "none"; };
  window.__t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  window.__scroller = (t) => {
    for (let e = t.parentElement; e && e !== document.body; e = e.parentElement) {
      const cs = getComputedStyle(e);
      if (/(auto|scroll|hidden|clip)/.test(cs.overflowX) || /inline-size/.test(cs.contain || "") || /inline-size/.test(cs.containerType || "")) return e;
    }
    return null;
  };
  window.__tableMeasure = (t) => {
    const sc = window.__scroller(t);
    const tb = t.getBoundingClientRect();
    const sr = sc ? sc.getBoundingClientRect() : null;
    const visRight = sr ? sr.left + sc.clientWidth : innerWidth;
    const cols = [...t.querySelectorAll("thead th")].filter(window.__v).map((th) => { const r = th.getBoundingClientRect(); return { h: window.__t(th) || "(blank)", w: Math.round(r.width), cutPx: Math.max(0, Math.round(r.right - visRight)), hidden: r.left >= visRight }; });
    const cellClip = [...t.querySelectorAll("tbody td")].filter((td) => window.__v(td) && td.scrollWidth > td.clientWidth + 1).length;
    return {
      dataSlot: t.getAttribute("data-slot"),
      visible: window.__v(t),
      headers: [...t.querySelectorAll("thead th")].map(window.__t),
      bodyRows: t.querySelectorAll("tbody tr").length,
      tableWidth: Math.round(tb.width),
      cols,
      cellsWithInternalClip: cellClip,
      scroller: sc ? { tag: sc.tagName.toLowerCase(), dataSlot: sc.getAttribute("data-slot"), cls: (sc.className || "").toString().slice(0, 80), overflowX: getComputedStyle(sc).overflowX, position: getComputedStyle(sc).position, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth } : null,
    };
  };
  window.__empties = (root) => [...root.querySelectorAll("p,td,h2,h3,div")].filter((e) => window.__v(e) && e.children.length <= 2 && /^(No |Nothing |There are no )/.test(window.__t(e)) && window.__t(e).length < 200).map((e) => {
    const box = e.closest('[data-slot="empty-state"]') || e.closest('[data-slot="card-content"]') || e.closest("td") || e.closest("div.rounded-lg") || e.parentElement;
    return { tag: e.tagName.toLowerCase(), text: window.__t(e).slice(0, 160), cls: (e.className || "").toString().slice(0, 60), height: Math.round(e.getBoundingClientRect().height), containerHeight: Math.round(box.getBoundingClientRect().height), containerSlot: box.getAttribute("data-slot"), outerHTML: box.outerHTML.slice(0, 4000) };
  });
  window.__rail = () => {
    const nav = document.querySelector('nav[aria-label="Settings navigation"]');
    if (!nav) return null;
    return [...nav.querySelectorAll('a[href*="/settings/connections"]')].filter(window.__v).map((a) => `${window.__t(a)}${a.getAttribute("aria-current") ? "*" : ""}`);
  };
  // WCAG contrast of each visible text element's own text (direct text nodes) against its composited
  // background (the ancestor background chain down from the first opaque one). Returns < 4.5 only.
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (c, base) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = base; cx.fillRect(0, 0, 1, 1); cx.globalCompositeOperation = "source-over"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return `rgb(${d[0]},${d[1]},${d[2]})`; };
  const trip = (s) => s.match(/\d+/g).slice(0, 3).map(Number);
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const isClear = (c) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); return cx.getImageData(0, 0, 1, 1).data[3] === 0; };
  const alphaOf = (c) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); return cx.getImageData(0, 0, 1, 1).data[3]; };
  window.__bg = (e) => { const chain = []; for (let x = e; x; x = x.parentElement) { const b = getComputedStyle(x).backgroundColor; if (!isClear(b)) { chain.push(b); if (alphaOf(b) === 255) break; } } let base = "rgb(255,255,255)"; for (const b of chain.reverse()) base = px(b, base); return base; };
  window.__contrast = (root) => {
    const out = [];
    for (const e of root.querySelectorAll("*")) {
      if (!window.__v(e)) continue;
      const own = [...e.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent).join("").replace(/\s+/g, " ").trim();
      if (!own || own.length < 2) continue;
      const cs = getComputedStyle(e);
      if (cs.opacity === "0") continue;
      const bg = window.__bg(e);
      const fg = px(cs.color, bg);
      const [l1, l2] = [lum(trip(fg)), lum(trip(bg))].sort((a, b) => b - a);
      const ratio = +((l1 + 0.05) / (l2 + 0.05)).toFixed(2);
      const large = parseFloat(cs.fontSize) >= 24 || (parseFloat(cs.fontSize) >= 18.66 && +cs.fontWeight >= 700);
      if (ratio < (large ? 3 : 4.5)) out.push({ text: own.slice(0, 60), tag: e.tagName.toLowerCase(), cls: (e.className || "").toString().slice(0, 90), fg, bg, ratio, fontPx: parseFloat(cs.fontSize), disabled: !!e.closest("button:disabled,[aria-disabled=true],[data-disabled]") });
    }
    const m = new Map(); for (const x of out) { const k = `${x.text}|${x.cls}`; if (!m.has(k)) m.set(k, x); }
    return [...m.values()].sort((a, b) => a.ratio - b.ratio).slice(0, 25);
  };
};

// 665F additions: the contrast of one element (any ratio), card-grid / card overflow, targeted contrast.
const HELPERS2 = () => {
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (c, base) => { cx.globalCompositeOperation = "copy"; cx.fillStyle = base; cx.fillRect(0, 0, 1, 1); cx.globalCompositeOperation = "source-over"; cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); const d = cx.getImageData(0, 0, 1, 1).data; return `rgb(${d[0]},${d[1]},${d[2]})`; };
  const trip = (s) => s.match(/\d+/g).slice(0, 3).map(Number);
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  window.__contrastOf = (e) => {
    const cs = getComputedStyle(e);
    const bg = window.__bg(e);
    const fg = px(cs.color, bg);
    const [l1, l2] = [lum(trip(fg)), lum(trip(bg))].sort((a, b) => b - a);
    return { text: window.__t(e).slice(0, 60), tag: e.tagName.toLowerCase(), cls: (e.className || "").toString().slice(0, 120), color: cs.color, fg, bg, ratio: +((l1 + 0.05) / (l2 + 0.05)).toFixed(2), fontPx: parseFloat(cs.fontSize), fontWeight: cs.fontWeight };
  };
  const dd = (arr, key) => { const m = new Map(); for (const x of arr) { const k = key(x); if (!m.has(k)) m.set(k, { ...x, n: 0 }); m.get(k).n++; } return [...m.values()]; };
  window.__targets = () => {
    const main = document.querySelector("main#main-content");
    if (!main) return null;
    const vis = window.__v;
    const links = [...main.querySelectorAll("a")].filter((a) => vis(a) && /→|View |Manage |Open /.test(window.__t(a))).map(window.__contrastOf);
    const badges = dd([...main.querySelectorAll('[data-slot="badge"]')].filter(vis).map((b) => ({ ...window.__contrastOf(b), variant: b.getAttribute("data-variant") || null })), (x) => `${x.text}|${x.fg}|${x.bg}`);
    const packTitles = [...main.querySelectorAll('[data-testid^="pack-card-"] h3')].filter(vis).map((h) => ({ testid: h.closest("[data-testid]").dataset.testid, ...window.__contrastOf(h) }));
    const form = [...main.querySelectorAll("div.rounded-lg.border")].find((d) => /Notification Type/.test(d.textContent) && /In-App/.test(d.textContent));
    const notifCategoryHeaders = form ? dd([...form.querySelectorAll("p.uppercase")].filter(vis).map(window.__contrastOf), (x) => `${x.fg}|${x.bg}`).map((x) => ({ ...x, sample: x.text })) : null;
    const subtle = dd([...main.querySelectorAll("p,span,label,td,th,dd,dt")].filter((e) => vis(e) && [...e.childNodes].some((n) => n.nodeType === 3 && n.textContent.trim().length > 1) && /text-(slate|muted)-?(400|500|foreground)?/.test((e.className || "").toString())).map(window.__contrastOf), (x) => `${x.cls}|${x.fg}|${x.bg}`).sort((a, b) => a.ratio - b.ratio).slice(0, 12);
    return { links, badges, packTitles, notifCategoryHeaders, subtle };
  };
  // Card grids and cards: client vs scroll width, and descendants that spill past the card's right edge.
  window.__cards = () => {
    const main = document.querySelector("main#main-content");
    if (!main) return null;
    const vis = window.__v;
    const grids = [...main.querySelectorAll("div.grid")].filter((g) => vis(g) && g.children.length > 1 && getComputedStyle(g).display === "grid" && !g.closest("table")).map((g) => {
      const gr = g.getBoundingClientRect();
      const kids = [...g.children].filter(vis);
      return { cls: (g.className || "").toString().slice(0, 90), cols: getComputedStyle(g).gridTemplateColumns.split(" ").length, template: getComputedStyle(g).gridTemplateColumns.slice(0, 80), children: kids.length, width: Math.round(gr.width), clientWidth: g.clientWidth, scrollWidth: g.scrollWidth, overflowPx: g.scrollWidth - g.clientWidth, childMaxRight: Math.round(Math.max(...kids.map((k) => k.getBoundingClientRect().right))) - Math.round(gr.right), childMinWidth: Math.round(Math.min(...kids.map((k) => k.getBoundingClientRect().width))) };
    }).filter((g) => g.children > 1).slice(0, 12);
    const cardEls = [...main.querySelectorAll('[data-slot="card"], div.rounded-lg.border')].filter((c) => vis(c) && !c.parentElement.closest('[data-slot="card"]'));
    const cards = cardEls.map((c) => {
      const r = c.getBoundingClientRect();
      const head = c.querySelector('h1,h2,h3,h4,[data-slot="card-title"]');
      const spill = [...c.querySelectorAll("*")].filter((e) => vis(e) && e.getBoundingClientRect().right > r.right + 1).map((e) => ({ tag: e.tagName.toLowerCase(), text: window.__t(e).slice(0, 40), cls: (e.className || "").toString().slice(0, 60), pastPx: Math.round(e.getBoundingClientRect().right - r.right) }));
      const clipped = [...c.querySelectorAll("*")].filter((e) => vis(e) && e.children.length === 0 && e.scrollWidth > e.clientWidth + 1 && /truncate|overflow-hidden|text-ellipsis/.test((e.className || "").toString())).length;
      return { head: head ? window.__t(head).slice(0, 50) : window.__t(c).slice(0, 30), width: Math.round(r.width), clientWidth: c.clientWidth, scrollWidth: c.scrollWidth, overflowPx: c.scrollWidth - c.clientWidth, spillCount: spill.length, spillMaxPx: spill.length ? Math.max(...spill.map((s) => s.pastPx)) : 0, spill: spill.sort((a, b) => b.pastPx - a.pastPx).slice(0, 3), truncatedLeaves: clipped };
    });
    const col = main.querySelector('[role="tabpanel"]') || main.firstElementChild;
    return { column: col ? { role: col.getAttribute("role"), clientWidth: col.clientWidth, scrollWidth: col.scrollWidth, overflowPx: col.scrollWidth - col.clientWidth } : null, grids, cards: cards.slice(0, 20) };
  };
};

// 666A: fee-note surface probe (compact, every variant).
const FEE = () => {
  const main = document.querySelector("main#main-content");
  if (!main) return { error: "no main#main-content" };
  const vis = window.__v, t = window.__t;
  const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const cs = getComputedStyle(main);
  const inner = main.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
  const first = main.firstElementChild;
  const lines = (e) => { const r = document.createRange(); r.selectNodeContents(e); const rects = [...r.getClientRects()]; const tops = new Set(rects.map((x) => Math.round(x.top))); return tops.size; };
  const tabLinks = [...main.querySelectorAll("a")].filter((a) => vis(a) && /^(Fee notes|Fee runs)$/.test(t(a))).map((a) => ({ text: t(a), href: a.getAttribute("href").replace(U, "{id}"), ariaCurrent: a.getAttribute("aria-current"), cls: a.className, color: getComputedStyle(a).color, borderBottomColor: getComputedStyle(a).borderBottomColor }));
  const h1 = main.querySelector("h1");
  const pill = h1 ? [...(h1.parentElement || main).querySelectorAll("span.rounded-full")].filter(vis).map((s) => ({ text: t(s), cls: s.className })) : [];
  const tileEls = [...main.querySelectorAll('[data-testid^="fee-notes-"]')].filter(vis);
  const tileGrid = tileEls.length ? tileEls[0].closest("div.grid") : null;
  const tiles = tileEls.map((v) => { const card = v.parentElement; const lab = card.querySelector("p"); return { testid: v.dataset.testid, label: lab && lab !== v ? t(lab) : null, value: t(v), valueColor: getComputedStyle(v).color, valueCls: v.className, contrast: window.__contrastOf(v).ratio, cardWidth: Math.round(card.getBoundingClientRect().width), cardHeight: Math.round(card.getBoundingClientRect().height) }; });
  const gridInfo = (g) => g ? { cls: g.className, width: Math.round(g.getBoundingClientRect().width), clientWidth: g.clientWidth, cols: getComputedStyle(g).gridTemplateColumns.split(" ").length, template: getComputedStyle(g).gridTemplateColumns, rows: getComputedStyle(g).gridTemplateRows.split(" ").length, parentWidth: g.parentElement.clientWidth, containerType: getComputedStyle(g).containerType, parentContainerType: getComputedStyle(g.parentElement).containerType } : null;
  // Billing-run summary cards (data-slot=card with an uppercase label span).
  const statCards = [...main.querySelectorAll('[data-slot="card"]')].filter((c) => vis(c) && c.querySelector("span.uppercase"));
  const statGrid = statCards.length ? statCards[0].closest("div.grid") : null;
  const stats = statCards.map((c) => { const sp = c.querySelectorAll("span"); return { label: t(sp[0]), value: sp[1] ? t(sp[1]) : null, width: Math.round(c.getBoundingClientRect().width), valueOverflow: sp[1] ? sp[1].scrollWidth - sp[1].clientWidth : null }; });
  const filterBar = [...main.querySelectorAll("div.flex-wrap")].find((d) => /^Filter:/.test(t(d)));
  const filters = filterBar ? [...filterBar.querySelectorAll("a")].map((a) => ({ text: t(a), href: a.getAttribute("href"), active: /bg-slate-900/.test(a.className) })) : null;
  const tables = [...main.querySelectorAll("table")].filter(vis).map((tb) => {
    const rows = [...tb.querySelectorAll("tbody tr")].filter(vis);
    const hs = rows.map((r) => Math.round(r.getBoundingClientRect().height));
    const firstCells = rows.map((r) => r.querySelector("td")).filter(Boolean);
    const links = [...tb.querySelectorAll("tbody a[href]")].map((a) => ({ text: t(a).slice(0, 40), href: a.getAttribute("href").replace(U, "{id}"), lines: lines(a), color: getComputedStyle(a).color, contrast: window.__contrastOf(a).ratio }));
    const headLines = [...tb.querySelectorAll("thead th")].filter(vis).map((th) => `${t(th)}:${lines(th)}`);
    return { ...window.__tableMeasure(tb), rowHeights: hs, rowHeightMin: hs.length ? Math.min(...hs) : null, rowHeightMax: hs.length ? Math.max(...hs) : null, tableTopToFold: Math.round(tb.getBoundingClientRect().top + scrollY), rowsAboveFold: rows.filter((r) => r.getBoundingClientRect().bottom + scrollY <= innerHeight).length, firstCellLines: firstCells.map((c) => `${t(c).slice(0, 30)}:${lines(c)}`).slice(0, 12), headLines, links: links.slice(0, 15), roleRows: tb.querySelectorAll('[role="row"], tr').length };
  });
  const steps = [...main.querySelectorAll('nav[aria-label="Wizard steps"] li')].map((li) => { const b = li.querySelector("div"); const s = li.querySelector("span"); return { n: t(b), label: t(s), badgeBg: getComputedStyle(b).backgroundColor, badgeColor: getComputedStyle(b).color, badgeContrast: window.__contrastOf(b).ratio, labelContrast: window.__contrastOf(s).ratio }; });
  const empties = [...main.querySelectorAll('[data-slot="empty-state"]')].filter(vis).map((e) => ({ text: t(e).slice(0, 160), height: Math.round(e.getBoundingClientRect().height), variant: e.getAttribute("data-variant") }));
  return {
    mainInner: Math.round(inner), mainClientWidth: main.clientWidth, mainPadding: `${cs.paddingLeft} ${cs.paddingRight}`, firstChild: first ? { tag: first.tagName.toLowerCase(), cls: (first.className || "").toString().slice(0, 60), width: Math.round(first.getBoundingClientRect().width) } : null,
    tabLinks, pill, tiles, tileGrid: gridInfo(tileGrid), stats, statGrid: gridInfo(statGrid), filters, tables, steps, empties,
    h1Text: h1 ? t(h1) : null, h1Empty: h1 ? t(h1) === "" : null,
  };
};

const COMPACT = () => {
  const main = document.querySelector("main#main-content");
  const txt = window.__t;
  const se = document.scrollingElement;
  const b = main ? main.getBoundingClientRect() : null;
  return {
    h1: main ? [...main.querySelectorAll("h1")].map(txt) : [],
    h1Count: main ? main.querySelectorAll("h1").length : 0,
    h1Page: document.querySelectorAll("h1").length,
    mainCount: document.querySelectorAll("main#main-content").length,
    mainElCount: document.querySelectorAll("main").length,
    docHeight: se.scrollHeight,
    hOverflow: se.scrollWidth > se.clientWidth ? se.scrollWidth - se.clientWidth : 0,
    mainBox: b ? { left: Math.round(b.left), width: Math.round(b.width), right: Math.round(b.right) } : null,
    fee: window.__fee(),
    cards: window.__cards(),
    emptyStates: main ? window.__empties(main) : [],
    lowContrast: main ? window.__contrast(main) : [],
    somethingWentWrong: document.body.innerText.includes("Something went wrong"),
    notFound: /could not be found|404|not found/i.test(main ? main.innerText : document.body.innerText),
    bodyTextHead: (main ? main.innerText : document.body.innerText).replace(/\s+/g, " ").slice(0, 300),
  };
};

const FULL = () => {
  const main = document.querySelector("main#main-content");
  const vis = window.__v, txt = window.__t;
  const name = (e) => e.getAttribute("aria-label") || (e.getAttribute("aria-labelledby") ? e.getAttribute("aria-labelledby").split(/\s+/).map((id) => document.getElementById(id)).filter(Boolean).map(txt).join(" ") : "") || txt(e) || e.getAttribute("title") || "";
  const labelFor = (e) => { if (e.getAttribute("aria-label")) return e.getAttribute("aria-label"); if (e.id) { const l = document.querySelector(`label[for="${CSS.escape(e.id)}"]`); if (l) return txt(l); } const w = e.closest("label"); return w ? txt(w).slice(0, 80) : null; };
  if (!main) return { error: "no main#main-content", title: document.title, body: document.body.innerText.slice(0, 300) };
  const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const testids = {};
  for (const e of main.querySelectorAll("[data-testid]")) { const k = e.dataset.testid.replace(U, "{uuid}"); testids[k] = (testids[k] || 0) + 1; }
  const dedupe = (arr) => { const m = new Map(); for (const x of arr) { const k = JSON.stringify(x); m.set(k, (m.get(k) || 0) + 1); } return [...m].map(([k, n]) => ({ ...JSON.parse(k), n })); };
  return {
    title: document.title,
    url: location.pathname.replace(U, "{uuid}") + location.search.replace(U, "{uuid}"),
    breadcrumb: [...document.querySelectorAll('nav[aria-label="breadcrumb"] li, nav[aria-label="Breadcrumb"] li')].map(txt).filter(Boolean),
    h1: [...main.querySelectorAll("h1")].map((h) => ({ text: txt(h), visible: vis(h), cls: h.className })),
    headings: [...main.querySelectorAll("h1,h2,h3,h4,h5,h6,[role=heading]")].filter(vis).map((h) => ({ tag: h.tagName.toLowerCase(), text: txt(h).slice(0, 100) })),
    testids,
    buttons: dedupe([...main.querySelectorAll('button,[role="button"],a[data-slot="button"]')].map((b) => ({ name: name(b).replace(U, "{uuid}").slice(0, 80), visible: vis(b), disabled: b.disabled || b.getAttribute("aria-disabled") === "true" || undefined, tag: b.tagName.toLowerCase() }))),
    links: dedupe([...main.querySelectorAll("a[href]")].map((a) => ({ name: name(a).slice(0, 60), href: a.getAttribute("href").replace(U, "{uuid}"), testid: a.dataset.testid ? a.dataset.testid.replace(U, "{uuid}") : undefined, ariaCurrent: a.getAttribute("aria-current") || undefined }))).slice(0, 60),
    controls: [...main.querySelectorAll('input,textarea,select,[role="combobox"],[role="switch"],[role="checkbox"]')].filter((e) => e.type !== "hidden" && !(e.tagName === "INPUT" && e.getAttribute("aria-hidden") === "true")).map((e) => ({ tag: e.tagName.toLowerCase(), type: e.getAttribute("type") || e.getAttribute("role") || undefined, id: e.id || undefined, testid: e.dataset.testid, label: labelFor(e), value: e.tagName === "BUTTON" ? (e.getAttribute("aria-checked") ?? txt(e).slice(0, 60)) : (e.value || "").slice(0, 80), disabled: e.disabled || undefined, visible: vis(e) })).slice(0, 60),
    tables: [...main.querySelectorAll("table")].map((tb) => ({ headers: [...tb.querySelectorAll("thead th")].map(txt), rows: [...tb.querySelectorAll("tbody tr")].map((tr) => [...tr.children].map((c) => txt(c).slice(0, 50))).slice(0, 15) })),
    chips: dedupe([...main.querySelectorAll('[data-slot="badge"]')].filter(vis).map((b) => ({ text: txt(b).slice(0, 40), variant: b.getAttribute("data-variant") || undefined, testid: b.dataset.testid }))),
    statusAlerts: [...main.querySelectorAll('[role="status"],[role="alert"]')].filter(vis).map((e) => txt(e).slice(0, 160)),
    navFeeLinks: [...document.querySelectorAll('nav a[href*="/invoices"]')].filter(vis).map((a) => `${txt(a)}${a.getAttribute("aria-current") ? "*" : ""}`),
  };
};

module.exports = { HELPERS, HELPERS2, FEE, COMPACT, FULL };
