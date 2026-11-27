// 669A in-page probe (page.evaluate). Pure DOM reads — no clicks. One JSON object per page: shell
// counts (h1 / level-1 headings / main), the page header (h1 text, font size + weight, subtitle),
// breadcrumb + active nav label (K-84), document overflow, every data-testid (UUIDs → <id>), every
// table in #main-content (table width vs its scroller / bordered card, headers in view, rows with
// cells and badges), every empty state ([data-slot=empty-state], *empty* testids, "No …" text) with
// its height, low-contrast text (composite < 4.5:1 — K-85 and the DARK-AUDIT read), links with their
// colour (K-37), raw ISO dates in the text (K-79), empty badges (K-81), the data-request header /
// timeline dates (K-68), inputs and their widths (K-60), tabs, and the SEL-COMPAT counts.
// Structure-agnostic: the same probe reads the before (raw tables, hand-rolled headers) and the after
// (DataTable / PageHeader) DOM.
function probe() {
  const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const R = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height), right: Math.round(r.right) }; };
  const main = document.querySelector("#main-content") || document.body;
  const cv = document.createElement("canvas");
  cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (layers) => { cx.globalAlpha = 1; cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of layers) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const bgLayers = (el) => { const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } } return ls; };
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  const colour = (el) => { const ls = bgLayers(el); const bg = px(ls); const fg = px([...ls, getComputedStyle(el).color]); return { fg: hex(fg), bg: hex(bg), ratio: ratio(fg, bg) }; };
  const cls = (e) => (e.className && e.className.baseVal != null ? e.className.baseVal : e.className || "");
  const visible = (e) => { const r = e.getBoundingClientRect(); const s = getComputedStyle(e); return r.width > 0 && r.height > 0 && s.visibility !== "hidden" && s.display !== "none"; };
  const ownText = (e) => norm([...e.childNodes].filter((n) => n.nodeType === 3).map((n) => n.textContent).join(" "));

  const out = { url: location.pathname + location.search, title: document.title, vw: innerWidth, docClientWidth: document.documentElement.clientWidth };
  out.h1Main = [...main.querySelectorAll("h1")].map((h) => norm(h.innerText));
  out.level1Main = main.querySelectorAll("h1,[role=heading][aria-level='1']").length;
  out.h1Page = document.querySelectorAll("h1").length;
  out.mainIdCount = document.querySelectorAll("main#main-content").length;
  out.mainElCount = document.querySelectorAll("main").length;
  out.docHOverflowPx = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  out.docHeight = document.documentElement.scrollHeight;
  const cs = getComputedStyle(main);
  out.mainInnerWidth = Math.round(main.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight));
  out.notFound = /could not be found|Page not found|\b404\b/i.test(norm(main.innerText).slice(0, 400));
  out.errorBoundary = /Something went wrong/.test(main.innerText);
  out.bodyHead = norm(main.innerText).slice(0, 400);
  const bc = document.querySelector('nav[aria-label="Breadcrumb"]');
  out.breadcrumb = bc ? norm(bc.innerText) : null;
  out.navActive = [...document.querySelectorAll('[data-sidebar="menu-button"][data-active="true"], nav a[aria-current="page"]')].map((a) => norm(a.innerText)).filter(Boolean);

  const h1 = main.querySelector("h1");
  if (h1) {
    const s = getComputedStyle(h1);
    const block = h1.closest("header") || h1.parentElement;
    // subtitle = the h1's next sibling <p>, else the next sibling of its wrapper (PageHeader: title row + description)
    const nxt = (e) => (e && e.nextElementSibling && e.nextElementSibling.tagName === "P" ? e.nextElementSibling : null);
    const sub = nxt(h1) || (h1.parentElement !== main && !/\bspace-y-\d/.test(cls(h1.parentElement)) ? nxt(h1.parentElement) : null);
    out.header = { text: norm(h1.innerText), cls: cls(h1), fontSize: s.fontSize, fontWeight: s.fontWeight, fontFamily: s.fontFamily.split(",")[0], box: R(h1), ...colour(h1), dataSlot: (h1.closest("[data-slot]") || {}).dataset ? (h1.closest("[data-slot]") || { dataset: {} }).dataset.slot || null : null, subtitle: sub ? norm(sub.innerText).slice(0, 160) : null, blockTag: block ? block.tagName.toLowerCase() : null };
  }
  const tids = {};
  for (const e of document.querySelectorAll("[data-testid]")) { const k = e.dataset.testid.replace(UUID, "<id>"); tids[k] = (tids[k] || 0) + 1; }
  out.testids = tids;
  const isBadge = (e) => e.dataset.slot === "badge";
  out.tables = [...main.querySelectorAll("table")].filter(visible).map((t) => {
    let sc = t.parentElement;
    while (sc && sc !== main && !/(auto|scroll|hidden)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const scR = sc && sc !== main ? sc.getBoundingClientRect() : main.getBoundingClientRect();
    let card = t.parentElement;
    while (card && card !== main && !(parseFloat(getComputedStyle(card).borderTopWidth) > 0 && parseFloat(getComputedStyle(card).borderLeftWidth) > 0)) card = card.parentElement;
    if (card === main) card = null;
    const ths = [...t.querySelectorAll("thead th")].map((th) => { const b = th.getBoundingClientRect(); const shown = getComputedStyle(th).display !== "none" && b.width > 0; return { text: norm(th.textContent), w: Math.round(b.width), shown, inView: shown && b.left < scR.right - 1 && b.right > scR.left + 1, fullyInView: shown && b.left >= scR.left - 1 && b.right <= scR.right + 1 }; });
    const rows = [...t.querySelectorAll("tbody tr")];
    return {
      testid: t.dataset.testid || null,
      wrapperTestid: t.parentElement.closest("[data-testid]") ? t.parentElement.closest("[data-testid]").dataset.testid.replace(UUID, "<id>") : null,
      width: Math.round(t.getBoundingClientRect().width),
      tableScrollWidth: t.scrollWidth,
      scroller: sc && sc !== main ? { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth } : null,
      container: { clientWidth: Math.round(scR.width) },
      card: card ? { clientWidth: card.clientWidth, testid: card.dataset.testid || null } : null,
      headers: ths,
      columnsInView: ths.filter((h) => h.inView).map((h) => h.text || "(blank)"),
      columnsFullyInView: ths.filter((h) => h.fullyInView).map((h) => h.text || "(blank)"),
      rowCount: rows.length,
      rows: rows.slice(0, 25).map((r) => ({
        testid: (r.dataset.testid || "").replace(UUID, "<id>"),
        cells: [...r.querySelectorAll("td")].map((td) => norm(td.innerText).slice(0, 80)),
        h: Math.round(r.getBoundingClientRect().height),
        badges: [...r.querySelectorAll("span,div")].filter(isBadge).map((b) => `${norm(b.innerText)} ${colour(b).ratio}`),
      })),
    };
  });
  // empty states: the EmptyState component, *empty* testids, and hand-rolled "No …" / permission text
  const empties = new Set([...main.querySelectorAll('[data-slot="empty-state"], [data-testid*="empty"]')]);
  for (const p of main.querySelectorAll("p,h2,h3,div")) {
    if (p.children.length > 2) continue;
    const t = norm(p.innerText);
    if (/^(No |Nothing|You do not have permission|Module Not Available)/.test(t) && ![...empties].some((e) => e.contains(p))) empties.add(p);
  }
  out.empties = [...empties].filter(visible).map((e) => ({ slot: e.dataset.slot || null, variant: e.dataset.variant || null, testid: e.dataset.testid || null, tag: e.tagName.toLowerCase(), text: norm(e.innerText).slice(0, 160), h: Math.round(e.getBoundingClientRect().height), w: Math.round(e.getBoundingClientRect().width), hasIcon: !!e.querySelector("svg"), heading: (e.querySelector("h1,h2,h3,h4") || { tagName: "" }).tagName.toLowerCase() || null, ...colour(e) }));
  // low-contrast leaf text (composite < 4.5)
  out.lowContrast = [...main.querySelectorAll("*")].filter((e) => ownText(e) && visible(e) && !e.closest("svg")).map((e) => ({ text: ownText(e).slice(0, 50), tag: e.tagName.toLowerCase(), cls: cls(e).split(/\s+/).filter((c) => /^(dark:)?(text|bg)-/.test(c)).join(" "), ...colour(e) })).filter((x) => x.ratio < 4.5).slice(0, 60);
  out.links = [...main.querySelectorAll("a")].filter(visible).map((a) => ({ text: norm(a.innerText).slice(0, 50), href: (a.getAttribute("href") || "").replace(UUID, "<id>"), cls: cls(a).split(/\s+/).filter((c) => /text-(teal|slate|primary)/.test(c)).join(" "), fontFamily: getComputedStyle(a).fontFamily.split(",")[0], ...colour(a) })).slice(0, 60);
  out.isoDates = [...main.querySelectorAll("*")].filter((e) => /\b\d{4}-\d{2}-\d{2}\b/.test(ownText(e)) && visible(e)).map((e) => ({ text: ownText(e).slice(0, 60), tag: e.tagName.toLowerCase(), mono: /mono/i.test(getComputedStyle(e).fontFamily) })).slice(0, 40);
  out.badges = [...main.querySelectorAll('[data-slot="badge"]')].filter(visible).map((b) => ({ text: norm(b.innerText), variant: b.dataset.variant || null, w: Math.round(b.getBoundingClientRect().width), ...colour(b) })).slice(0, 60);
  out.emptyBadges = out.badges.filter((b) => !b.text).length;
  out.inputs = [...main.querySelectorAll("input,select,textarea")].filter(visible).map((i) => ({ tag: i.tagName.toLowerCase(), type: i.type, name: i.name || i.id || i.getAttribute("aria-label") || i.getAttribute("placeholder") || null, testid: i.dataset.testid || null, value: i.tagName === "SELECT" ? (i.options[i.selectedIndex] || {}).text : i.value, w: Math.round(i.getBoundingClientRect().width) }));
  out.tabs = [...main.querySelectorAll('[role="tab"]')].filter(visible).map((t) => `${norm(t.innerText) || t.getAttribute("aria-label")}${t.getAttribute("aria-selected") === "true" ? "*" : ""}`);
  out.headings = [...main.querySelectorAll("h1,h2,h3,[role=heading]")].filter(visible).map((h) => `${h.tagName.toLowerCase()}: ${norm(h.innerText).slice(0, 70)}`);
  const btn = {};
  for (const b of main.querySelectorAll("button")) { if (!visible(b)) continue; const k = `${norm(b.innerText) || b.getAttribute("aria-label") || "(icon)"}${b.disabled ? " [disabled]" : ""}`.slice(0, 60); btn[k] = (btn[k] || 0) + 1; }
  out.buttons = btn;
  // K-68: data-request header meta line + timeline text
  const reqLine = [...main.querySelectorAll("span,p,div")].find((e) => /^Requested:/.test(ownText(e)) || /^Requested:/.test(norm(e.innerText)) && e.children.length === 0);
  const tl = [...main.querySelectorAll("h2,h3")].find((h) => norm(h.textContent).toLowerCase() === "status timeline");
  out.dsar = { requested: reqLine ? norm(reqLine.innerText) : null, metaLine: reqLine && reqLine.parentElement ? norm(reqLine.parentElement.innerText) : null, timeline: tl ? norm(tl.parentElement.textContent).slice(0, 400) : null };
  // K-67 / K-85: information-request metadata panel labels
  const ir = [...main.querySelectorAll("div")].find((d) => /grid/.test(cls(d)) && /Reminder Interval/.test(d.innerText) && d.children.length >= 4);
  out.irMeta = ir ? [...ir.children].map((c) => ({ text: norm(c.innerText).slice(0, 80), label: (() => { const l = c.firstElementChild; return l ? { text: norm(l.innerText), ...colour(l) } : null; })(), small: [...c.querySelectorAll("p")].map((p) => ({ text: norm(p.innerText).slice(0, 50), ...colour(p) })) })) : null;
  const headingsNamed = (lvl, name, exact = true) => [...document.querySelectorAll(lvl ? `h${lvl},[role=heading][aria-level="${lvl}"]` : "h1,h2,h3,h4,h5,h6,[role=heading]")].filter((h) => (exact ? norm(h.innerText) === name : norm(h.innerText).toLowerCase().includes(name.toLowerCase()))).length;
  out.sel = {
    h1Compliance: headingsNamed(1, "Compliance"),
    h1ComplianceSubstring: headingsNamed(1, "Compliance", false),
    h1DataRequests: headingsNamed(1, "Data Requests"),
    h1Disbursements: headingsNamed(1, "Disbursements"),
    headingContainsDisbursements: [...main.querySelectorAll("h1,h2,h3,h4,h5,h6,[role=heading]")].filter((h) => /disbursements/i.test(norm(h.innerText))).map((h) => `${h.tagName.toLowerCase()}: ${norm(h.innerText).slice(0, 60)}`),
    h2Sections: [...main.querySelectorAll("h2")].map((h) => norm(h.innerText)).slice(0, 20),
    adversePartyTable: document.querySelectorAll('[data-testid="adverse-party-table"]').length,
    disbursementRows: document.querySelectorAll('[data-testid^="disbursement-row-"]').length,
    disbursementsListPage: document.querySelectorAll('[data-testid="disbursements-list-page"]').length,
    statementsEmpty: document.querySelectorAll('[data-testid="statements-empty"]').length,
    tariffItemSearch: document.querySelectorAll('[data-testid="tariff-item-search"]').length,
    mainTbodyTr: main.querySelectorAll("tbody tr").length,
  };
  return out;
}
module.exports = { probe };
