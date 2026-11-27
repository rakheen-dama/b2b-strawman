// 667A OBS-after-fix: re-measure F-667A-A4 (sticky Actions over Amount at 1024) and F-667A-A1 (ledger tiles
// dl/dt/dd markup). Read-only: navigation, scrollLeft, DOM reads, axe. Never clicks Approve/Reject/Reverse.
// Writes ../a4-a1.json + two crops into ../ (the after-fix/ dir). Usage (Node 20): node 07-a4-a1.cjs
const path = require("path");
const fs = require("fs");
process.env.PHASE = "after-fix";
const L = require("../../before/scripts/lib.cjs");
const OUT = path.resolve(__dirname, "..");
const S = L.readSeed();
const K = S.clients.khumalo, N = S.clients.ndlovu, AC = S.clients.acme;
const TX = `${L.TA}/transactions`, CL = `${L.TA}/client-ledgers`;
const AXE = fs.readFileSync(path.resolve(L.W, "../b2b-strawman/node_modules/.pnpm/axe-core@4.11.3/node_modules/axe-core/axe.min.js"), "utf8");

// A4: per row at scrollLeft 0 — Amount/Status right edge vs the sticky Actions cell's left edge; Actions inside
// the scroller; approval buttons inside their cell (no clipping) and their line count; row heights.
function a4Probe() {
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const t = document.querySelector('[data-testid="transactions-table"]');
  if (!t) return { error: "no table" };
  let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
  sc.scrollLeft = 0;
  const heads = [...t.querySelectorAll("thead th")].map((h) => norm(h.textContent));
  const hw = Object.fromEntries([...t.querySelectorAll("thead th")].map((h) => [norm(h.textContent) || "(blank)", Math.round(h.getBoundingClientRect().width)]));
  const ai = heads.indexOf("Amount"), si = heads.indexOf("Status"), xi = heads.indexOf("Actions");
  const s = sc.getBoundingClientRect();
  const hitShows = (c) => { const b = c.getBoundingClientRect(); const cx = Math.min(Math.max(b.left + b.width / 2, s.left + 1), s.right - 1); const y = b.top + b.height / 2; if (y < 0 || y > innerHeight) return null; const hit = document.elementFromPoint(cx, y); return !!hit && c.contains(hit); };
  const rows = [...t.querySelectorAll("tbody tr")].map((tr) => {
    const c = [...tr.children]; const a = c[ai].getBoundingClientRect(), st = c[si].getBoundingClientRect();
    const r = { id: (tr.dataset.testid || "").replace("transaction-row-", "").slice(0, 8), rowH: Math.round(tr.getBoundingClientRect().height), amountRight: Math.round(a.right - s.left), statusRight: Math.round(st.right - s.left) };
    if (xi >= 0) {
      const x = c[xi], xb = x.getBoundingClientRect();
      r.actionsLeft = Math.round(xb.left - s.left); r.actionsRight = Math.round(xb.right - s.left); r.actionsW = Math.round(xb.width);
      r.gapAmount = Math.round(xb.left - a.right); r.gapStatus = Math.round(xb.left - st.right);
      r.actionsInside = xb.left >= s.left - 0.5 && xb.right <= s.right + 0.5;
      r.actionsCellOverflow = x.scrollWidth > x.clientWidth + 1;
      const btns = [...x.querySelectorAll("button, a")].filter((b) => b.getBoundingClientRect().width > 0);
      r.buttons = btns.map((b) => { const bb = b.getBoundingClientRect(); return { t: norm(b.innerText), clipped: bb.left < xb.left - 0.5 || bb.right > xb.right + 0.5 || b.scrollWidth > b.clientWidth + 1, top: Math.round(bb.top - xb.top) }; });
      r.buttonLines = new Set(r.buttons.map((b) => b.top)).size;
      r.actionsText = norm(x.innerText);
      r.amountShown = hitShows(c[ai]); r.statusShown = hitShows(c[si]);
    }
    return r;
  });
  const withX = rows.filter((r) => r.actionsLeft !== undefined);
  return {
    table: Math.round(t.getBoundingClientRect().width), scroller: sc.clientWidth, scrollWidth: sc.scrollWidth, heads, hw, rowCount: rows.length,
    actionsSticky: xi >= 0 ? getComputedStyle(t.querySelector("tbody tr").children[xi]).position : null,
    minGapAmount: withX.length ? Math.min(...withX.map((r) => r.gapAmount)) : null,
    minGapStatus: withX.length ? Math.min(...withX.map((r) => r.gapStatus)) : null,
    amountClearAll: withX.every((r) => r.gapAmount >= 0), amountCovered: withX.filter((r) => r.gapAmount < 0).length,
    statusCovered: withX.filter((r) => r.gapStatus < 0).length,
    actionsInsideAll: withX.every((r) => r.actionsInside), actionsMaxW: withX.length ? Math.max(...withX.map((r) => r.actionsW)) : null,
    buttonsClipped: withX.reduce((n, r) => n + r.buttons.filter((b) => b.clipped).length, 0), actionsCellOverflow: withX.filter((r) => r.actionsCellOverflow).length,
    maxButtonLines: withX.length ? Math.max(...withX.map((r) => r.buttonLines)) : null,
    nonEmptyActions: withX.filter((r) => r.actionsText).length,
    rowH: { min: Math.min(...rows.map((r) => r.rowH)), max: Math.max(...rows.map((r) => r.rowH)) },
    rows,
  };
}

function tilesProbe() {
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const dl = document.querySelector('[data-testid="ledger-totals-breakdown"]');
  if (!dl) return { error: "no breakdown" };
  return {
    tag: dl.tagName.toLowerCase(), count: document.querySelectorAll('[data-testid="ledger-totals-breakdown"]').length,
    childTags: [...dl.children].map((c) => c.tagName.toLowerCase() + "[" + [...c.children].map((g) => g.tagName.toLowerCase()).join("+") + "]"),
    tiles: [...dl.querySelectorAll('[data-testid^="ledger-total-"]')].map((e) => {
      const r = e.getBoundingClientRect();
      const spill = [...e.querySelectorAll("*")].filter((c) => { const q = c.getBoundingClientRect(); return q.width && (q.right > r.right + 0.5 || q.left < r.left - 0.5); }).length;
      return { testid: e.dataset.testid, count: document.querySelectorAll(`[data-testid="${e.dataset.testid}"]`).length, parent: e.parentElement === dl, dt: e.querySelectorAll("dt").length, dd: e.querySelectorAll("dd").length, dtText: norm(e.querySelector("dt") && e.querySelector("dt").textContent), ddText: norm(e.querySelector("dd") && e.querySelector("dd").textContent), w: Math.round(r.width), h: Math.round(r.height), overflow: e.scrollWidth > e.clientWidth, spill };
    }),
  };
}

const res = { at: new Date().toISOString(), a4: {}, tiles: {}, axe: {}, console: [] };
async function open(page, url) { await page.goto(url, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(600); }

(async () => {
  const browser = await L.chromium.launch();
  const states = {}; for (const w of ["alice", "bob", "carol"]) states[w] = await L.loginState(browser, w);
  const views = { alice: [["await", `${TX}?status=AWAITING_APPROVAL`], ["p1", TX], ["p2", `${TX}?page=1`]], bob: [["await", `${TX}?status=AWAITING_APPROVAL`], ["p1", TX]], carol: [["await", `${TX}?status=AWAITING_APPROVAL`], ["p1", TX]] };
  for (const who of ["alice", "bob", "carol"]) for (const v of ["1024-light", "1024-dark", "1440-light", "1440-dark"]) {
    const [w, theme] = v.split("-");
    const ctx = await L.newCtx(browser, states[who], { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage(); const lbl = { label: "" }; L.watch(page, res.console, lbl);
    for (const [k, url] of views[who]) {
      lbl.label = `${who} ${k} ${v}`; await open(page, url);
      await page.getByTestId("transactions-table").scrollIntoViewIfNeeded();
      res.a4[`${who}/${k}/${v}`] = await page.evaluate(a4Probe);
      if (who === "alice" && k === "await" && v === "1024-light") await page.getByTestId("transactions-table").screenshot({ path: path.join(OUT, "crop-F-667A-A4-1024-await-after-fix.png") });
    }
    if (who === "alice") for (const [c, id] of [["khumalo", K], ["ndlovu", N], ["acme", AC]]) {
      lbl.label = `${who} hist-${c} ${v}`; await open(page, `${CL}/${id}`);
      res.tiles[`${c}/${v}`] = await page.evaluate(tilesProbe);
      if (c === "khumalo" && v === "1440-light") await page.getByTestId("ledger-totals-breakdown").screenshot({ path: path.join(OUT, "crop-F-667A-A1-tiles-1440-after-fix.png") });
      if (c !== "acme") {
        await page.addScriptTag({ content: AXE });
        res.axe[`hist-${c}/${v}`] = await page.evaluate(async () => { const r = await window.axe.run(document.querySelector("#main-content"), { resultTypes: ["violations"] }); return r.violations.map((x) => ({ id: x.id, impact: x.impact, nodes: x.nodes.length, sample: x.nodes.slice(0, 2).map((n) => n.target.join(" ")) })); });
      }
    }
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "a4-a1.json"), JSON.stringify(res, null, 1));
  for (const [k, m] of Object.entries(res.a4)) console.log(k, `table ${m.table}/${m.scroller} sticky ${m.actionsSticky} gapA ${m.minGapAmount} gapS ${m.minGapStatus} amtCov ${m.amountCovered} stCov ${m.statusCovered} inside ${m.actionsInsideAll} actW ${m.actionsMaxW} clip ${m.buttonsClipped} cellOv ${m.actionsCellOverflow} btnLines ${m.maxButtonLines} nonEmpty ${m.nonEmptyActions}/${m.rowCount} rowH ${m.rowH.min}..${m.rowH.max}`);
  for (const [k, m] of Object.entries(res.tiles)) console.log("tiles", k, m.tag, m.count, JSON.stringify(m.childTags), m.tiles.map((t) => `${t.testid}×${t.count} dt${t.dt} dd${t.dd} "${t.ddText}" ${t.w}x${t.h}${t.overflow ? " OVF" : ""}${t.spill ? " spill" + t.spill : ""}`).join(" | "));
  for (const [k, m] of Object.entries(res.axe)) console.log("axe", k, JSON.stringify(m.map((x) => `${x.id}:${x.impact}×${x.nodes}`)));
  console.log("console", res.console.length);
})().catch((e) => { console.error("FAIL", e); process.exit(1); });
