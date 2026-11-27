// 667A in-page probe (page.evaluate). Pure DOM reads — no clicks. Returns one JSON object per page:
// shell counts (h1 / main), document overflow, every data-testid (UUIDs normalised to <id>), every
// table in #main-content (scroller clientWidth/scrollWidth, header widths, row count, row cells),
// the pager, filter-link hrefs, the ledger totals breakdown, buttons, K-10 reference cells.
function probe(opts) {
  const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const R = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height) }; };
  const main = document.querySelector("#main-content") || document.body;
  const out = { url: location.pathname + location.search, title: document.title };
  out.h1Main = [...main.querySelectorAll("h1")].map((h) => norm(h.innerText));
  out.h1Page = document.querySelectorAll("h1").length;
  out.mainIdCount = document.querySelectorAll("main#main-content").length;
  out.mainElCount = document.querySelectorAll("main").length;
  out.mainContentId = document.querySelectorAll("#main-content").length;
  out.docHOverflowPx = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  out.docHeight = document.documentElement.scrollHeight;
  const cs = getComputedStyle(main);
  out.mainInnerWidth = Math.round(main.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight));
  out.notFound = /Page not found|could not be found|404/i.test(norm(main.innerText).slice(0, 400));
  out.bodyHead = norm(main.innerText).slice(0, 240);
  const h1 = main.querySelector("h1");
  if (h1) { const s = getComputedStyle(h1); out.h1Style = { cls: h1.className, fontSize: s.fontSize, fontFamily: s.fontFamily.split(",")[0], color: s.color, box: R(h1) }; }
  // all testids
  const tids = {};
  for (const e of document.querySelectorAll("[data-testid]")) { const k = e.dataset.testid.replace(UUID, "<id>"); tids[k] = (tids[k] || 0) + 1; }
  out.testids = tids;
  // tables
  out.tables = [...main.querySelectorAll("table")].map((t) => {
    let sc = t.parentElement;
    while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const card = t.closest('[data-slot="card"]') || t.closest(".rounded-xl, .rounded-lg");
    const ths = [...t.querySelectorAll("thead th")].map((th) => ({ text: norm(th.innerText), w: Math.round(th.getBoundingClientRect().width), align: getComputedStyle(th).textAlign }));
    const rows = [...t.querySelectorAll("tbody tr")];
    const scR = sc && sc !== main ? sc.getBoundingClientRect() : null;
    return {
      testid: t.dataset.testid || null,
      width: Math.round(t.getBoundingClientRect().width),
      scrollWidth: t.scrollWidth,
      scroller: sc && sc !== main ? { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflows: sc.scrollWidth > sc.clientWidth, overflowPx: sc.scrollWidth - sc.clientWidth, cls: sc.className } : null,
      card: card ? { w: Math.round(card.getBoundingClientRect().width) } : null,
      headers: ths,
      rowCount: rows.length,
      rowTestidPrefixes: [...new Set(rows.map((r) => (r.dataset.testid || "(none)").replace(UUID, "<id>")))],
      rows: rows.map((r) => {
        const tds = [...r.querySelectorAll("td")];
        const last = tds[tds.length - 1];
        const lr = last && last.getBoundingClientRect();
        return {
          id: (r.dataset.testid || "").replace(/^(transaction|history|ledger)-row-/, ""),
          testid: (r.dataset.testid || "").replace(UUID, "<id>"),
          cells: tds.map((td) => norm(td.innerText).slice(0, 80)),
          tdCount: tds.length,
          h: Math.round(r.getBoundingClientRect().height),
          buttons: [...r.querySelectorAll("button")].map((b) => `${norm(b.innerText)}${b.disabled ? " [disabled]" : ""}${b.title ? ` (title: ${b.title})` : ""}`),
          links: [...r.querySelectorAll("a")].map((a) => `${norm(a.innerText)} → ${a.getAttribute("href")}`),
          lastCellOutOfView: scR && lr ? Math.round(lr.right - scR.right) > 0 : false,
          lastCellRightMinusScroller: scR && lr ? Math.round(lr.right - scR.right) : null,
        };
      }),
    };
  });
  // K-10: the target rows' Reference cell (2nd td)
  out.k10 = (opts.k10 || []).map(([key, id]) => {
    const r = document.querySelector(`[data-testid="transaction-row-${id}"], [data-testid="history-row-${id}"]`);
    if (!r) return { key, present: false };
    const tds = [...r.querySelectorAll("td")];
    const ref = tds[1];
    const s = getComputedStyle(ref);
    const lh = parseFloat(s.lineHeight) || 20;
    const t = r.closest("table");
    let sc = t.parentElement;
    while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const scR = sc.getBoundingClientRect();
    return {
      key, present: true, refLength: norm(ref.innerText).length,
      // the text itself (a Range over the cell): line boxes and the widest line — the cell's own
      // height is the row's (the 50 px Date column wraps "25 Nov 2026" onto 3 lines)
      refText: (() => { const rg = document.createRange(); rg.selectNodeContents(ref); const rs = [...rg.getClientRects()]; return { lines: new Set(rs.map((x) => Math.round(x.top))).size, widestLine: Math.round(Math.max(0, ...rs.map((x) => x.width))) }; })(),
      dateCell: (() => { const rg = document.createRange(); rg.selectNodeContents(tds[0]); return { w: Math.round(tds[0].getBoundingClientRect().width), lines: new Set([...rg.getClientRects()].map((x) => Math.round(x.top))).size }; })(),
      refCell: { w: Math.round(ref.getBoundingClientRect().width), h: Math.round(ref.getBoundingClientRect().height), clientWidth: ref.clientWidth, scrollWidth: ref.scrollWidth, lines: Math.round((ref.getBoundingClientRect().height - parseFloat(s.paddingTop) - parseFloat(s.paddingBottom)) / lh), whiteSpace: s.whiteSpace, overflowWrap: s.overflowWrap, wordBreak: s.wordBreak },
      rowH: Math.round(r.getBoundingClientRect().height),
      table: { width: Math.round(t.getBoundingClientRect().width), scrollWidth: t.scrollWidth },
      scroller: { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth },
      cells: tds.map((td, i) => { const b = td.getBoundingClientRect(); return { i, text: norm(td.innerText).slice(0, 30), x: Math.round(b.left - scR.left), w: Math.round(b.width), rightBeyondScroller: Math.round(b.right - scR.right) }; }),
    };
  });
  // pager
  const pg = [...main.querySelectorAll("p")].find((p) => /^Page \d+ of \d+$/.test(norm(p.innerText)));
  if (pg) {
    const box = pg.parentElement;
    const ctl = (name) => { const e = [...box.querySelectorAll("a,button")].find((x) => norm(x.innerText) === name); return e ? { tag: e.tagName.toLowerCase(), href: e.getAttribute("href"), disabled: !!e.disabled } : null; };
    out.pager = { text: norm(pg.innerText), prev: ctl("Previous"), next: ctl("Next") };
  } else out.pager = null;
  out.found = [...main.querySelectorAll("p,div")].filter((e) => e.children.length === 0 && /\d+ (transactions?|clients?) found$/.test(norm(e.innerText))).map((e) => norm(e.innerText));
  const links = (sel) => [...document.querySelectorAll(`${sel} a`)].map((a) => ({ text: norm(a.innerText), href: a.getAttribute("href"), active: /bg-slate-900/.test(a.className) }));
  out.statusLinks = links('[data-testid="status-filters"]');
  out.typeLinks = links('[data-testid="type-filters"]');
  const nz = document.querySelector('[data-testid="non-zero-filter"]');
  out.nonZeroLink = nz ? { href: nz.getAttribute("href"), active: /bg-slate-900/.test(nz.className) } : null;
  out.inputs = [...main.querySelectorAll("input")].map((i) => ({ id: i.id || null, name: i.name || null, type: i.type, value: i.value, placeholder: i.placeholder || null, testid: i.dataset.testid || null, w: Math.round(i.getBoundingClientRect().width) }));
  const btn = {};
  for (const b of main.querySelectorAll("button")) { const k = `${norm(b.innerText) || b.getAttribute("aria-label") || "(icon)"}${b.disabled ? " [disabled]" : ""}${b.dataset.testid ? ` #${b.dataset.testid}` : ""}`; btn[k] = (btn[k] || 0) + 1; }
  out.buttons = btn;
  // ledger totals breakdown + summary
  const bd = document.querySelector('[data-testid="ledger-totals-breakdown"]');
  out.ledgerTotals = bd ? [...document.querySelectorAll('[data-testid^="ledger-total-"]')].map((e) => ({ testid: e.dataset.testid, dt: norm((e.querySelector("dt") || {}).innerText), dd: norm((e.querySelector("dd") || {}).innerText), inSummary: !!e.closest('[data-testid="client-summary"]') })) : null;
  const sum = document.querySelector('[data-testid="client-summary"]');
  out.clientSummary = sum ? { text: norm(sum.innerText), box: R(sum), cards: [...sum.children].map((c) => R(c)) } : null;
  const ps = document.querySelector('[data-testid="print-statement"]');
  out.printStatement = ps ? { box: R(ps), text: norm(ps.innerText) } : null;
  out.genStatementTitle = [...main.querySelectorAll("*")].filter((e) => e.children.length === 0 && norm(e.innerText) === "Generate Statement").map((e) => `${e.tagName.toLowerCase()}[data-slot=${e.dataset.slot || ""}]`);
  // headings
  out.headings = [...main.querySelectorAll("h1,h2,h3,[data-slot=card-title]")].map((h) => `${h.tagName.toLowerCase()}${h.dataset.slot ? "." + h.dataset.slot : ""}: ${norm(h.innerText).slice(0, 60)}`);
  // spec selector probes
  const nav = document.querySelector('nav[aria-label="Main navigation"]');
  out.sel = {
    navInterestLinkExact: nav ? [...nav.querySelectorAll("a")].filter((a) => norm(a.innerText) === "Interest").length : null,
    linksNamedInterestExactInMain: [...main.querySelectorAll("a")].filter((a) => norm(a.innerText) === "Interest").length,
    lpffRateHeaders: [...main.querySelectorAll("th")].filter((t) => norm(t.innerText) === "LPFF Rate").length,
    mainTbodyTr: main.querySelectorAll("tbody tr").length,
    recordTxButton: [...main.querySelectorAll("button")].filter((b) => norm(b.innerText) === "Record Transaction").length,
    recordTxLink: [...main.querySelectorAll("a")].filter((a) => norm(a.innerText).includes("Record Transaction")).length,
    clientLedgersLink: [...main.querySelectorAll("a")].filter((a) => norm(a.innerText).includes("Client Ledgers")).length,
    searchButtonExact: [...main.querySelectorAll("button")].filter((b) => norm(b.innerText) === "Search").length,
  };
  return out;
}
module.exports = { probe };
