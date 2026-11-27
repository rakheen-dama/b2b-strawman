// 666A OBS-after in-page probes (NEW file; the copied probes.cjs is unchanged). probes.cjs's FEE()
// reads the before markup (tile label = first <p>, run stat cards = span.uppercase, count pill next to
// the h1), so the kit markup needs its own reader. Needs P.HELPERS + P.HELPERS2 loaded first
// (__v, __t, __bg, __contrastOf, __tableMeasure, __contrast). AFTER() returns, for main#main-content:
// PageHeader slots, the Fee notes | Fee runs sub-nav (aria-current + the ::after underline), the status
// filter links, the kit StatCards (label/value/colour/contrast/spill) + their grid and @container,
// every table (scroller, focusability, edge fades, DOM vs visible headers, row heights, line counts,
// links, clipped cells), EmptyStates (variant, height, border style), dashed borders, the unbilled
// grand total and filter form placement, wizard step badges, and a dark-surface audit.
const AFTER = () => {
  const main = document.querySelector("main#main-content");
  if (!main) return { error: "no main#main-content" };
  const vis = window.__v, t = window.__t, C = window.__contrastOf;
  const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const R = (e) => { const b = e.getBoundingClientRect(); return { x: Math.round(b.left), y: Math.round(b.top + scrollY), w: Math.round(b.width), h: Math.round(b.height), right: Math.round(b.right) }; };
  const lines = (e) => { const r = document.createRange(); r.selectNodeContents(e); return new Set([...r.getClientRects()].filter((x) => x.width > 0).map((x) => Math.round(x.top))).size; };
  const slots = (e) => { const out = []; for (let x = e; x && x !== main; x = x.parentElement) if (x.dataset && x.dataset.slot) out.push(x.dataset.slot); return out; };
  // Page header
  const ph = main.querySelector('[data-slot="page-header"]');
  const h1s = [...main.querySelectorAll("h1")];
  const h1 = h1s[0] || null;
  const actions = ph && ph.querySelector('[data-slot="page-header-actions"]');
  const header = {
    pageHeader: !!ph, h1Count: h1s.length, h1Text: h1 ? t(h1) : null, h1InPageHeader: !!(h1 && h1.closest('[data-slot="page-header"]')),
    h1Buttons: h1 ? [...h1.querySelectorAll("button")].map((b) => b.getAttribute("aria-label") || t(b)) : [],
    h1HasBadge: !!(h1 && h1.querySelector('[data-slot="badge"], span.rounded-full')),
    description: ph && ph.querySelector('[data-slot="page-header-description"]') ? t(ph.querySelector('[data-slot="page-header-description"]')).slice(0, 220) : null,
    actions: actions ? [...actions.querySelectorAll("a,button,span.rounded-full")].filter(vis).map((e) => ({ tag: e.tagName.toLowerCase(), text: t(e).slice(0, 40), href: e.getAttribute("href") ? e.getAttribute("href").replace(U, "{id}") : undefined, cls: (e.className || "").toString().slice(0, 80) })) : [],
    toolbar: ph && ph.querySelector('[data-slot="page-header-toolbar"]') ? t(ph.querySelector('[data-slot="page-header-toolbar"]')).slice(0, 120) : null,
    breadcrumb: [...document.querySelectorAll('nav[aria-label="breadcrumb"] li, nav[aria-label="Breadcrumb"] li')].map(t).filter(Boolean),
    backLink: [...main.querySelectorAll("a")].filter((a) => vis(a) && /^Back to/.test(t(a))).map((a) => ({ text: t(a), ...C(a) })),
  };
  // Sub-nav
  const subnav = [...main.querySelectorAll("a")].filter((a) => vis(a) && /^(Fee notes|Fee runs)$/.test(t(a))).map((a) => { const af = getComputedStyle(a, "::after"); return { text: t(a), href: a.getAttribute("href"), ariaCurrent: a.getAttribute("aria-current"), inNav: !!a.closest("nav"), underlineOpacity: af.opacity, underlineHeight: af.height, underlineBg: af.backgroundColor, contrast: C(a).ratio, color: C(a).fg }; });
  // Status filter links (the "Filter:" row)
  const fbar = [...main.querySelectorAll("div")].find((d) => /^Filter:/.test(t(d)) && d.querySelectorAll("a").length >= 5 && d.querySelectorAll("div").length < 3);
  const filters = fbar ? { slots: slots(fbar), links: [...fbar.querySelectorAll("a")].map((a) => ({ text: t(a), href: a.getAttribute("href"), active: /bg-slate-900/.test(a.className), ariaCurrent: a.getAttribute("aria-current"), contrast: C(a).ratio })) } : null;
  // StatCards
  const cards = [...main.querySelectorAll('[data-slot="stat-card"]')].filter(vis);
  const deepest = (e) => { let x = e; while (x.children.length === 1 && x.firstElementChild.tagName !== "svg") x = x.firstElementChild; return x; };
  const stat = cards.map((c) => {
    const lab = c.querySelector('[data-slot="stat-card-label"]'), val = c.querySelector('[data-slot="stat-card-value"]');
    const inner = val ? deepest(val) : null;
    const cr = c.getBoundingClientRect();
    const spill = [...c.querySelectorAll("*")].filter((e) => vis(e) && e.getBoundingClientRect().right > cr.right + 0.5).map((e) => Math.round(e.getBoundingClientRect().right - cr.right));
    return { testid: c.dataset.testid || null, state: c.dataset.state || null, label: lab ? t(lab) : null, value: val ? t(val) : null, valueCls: inner ? (inner.className || "").toString().slice(0, 80) : null, valueColor: inner ? getComputedStyle(inner).color : null, valueContrast: inner ? C(inner).ratio : null, valueFontPx: inner ? parseFloat(getComputedStyle(inner).fontSize) : null, valueFont: val ? getComputedStyle(val).fontFamily.slice(0, 40) : null, cardWidth: Math.round(cr.width), cardHeight: Math.round(cr.height), cardClient: c.clientWidth, cardScroll: c.scrollWidth, valueClient: val ? val.clientWidth : null, valueScroll: val ? val.scrollWidth : null, valueSpillPx: val ? Math.max(0, Math.round(val.getBoundingClientRect().right - (cr.right - parseFloat(getComputedStyle(c).paddingRight)))) : null, valueLines: val ? lines(val) : null, descSpillMaxPx: spill.length ? Math.max(...spill) : 0 };
  });
  const sg = cards.length ? cards[0].parentElement : null;
  const statGrid = sg ? { cls: sg.className, width: Math.round(sg.getBoundingClientRect().width), display: getComputedStyle(sg).display, cols: getComputedStyle(sg).display === "grid" ? getComputedStyle(sg).gridTemplateColumns.split(" ").length : null, template: getComputedStyle(sg).gridTemplateColumns, rows: getComputedStyle(sg).display === "grid" ? getComputedStyle(sg).gridTemplateRows.split(" ").length : null, containerWidth: sg.parentElement ? Math.round(sg.parentElement.getBoundingClientRect().width) : null, containerType: sg.parentElement ? getComputedStyle(sg.parentElement).containerType : null } : null;
  // Tables
  const fadeOf = (sc) => { const out = []; const chk = (e, where) => { if (!e) return; for (const ps of [null, "::before", "::after"]) { const cs = getComputedStyle(e, ps); const mask = cs.maskImage || cs.webkitMaskImage || "none"; if (/gradient/.test(cs.backgroundImage) || mask !== "none") out.push({ where, pseudo: ps, bg: cs.backgroundImage.slice(0, 60), mask: mask.slice(0, 60) }); } }; chk(sc, "scroller"); chk(sc.parentElement, "parent"); [...sc.parentElement.children].filter((c) => c !== sc).forEach((c) => chk(c, "sibling")); [...sc.children].filter((c) => c.tagName !== "TABLE").forEach((c) => chk(c, "child")); return out; };
  const tables = [...main.querySelectorAll("table")].filter(vis).map((tb) => {
    const sc = tb.closest('[data-slot="table-container"]') || window.__scroller(tb);
    const scr = sc ? sc.getBoundingClientRect() : null;
    const ths = [...tb.querySelectorAll("thead th")];
    const rows = [...tb.querySelectorAll("tbody tr")].filter(vis);
    const hs = rows.map((r) => Math.round(r.getBoundingClientRect().height));
    const clipped = [...tb.querySelectorAll("tbody td, thead th")].filter((c) => vis(c) && c.scrollWidth > c.clientWidth + 1).map((c) => t(c).slice(0, 30));
    const offRight = scr ? ths.filter(vis).map((th) => ({ h: t(th) || "(blank)", w: Math.round(th.getBoundingClientRect().width), cutPx: Math.max(0, Math.round(th.getBoundingClientRect().right - (scr.left + sc.clientWidth))), left: Math.round(th.getBoundingClientRect().left - scr.left) })) : [];
    return {
      testid: tb.dataset.testid || null, slots: slots(tb), inDataTable: !!tb.closest('[data-slot="data-table"]'),
      headersDom: ths.map((th) => t(th) || "(blank)"), headersVisible: ths.filter(vis).map((th) => t(th) || "(blank)"),
      headLines: ths.filter(vis).map((th) => `${t(th)}:${lines(th)}`), cols: offRight,
      tableWidth: Math.round(tb.getBoundingClientRect().width), bodyRows: rows.length,
      scroller: sc ? { slot: sc.dataset.slot || null, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth, tabindex: sc.getAttribute("tabindex"), role: sc.getAttribute("role"), ariaLabel: sc.getAttribute("aria-label"), fades: fadeOf(sc) } : null,
      rowHeights: hs, rowHeightMin: hs.length ? Math.min(...hs) : null, rowHeightMax: hs.length ? Math.max(...hs) : null,
      rowsAboveFold: rows.filter((r) => r.getBoundingClientRect().bottom + scrollY <= innerHeight).length, tableTop: Math.round(tb.getBoundingClientRect().top + scrollY),
      firstCellLines: rows.map((r) => r.querySelector("td")).filter(Boolean).map((c) => `${t(c).slice(0, 34)}:${lines(c)}`),
      links: [...tb.querySelectorAll("tbody a[href]")].map((a) => ({ text: t(a).slice(0, 44), href: a.getAttribute("href").replace(U, "{id}"), lines: lines(a), contrast: C(a).ratio, fg: C(a).fg, bg: C(a).bg })),
      rowCursor: rows.length ? getComputedStyle(rows[0]).cursor : null,
      checkboxes: [...tb.querySelectorAll('[role="checkbox"], input[type="checkbox"]')].map((c) => ({ label: c.getAttribute("aria-label"), checked: c.getAttribute("aria-checked") || c.getAttribute("data-state") || String(c.checked) })),
      clippedCells: clipped.slice(0, 10),
      cellTexts: rows.slice(0, 25).map((r) => [...r.children].map((c) => (vis(c) ? t(c).slice(0, 40) : "<hidden>"))),
    };
  });
  const empties = [...main.querySelectorAll('[data-slot="empty-state"]')].filter(vis).map((e) => ({ variant: e.dataset.variant, text: t(e).slice(0, 160), heading: e.querySelector("h1,h2,h3,h4") ? `${e.querySelector("h1,h2,h3,h4").tagName.toLowerCase()}:${t(e.querySelector("h1,h2,h3,h4"))}` : null, height: Math.round(e.getBoundingClientRect().height), width: Math.round(e.getBoundingClientRect().width), borderStyle: getComputedStyle(e).borderTopStyle, inTable: !!e.closest("table") }));
  const dashed = [...main.querySelectorAll("*")].filter((e) => vis(e) && /dashed/.test(getComputedStyle(e).borderTopStyle)).map((e) => ({ tag: e.tagName.toLowerCase(), text: t(e).slice(0, 60), cls: (e.className || "").toString().slice(0, 60) }));
  const gt = main.querySelector('[data-testid="unbilled-grand-total"]');
  const grandTotal = gt ? { text: t(gt), rect: R(gt), slots: slots(gt), inTable: !!gt.closest("table"), html: gt.outerHTML.slice(0, 300) } : null;
  const form = main.querySelector('form[aria-label="Unbilled work filters"]');
  const unbilledForm = form ? { slots: slots(form), inDataTable: !!form.closest('[data-slot="data-table"]'), rect: R(form), help: main.querySelector('[data-testid="unbilled-period-help"]') ? { text: t(main.querySelector('[data-testid="unbilled-period-help"]')), slots: slots(main.querySelector('[data-testid="unbilled-period-help"]')) } : null } : null;
  const steps = [...main.querySelectorAll('nav[aria-label="Wizard steps"] li')].map((li) => { const b = li.querySelector("div"), s = li.querySelector("span"); return { n: t(b), label: t(s), badgeCls: b.className.replace(/\s+/g, " ").slice(0, 120), badge: C(b), label_: C(s), labelLines: lines(s) }; });
  // Dark audit: large bright surfaces and every visible icon's colour (manual review in dark).
  const lum = (c) => { const m = c.match(/\d+/g).map(Number); const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(m[0]) + 0.7152 * f(m[1]) + 0.0722 * f(m[2]); };
  const bright = [...main.querySelectorAll("div,section,td,th,tr,table,span,p")].filter((e) => { if (!vis(e)) return false; const b = e.getBoundingClientRect(); if (b.width * b.height < 600) return false; const bg = getComputedStyle(e).backgroundColor; if (/rgba\(0, 0, 0, 0\)|transparent/.test(bg)) return false; return lum(window.__bg(e)) > 0.6; }).map((e) => ({ tag: e.tagName.toLowerCase(), cls: (e.className || "").toString().slice(0, 70), bg: window.__bg(e), text: t(e).slice(0, 40), area: Math.round(e.getBoundingClientRect().width * e.getBoundingClientRect().height) })).slice(0, 10);
  const icons = [...main.querySelectorAll("svg")].filter(vis).map((s) => ({ cls: (s.getAttribute("class") || "").slice(0, 60), color: getComputedStyle(s).color, ctx: t(s.closest("button,a,td,div") || s.parentElement).slice(0, 40) }));
  const iconsD = []; const im = new Map(); for (const i of icons) { const k = `${i.cls}|${i.color}`; if (!im.has(k)) { im.set(k, 1); iconsD.push(i); } }
  const nowrapClipped = [...main.querySelectorAll("td,th,p,span,a")].filter((e) => vis(e) && getComputedStyle(e).whiteSpace === "nowrap" && e.scrollWidth > e.clientWidth + 1 && getComputedStyle(e).overflow !== "visible").map((e) => t(e).slice(0, 40)).slice(0, 10);
  return { header, subnav, filters, stat, statGrid, tables, empties, dashed, grandTotal, unbilledForm, steps, dark: { bright, icons: iconsD.slice(0, 20), nowrapClipped }, subtle: window.__targets ? window.__targets().subtle : null, lowContrast: window.__contrast(main) };
};

module.exports = { AFTER };
