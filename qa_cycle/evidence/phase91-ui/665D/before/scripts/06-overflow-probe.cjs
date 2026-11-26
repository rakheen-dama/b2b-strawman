// 665D OBS step 6: what is cut at 1024×768 (read-only, Alice, light). 01-capture measured document
// h-overflow 0 on every Fees tab, but the Rates table scroller is 494/725 and the Tax rates scroller
// 446/519. For each table on `tariffs` (Billing and, after a client-side click, Cost Rates) and `tax`:
// the scroller box, each header's right edge vs the scroller's visible right edge (columns hidden at
// scrollLeft 0), whether each row-action button is inside the visible box, and the scroller's
// position / overflow. Plus the family tab strip (clientWidth vs scrollWidth, the last tab's
// visible width). Crops of the table regions (defect PNGs) and of the tab strip.
// Writes $RAW/overflow-probe.json + tiles; 05-assemble merges them.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const out = { at: new Date().toISOString(), tabs: {} };
const tiles = [];
const PROBE = (TABLIST) => {
  const vis = (e) => { const b = e.getBoundingClientRect(); return b.width > 0 && b.height > 0 && getComputedStyle(e).display !== "none"; };
  const t = (e) => (e.innerText || e.textContent || "").replace(/\s+/g, " ").trim();
  const se = document.scrollingElement;
  const tables = [...document.querySelectorAll("#main-content table")].filter(vis).map((tb) => {
    let sc = tb.parentElement;
    while (sc && sc !== document.body && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
    const sb = sc.getBoundingClientRect();
    const right = sb.left + sc.clientWidth;
    const heads = [...tb.querySelectorAll("thead th")].filter(vis).map((h) => { const b = h.getBoundingClientRect(); return { text: t(h), left: Math.round(b.left), right: Math.round(b.right), hiddenPx: Math.max(0, Math.round(b.right - right)), fullyHidden: b.left >= right }; });
    const actions = [...tb.querySelectorAll("tbody button")].filter(vis).map((b) => { const r = b.getBoundingClientRect(); return { name: b.getAttribute("aria-label") || t(b), inView: r.right <= right + 0.5, rightPastPx: Math.max(0, Math.round(r.right - right)) }; });
    return {
      headers: heads.map((h) => h.text).join("|"),
      dataSlot: tb.getAttribute("data-slot"),
      scroller: { cls: sc.className.toString().slice(0, 90), dataSlot: sc.getAttribute("data-slot"), position: getComputedStyle(sc).position, overflowX: getComputedStyle(sc).overflowX, left: Math.round(sb.left), clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflowPx: sc.scrollWidth - sc.clientWidth },
      columnsCut: heads.filter((h) => h.hiddenPx > 0).map((h) => `${h.text}${h.fullyHidden ? " (fully hidden)" : ` (${h.hiddenPx}px cut)`}`),
      actionButtons: actions.length,
      actionButtonsOutOfView: actions.filter((x) => !x.inView).map((x) => `${x.name} (+${x.rightPastPx}px)`),
      box: { x: Math.round(sb.left), y: Math.round(sb.top + window.scrollY), w: Math.round(sb.width), h: Math.round(sb.height) },
    };
  });
  const strip = document.querySelector(`#main-content [role="tablist"][aria-label="${TABLIST}"]`);
  let tabStrip = null;
  if (strip) {
    const sr = strip.getBoundingClientRect();
    const last = [...strip.querySelectorAll('[role="tab"]')].pop();
    const lb = last.getBoundingClientRect();
    tabStrip = { clientWidth: strip.clientWidth, scrollWidth: strip.scrollWidth, overflowX: getComputedStyle(strip).overflowX, lastTab: t(last), lastTabWidth: Math.round(lb.width), lastTabVisiblePx: Math.max(0, Math.round(Math.min(lb.right, sr.right) - lb.left)), box: { x: Math.round(sr.left), y: Math.round(sr.top), w: Math.round(sr.width), h: Math.round(sr.height) } };
  }
  return { viewport: document.documentElement.clientWidth, docScrollWidth: se.scrollWidth, docOverflowPx: se.scrollWidth - se.clientWidth, tables, tabStrip };
};

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1024, height: 768, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  const crop = async (key, box, caption) => {
    const f = path.join(L.RAW, `${key}.png`);
    await p.screenshot({ path: f, fullPage: true, clip: { x: Math.max(0, box.x - 8), y: Math.max(0, box.y - 8), width: Math.min(1024 - Math.max(0, box.x - 8), box.w + 16), height: box.h + 16 } });
    tiles.push([key, f, caption]);
  };
  for (const tab of ["tariffs", "tax", "time-recording", "fee-runs", "collections", "trust-accounting"]) {
    await p.goto(`${L.BASE}/settings/fees?tab=${tab}`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    await p.waitForTimeout(400);
    const r = await p.evaluate(PROBE, L.TABLIST);
    out.tabs[tab] = r;
    if (tab === "tariffs") {
      await crop("defect__rates-billing-table-1024", r.tables[0].box, `Alice 1024×768 light — Rates › Billing Rates table at scrollLeft 0: scroller ${r.tables[0].scroller.clientWidth}/${r.tables[0].scroller.scrollWidth}; cut: ${r.tables[0].columnsCut.join(", ")}`);
      await crop("tabstrip__fees-1024", r.tabStrip.box, `Alice 1024×768 light — Fees & Trust tab strip: ${r.tabStrip.clientWidth}/${r.tabStrip.scrollWidth}, last tab "${r.tabStrip.lastTab}" ${r.tabStrip.lastTabVisiblePx}/${r.tabStrip.lastTabWidth}px visible`);
      await p.getByRole("tab", { name: "Cost Rates" }).click();
      await p.waitForTimeout(400);
      out.tabs["tariffs|cost"] = await p.evaluate(PROBE, L.TABLIST);
    }
    if (tab === "tax") await crop("defect__tax-rates-table-1024", r.tables[0].box, `Alice 1024×768 light — Tax › Tax Rates table at scrollLeft 0: scroller ${r.tables[0].scroller.clientWidth}/${r.tables[0].scroller.scrollWidth}; cut: ${r.tables[0].columnsCut.join(", ")}; row actions out of view: ${r.tables[0].actionButtonsOutOfView.length}/${r.tables[0].actionButtons}`);
    console.log(tab, JSON.stringify({ doc: r.docOverflowPx, strip: r.tabStrip && `${r.tabStrip.clientWidth}/${r.tabStrip.scrollWidth} last ${r.tabStrip.lastTabVisiblePx}/${r.tabStrip.lastTabWidth}`, tables: r.tables.map((x) => ({ h: x.headers, sc: `${x.scroller.clientWidth}/${x.scroller.scrollWidth} ${x.scroller.position}`, cut: x.columnsCut, out: `${x.actionButtonsOutOfView.length}/${x.actionButtons}` })) }));
  }
  console.log("tariffs|cost", JSON.stringify(out.tabs["tariffs|cost"].tables.map((x) => ({ h: x.headers, sc: `${x.scroller.clientWidth}/${x.scroller.scrollWidth}`, cut: x.columnsCut, out: `${x.actionButtonsOutOfView.length}/${x.actionButtons}` }))));
  await ctx.close();
  await browser.close();
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "overflow-probe.json"), JSON.stringify(out));
})();
