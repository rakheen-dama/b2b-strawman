// 667A OBS-after: the checks the before set lacks (read-only: navigation, scrollLeft, DOM reads; no clicks
// on Approve/Reject/Reverse). Reuses before/scripts/lib.cjs (same logins, same stack, same seed ids).
// Writes ../checks.json + crop PNGs into ../ (the after/ dir). Raw tiles → /tmp/667A-after-raw/.
// Usage (Node 20): node 04-after-checks.cjs
const path = require("path");
const fs = require("fs");
process.env.PHASE = "after";
const L = require("../../before/scripts/lib.cjs");
const OUT = path.resolve(__dirname, "..");
const S = L.readSeed();
const K = S.clients.khumalo, N = S.clients.ndlovu, AC = S.clients.acme;
const ID = S.ids;
const TX = `${L.TA}/transactions`, CL = `${L.TA}/client-ledgers`;
const AXE = fs.readFileSync(path.resolve(L.W, "../b2b-strawman/node_modules/.pnpm/axe-core@4.11.3/node_modules/axe-core/axe.min.js"), "utf8");

// ---------- in-page helpers (stringified into the page) ----------
function pageFns() {
  const norm = (s) => (s || "").replace(/\s+/g, " ").trim();
  const cv = document.createElement("canvas"); cv.width = cv.height = 1; const x = cv.getContext("2d", { willReadFrequently: true });
  const paint = (cols) => { x.globalCompositeOperation = "source-over"; x.fillStyle = "#fff"; x.fillRect(0, 0, 1, 1); for (const c of cols) { x.fillStyle = "rgba(0,0,0,0)"; x.fillStyle = c; x.fillRect(0, 0, 1, 1); } const d = x.getImageData(0, 0, 1, 1).data; return [d[0], d[1], d[2]]; };
  const lum = ([r, g, b]) => { const f = (v) => { v /= 255; return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4); }; return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b); };
  const ratio = (a, b) => { const [l1, l2] = [lum(a), lum(b)].sort((p, q) => q - p); return +((l1 + 0.05) / (l2 + 0.05)).toFixed(2); };
  const hex = (a) => "#" + a.map((v) => v.toString(16).padStart(2, "0")).join("");
  // composited background: every ancestor's background-color painted root → element
  window.__contrast = (el) => {
    const chain = []; for (let e = el; e; e = e.parentElement) chain.unshift(getComputedStyle(e).backgroundColor);
    const bg = paint(chain); const fg = paint([...chain, getComputedStyle(el).color]);
    return { fg: hex(fg), bg: hex(bg), ratio: ratio(fg, bg), fontSize: getComputedStyle(el).fontSize, fontWeight: getComputedStyle(el).fontWeight };
  };
  window.__lines = (el) => { const rg = document.createRange(); rg.selectNodeContents(el); return new Set([...rg.getClientRects()].filter((r) => r.width > 0).map((r) => Math.round(r.top))).size; };
  window.__scroller = (t) => { let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement; return sc; };
  window.__norm = norm;
  // is the centre of `el` actually showing `el` (not covered by the sticky column), and inside the scroller?
  window.__visible = (el, sc) => {
    const r = el.getBoundingClientRect(), s = sc.getBoundingClientRect();
    const inside = r.left >= s.left - 0.5 && r.right <= s.right + 0.5;
    const cx = Math.min(Math.max(r.left + r.width / 2, s.left + 1), s.right - 1), cy = r.top + r.height / 2;
    const hit = document.elementFromPoint(cx, cy);
    return { inside, uncovered: !!hit && (el === hit || el.contains(hit)), left: Math.round(r.left - s.left), right: Math.round(r.right - s.left), scW: sc.clientWidth };
  };
}

// table probe: per column, the cells' visibility at scrollLeft 0 and at the max scroll; Date/Type line checks
function tableProbe(testid) {
  const t = document.querySelector(`[data-testid="${testid}"]`);
  if (!t) return null;
  const sc = window.__scroller(t);
  const heads = [...t.querySelectorAll("thead th")].map((h) => window.__norm(h.textContent));
  const rows = [...t.querySelectorAll("tbody tr")];
  const at = (label) => {
    const out = {};
    for (const [ci, h] of heads.entries()) {
      const cells = rows.map((r) => r.children[ci]).filter(Boolean);
      const vis = cells.map((c) => window.__visible(c, sc));
      out[h] = { inside: vis.filter((v) => v.inside).length, uncovered: vis.filter((v) => v.uncovered).length, of: cells.length, x: vis[0] && `${vis[0].left}..${vis[0].right}` };
    }
    return { scrollLeft: Math.round(sc.scrollLeft), cols: out };
  };
  sc.scrollLeft = 0; const s0 = at("0");
  sc.scrollLeft = sc.scrollWidth; const sMax = at("max");
  sc.scrollLeft = 0;
  const col = (name) => heads.findIndex((h) => h.toLowerCase() === name);
  const di = col("date"), ti = col("type");
  const dateLines = di < 0 ? null : rows.map((r) => window.__lines(r.children[di]));
  const typeCells = ti < 0 ? null : rows.map((r) => { const c = r.children[ti]; const b = c.querySelector('[data-slot="badge"], .rounded-md, span'); const cr = c.getBoundingClientRect(), br = b ? b.getBoundingClientRect() : cr; return { w: Math.round(cr.width), badgeW: Math.round(br.width), badgeInside: br.right <= cr.right + 0.5, badgeLines: b ? window.__lines(b) : 0, text: window.__norm(c.innerText).slice(0, 40) }; });
  const actions = heads.map((h) => h.toLowerCase()).indexOf("actions");
  const actCs = actions < 0 ? null : getComputedStyle(rows[0].children[actions]);
  return {
    scroller: { clientWidth: sc.clientWidth, scrollWidth: sc.scrollWidth }, heads, rowCount: rows.length, s0, sMax,
    dateMaxLines: dateLines && Math.max(...dateLines), dateWrapped: dateLines && dateLines.filter((n) => n > 1).length,
    typeMinW: typeCells && Math.min(...typeCells.map((c) => c.w)), typeBadgeOutside: typeCells && typeCells.filter((c) => !c.badgeInside).length, typeMaxLines: typeCells && Math.max(...typeCells.map((c) => c.badgeLines)), typeMulti: typeCells && typeCells.filter((c) => c.badgeLines > 1).map((c) => `${c.text} (${c.badgeLines} lines, ${c.badgeW}/${c.w})`).slice(0, 4),
    actionsSticky: actCs && { position: actCs.position, right: actCs.right, bg: actCs.backgroundColor },
  };
}

function tilesProbe() {
  const sum = document.querySelector('[data-testid="client-summary"]');
  const bd = document.querySelector('[data-testid="ledger-totals-breakdown"]');
  const tiles = [...document.querySelectorAll('[data-testid^="ledger-total-"]')].map((e) => {
    const dd = e.querySelectorAll("dd"); const r = e.getBoundingClientRect();
    const spill = [...e.querySelectorAll("*")].filter((c) => { const q = c.getBoundingClientRect(); return q.width && (q.right > r.right + 0.5 || q.left < r.left - 0.5); }).length;
    return { testid: e.dataset.testid, count: document.querySelectorAll(`[data-testid="${e.dataset.testid}"]`).length, ddCount: dd.length, dd: window.__norm(dd[0] && dd[0].textContent), label: window.__norm((e.querySelector('[data-slot="stat-card-label"]') || {}).textContent), w: Math.round(r.width), scrollW: e.scrollWidth, clientW: e.clientWidth, overflow: e.scrollWidth > e.clientWidth, spill, ddParent: dd[0] && dd[0].parentElement.tagName.toLowerCase() + (dd[0].parentElement.dataset.slot ? "." + dd[0].parentElement.dataset.slot : ""), dtCount: e.querySelectorAll("dt").length };
  });
  return { breakdownCount: document.querySelectorAll('[data-testid="ledger-totals-breakdown"]').length, inSummary: !!(bd && sum && sum.contains(bd)), summaryText: sum && window.__norm(sum.innerText), tiles };
}

const cons = [];
const res = { at: new Date().toISOString(), stack: process.env.STACK || "", pages: {}, contrast: {}, tiles: {}, empty: {}, axe: {}, k89: {}, crops: [] };

async function open(page, url) { const r = await page.goto(url, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(600); await page.evaluate(pageFns); return r && r.status(); }
const shell = (page) => page.evaluate(() => ({ mainId: document.querySelectorAll("main#main-content").length, main: document.querySelectorAll("main").length, h1Main: [...document.querySelectorAll("#main-content h1")].map((h) => window.__norm(h.innerText)), h1Page: document.querySelectorAll("h1").length, docHOverflow: document.documentElement.scrollWidth - document.documentElement.clientWidth }));

(async () => {
  const browser = await L.chromium.launch();
  const states = {}; for (const w of ["alice", "bob", "carol"]) states[w] = await L.loginState(browser, w);
  for (const [who, v] of [["alice", "1024-light"], ["alice", "1440-light"], ["alice", "1024-dark"], ["alice", "1440-dark"], ["bob", "1024-light"], ["carol", "1024-light"]]) {
    const [w, theme] = v.split("-");
    const ctx = await L.newCtx(browser, states[who], { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage(); const lbl = { label: "" }; L.watch(page, cons, lbl);
    const P = (k, val) => { (res.pages[k] = res.pages[k] || {})[`${who}/${v}`] = val; };
    const targets = who === "alice" ? [["tx", TX, "transactions-table"], ["tx-p2", `${TX}?page=1`, "transactions-table"], ["tx-await", `${TX}?status=AWAITING_APPROVAL`, "transactions-table"], ["hist", `${CL}/${K}`, "history-table"], ["hist-p2", `${CL}/${K}?page=1`, "history-table"], ["cl", CL, "client-ledgers-table"]]
      : who === "bob" ? [["tx-await", `${TX}?status=AWAITING_APPROVAL`, "transactions-table"]] : [["tx", TX, "transactions-table"], ["hist", `${CL}/${K}`, "history-table"]];
    for (const [k, url, tid] of targets) {
      lbl.label = `${who} ${k} ${v}`; await open(page, url);
      const m = await page.evaluate(tableProbe, tid);
      m.shell = await shell(page);
      if (k === "tx-await") m.approveButtons = await page.evaluate(() => { const t = document.querySelector('[data-testid="transactions-table"]'); const sc = window.__scroller(t); return [...t.querySelectorAll('[data-testid="approve-button"], [data-testid="reject-button"]')].map((b) => ({ id: b.dataset.testid, text: window.__norm(b.innerText), disabled: b.disabled, ...window.__visible(b, sc) })); });
      P(k, m);
      if (k === "cl") {
        // K-89 per-row sums of the visible columns vs the General balance; contrast of the client-name links
        res.k89[`${who}/${v}`] = await page.evaluate(() => {
          const t = document.querySelector('[data-testid="client-ledgers-table"]'); const heads = [...t.querySelectorAll("thead th")].map((h) => window.__norm(h.textContent));
          const num = (s) => +(s || "").replace(/[R\s ]/g, "").replace(/−/g, "-").replace(",", ".");
          return { heads, rows: [...t.querySelectorAll("tbody tr")].map((r) => { const c = [...r.children].map((td) => window.__norm(td.innerText)); const g = (n) => num(c[heads.indexOf(n)]); const visible = g("Total Deposits") - g("Total Payments") - g("Total Fee Transfers") - g("Total Refunds") - (heads.includes("Placed in s86(4)") ? g("Placed in s86(4)") : 0); return { client: c[0], balance: g(heads[1]), visibleSum: +visible.toFixed(2), gap: +(g(heads[1]) - visible).toFixed(2) }; }) };
        });
        res.contrast[`cl-client-link/${who}/${v}`] = await page.evaluate(() => [...document.querySelectorAll('[data-testid="client-ledgers-table"] tbody a')].map((a) => ({ text: a.innerText, cls: a.className, ...window.__contrast(a) })));
      }
      if (k === "tx" || k === "tx-p2") res.contrast[`${k}-deposit-amount/${who}/${v}`] = await page.evaluate(() => { const els = [...document.querySelectorAll('[data-testid="transactions-table"] [data-direction="in"]')]; const u = {}; for (const e of els) { const c = window.__contrast(e); const key = `${c.fg} on ${c.bg}`; u[key] = u[key] || { ...c, n: 0, cls: e.className }; u[key].n++; } return Object.values(u); });
      if (k === "hist") res.tiles[`khumalo/${who}/${v}`] = await page.evaluate(tilesProbe);
      // crops: K-10 at scrollLeft 0 and at the max (1024 light, Alice), and the sticky Actions over Amount/Status
      if (who === "alice" && v === "1024-light" && k === "tx") {
        const card = page.locator('[data-testid="transactions-table"]').locator("xpath=ancestor::*[@data-slot='card'][1]");
        const row = page.getByTestId(`transaction-row-${ID.k10Token}`); await row.scrollIntoViewIfNeeded();
        const bx = await page.evaluate((ids) => { const t = document.querySelector('[data-testid="transactions-table"]'); const sc = window.__scroller(t); const s = sc.getBoundingClientRect(); const rs = ids.map((i) => document.querySelector(`[data-testid="transaction-row-${i}"]`).getBoundingClientRect()); const th = t.querySelector("thead").getBoundingClientRect(); return { x: Math.floor(s.left) - 4, y: Math.floor(th.top + scrollY) - 4, w: Math.ceil(s.width) + 8, h: Math.ceil(Math.max(...rs.map((r) => r.bottom)) - th.top) + 8 }; }, [ID.k10Spaced, ID.k10Token]);
        for (const pos of ["start", "end"]) {
          await page.evaluate((p) => { const sc = window.__scroller(document.querySelector('[data-testid="transactions-table"]')); sc.scrollLeft = p === "end" ? sc.scrollWidth : 0; }, pos);
          const f = path.join(OUT, `crop-k10-1024-light-scroll-${pos}.png`); await page.screenshot({ path: f, fullPage: true, clip: { x: bx.x, y: bx.y, width: Math.min(bx.w, 1024 - bx.x), height: Math.min(bx.h, 900) } }); res.crops.push(path.basename(f));
        }
        void card;
      }
    }
    if (who === "alice") {
      // ledger tiles on the other two clients; out-of-range pages; empty states; tx filters with no match
      for (const [c, id] of [["ndlovu", N], ["acme", AC]]) { lbl.label = `${who} hist-${c} ${v}`; await open(page, `${CL}/${id}`); res.tiles[`${c}/${who}/${v}`] = await page.evaluate(tilesProbe); }
      if (v.endsWith("light")) for (const [k, url] of [["tx-out-of-range", `${TX}?page=9`], ["tx-no-match", `${TX}?status=REJECTED`], ["hist-out-of-range", `${CL}/${K}?page=5`], ["hist-no-match", `${CL}/${K}?status=REJECTED`], ["cl-no-match", `${CL}?search=zzz-667A-no-match`], ["cl-out-of-range", `${CL}?page=1`]]) {
        lbl.label = `${who} ${k} ${v}`; const st = await open(page, url);
        res.empty[`${k}/${v}`] = { http: st, ...(await shell(page)), emptyState: await page.getByTestId("empty-state").count(), text: await page.evaluate(() => window.__norm(document.querySelector("#main-content").innerText).slice(0, 400)), pager: await page.evaluate(() => { const p = [...document.querySelectorAll("#main-content p, #main-content span")].find((e) => /^Page \d+ of \d+$/.test(window.__norm(e.innerText))); if (!p) return null; const box = p.parentElement; return { text: window.__norm(p.innerText), links: [...box.querySelectorAll("a,button")].map((e) => `${window.__norm(e.innerText)}${e.getAttribute("href") ? " → " + e.getAttribute("href") : ""}${e.disabled ? " [disabled]" : ""}`) }; }) };
        if (k === "tx-out-of-range" && v === "1440-light") { const f = path.join(OUT, "crop-F-667A-A2-out-of-range-pager.png"); const card = page.locator('[data-slot="card"]').filter({ hasText: "Transaction History" }).first(); await card.screenshot({ path: f }); res.crops.push(path.basename(f)); }
      }
      if (v.endsWith("light")) for (const [k, url] of [["tx", TX], ["cl", CL], ["hist", `${CL}/${K}`]]) {
        lbl.label = `${who} axe ${k} ${v}`; await open(page, url); await page.addScriptTag({ content: AXE });
        res.axe[`${k}/${v}`] = await page.evaluate(async () => { const r = await window.axe.run(document.querySelector("#main-content"), { resultTypes: ["violations"] }); return r.violations.map((x) => ({ id: x.id, impact: x.impact, nodes: x.nodes.length, sample: x.nodes.slice(0, 2).map((n) => n.target.join(" ") + " :: " + (n.failureSummary || "").split("\n").slice(0, 2).join(" ")) })); });
      }
    }
    await ctx.close();
  }
  res.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(OUT, "checks.json"), JSON.stringify(res, null, 1));
  console.log("ok; console", cons.length, "crops", res.crops.join(" "));
})().catch((e) => { console.error("CHECKS FAIL", e); process.exit(1); });
