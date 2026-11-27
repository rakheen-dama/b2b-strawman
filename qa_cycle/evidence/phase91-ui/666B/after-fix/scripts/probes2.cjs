// 666B OBS-after in-page probes (new; probes.cjs is the OBS-before copy, unchanged). Run after
// probes.cjs HELPERS + HELPERS2 are installed (uses __v, __t, __tableMeasure, __contrastOf, __bg).
// HERO: the DetailHero on the fee-note detail page — h1 (+ HelpTip), identifier chip, status slot,
// subtitle meta row (rows by top position), actions (variant / size / box), the tile strip (grid
// template, rows × cols by position, each tile's label / value / hint text, scroll vs client width
// of every descendant, contrast of label / value / hint, the danger icon's colour vs the composited
// tile background), and the totals block rows parsed so tiles can be compared with it.
// TABLES: every <table> in main with its section heading, headers, rows (testids), measure (table
// vs scroller), and per-cell line counts (height / line-height) for prose columns.
// EMPTIES: every [data-slot="empty-state"] in main (variant, text, height).
// HEADINGS: every h2/h3 in main (font family / size / weight / class).
const HERO = () => {
  const main = document.querySelector("main#main-content");
  if (!main) return { error: "no main#main-content" };
  const vis = window.__v, t = window.__t;
  const box = (e) => { if (!e) return null; const r = e.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height) }; };
  const heroes = [...main.querySelectorAll('[data-slot="detail-hero"]')];
  const hero = heroes[0] || null;
  const h1s = [...main.querySelectorAll("h1")];
  const h1 = h1s[0] || null;
  const rowsOf = (els) => { const tops = []; for (const e of els) { const y = Math.round(e.getBoundingClientRect().top); if (!tops.some((x) => Math.abs(x - y) <= 3)) tops.push(y); } return tops.length; };
  const overflowIn = (root) => [...root.querySelectorAll("*")].concat([root]).filter((e) => vis(e) && e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).display !== "inline").map((e) => ({ tag: e.tagName.toLowerCase(), slot: e.getAttribute("data-slot"), text: t(e).slice(0, 40), scrollWidth: e.scrollWidth, clientWidth: e.clientWidth }));
  const clipStyle = (e) => { const cs = getComputedStyle(e); return { overflow: cs.overflow, textOverflow: cs.textOverflow, whiteSpace: cs.whiteSpace }; };
  const subtitleSlot = hero ? hero.querySelector('[data-slot="detail-hero-subtitle"]') : null;
  const metaSpans = subtitleSlot ? [...subtitleSlot.querySelectorAll("span")].filter((s) => vis(s) && s.parentElement && s.parentElement.parentElement === subtitleSlot) : [];
  const actionsSlot = hero ? hero.querySelector('[data-slot="detail-hero-actions"]') : null;
  const statusSlot = hero ? hero.querySelector('[data-slot="detail-hero-status"]') : null;
  const tilesSlot = hero ? hero.querySelector('[data-slot="detail-hero-tiles"]') : null;
  const tileEls = tilesSlot ? [...tilesSlot.querySelectorAll('[data-slot="stat-card"]')].filter(vis) : [];
  const tiles = tileEls.map((c) => {
    const lab = c.querySelector('[data-slot="stat-card-label"]');
    const val = c.querySelector('[data-slot="stat-card-value"]');
    const hint = c.querySelector('[data-slot="stat-card-hint"]');
    const svg = hint ? hint.querySelector("svg") : null;
    let icon = null;
    if (svg) {
      const cs = getComputedStyle(svg);
      const fake = document.createElement("span"); fake.style.color = cs.color; fake.textContent = "x"; svg.parentElement.appendChild(fake);
      const cr = window.__contrastOf(fake); fake.remove();
      icon = { cls: (svg.getAttribute("class") || "").slice(0, 80), color: cs.color, stroke: cs.stroke, ariaHidden: svg.getAttribute("aria-hidden"), box: box(svg), contrastVsTile: cr.ratio, fg: cr.fg, bg: cr.bg };
    }
    return {
      testid: c.dataset.testid || null, role: c.getAttribute("role"), ariaLabelledby: !!c.getAttribute("aria-labelledby"),
      label: lab ? t(lab) : null, value: val ? t(val) : null, hint: hint ? t(hint) : null,
      box: box(c), labelBox: box(lab), valueBox: box(val),
      valueScroll: val ? { scrollWidth: val.scrollWidth, clientWidth: val.clientWidth, ...clipStyle(val), lines: Math.round(val.getBoundingClientRect().height / parseFloat(getComputedStyle(val).lineHeight || 28)) } : null,
      labelScroll: lab ? { scrollWidth: lab.scrollWidth, clientWidth: lab.clientWidth } : null,
      overflowEls: overflowIn(c),
      contrast: { label: lab ? window.__contrastOf(lab) : null, value: val ? window.__contrastOf(val) : null, hint: hint ? window.__contrastOf(hint) : null },
      icon, tileBg: getComputedStyle(c).backgroundColor,
    };
  });
  const subtotal = [...main.querySelectorAll("span")].find((s) => t(s) === "Subtotal");
  const totals = subtotal ? subtotal.closest("div.max-w-xs") : null;
  const totalsRows = totals ? [...totals.children].filter(vis).map((r) => t(r)) : [];
  const totalsMap = {};
  for (const r of totalsRows) { const m = r.match(/^(Total|Amount paid|Balance due)\s+(.*)$/); if (m) totalsMap[m[1]] = m[2]; }
  const tileMap = Object.fromEntries(tiles.map((x) => [x.label, x.value]));
  const agree = {};
  for (const k of ["Total", "Amount paid", "Balance due"]) if (k in tileMap || k in totalsMap) agree[k] = { tile: tileMap[k] ?? null, totals: totalsMap[k] ?? null, equal: tileMap[k] === totalsMap[k] };
  const tc = [...document.querySelectorAll('[data-testid^="fee-note-hero-tile-"]')].map((e) => e.dataset.testid);
  const gcs = tilesSlot ? getComputedStyle(tilesSlot) : null;
  const helpBtns = h1 ? [...h1.querySelectorAll("button,[role=button]")] : [];
  return {
    heroCount: heroes.length, heroBox: box(hero), heroCls: hero ? (hero.className || "").toString().slice(0, 120) : null,
    heroRow: hero ? box(hero.firstElementChild) : null,
    h1Count: h1s.length, h1PageCount: document.querySelectorAll("h1").length,
    h1: h1 ? { text: t(h1), insideHero: hero ? hero.contains(h1) : false, helpTip: helpBtns.map((b) => b.getAttribute("aria-label") || t(b)), fontPx: parseFloat(getComputedStyle(h1).fontSize), fontFamily: getComputedStyle(h1).fontFamily.slice(0, 40), box: box(h1) } : null,
    identifierChips: hero ? hero.querySelectorAll('[data-slot="detail-hero-identifier"]').length : 0,
    status: statusSlot ? [...statusSlot.querySelectorAll('[data-slot="badge"]')].filter(vis).map((b) => ({ text: t(b), variant: b.getAttribute("data-variant"), testid: b.dataset.testid || null, contrast: window.__contrastOf(b).ratio })) : [],
    meta: metaSpans.map(t), metaRows: rowsOf(metaSpans), subtitleBox: box(subtitleSlot),
    actions: actionsSlot ? [...actionsSlot.querySelectorAll("button,a")].filter(vis).map((b) => ({ text: t(b) || b.getAttribute("aria-label"), variant: b.getAttribute("data-variant"), size: b.getAttribute("data-size"), box: box(b), contrast: window.__contrastOf(b).ratio, bg: getComputedStyle(b).backgroundColor, color: getComputedStyle(b).color })) : [],
    actionsBox: box(actionsSlot), actionsRows: actionsSlot ? rowsOf([...actionsSlot.children].filter(vis)) : 0,
    tileGrid: tilesSlot ? { cls: (tilesSlot.className || "").toString(), templateCols: gcs.gridTemplateColumns.split(" ").length, template: gcs.gridTemplateColumns, rows: rowsOf(tileEls), cols: tileEls.length ? tileEls.filter((e) => Math.abs(e.getBoundingClientRect().top - tileEls[0].getBoundingClientRect().top) <= 3).length : 0, box: box(tilesSlot), clientWidth: tilesSlot.clientWidth, scrollWidth: tilesSlot.scrollWidth } : null,
    tiles, tileTestids: tc, totalsRows, agree,
    heroOverflow: hero ? overflowIn(hero).filter((x) => x.slot !== "stat-card") : [],
  };
};

const TABLES = () => {
  const main = document.querySelector("main#main-content");
  if (!main) return [];
  const vis = window.__v, t = window.__t;
  const headingOf = (tb) => {
    for (let e = tb; e && e !== main; e = e.parentElement) {
      let s = e.previousElementSibling;
      while (s) { const h = s.matches("h2,h3") ? s : s.querySelector("h2,h3"); if (h) return t(h); s = s.previousElementSibling; }
    }
    return null;
  };
  return [...main.querySelectorAll("table")].filter(vis).map((tb) => {
    const m = window.__tableMeasure(tb);
    const ths = [...tb.querySelectorAll("thead th")].filter(vis).map(t);
    const rows = [...tb.querySelectorAll("tbody tr")].filter(vis);
    const lineCounts = rows.map((r) => [...r.children].filter(vis).map((c) => { const lh = parseFloat(getComputedStyle(c).lineHeight) || 20; const pt = parseFloat(getComputedStyle(c).paddingTop) + parseFloat(getComputedStyle(c).paddingBottom); const inner = c.firstElementChild && c.children.length === 1 ? c.firstElementChild.getBoundingClientRect().height : c.getBoundingClientRect().height - pt; return Math.max(1, Math.round(inner / lh)); }));
    const frame = tb.closest('[data-slot="data-table"]') || tb.closest(".rounded-lg.border") || tb.parentElement;
    return { heading: headingOf(tb), headers: ths, rowCount: rows.length, rowTestids: rows.map((r) => r.dataset.testid || null), rowCells: rows.slice(0, 6).map((r) => [...r.children].filter(vis).map((c) => t(c).slice(0, 50))), lineCounts: lineCounts.slice(0, 6), measure: m, frame: { slot: frame.getAttribute("data-slot"), cls: (frame.className || "").toString().slice(0, 100), w: Math.round(frame.getBoundingClientRect().width), clientWidth: frame.clientWidth, scrollWidth: frame.scrollWidth } };
  });
};

const EMPTIES = () => {
  const main = document.querySelector("main#main-content");
  if (!main) return [];
  return [...main.querySelectorAll('[data-slot="empty-state"]')].filter(window.__v).map((e) => ({ variant: e.getAttribute("data-variant"), text: window.__t(e).slice(0, 160), height: Math.round(e.getBoundingClientRect().height), width: Math.round(e.getBoundingClientRect().width), testid: e.dataset.testid || (e.closest("[data-testid]") || {}).dataset?.testid || null }));
};

const HEADINGS = () => {
  const main = document.querySelector("main#main-content");
  if (!main) return [];
  return [...main.querySelectorAll("h2,h3")].filter(window.__v).map((h) => { const cs = getComputedStyle(h); return { tag: h.tagName.toLowerCase(), text: window.__t(h).slice(0, 50), fontFamily: cs.fontFamily.split(",")[0].replace(/"/g, ""), fontPx: parseFloat(cs.fontSize), fontWeight: cs.fontWeight, cls: (h.className || "").toString().slice(0, 80) }; });
};

module.exports = { HERO, TABLES, EMPTIES, HEADINGS };
