// 666B in-page probes for 01-capture / 03-lifecycle. HELPERS, HELPERS2 and FULL are copied verbatim
// from 666A/before/scripts/probes.cjs (that file is not edited) — visibility / text helpers, table
// measure with per-column cut, contrast, card / grid overflow, the full page inventory. DETAIL and
// COMPACT2 (new) probe the fee-note detail page.
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

// 666B: fee-note DETAIL probe (new). Header block (h1 + HelpTip, status / take-on badges, meta row,
// action buttons with variant / size / classes), content frame widths, totals block, the balance /
// payment testids, payment form, payment history, indicators, read-only details or draft form, line
// table (inline edit / delete), custom fields, generated documents, collection activity, audit trail,
// and the order + size of the page's sections.
const DETAIL = () => {
  const main = document.querySelector("main#main-content");
  if (!main) return { error: "no main#main-content" };
  const vis = window.__v, t = window.__t;
  const box = (e) => { if (!e) return null; const r = e.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height) }; };
  const cs = getComputedStyle(main);
  const inner = main.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
  const page = main.querySelector("div.space-y-8") || main.firstElementChild;
  const h1 = main.querySelector("h1");
  const header = h1 ? h1.closest("div.flex.items-start.justify-between") : null;
  const left = header ? header.firstElementChild : null;
  const actions = header ? header.lastElementChild : null;
  const btn = (b) => ({ text: t(b) || b.getAttribute("aria-label"), variant: b.getAttribute("data-variant"), size: b.getAttribute("data-size"), slot: b.getAttribute("data-slot"), disabled: b.disabled || undefined, cls: (b.className || "").toString().slice(0, 160), box: box(b), bg: getComputedStyle(b).backgroundColor, color: getComputedStyle(b).color, contrast: window.__contrastOf(b).ratio });
  const badges = left ? [...left.querySelectorAll('[data-slot="badge"]')].filter(vis).map((b) => ({ text: t(b), variant: b.getAttribute("data-variant"), testid: b.dataset.testid, cls: (b.className || "").toString().slice(0, 120), contrast: window.__contrastOf(b).ratio })) : [];
  const meta = left ? [...(left.querySelector("div.mt-2") || document.createElement("div")).children].map(t) : [];
  const h1Info = h1 ? { text: t(h1), ownText: [...h1.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent).join("").trim(), fontPx: parseFloat(getComputedStyle(h1).fontSize), fontFamily: getComputedStyle(h1).fontFamily.slice(0, 60), cls: h1.className, helpTip: [...h1.querySelectorAll("button,[role=button]")].map((b) => b.getAttribute("aria-label") || t(b)), box: box(h1) } : null;
  const subtotal = [...main.querySelectorAll("span")].find((s) => t(s) === "Subtotal");
  const totals = subtotal ? subtotal.closest("div.max-w-xs") : null;
  const totalsRows = totals ? [...totals.children].filter(vis).map((r) => t(r)) : [];
  const ids = ["invoice-balance-due", "totals-balance-due", "record-payment-form", "payment-method-select", "payment-form-balance-due", "send-override-dialog", "partially-paid-indicator", "fee-note-takeon-badge", "custom-field-section", "field-group-selector", "generated-documents-list", "collections-history-empty", "invoice-audit-section", "add-disbursements-button", "payment-provider-method-compact"];
  const testids = {};
  for (const id of ids) { const els = [...document.querySelectorAll(`[data-testid="${id}"]`)]; testids[id] = { n: els.length, visible: els.filter(vis).length, text: els[0] ? t(els[0]).slice(0, 200) : null }; }
  const h3 = (txt) => [...main.querySelectorAll("h2,h3")].find((h) => t(h) === txt);
  const sectionOf = (h) => (h ? h.closest("div.rounded-lg, div.space-y-4") || h.parentElement : null);
  const ph = h3("Payment History");
  const phBox = sectionOf(ph);
  const paymentHistory = ph ? { text: t(phBox).slice(0, 400), headers: [...phBox.querySelectorAll("thead th")].filter(vis).map(t), rows: [...phBox.querySelectorAll("tbody tr")].map((r) => [...r.children].filter(vis).map((c) => t(c).slice(0, 40))), box: box(phBox), table: phBox.querySelector("table") ? window.__tableMeasure(phBox.querySelector("table")) : null } : null;
  const indicators = [...main.querySelectorAll("div.rounded-lg")].filter((d) => vis(d) && /^(Payment Received|Part payment received|This fee note has been voided\.|Validation issues found|Online Payment Link|Record Payment)/.test(t(d))).map((d) => ({ text: t(d).slice(0, 240), cls: (d.className || "").toString().slice(0, 100), box: box(d) }));
  const lt = h3("Line Items");
  const ltBox = lt ? lt.closest("div.space-y-4") : null;
  const ltTable = ltBox ? ltBox.querySelector("table") : null;
  const lineTable = ltBox ? { headers: ltTable ? [...ltTable.querySelectorAll("thead th")].filter(vis).map(t) : [], rows: ltTable ? [...ltTable.querySelectorAll("tbody tr")].map((r) => ({ testid: r.dataset.testid, cells: [...r.children].filter(vis).map((c) => t(c).slice(0, 50)), buttons: [...r.querySelectorAll("button")].map((b) => b.getAttribute("aria-label") || t(b)) })) : [], addLine: [...ltBox.querySelectorAll("button")].filter((b) => t(b) === "Add Line").length, empty: ltTable ? null : t(ltBox).slice(0, 120), measure: ltTable ? window.__tableMeasure(ltTable) : null } : null;
  const details = h3("Fee note details");
  const detBox = details ? details.parentElement : null;
  const readonly = detBox && detBox.querySelector("dl") ? [...detBox.querySelectorAll("dl > div")].map((d) => `${t(d.querySelector("dt"))}: ${t(d.querySelector("dd"))}`) : null;
  const draftForm = detBox && !detBox.querySelector("dl") ? [...detBox.querySelectorAll("input,textarea,select")].map((e) => ({ id: e.id, type: e.type, value: (e.value || "").slice(0, 60), placeholder: e.placeholder || undefined })) : null;
  const cf = document.querySelector('[data-testid="custom-field-section"]');
  const fg = document.querySelector('[data-testid="field-group-selector"]');
  const gd = h3("Generated Documents");
  const ca = h3("Collection activity");
  const au = document.querySelector('[data-testid="invoice-audit-section"]');
  const sec = (e, n = 300) => (e ? { text: t(e).slice(0, n), box: box(e) } : null);
  const sections = page ? [...page.children].filter(vis).map((c) => { const hh = c.querySelector("h1,h2,h3"); return { tag: c.tagName.toLowerCase(), testid: c.dataset.testid, heading: hh ? t(hh).slice(0, 50) : t(c).slice(0, 40), box: box(c) }; }) : [];
  const clientSections = h1 && header ? [...header.parentElement.children].filter(vis).map((c) => { const hh = c.querySelector("h1,h2,h3"); return { testid: c.dataset.testid, heading: hh ? t(hh).slice(0, 50) : t(c).slice(0, 50), box: box(c) }; }) : [];
  return {
    mainInner: Math.round(inner), mainClientWidth: main.clientWidth, mainPadding: `${cs.paddingLeft} ${cs.paddingRight}`, pageFrame: box(page), pageFrameCls: page ? (page.className || "").toString() : null,
    header: header ? { box: box(header), left: box(left), actions: box(actions), cls: header.className, actionsCls: actions.className } : null,
    h1: h1Info, badges, meta, buttons: actions ? [...actions.querySelectorAll("button,a")].filter(vis).map(btn) : [],
    backLink: [...main.querySelectorAll("a")].filter((a) => vis(a) && /Back to fee notes/.test(t(a))).map((a) => ({ text: t(a), href: a.getAttribute("href"), contrast: window.__contrastOf(a).ratio })),
    generateDoc: [...main.querySelectorAll("button")].filter((b) => vis(b) && /Generate/.test(t(b))).map((b) => t(b)),
    alerts: [...main.querySelectorAll('[role="alert"]')].filter(vis).map((e) => t(e).slice(0, 200)),
    totals: totals ? { rows: totalsRows, box: box(totals), cls: totals.className } : null,
    testids, placeholderLineDesc: document.querySelectorAll('[placeholder="Line item description"]').length,
    indicators, paymentHistory, lineTable, readonly, draftForm,
    customFields: sec(cf), fieldGroupSelector: sec(fg), generatedDocs: sec(sectionOf(gd)), collectionActivity: sec(sectionOf(ca)), audit: sec(au, 500), auditTimelineItems: au ? au.querySelectorAll("li, [data-testid^='audit-']").length : null,
    sections, clientSections,
  };
};

const COMPACT2 = () => {
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
    detail: window.__detail(),
    cards: window.__cards(),
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

module.exports = { HELPERS, HELPERS2, DETAIL, COMPACT2, FULL };
