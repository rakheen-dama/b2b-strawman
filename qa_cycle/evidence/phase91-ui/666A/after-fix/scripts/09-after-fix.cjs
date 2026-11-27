// 666A OBS after-fix (NEW, after-fix only): re-observe the three 1024 fixes of 97d84aeed and sweep the
// 666A surfaces. Read-only: page loads only (no clicks that write, no wizard Next, no SQL).
// lib.cjs / probes.cjs / probes-after.cjs are copied unchanged from ../../after/scripts/ (PHASE=after-fix
// picks ../../after-fix/ for output and /tmp/666A-after-fix-raw/ for raw tiles).
//   Alice: 6 surfaces (/invoices, /billing-runs, run detail, /billing-runs/new step 1, /collections,
//          /unbilled) × 1024×768 / 1440×900 × light / dark: full-page PNG, AFTER() probe (copied),
//          COLS() per-column probe (visible? width, x range in the scroller, in view, header + cell
//          line counts, inline min-width, clipped cells, row heights), page globals, console.
//   Crops: the table container of the fee runs list (A1), the run Items (A2) and the fee notes list
//          (dates) at 1024 light + dark → ../crop-fixed-*.png.
//   Carol (Member, mock-auth-token cookie via L.loginState): /invoices + /billing-runs at 1440 light.
// Writes ../measurements.json, ../console.json (single line), ../sheet-*.png, ../crop-fixed-*.png.
// Usage: PHASE=after-fix node 09-after-fix.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");
const A = require("./probes-after.cjs");

const RUN_ID = "af572249-eb88-4571-a33c-c5c8e5d3e9dd";
const U = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/g;
const VARIANTS = [[1024, 768, "light"], [1024, 768, "dark"], [1440, 900, "light"], [1440, 900, "dark"]];
const SURF = [
  ["list", "/invoices"],
  ["billing-runs", "/invoices/billing-runs"],
  ["billing-run-detail", `/invoices/billing-runs/${RUN_ID}`],
  ["billing-runs-new", "/invoices/billing-runs/new"],
  ["collections", "/invoices/collections"],
  ["unbilled", "/invoices/unbilled"],
];
const CROPS = { "billing-runs": "crop-fixed-A1-runs-list", "billing-run-detail": "crop-fixed-A2-run-items", list: "crop-fixed-dates-fee-notes" };

const COLS = () => {
  const lines = (e) => { const r = document.createRange(); r.selectNodeContents(e); return new Set([...r.getClientRects()].filter((x) => x.width > 0).map((x) => Math.round(x.top))).size; };
  const t = (e) => (e.textContent || "").replace(/\s+/g, " ").trim();
  return [...document.querySelectorAll("#main-content table")].map((tb) => {
    const sc = tb.closest('[data-slot="table-container"]') || tb.parentElement;
    const sr = sc.getBoundingClientRect();
    const ths = [...tb.querySelectorAll("thead th")];
    const rows = [...tb.querySelectorAll("tbody tr")];
    const cols = ths.map((th, i) => {
      const r = th.getBoundingClientRect();
      const vis = getComputedStyle(th).display !== "none" && r.width > 0;
      const cells = rows.map((row) => row.children[i]).filter(Boolean);
      const cl = cells.map((c) => (getComputedStyle(c).display === "none" ? 0 : lines(c)));
      return {
        h: t(th) || "(blank)", vis, w: Math.round(r.width), x0: vis ? Math.round(r.left - sr.left) : null, x1: vis ? Math.round(r.right - sr.left) : null,
        inView: vis ? r.right <= sr.left + sc.clientWidth + 0.5 : null, headLines: vis ? lines(th) : 0,
        cellLinesMax: cl.length ? Math.max(...cl) : 0, cellLines: cl, minWidthTh: th.style.minWidth || null, minWidthTd: cells[0] ? cells[0].style.minWidth || null : null,
        clippedCells: cells.filter((c) => getComputedStyle(c).display !== "none" && c.scrollWidth > c.clientWidth + 1).length,
        cellsSample: cells.slice(0, 3).map((c) => t(c).slice(0, 44)),
      };
    });
    const hs = rows.map((r) => Math.round(r.getBoundingClientRect().height));
    return { slotPath: sc.dataset.slot || null, clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth, overflow: sc.scrollWidth - sc.clientWidth, tableW: Math.round(tb.getBoundingClientRect().width), visibleCols: cols.filter((c) => c.vis).map((c) => c.h), hiddenCols: cols.filter((c) => !c.vis).map((c) => c.h), cols, rowH: { min: hs.length ? Math.min(...hs) : null, max: hs.length ? Math.max(...hs) : null, n: hs.length, all: hs }, rowsAboveFold: rows.filter((r) => r.getBoundingClientRect().bottom + scrollY <= innerHeight).length };
  });
};

const settle = async (p) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(700); };
const consoleSink = [];
const lab = { label: "" };
const tiles = {};
const crops = [];

(async () => {
  const out = { at: new Date().toISOString(), phase: L.PHASE, runId: RUN_ID, alice: {}, carol: {} };
  const tA = await L.idpToken("alice");
  const runs = L.list((await L.apiGet(tA, "/api/billing-runs?size=100")).json);
  out.api = { runs: runs.map((r) => ({ id: r.id, status: r.status, periodFrom: r.periodFrom, periodTo: r.periodTo })), items: ((await L.apiGet(tA, `/api/billing-runs/${RUN_ID}/items`)).json || []).map((x) => ({ clientName: x.clientName, status: x.status, failureReason: x.failureReason })), invoices: L.list((await L.apiGet(tA, "/api/invoices")).json).length };
  if (!runs.find((r) => r.id === RUN_ID)) throw new Error(`run ${RUN_ID} not on the stack`);
  const browser = await L.chromium.launch();
  const SA = await L.loginState(browser, "alice");
  for (const [w, h, th] of VARIANTS) {
    const v = `${w}-${th}`;
    const ctx = await L.newCtx(browser, SA, { width: w, height: h, theme: th, extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    for (const [sk, url] of SURF) {
      lab.label = `09|alice|${sk}|${v}`;
      const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
      await settle(p);
      await p.evaluate(P.HELPERS, "");
      await p.evaluate(P.HELPERS2);
      await p.evaluate(`window.__after = (${A.AFTER.toString()})`);
      const after = await p.evaluate(() => window.__after());
      const cols = await p.evaluate(COLS);
      const g = await p.evaluate(() => ({ mainId: document.querySelectorAll("main#main-content").length, main: document.querySelectorAll("main").length, h1Main: document.querySelectorAll("#main-content h1").length, h1Page: document.querySelectorAll("h1").length, h1Text: [...document.querySelectorAll("#main-content h1")].map((x) => x.textContent.replace(/\s+/g, " ").trim()), hOverflow: document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth, docH: document.scrollingElement.scrollHeight, theme: document.documentElement.className.includes("dark") ? "dark" : "light" }));
      g.status = resp ? resp.status() : null;
      g.url = p.url().replace(L.BASE, "").replace(U, "{id}");
      g.title = await p.title();
      (out.alice[sk] = out.alice[sk] || {})[v] = { ...g, cols, after: { header: after.header, tables: after.tables, empties: after.empties, steps: after.steps, dark: after.dark, lowContrast: (after.lowContrast || []).slice(0, 12) } };
      const f = path.join(L.RAW, `s__${sk}-${v}.png`);
      await p.screenshot({ path: f, fullPage: true });
      const c0 = cols[0];
      (tiles[sk] = tiles[sk] || []).push([`Alice ${w}×${h} ${th} — ${g.url} — HTTP ${g.status}; h1 ${JSON.stringify(g.h1Text)}; doc h-overflow ${g.hOverflow}${c0 ? `; table ${c0.scrollWidth}/${c0.clientWidth}, visible: ${c0.visibleCols.join(" | ")}; rows ${c0.rowH.min}–${c0.rowH.max} px` : ""}`, f]);
      if (CROPS[sk] && w === 1024) {
        const loc = p.locator('#main-content [data-slot="table-container"]').first();
        if (await loc.count()) {
          await loc.scrollIntoViewIfNeeded();
          const cf = path.join(L.OUT, `${CROPS[sk]}-1024-${th}.png`);
          await loc.screenshot({ path: cf });
          crops.push([`${path.basename(cf)} — ${url.replace(U, "{id}")} 1024 ${th}: ${c0.scrollWidth}/${c0.clientWidth}; ${c0.cols.map((c) => (c.vis ? `${c.h} ${c.w}px/${c.cellLinesMax}L` : `${c.h} hidden`)).join(", ")}; rows ${c0.rowH.all.join("/")} px`, cf]);
        } else crops.push([`${CROPS[sk]} 1024 ${th}: no table-container`, null]);
      }
    }
    await ctx.close();
  }
  // Carol (Member) at 1440 light.
  const tC = await L.idpToken("carol");
  const capsC = (await L.apiGet(tC, "/api/me/capabilities")).json;
  out.carol.caps = { role: capsC && capsC.role, n: capsC && (capsC.capabilities || []).length };
  if (!capsC || capsC.role !== "Member") throw new Error(`carol role ${capsC && capsC.role}, expected Member`);
  const SC = await L.loginState(browser, "carol");
  {
    const ctx = await L.newCtx(browser, SC, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    L.watch(p, consoleSink, lab);
    for (const [sk, url] of [["list", "/invoices"], ["billing-runs", "/invoices/billing-runs"]]) {
      lab.label = `09|carol|${sk}|1440-light`;
      const resp = await p.goto(`${L.BASE}${url}`, { waitUntil: "load" });
      await settle(p);
      const g = await p.evaluate(() => ({ mainId: document.querySelectorAll("main#main-content").length, main: document.querySelectorAll("main").length, h1Main: [...document.querySelectorAll("#main-content h1")].map((x) => x.textContent.replace(/\s+/g, " ").trim()), h1Page: document.querySelectorAll("h1").length, mainText: (document.querySelector("#main-content") || document.body).innerText.replace(/\s+/g, " ").trim().slice(0, 240), tables: document.querySelectorAll("#main-content table").length, hOverflow: document.scrollingElement.scrollWidth - document.scrollingElement.clientWidth }));
      g.status = resp ? resp.status() : null;
      g.url = p.url().replace(L.BASE, "");
      out.carol[sk] = g;
      const f = path.join(L.RAW, `carol__${sk}-1440-light.png`);
      await p.screenshot({ path: f, fullPage: true });
      (tiles.carol = tiles.carol || []).push([`Carol (Member) 1440×900 light — ${url} — HTTP ${g.status}; h1 ${JSON.stringify(g.h1Main)}; "${g.mainText.slice(0, 110)}"`, f]);
    }
    await ctx.close();
  }
  out.console = { errors: consoleSink.filter((c) => c.type === "error" || c.type === "pageerror").length, warnings: consoleSink.filter((c) => c.type === "warning").length, hydration: consoleSink.filter((c) => /hydrat/i.test(c.text)).length, pageerrors: consoleSink.filter((c) => c.type === "pageerror").length };
  fs.writeFileSync(path.join(L.OUT, "measurements.json"), JSON.stringify(out));
  fs.writeFileSync(path.join(L.OUT, "console.json"), JSON.stringify({ at: out.at, pagesOpened: SURF.length * VARIANTS.length + 2, ...out.console, messages: consoleSink }));
  const VC = "columns: 1024 light | 1024 dark | 1440 light | 1440 dark (full page)";
  const H = (s) => `666A OBS after-fix @97d84aeed — ${s}`;
  const names = { list: "/invoices (Fee notes)", "billing-runs": "/invoices/billing-runs (Fee runs list)", "billing-run-detail": `/invoices/billing-runs/${RUN_ID.slice(0, 8)}… (run detail, COMPLETED)`, "billing-runs-new": "/invoices/billing-runs/new (wizard step 1 only)", collections: "/invoices/collections (Debtors)", unbilled: "/invoices/unbilled (default period)" };
  for (const [sk] of SURF) await L.sheet(browser, path.join(L.OUT, `sheet-${sk}-alice.png`), H(`${names[sk]}, Alice (Owner); ${VC}`), tiles[sk], 4, 480);
  await L.sheet(browser, path.join(L.OUT, "sheet-carol.png"), H("Carol (Member), 1440×900 light: /invoices | /invoices/billing-runs"), tiles.carol, 2, 720);
  await L.sheet(browser, path.join(L.OUT, "sheet-crops-1024.png"), H("table containers at 1024×768 (A1 runs list, A2 run items, fee notes dates) light | dark"), crops, 2, 720);
  await browser.close();
  // Console summary
  console.log("console", JSON.stringify(out.console), JSON.stringify(consoleSink.slice(0, 6)));
  for (const [sk] of SURF)
    for (const [w, , th] of VARIANTS) {
      const m = out.alice[sk][`${w}-${th}`];
      console.log(sk, `${w}-${th}`, JSON.stringify({ st: m.status, h1: m.h1Text, h1n: m.h1Main, main: `${m.mainId}/${m.main}`, hOv: m.hOverflow, tables: m.cols.map((c) => ({ sc: `${c.scrollWidth}/${c.clientWidth}`, rows: `${c.rowH.min}-${c.rowH.max}`, cols: c.cols.map((x) => (x.vis ? `${x.h}:${x.w}px:${x.x0}-${x.x1}:${x.inView ? "in" : "OUT"}:h${x.headLines}/c${x.cellLinesMax}${x.minWidthTd ? ":mw" + x.minWidthTd : ""}${x.clippedCells ? ":CLIP" + x.clippedCells : ""}` : `${x.h}:hidden`)) })) }));
    }
  console.log("carol", JSON.stringify(out.carol));
})();
