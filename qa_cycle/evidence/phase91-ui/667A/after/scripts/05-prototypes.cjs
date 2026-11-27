// 667A OBS-after: measured repro + live-DOM prototypes for the new findings (no product code edited,
// nothing saved: every change is an in-page DOM/style edit on the running stack). Alice, light.
// Writes ../prototypes.json + crop PNGs into ../. Usage (Node 20): node 05-prototypes.cjs
const path = require("path");
const fs = require("fs");
process.env.PHASE = "after";
const L = require("../../before/scripts/lib.cjs");
const OUT = path.resolve(__dirname, "..");
const S = L.readSeed();
const K = S.clients.khumalo;
const TX = `${L.TA}/transactions`, CL = `${L.TA}/client-ledgers`;
const AXE = fs.readFileSync(path.resolve(L.W, "../b2b-strawman/node_modules/.pnpm/axe-core@4.11.3/node_modules/axe-core/axe.min.js"), "utf8");
const res = {};
const cons = [];
async function open(page, url) { await page.goto(url, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(600); }
const axe = async (page, rules) => { if (!(await page.evaluate(() => !!window.axe))) await page.addScriptTag({ content: AXE }); return page.evaluate(async (r) => (await window.axe.run(document.querySelector("#main-content"), { runOnly: r })).violations.map((v) => `${v.id}:${v.nodes.length}`), rules); };

(async () => {
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  for (const w of [1024, 1440]) {
    const ctx = await L.newCtx(browser, st, { width: w, height: w === 1024 ? 768 : 900, theme: "light", extra: L.EXTRA });
    const page = await ctx.newPage(); L.watch(page, cons, { label: `proto ${w}` });
    const R = (res[w] = {});
    // ---- A1: ledger tiles — dl > Card(role=group) > … > dd (no dt). Prototype: flatten each tile to dl > div(testid, card classes) > dt + dd.
    await open(page, `${CL}/${K}`);
    R.tilesAxeBefore = await axe(page, ["definition-list", "dlitem"]);
    const bd = page.getByTestId("ledger-totals-breakdown");
    await bd.screenshot({ path: path.join(OUT, `crop-F-667A-A1-tiles-${w}-as-built.png`) });
    R.tilesProto = await page.evaluate(() => {
      const dl = document.querySelector('[data-testid="ledger-totals-breakdown"]');
      const before = [...dl.children].map((c) => Math.round(c.getBoundingClientRect().height));
      for (const card of [...dl.children]) {
        const label = card.querySelector('[data-slot="stat-card-label"]'), value = card.querySelector('[data-slot="stat-card-value"]'), dd = value.querySelector("dd");
        const div = document.createElement("div");
        div.className = card.className; div.dataset.testid = card.dataset.testid; div.dataset.slot = "stat-card";
        const dt = document.createElement("dt"); dt.className = label.className + " block"; dt.textContent = label.textContent;
        const nd = document.createElement("dd"); nd.className = value.className + " mt-0.5"; nd.textContent = dd.textContent;
        div.append(dt, nd); card.replaceWith(div);
      }
      return { heightsBefore: before, heightsAfter: [...dl.children].map((c) => Math.round(c.getBoundingClientRect().height)), dd: [...dl.querySelectorAll('[data-testid^="ledger-total-"]')].map((t) => `${t.dataset.testid}=${t.querySelector("dd").textContent}`) };
    });
    R.tilesAxeAfterProto = await axe(page, ["definition-list", "dlitem"]);
    await bd.screenshot({ path: path.join(OUT, `crop-F-667A-A1-tiles-${w}-prototype.png`) });
    // ---- A2: out-of-range page — the pager says "Page 10 of 3", Previous → page=8 (still out of range)
    await open(page, `${TX}?page=9`);
    R.outOfRange = await page.evaluate(() => { const m = document.querySelector("#main-content"); const p = [...m.querySelectorAll("p,span")].find((e) => /^Page \d+ of \d+$/.test((e.textContent || "").trim())); return { pager: p && p.innerText, prev: p && (p.parentElement.querySelector("a") || {}).getAttribute && p.parentElement.querySelector("a").getAttribute("href"), emptyText: [...m.querySelectorAll("*")].filter((e) => e.children.length === 0 && /No transactions|match/i.test(e.textContent || "")).map((e) => e.textContent.trim()), found: [...m.querySelectorAll("*")].filter((e) => e.children.length === 0 && /transactions found$/.test((e.textContent || "").trim())).map((e) => e.textContent.trim()) }; });
    await open(page, `${TX}?status=REJECTED`);
    R.noMatch = await page.evaluate(() => { const m = document.querySelector("#main-content"); return { emptyText: [...m.querySelectorAll("*")].filter((e) => e.children.length === 0 && /No transactions|match/i.test(e.textContent || "")).map((e) => e.textContent.trim()), tables: m.querySelectorAll("table").length }; });
    // ---- A3: K-89 — the visible columns vs General balance; prototype two more columns (shown when used): "Transfers (net)" and "Returned from s86(4)"
    await open(page, CL);
    const t = page.getByTestId("client-ledgers-table");
    await t.locator("xpath=ancestor::*[@data-slot='card'][1]").screenshot({ path: path.join(OUT, `crop-F-667A-A3-k89-${w}-as-built.png`) });
    const tok = await L.idpToken("alice");
    const api = L.list((await L.apiGet(tok, `/api/trust-accounts/${S.account.id}/client-ledgers?size=50`)).json);
    R.k89Proto = await page.evaluate((rows) => {
      const t = document.querySelector('[data-testid="client-ledgers-table"]');
      let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
      const before = { table: Math.round(t.getBoundingClientRect().width), scroller: sc.clientWidth };
      const heads = [...t.querySelectorAll("thead th")]; const iPlaced = heads.findIndex((h) => /placed/i.test(h.textContent));
      const fmt = (n) => "R " + Math.abs(n).toLocaleString("fr-FR", { minimumFractionDigits: 2 }).replace(/ /g, " ").replace(".", ",") ; // display only
      const add = (title, f) => { const h = heads[iPlaced].cloneNode(true); h.textContent = title; heads[iPlaced].before(h); for (const r of t.querySelectorAll("tbody tr")) { const td = r.children[iPlaced].cloneNode(true); const name = r.children[0].innerText.trim(); const x = rows.find((y) => y.clientName === name); const v = f(x); td.textContent = (v < 0 ? "−" : "") + fmt(v); r.children[iPlaced].before(td); } };
      add("Transfers (net)", (x) => x.totalTransfersIn - x.totalTransfersOut);
      add("Returned from s86(4)", (x) => x.totalInvestmentReturns);
      const sums = rows.map((x) => ({ client: x.clientName, balance: x.balance, visibleSumWithProto: x.totalDeposits + (x.totalTransfersIn - x.totalTransfersOut) + x.totalInvestmentReturns - x.totalPayments - x.totalFeeTransfers - x.totalRefunds - x.totalInvestmentPlacements + (x.broughtForward || 0) }));
      return { before, after: { table: Math.round(t.getBoundingClientRect().width), scroller: sc.clientWidth, scrollWidth: sc.scrollWidth }, sums };
    }, api);
    await t.locator("xpath=ancestor::*[@data-slot='card'][1]").screenshot({ path: path.join(OUT, `crop-F-667A-A3-k89-${w}-prototype.png`) });
    if (w === 1024) {
      // ---- O-2: client ledgers at 1024 — no sticky identifier column; prototype: sticky-left Client Name (bg-card, right rule)
      await open(page, CL);
      R.clStickyProto = await page.evaluate(() => {
        const t = document.querySelector('[data-testid="client-ledgers-table"]'); let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
        const vis = (el) => { const r = el.getBoundingClientRect(), s = sc.getBoundingClientRect(); return r.left >= s.left - 0.5 && r.right <= s.right + 0.5; };
        sc.scrollLeft = sc.scrollWidth; const nameVisibleAtMaxBefore = [...t.querySelectorAll("tbody tr")].map((r) => vis(r.children[0]));
        sc.scrollLeft = 0;
        for (const c of t.querySelectorAll("tr > :first-child")) { c.style.position = "sticky"; c.style.left = "0"; c.style.zIndex = "1"; c.style.background = "var(--card)"; c.style.boxShadow = "inset -1px 0 0 var(--border)"; }
        sc.scrollLeft = sc.scrollWidth; const nameVisibleAtMaxProto = [...t.querySelectorAll("tbody tr")].map((r) => vis(r.children[0]));
        return { scroller: `${sc.clientWidth}/${sc.scrollWidth}`, nameVisibleAtMaxBefore, nameVisibleAtMaxProto };
      });
      await page.getByTestId("client-ledgers-table").locator("xpath=ancestor::*[@data-slot='card'][1]").screenshot({ path: path.join(OUT, "crop-O-2-cl-1024-sticky-name-prototype.png") });
      // ---- O-1: transactions at 1024 — what the sticky Actions covers at scrollLeft 0 (rows in view)
      await open(page, `${TX}?status=AWAITING_APPROVAL`);
      await page.getByTestId("transactions-table").scrollIntoViewIfNeeded();
      R.txStickyCover = await page.evaluate(() => {
        const t = document.querySelector('[data-testid="transactions-table"]'); let sc = t.parentElement; while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
        const heads = [...t.querySelectorAll("thead th")].map((h) => h.textContent.trim());
        const probe = () => [...t.querySelectorAll("tbody tr")].map((r) => Object.fromEntries(heads.map((h, i) => { const c = r.children[i]; const b = c.getBoundingClientRect(); const s = sc.getBoundingClientRect(); const cx = Math.min(Math.max(b.left + b.width / 2, s.left + 2), s.right - 2); const hit = document.elementFromPoint(cx, b.top + b.height / 2); return [h, !!hit && c.contains(hit) ? "shown" : hit && hit.closest("td") && hit.closest("td") !== c ? "covered" : "out"]; })));
        sc.scrollLeft = 0; const at0 = probe(); sc.scrollLeft = sc.scrollWidth; const atMax = probe(); sc.scrollLeft = 0;
        return { scroller: `${sc.clientWidth}/${sc.scrollWidth}`, at0, atMax };
      });
    }
    await ctx.close();
  }
  res.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(OUT, "prototypes.json"), JSON.stringify(res, null, 1));
  console.log(JSON.stringify(res, null, 1).slice(0, 6000));
})().catch((e) => { console.error("PROTO FAIL", e); process.exit(1); });
