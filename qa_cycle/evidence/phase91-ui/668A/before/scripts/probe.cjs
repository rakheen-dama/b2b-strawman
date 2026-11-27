// 668A in-page probe (page.evaluate). Pure DOM reads — no clicks. One JSON object per page: shell
// counts (h1 / main), the page header block (K-96: title-block width, h1 / subtitle line counts, the
// right edge of every header link/button vs the viewport), document overflow + height (K-97), every
// data-testid (UUIDs → <id>), every table in #main-content (scroller clientWidth/scrollWidth, the
// bordered card's clientWidth, header widths + which columns are in view, rows with cells and badges
// with composite contrast — K-09, the court-date list, the timesheet grid), `text-slate-400` text with
// composite contrast (K-39), the header notification badge (K-31), diary state (view tabs, range,
// events with status/overdue chips, filter chips), notifications list state, time-cell inputs, and the
// SEL-COMPAT counts. Structure-agnostic on purpose: the same probe reads the before (raw tables) and
// the after (DataTable / PageHeader) DOM.
function probe() {
  const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const R = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height), right: Math.round(r.right) }; };
  const main = document.querySelector("#main-content") || document.body;
  // ---- colour: composite every ancestor background (alpha-aware, any CSS colour syntax) on a canvas
  const cv = document.createElement("canvas");
  cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (layers) => { cx.globalAlpha = 1; cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of layers) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const bgLayers = (el) => { const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } } return ls; };
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  const colour = (el) => { const ls = bgLayers(el); const bg = px(ls); const fg = px([...ls, getComputedStyle(el).color]); return { fg: hex(fg), bg: hex(bg), ratio: ratio(fg, bg), color: getComputedStyle(el).color }; };
  const lines = (el) => { if (!el) return null; const rg = document.createRange(); rg.selectNodeContents(el); return new Set([...rg.getClientRects()].filter((x) => x.width > 0).map((x) => Math.round(x.top))).size; };
  const cls = (e) => (e.className && e.className.baseVal != null ? e.className.baseVal : e.className || "");

  const out = { url: location.pathname + location.search, title: document.title, vw: innerWidth, docClientWidth: document.documentElement.clientWidth };
  out.h1Main = [...main.querySelectorAll("h1")].map((h) => norm(h.innerText));
  out.h1Page = document.querySelectorAll("h1").length;
  out.h1PageTexts = [...document.querySelectorAll("h1")].map((h) => norm(h.innerText));
  out.mainIdCount = document.querySelectorAll("main#main-content").length;
  out.mainElCount = document.querySelectorAll("main").length;
  out.docHOverflowPx = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  out.docHeight = document.documentElement.scrollHeight;
  const cs = getComputedStyle(main);
  out.mainInnerWidth = Math.round(main.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight));
  out.mainRight = Math.round(main.getBoundingClientRect().right - parseFloat(cs.paddingRight));
  out.notFound = /Page not found|could not be found|404/i.test(norm(main.innerText).slice(0, 400));
  out.errorBoundary = /Something went wrong/.test(main.innerText);
  out.bodyHead = norm(main.innerText).slice(0, 300);

  // ---- header block (K-96): the h1, its title block (h1 + subtitle), the header row (title block + actions)
  const h1 = main.querySelector("h1");
  if (h1) {
    const s = getComputedStyle(h1);
    const titleBlock = h1.parentElement;
    const sub = titleBlock && [...titleBlock.querySelectorAll("p")].find((p) => p !== h1);
    // the header row = the h1's ancestor that is a direct child of the page root (a space-y-* stack)
    let headerRow = h1;
    while (headerRow.parentElement && headerRow.parentElement !== main && !/\bspace-y-\d/.test(cls(headerRow.parentElement))) headerRow = headerRow.parentElement;
    out.header = {
      h1: { text: norm(h1.innerText), cls: cls(h1), fontSize: s.fontSize, fontFamily: s.fontFamily.split(",")[0], box: R(h1), lines: lines(h1), contrast: colour(h1) },
      titleBlock: titleBlock && { box: R(titleBlock), cls: cls(titleBlock) },
      subtitle: sub ? { text: norm(sub.innerText), box: R(sub), lines: lines(sub), ...colour(sub) } : null,
      row: headerRow && { box: R(headerRow), cls: cls(headerRow), scrollWidth: headerRow.scrollWidth, clientWidth: headerRow.clientWidth },
      controls: headerRow ? [...headerRow.querySelectorAll("a,button")].filter((a) => !h1.contains(a)).map((a) => ({ tag: a.tagName.toLowerCase(), text: norm(a.innerText) || a.getAttribute("aria-label") || "(icon)", href: a.getAttribute("href"), box: R(a), pastViewportPx: Math.round(a.getBoundingClientRect().right - document.documentElement.clientWidth), pastMainPx: Math.round(a.getBoundingClientRect().right - (main.getBoundingClientRect().right - parseFloat(cs.paddingRight))) })) : [],
    };
  }
  const tids = {};
  for (const e of document.querySelectorAll("[data-testid]")) { const k = e.dataset.testid.replace(UUID, "<id>"); tids[k] = (tids[k] || 0) + 1; }
  out.testids = tids;
  const isBadge = (e) => e.dataset.slot === "badge";
  const badgeInfo = (b) => ({ text: norm(b.innerText), cls: cls(b), variant: b.dataset.variant || null, ...colour(b), box: R(b) });

  // ---- tables (K-09, court-date list, timesheet grid)
  const sectionOf = (t) => { // nearest preceding heading text (h2/h3) in the DOM
    let e = t;
    while (e && e !== main) { let p = e.previousElementSibling; while (p) { const h = p.matches("h1,h2,h3,button") ? p : p.querySelector("h2,h3"); if (h && norm(h.innerText)) return norm(h.innerText).slice(0, 60); p = p.previousElementSibling; } e = e.parentElement; }
    return null;
  };
  out.tables = [...main.querySelectorAll("table")].map((t) => {
    let sc = t.parentElement;
    while (sc && sc !== main && !/(auto|scroll|hidden)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const scR = sc && sc !== main ? sc.getBoundingClientRect() : main.getBoundingClientRect();
    let card = t.parentElement;
    while (card && card !== main && !(parseFloat(getComputedStyle(card).borderTopWidth) > 0 && parseFloat(getComputedStyle(card).borderLeftWidth) > 0)) card = card.parentElement;
    if (card === main) card = null;
    const ths = [...t.querySelectorAll("thead th")].map((th) => { const b = th.getBoundingClientRect(); const shown = getComputedStyle(th).display !== "none" && b.width > 0; return { text: norm(th.innerText), sr: norm(th.textContent), w: Math.round(b.width), x: Math.round(b.left - scR.left), shown, inView: shown && b.left < scR.right - 1 && b.right > scR.left + 1, fullyInView: shown && b.left >= scR.left - 1 && b.right <= scR.right + 1 }; });
    const rows = [...t.querySelectorAll("tbody tr")];
    return {
      testid: t.dataset.testid || null,
      wrapperTestid: t.closest("[data-testid]") ? t.closest("[data-testid]").dataset.testid.replace(UUID, "<id>") : null,
      section: sectionOf(t),
      width: Math.round(t.getBoundingClientRect().width),
      tableScrollWidth: t.scrollWidth,
      scroller: sc && sc !== main ? { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth, cls: cls(sc).slice(0, 120) } : null,
      card: card ? { clientWidth: card.clientWidth, w: Math.round(card.getBoundingClientRect().width), testid: card.dataset.testid || null } : null,
      headers: ths,
      columnsInView: ths.filter((h) => h.inView).map((h) => h.sr || "(blank)"),
      columnsFullyInView: ths.filter((h) => h.fullyInView).map((h) => h.sr || "(blank)"),
      rowCount: rows.length,
      tfoot: [...t.querySelectorAll("tfoot tr")].map((r) => norm(r.innerText)),
      rows: rows.slice(0, 40).map((r) => {
        const tds = [...r.querySelectorAll("td")];
        return {
          testid: (r.dataset.testid || "").replace(UUID, "<id>"),
          cells: tds.map((td) => norm(td.innerText).slice(0, 90)),
          h: Math.round(r.getBoundingClientRect().height),
          badges: [...r.querySelectorAll("span,div")].filter(isBadge).map(badgeInfo),
          inputs: r.querySelectorAll("input").length,
          lastCellRightMinusScroller: tds.length ? Math.round(tds[tds.length - 1].getBoundingClientRect().right - scR.right) : null,
          truncated: tds.map((td) => { const x = td.querySelector(".truncate") || td.querySelector("[data-slot=badge]"); return x ? x.scrollWidth > x.clientWidth + 1 : false; }),
        };
      }),
    };
  });

  // ---- K-39: every element whose own class has the `text-slate-400` token (light hint/empty text)
  out.slate400 = [...main.querySelectorAll("*")].filter((e) => cls(e).split(/\s+/).includes("text-slate-400") && norm(e.innerText)).slice(0, 40).map((e) => ({ text: norm(e.innerText).slice(0, 60), tag: e.tagName.toLowerCase(), within: (e.closest("[data-testid]") || {}).dataset ? e.closest("[data-testid]").dataset.testid : null, ...colour(e) }));
  out.agendaHints = [...main.querySelectorAll('[data-testid="todays-diary"] p, [data-testid="next-deadline"] p, [data-testid="time-progress-today"] span')].filter((e) => e.children.length === 0 && norm(e.innerText)).map((e) => ({ text: norm(e.innerText).slice(0, 60), cls: cls(e), within: e.closest("[data-testid]").dataset.testid, ...colour(e) }));

  // ---- K-31: the header bell badge (outside main)
  const bell = [...document.querySelectorAll('button[aria-label="Notifications"]')].find((b) => !main.contains(b));
  const badge = bell && bell.querySelector('span[aria-hidden="true"]');
  out.bell = bell ? { box: R(bell), live: norm((bell.querySelector("[aria-live]") || {}).innerText || (bell.querySelector("[aria-live]") || {}).textContent), expanded: bell.getAttribute("aria-expanded"), badge: badge ? { text: norm(badge.innerText), cls: cls(badge), box: R(badge), ...colour(badge) } : null } : null;

  // ---- diary
  out.diary = {
    tabs: [...main.querySelectorAll('[role="tablist"][aria-label="Diary view"] [role="tab"]')].map((t) => `${norm(t.innerText)}${t.getAttribute("aria-selected") === "true" ? "*" : ""}`),
    range: norm((main.querySelector('[data-testid="diary-range"]') || {}).innerText),
    filterChips: [...main.querySelectorAll('[data-testid="diary-filter-row"] button')].map((b) => `${norm(b.innerText) || b.getAttribute("aria-label")}${b.getAttribute("aria-pressed") === "true" ? "*" : b.getAttribute("aria-pressed") === "false" ? "" : "?"}`),
    filterSelects: [...main.querySelectorAll('[data-testid="diary-filter-row"] [role="combobox"], [data-testid="diary-filter-row"] select')].map((c) => norm(c.innerText || c.value)),
    events: [...main.querySelectorAll('[data-testid^="diary-event-"]')].map((e) => ({ id: (e.dataset.testid.match(UUID) || [null])[0], text: norm(e.innerText).slice(0, 140), badges: [...e.querySelectorAll("span,div")].filter(isBadge).map((b) => `${norm(b.innerText)} ${colour(b).ratio}`), box: R(e) })),
    listRows: [...main.querySelectorAll('[data-testid^="diary-list-row-"]')].map((r) => ({ kind: r.dataset.kind, cells: [...r.querySelectorAll("td")].map((td) => norm(td.innerText).slice(0, 60)) })),
    groups: [...main.querySelectorAll('[data-testid^="diary-list-group-"],[data-testid^="diary-day-group-"]')].map((g) => g.dataset.testid),
    monthChips: [...main.querySelectorAll('[data-testid^="diary-month-chip-"]')].map((c) => norm(c.innerText).slice(0, 60)),
    weekDays: [...main.querySelectorAll('[data-testid^="diary-day-2"]')].map((d) => `${d.dataset.testid.slice(11)}:${d.querySelectorAll('[data-testid^="diary-event-"]').length}`),
  };
  // ---- notifications page
  const notifList = [...main.querySelectorAll("div")].find((d) => /divide-y/.test(cls(d)) && d.querySelector('[aria-label="Unread"], button'));
  out.notifications = {
    toggles: [...main.querySelectorAll("button")].filter((b) => ["All", "Unread"].includes(norm(b.innerText))).map((b) => ({ text: norm(b.innerText), pressed: b.getAttribute("aria-pressed"), selectedCls: /bg-slate-100|bg-slate-800/.test(cls(b)), ...colour(b) })),
    items: notifList ? notifList.children.length : 0,
    unreadDots: main.querySelectorAll('[aria-label="Unread"]').length,
    markAll: [...main.querySelectorAll("button")].filter((b) => /Mark all as read/.test(b.innerText)).length,
    loadMore: [...main.querySelectorAll("button")].filter((b) => /Load more/.test(b.innerText)).length,
  };
  // ---- empties, buttons, inputs, headings, cards
  const empties = new Set([...main.querySelectorAll('[data-testid*="empty"]')]);
  for (const p of main.querySelectorAll("p,h2,h3")) if (/^(No|Nothing|You're all caught up|All caught up)\b/.test(norm(p.innerText))) empties.add(p);
  out.empties = [...empties].map((e) => ({ testid: e.dataset.testid || null, tag: e.tagName.toLowerCase(), text: norm(e.innerText).slice(0, 160), box: R(e), hasIcon: !!e.querySelector("svg"), ...colour(e) }));
  const btn = {};
  for (const b of main.querySelectorAll("button")) { const k = `${norm(b.innerText) || b.getAttribute("aria-label") || "(icon)"}${b.disabled ? " [disabled]" : ""}${b.dataset.testid ? ` #${b.dataset.testid.replace(UUID, "<id>")}` : ""}`.slice(0, 80); btn[k] = (btn[k] || 0) + 1; }
  out.buttons = btn;
  out.tabs = [...main.querySelectorAll('[role="tab"]')].map((t) => `${norm(t.innerText) || t.getAttribute("aria-label")}${t.getAttribute("aria-selected") === "true" ? "*" : ""}`);
  out.timeInputs = [...main.querySelectorAll('input[aria-label^="Hours for day"]')].map((i) => ({ label: i.getAttribute("aria-label"), value: i.value, w: Math.round(i.getBoundingClientRect().width) }));
  out.headings = [...main.querySelectorAll("h1,h2,h3,[data-slot=card-title]")].map((h) => `${h.tagName.toLowerCase()}${h.dataset.slot ? "." + h.dataset.slot : ""}: ${norm(h.innerText).slice(0, 70)}`);
  out.cards = [...main.querySelectorAll('[data-slot="card"]')].map((c) => ({ testid: c.dataset.testid || null, title: norm((c.querySelector('[data-slot="card-title"],h2,h3') || {}).innerText).slice(0, 60), box: R(c) }));
  const headingsNamed = (lvl, name) => [...document.querySelectorAll(lvl ? `h${lvl},[role=heading][aria-level="${lvl}"]` : "h1,h2,h3,h4,h5,h6,[role=heading]")].filter((h) => norm(h.innerText) === name).length;
  out.sel = {
    h1MyWork: headingsNamed(1, "My Work"),
    h1Notifications: headingsNamed(1, "Notifications"),
    h1Diary: headingsNamed(1, "Diary"),
    h1Timesheet: headingsNamed(1, "Timesheet"),
    headingByTask: headingsNamed(null, "By Task"),
    linkTimesheetReport: [...document.querySelectorAll("a")].filter((a) => /Timesheet Report/.test(a.textContent)).length,
    linkTimesheet: [...main.querySelectorAll("a")].filter((a) => norm(a.textContent) === "Timesheet").map((a) => a.getAttribute("href")),
    mainH1: main.querySelectorAll("h1,[role=heading][aria-level='1']").length,
    viewAllNotifications: [...document.querySelectorAll("a")].filter((a) => norm(a.textContent) === "View all notifications").length,
    bellButtonsExact: [...document.querySelectorAll("button")].filter((b) => b.getAttribute("aria-label") === "Notifications").length,
    notificationsDialog: document.querySelectorAll('[role="dialog"][aria-label="Notifications"]').length,
    courtDateListRows: document.querySelectorAll('[data-testid="court-date-list"] tbody tr').length,
    mainTbodyTr: main.querySelectorAll("tbody tr").length,
    savedViewSelector: document.querySelectorAll('[data-testid="saved-view-selector"]').length,
    saveViewButton: [...main.querySelectorAll("button")].filter((b) => /Save View/i.test(b.innerText)).length,
  };
  return out;
}
module.exports = { probe };
