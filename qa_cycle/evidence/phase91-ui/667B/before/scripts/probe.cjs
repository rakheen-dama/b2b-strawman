// 667B in-page probe (page.evaluate). Pure DOM reads — no clicks. One JSON object per page: shell
// counts (h1 / main), the page header block, document overflow, every data-testid (UUIDs → <id>),
// every table in #main-content (scroller clientWidth/scrollWidth, card width, header widths, rows
// with cells, per-cell boxes against the scroller, badges with colours + contrast, links), empty
// states, the pager, "N … found" texts, report cards, teal text/fills with composite contrast (K-36,
// K-76), the wizard step badges, buttons, inputs, headings and the spec-selector counts.
// Structure-agnostic on purpose: the same probe reads the before (raw tables) and the after
// (DataTable) DOM.
function probe() {
  const UUID = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const R = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top + scrollY), w: Math.round(r.width), h: Math.round(r.height) }; };
  const main = document.querySelector("#main-content") || document.body;
  // ---- colour: composite every ancestor background (alpha-aware, any CSS colour syntax) on white
  const cv = document.createElement("canvas");
  cv.width = cv.height = 1;
  const cx = cv.getContext("2d", { willReadFrequently: true });
  const px = (layers) => { cx.globalAlpha = 1; cx.fillStyle = "#fff"; cx.fillRect(0, 0, 1, 1); for (const c of layers) { cx.fillStyle = "rgba(0,0,0,0)"; cx.fillStyle = c; cx.fillRect(0, 0, 1, 1); } const d = cx.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const bgLayers = (el) => { const ls = []; for (let e = el; e; e = e.parentElement) { const b = getComputedStyle(e).backgroundColor; if (b && b !== "rgba(0, 0, 0, 0)" && b !== "transparent") { ls.unshift(b); if (px([b]).join() === px(["#000", b]).join()) break; } } return ls; }; // up to <html>; none opaque → white base
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [l1, l2] = [lum(a), lum(b)].sort((x, y) => y - x); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  const colour = (el) => { const ls = bgLayers(el); const bg = px(ls); const fg = px([...ls, getComputedStyle(el).color]); return { fg: hex(fg), bg: hex(bg), ratio: ratio(fg, bg) }; };

  const out = { url: location.pathname + location.search, title: document.title };
  out.h1Main = [...main.querySelectorAll("h1")].map((h) => norm(h.innerText));
  out.h1Page = document.querySelectorAll("h1").length;
  out.mainIdCount = document.querySelectorAll("main#main-content").length;
  out.mainElCount = document.querySelectorAll("main").length;
  out.docHOverflowPx = document.documentElement.scrollWidth - document.documentElement.clientWidth;
  out.docHeight = document.documentElement.scrollHeight;
  const cs = getComputedStyle(main);
  out.mainInnerWidth = Math.round(main.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight));
  out.notFound = /Page not found|could not be found|404/i.test(norm(main.innerText).slice(0, 400));
  out.bodyHead = norm(main.innerText).slice(0, 300);
  const h1 = main.querySelector("h1");
  if (h1) {
    const s = getComputedStyle(h1);
    // the page root: the nearest ancestor of the h1 that stacks page blocks (space-y-4+ / gap, ≥ 2
    // children); the header block = the root's child that holds the h1 (h1 + description + actions)
    let first = h1;
    while (first.parentElement && first.parentElement !== main) { first = first.parentElement; if (/\bspace-y-([4-9]|1\d)\b/.test(first.className) && first.children.length > 1) break; }
    let hb = h1;
    while (hb.parentElement && hb.parentElement !== first) hb = hb.parentElement;
    out.h1Style = { cls: h1.className, fontSize: s.fontSize, fontFamily: s.fontFamily.split(",")[0], color: s.color, box: R(h1), contrast: colour(h1) };
    out.header = hb && { box: R(hb), text: norm(hb.innerText).slice(0, 200), actions: [...hb.querySelectorAll("a,button")].map((a) => `${a.tagName.toLowerCase()}:${norm(a.innerText)}${a.getAttribute("href") ? " → " + a.getAttribute("href") : ""}`), pageRootCls: first && first.className, pageRootGap: first && getComputedStyle(first).rowGap };
    // vertical rhythm: the page root's direct children (top, height) — the spacing between blocks
    out.rootBlocks = first ? [...first.children].map((c) => ({ tag: c.tagName.toLowerCase(), testid: c.dataset.testid || null, box: R(c) })) : [];
  }
  const tids = {};
  for (const e of document.querySelectorAll("[data-testid]")) { const k = e.dataset.testid.replace(UUID, "<id>"); tids[k] = (tids[k] || 0) + 1; }
  out.testids = tids;
  const badgeInfo = (b) => ({ text: norm(b.innerText), cls: b.className, slot: b.dataset.slot || null, variant: b.dataset.variant || null, ...colour(b), box: R(b) });
  const isBadge = (e) => e.dataset.slot === "badge";
  out.tables = [...main.querySelectorAll("table")].map((t) => {
    let sc = t.parentElement;
    while (sc && sc !== main && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const card = t.closest('[data-slot="card"]') || t.closest(".rounded-xl, .rounded-lg");
    const scR = sc && sc !== main ? sc.getBoundingClientRect() : null;
    const ths = [...t.querySelectorAll("thead th")].map((th) => { const b = th.getBoundingClientRect(); return { text: norm(th.innerText), sr: norm(th.textContent), w: Math.round(b.width), x: scR ? Math.round(b.left - scR.left) : null, align: getComputedStyle(th).textAlign, sticky: getComputedStyle(th).position === "sticky" }; });
    const rows = [...t.querySelectorAll("tbody tr")];
    return {
      testid: t.dataset.testid || null,
      wrapperTestid: (t.closest("[data-testid]") || {}).dataset ? t.closest("[data-testid]").dataset.testid.replace(UUID, "<id>") : null,
      width: Math.round(t.getBoundingClientRect().width),
      scroller: scR ? { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflows: sc.scrollWidth > sc.clientWidth, overflowPx: sc.scrollWidth - sc.clientWidth, cls: sc.className } : null,
      card: card ? { w: Math.round(card.getBoundingClientRect().width) } : null,
      headers: ths,
      rowCount: rows.length,
      rowTestidPrefixes: [...new Set(rows.map((r) => (r.dataset.testid || "(none)").replace(UUID, "<id>")))],
      tfoot: [...t.querySelectorAll("tfoot tr")].map((r) => norm(r.innerText)),
      rows: rows.map((r) => {
        const tds = [...r.querySelectorAll("td")];
        return {
          testid: (r.dataset.testid || "").replace(UUID, "<id>"),
          id: ((r.dataset.testid || "").match(UUID) || [null])[0],
          cells: tds.map((td) => norm(td.innerText).slice(0, 90)),
          tdCount: tds.length,
          h: Math.round(r.getBoundingClientRect().height),
          cellBoxes: tds.map((td) => { const b = td.getBoundingClientRect(); return { x: scR ? Math.round(b.left - scR.left) : Math.round(b.left), w: Math.round(b.width), h: Math.round(b.height), right: scR ? Math.round(b.right - scR.right) : null, mono: /font-mono/.test(td.className + " " + [...td.querySelectorAll("*")].map((x) => x.className).join(" ")) }; }),
          badges: [...r.querySelectorAll("span,div")].filter(isBadge).map(badgeInfo),
          buttons: [...r.querySelectorAll("button")].map((b) => `${norm(b.innerText) || b.getAttribute("aria-label") || "(icon)"}${b.disabled ? " [disabled]" : ""}${b.dataset.testid ? " #" + b.dataset.testid.replace(UUID, "<id>") : ""}`),
          links: [...r.querySelectorAll("a")].map((a) => ({ text: norm(a.innerText), href: a.getAttribute("href"), testid: a.dataset.testid ? a.dataset.testid.replace(UUID, "<id>") : null, ...colour(a) })),
          lastCellRightMinusScroller: scR && tds.length ? Math.round(tds[tds.length - 1].getBoundingClientRect().right - scR.right) : null,
        };
      }),
    };
  });
  // empty states: testid'd empties + "No …" paragraphs in cards
  const empties = new Set([...main.querySelectorAll('[data-testid*="empty"]')]);
  for (const p of main.querySelectorAll("p")) if (/^(No|Nothing) [a-z]/.test(norm(p.innerText)) || /need financial visibility/.test(p.innerText)) empties.add(p);
  out.empties = [...empties].map((e) => { const card = e.closest('[data-slot="card"]'); const p = e.matches("p") ? e : e.querySelector("p") || e; return { testid: e.dataset.testid || null, tag: e.tagName.toLowerCase(), cls: e.className, text: norm(e.innerText).slice(0, 160), box: R(e), cardBox: R(card), hasIcon: !!e.querySelector("svg"), hasAction: !!e.querySelector("a,button"), ...colour(p) }; });
  // pager
  const pg = [...main.querySelectorAll("p,span,div")].find((p) => p.children.length === 0 && /^Page \d+ of \d+$/.test(norm(p.innerText)));
  if (pg) {
    let box = pg.parentElement;
    while (box && box !== main && !box.querySelector("a,button")) box = box.parentElement;
    const ctl = (name) => { const e = [...box.querySelectorAll("a,button")].find((x) => norm(x.innerText) === name || x.getAttribute("aria-label") === name); return e ? { tag: e.tagName.toLowerCase(), href: e.getAttribute("href"), disabled: !!e.disabled || e.getAttribute("aria-disabled") === "true" } : null; };
    out.pager = { text: norm(pg.innerText), prev: ctl("Previous"), next: ctl("Next") };
  } else out.pager = null;
  out.found = [...main.querySelectorAll("p,div,span")].filter((e) => e.children.length === 0 && /^\d+ [a-z]+(s)? found$/.test(norm(e.innerText))).map((e) => norm(e.innerText));
  out.reportCards = [...main.querySelectorAll('[data-testid^="report-card-"]')].map((c) => { const a = c.closest("a"); const run = [...c.querySelectorAll("span,a,p")].find((x) => /Run Report/.test(x.innerText)); return { testid: c.dataset.testid, box: R(c), title: norm((c.querySelector('[data-slot="card-title"]') || {}).innerText), href: a && a.getAttribute("href"), run: run && { text: norm(run.innerText), cls: run.className, ...colour(run) } }; });
  // teal text / fills (K-36 text-teal-600, K-76 bg-teal-600) — every element in main using a teal utility
  out.teal = [...main.querySelectorAll('[class*="teal-"]')].slice(0, 60).map((e) => ({ tag: e.tagName.toLowerCase(), text: norm(e.innerText).slice(0, 40), cls: (e.className.baseVal != null ? e.className.baseVal : e.className).split(/\s+/).filter((c) => /teal/.test(c)).join(" "), ...colour(e) }));
  const si = main.querySelector('[data-testid="step-indicator"]');
  out.stepIndicator = si ? { box: R(si), scrollWidth: si.scrollWidth, clientWidth: si.clientWidth, ...(() => { const wr = si.closest(".max-w-7xl") || main; const right = wr.getBoundingClientRect().right - parseFloat(getComputedStyle(wr).paddingRight); return { contentRight: Math.round(right), rightOverflowPx: Math.round(Math.max(...[...si.querySelectorAll("li")].map((li) => li.getBoundingClientRect().right)) - right) }; })() } : null;
  out.steps = si ?[...si.querySelectorAll("li")].map((li) => { const b = li.querySelector("div,span"); const label = [...li.querySelectorAll("span")].map((s) => norm(s.innerText)).filter(Boolean)[0]; return { num: norm(b && b.innerText), label, badgeCls: b && b.className, box: R(b), ...(b ? colour(b) : {}), labelColour: li.querySelector("span") ? colour(li.querySelector("span")) : null }; }) : null;
  const btn = {};
  for (const b of main.querySelectorAll("button")) { const k = `${norm(b.innerText) || b.getAttribute("aria-label") || "(icon)"}${b.disabled ? " [disabled]" : ""}${b.dataset.testid ? ` #${b.dataset.testid.replace(UUID, "<id>")}` : ""}`; btn[k] = (btn[k] || 0) + 1; }
  out.buttons = btn;
  out.inputs = [...main.querySelectorAll("input,select,[role=combobox]")].map((i) => ({ tag: i.tagName.toLowerCase(), id: i.id || null, type: i.type || null, value: i.value != null ? String(i.value).slice(0, 60) : norm(i.innerText), testid: i.dataset.testid || null, max: i.getAttribute("max"), disabled: !!i.disabled, w: Math.round(i.getBoundingClientRect().width), options: i.tagName === "SELECT" ? [...i.options].map((o) => norm(o.text)) : undefined }));
  out.headings = [...main.querySelectorAll("h1,h2,h3,[data-slot=card-title]")].map((h) => `${h.tagName.toLowerCase()}${h.dataset.slot ? "." + h.dataset.slot : ""}: ${norm(h.innerText).slice(0, 70)}`);
  out.cards = [...main.querySelectorAll('[data-slot="card"]')].map((c) => ({ testid: c.dataset.testid || null, title: norm((c.querySelector('[data-slot="card-title"]') || {}).innerText).slice(0, 60), box: R(c) }));
  const leafText = (t) => [...main.querySelectorAll("*")].filter((e) => e.children.length === 0 && norm(e.innerText).includes(t)).length;
  out.sel = {
    navInterestExactAll: [...document.querySelectorAll("a")].filter((a) => norm(a.textContent) === "Interest").length,
    navInterestContainsInNav: [...document.querySelectorAll("nav a")].filter((a) => /\bInterest\b/.test(a.textContent)).map((a) => norm(a.textContent)),
    lpffRateHeaders: [...document.querySelectorAll("th,[role=columnheader]")].filter((t) => norm(t.innerText) === "LPFF Rate").length,
    textSection35: leafText("Section 35"),
    textInvestmentRegister: leafText("Investment Register"),
    mainTbodyTr: main.querySelectorAll("tbody tr").length,
    statementRowPrefix: document.querySelectorAll('[data-testid^="statement-row-"]').length,
    openStatementRowPrefix: document.querySelectorAll('[data-testid^="open-statement-row-"]').length,
    investmentRowPrefix: document.querySelectorAll('[data-testid^="investment-row-"]').length,
    reportCardPrefix: document.querySelectorAll('[data-testid^="report-card-"]').length,
    columnheaders: [...main.querySelectorAll("th,[role=columnheader]")].map((t) => norm(t.textContent)),
    fundNoteLines: [...main.querySelectorAll('[data-testid="investment-interest-fund-note"]')].map((n) => { const rg = document.createRange(); rg.selectNodeContents(n); return { lines: new Set([...rg.getClientRects()].map((x) => Math.round(x.top))).size, mono: getComputedStyle(n).fontFamily.split(",")[0] }; }),
  };
  return out;
}
module.exports = { probe };
