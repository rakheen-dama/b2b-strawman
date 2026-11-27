// 667A step 1: capture (read-only on the data — no approve, no reverse, no record; dialogs/menus are
// opened and cancelled). Reads the ids from before/seed.json (00-seed.cjs, run once). Re-run
// UNEDITED with PHASE=after for the OBS-after against the same data.
// Personas through /mock-login: alice (owner), carol (member), bob (admin — the second-approver view).
// Variants 1024×768 / 1440×900 × light / dark (theme via localStorage `kazi-theme`, rail expanded,
// DPR 1, reduced motion, Africa/Johannesburg). Raw PNGs → /tmp/667A-$PHASE-raw/ (never committed);
// JSON → $RAW/capture.json, assembled by 02-assemble.cjs.
// Usage: PHASE=before|after node 01-capture.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const { probe } = require("./probe.cjs");

const S = L.readSeed();
const K = S.clients.khumalo, N = S.clients.ndlovu, AC = S.clients.acme, KM = S.matters.khumalo;
const ID = S.ids;
const TX = `${L.TA}/transactions`, CL = `${L.TA}/client-ledgers`;
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const LL = ["1024-light", "1440-light"];
const K10 = [["k10Spaced", ID.k10Spaced], ["k10Token", ID.k10Token]];
// key, url, {persona: variants}, shot (contact-sheet tile), k10 probe
const TARGETS = [
  ["tx", TX, { alice: V4, carol: V4, bob: ["1440-light"] }, true, K10],
  ["tx-p2", `${TX}?page=1`, { alice: LL, carol: ["1440-light"] }, true],
  ["tx-p3", `${TX}?page=2`, { alice: ["1440-light"] }, true],
  ["tx-await", `${TX}?status=AWAITING_APPROVAL`, { alice: LL, carol: ["1440-light"], bob: V4 }, true],
  ["tx-type-p1", `${TX}?type=DEPOSIT&page=1`, { alice: ["1440-light"] }, true],
  ["tx-adv", `${TX}?dateFrom=2026-11-01&dateTo=2026-11-30&customerId=${K}&projectId=${KM}&page=1`, { alice: ["1440-light"] }, true],
  ["tx-reversed", `${TX}?status=REVERSED`, { alice: ["1440-light"] }, false],
  ["cl", CL, { alice: V4, carol: V4 }, true],
  ["cl-nonzero", `${CL}?nonZeroOnly=true`, { alice: ["1440-light"] }, true],
  ["cl-search", `${CL}?search=Khumalo`, { alice: ["1440-light"] }, true],
  ["cl-empty", `${CL}?search=zzz-667A-no-match`, { alice: ["1440-light"], carol: ["1440-light"] }, true],
  ["cl-p1", `${CL}?page=1`, { alice: ["1440-light"] }, true],
  ["cl-nonzero-search", `${CL}?nonZeroOnly=true&search=Ndlovu`, { alice: ["1440-light"] }, true],
  ["hist", `${CL}/${K}`, { alice: V4, carol: V4 }, true],
  ["hist-p2", `${CL}/${K}?page=1`, { alice: LL, carol: ["1440-light"] }, true],
  ["hist-type-p1", `${CL}/${K}?type=DEPOSIT&page=1`, { alice: ["1440-light"] }, true],
  ["hist-status", `${CL}/${K}?status=REVERSED`, { alice: ["1440-light"] }, true],
  ["hist-dates", `${CL}/${K}?dateFrom=2026-11-10&dateTo=2026-11-20`, { alice: ["1440-light"] }, true],
  ["hist-ndlovu", `${CL}/${N}`, { alice: ["1440-light"] }, true],
  ["hist-acme", `${CL}/${AC}`, { alice: ["1440-light"] }, true],
  ["landing", L.TA, { alice: ["1440-light"], carol: ["1440-light"] }, false],
];

const cons = [];
const out = { phase: L.PHASE, at: new Date().toISOString(), seedIds: ID, caps: {}, pages: {}, rowCrops: {}, k10Crops: {}, interactions: {} };

async function capture(browser, who, state) {
  const t = await L.idpToken(who);
  const cap = (await L.apiGet(t, "/api/me/capabilities")).json;
  out.caps[who] = cap && { role: cap.role, isAdmin: cap.isAdmin, isOwner: cap.isOwner, capabilities: cap.capabilities };
  const variants = [...new Set(TARGETS.flatMap(([, , p]) => p[who] || []))];
  for (const v of variants) {
    const [w, theme] = v.split("-");
    const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    const lbl = { label: "" };
    L.watch(page, cons, lbl);
    for (const [key, url, per, shot, k10] of TARGETS) {
      if (!(per[who] || []).includes(v)) continue;
      lbl.label = `${who} ${key} ${v}`;
      const resp = await page.goto(url, { waitUntil: "load" });
      await page.waitForLoadState("networkidle").catch(() => {});
      await page.waitForTimeout(600);
      const m = await page.evaluate(probe, { k10: k10 || [] });
      m.httpStatus = resp ? resp.status() : null;
      (out.pages[key] = out.pages[key] || {})[`${who}/${v}`] = m;
      // K-10 split: the same probe with the unbroken-token row hidden (display:none, restored after)
      // = what the spaced 150-char reference alone does to the table. DOM-only, nothing saved.
      if (k10) {
        m.k10SpacedOnly = await page.evaluate(([fn, id, k]) => {
          const r = document.querySelector(`[data-testid="transaction-row-${id}"]`);
          if (!r) return null;
          r.style.display = "none";
          const res = new Function(`return (${fn})`)()({ k10: k });
          r.style.display = "";
          return { k10: res.k10, table: res.tables[0] && { width: res.tables[0].width, scroller: res.tables[0].scroller, headers: res.tables[0].headers } };
        }, [probe.toString(), ID.k10Token, [["k10Spaced", ID.k10Spaced]]]);
      }
      if (shot) await page.screenshot({ path: path.join(L.RAW, `${key}--${who}--${v}.png`), fullPage: true });
      // approval-row crops (1440 light) and K-10 crops
      if (key === "tx-await" && v === "1440-light")
        for (const k of ["await0", "await1"]) {
          const row = page.getByTestId(`transaction-row-${ID[k]}`);
          if (await row.count()) { const f = path.join(L.RAW, `row-${k}--${who}.png`); await row.screenshot({ path: f }); out.rowCrops[`${k}/${who}`] = f; }
        }
      if (key === "tx" && who === "alice" && v.endsWith("light")) {
        const box = await page.evaluate((ids) => {
          const rs = ids.map((id) => document.querySelector(`[data-testid="transaction-row-${id}"]`)).filter(Boolean);
          if (!rs.length) return null;
          const t = rs[0].closest("table");
          let sc = t.parentElement;
          while (sc && !/(auto|scroll)/.test(getComputedStyle(sc).overflowX)) sc = sc.parentElement;
          const s = (sc || t).getBoundingClientRect(), th = t.querySelector("thead").getBoundingClientRect();
          const top = Math.min(...rs.map((r) => r.getBoundingClientRect().top)), bot = Math.max(...rs.map((r) => r.getBoundingClientRect().bottom));
          const card = t.closest('[data-slot="card"]') || sc;
          const c = card.getBoundingClientRect();
          return { x: Math.max(0, Math.floor(c.left) - 8), y: Math.floor(th.top + scrollY) - 8, w: Math.ceil(c.width) + 16, h: Math.ceil(bot - th.top) + 16, topRows: Math.round(top + scrollY) };
        }, [ID.k10Spaced, ID.k10Token]);
        if (box) {
          // header + every row down to the two K-10 rows, full card width
          const f = path.join(L.RAW, `k10--${v}.png`);
          await page.screenshot({ path: f, fullPage: true, clip: { x: box.x, y: box.y, width: Math.min(box.w, +w - box.x), height: box.h } });
          out.k10Crops[v] = { file: f, clip: box };
        }
      }
    }
    await ctx.close();
  }
}

// Interactions (Alice, 1440×900 light): navigation-only; record the URL each produces.
async function interact(browser, state) {
  const ctx = await L.newCtx(browser, state, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const page = await ctx.newPage();
  const lbl = { label: "alice interactions" };
  L.watch(page, cons, lbl);
  const I = out.interactions;
  const go = async (u) => { await page.goto(u, { waitUntil: "load" }); await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(500); };
  const rel = () => { const u = new URL(page.url()); return u.pathname + u.search; };
  const waitUrlChange = async (from, ms = 8000) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (rel() !== from) break; await page.waitForTimeout(150); } await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(400); return rel(); };
  const safe = async (k, fn) => { try { I[k] = await fn(); } catch (e) { I[k] = { error: e.message.split("\n")[0] }; } };

  await safe("txDateFromDropsPage", async () => { await go(`${TX}?type=DEPOSIT&page=1`); const from = rel(); await page.locator("#filter-dateFrom").fill("2026-11-01"); return { from, to: await waitUrlChange(from) }; });
  await safe("txDateToDropsPage", async () => { await go(`${TX}?type=DEPOSIT&page=1`); const from = rel(); await page.locator("#filter-dateTo").fill("2026-11-30"); return { from, to: await waitUrlChange(from) }; });
  await safe("txClientIdDropsPage", async () => { await go(`${TX}?type=DEPOSIT&page=1`); const from = rel(); await page.locator("#filter-customerId").fill(K); return { from, to: await waitUrlChange(from) }; });
  await safe("txMatterIdDropsPage", async () => { await go(`${TX}?page=1`); const from = rel(); await page.locator("#filter-projectId").fill(KM); return { from, to: await waitUrlChange(from) }; });
  await safe("txStatusLinkDropsPage", async () => { await go(`${TX}?type=DEPOSIT&page=1`); const from = rel(); await page.locator('[data-testid="status-filters"] a', { hasText: "Recorded" }).click(); return { from, to: await waitUrlChange(from) }; });
  await safe("txTypeLinkDropsPage", async () => { await go(`${TX}?status=RECORDED&page=1`); const from = rel(); await page.locator('[data-testid="type-filters"] a', { hasText: /^Payment$/ }).click(); return { from, to: await waitUrlChange(from) }; });
  await safe("txNextPrev", async () => {
    await go(`${TX}?type=DEPOSIT`); const a = rel();
    await page.locator("#main-content").getByRole("link", { name: "Next" }).click(); const b = await waitUrlChange(a);
    await page.locator("#main-content").getByRole("link", { name: "Previous" }).click(); const c = await waitUrlChange(b);
    return { start: a, afterNext: b, afterPrevious: c };
  });
  await safe("txPage2DeepLink", async () => { await go(`${TX}?page=1`); const m = await page.evaluate(probe, {}); return { url: rel(), pager: m.pager, rows: m.tables[0] && m.tables[0].rowCount }; });
  await safe("txOutOfRange", async () => { await go(`${TX}?page=9`); const m = await page.evaluate(probe, {}); return { url: rel(), pager: m.pager, found: m.found, rows: m.tables.length ? m.tables[0].rowCount : 0, body: m.bodyHead }; });
  await safe("recordTxMenu", async () => {
    await go(TX); await page.getByTestId("record-transaction-button").click(); await page.waitForTimeout(400);
    const items = await page.getByRole("menuitem").allInnerTexts(); await page.keyboard.press("Escape"); return { items };
  });
  await safe("reverseDialogProbe", async () => {
    await go(TX); const row = page.getByTestId(`transaction-row-${ID.reverseProbe}`); await row.scrollIntoViewIfNeeded();
    await row.getByTestId("reverse-button").click(); const dlg = page.getByRole("dialog"); await dlg.waitFor(); await page.waitForTimeout(400);
    const d = await dlg.evaluate((x) => ({ text: x.innerText.replace(/\s+/g, " ").trim(), testids: [...x.querySelectorAll("[data-testid]")].map((e) => e.dataset.testid), dateMin: (x.querySelector('[data-testid="reversal-date-input"]') || {}).min }));
    const f = path.join(L.RAW, "reverse-dialog-probe.png"); await page.screenshot({ path: f });
    await dlg.getByRole("button", { name: "Cancel" }).click(); await dlg.waitFor({ state: "hidden" });
    return { ...d, file: f, cancelled: true };
  });
  await safe("clSearchButton", async () => { await go(CL); const from = rel(); await page.getByTestId("client-search").fill("Khumalo"); await page.getByRole("button", { name: "Search", exact: true }).click(); return { from, to: await waitUrlChange(from) }; });
  await safe("clSearchKeepsNonZeroDropsPage", async () => { await go(`${CL}?nonZeroOnly=true&page=1`); const from = rel(); await page.getByTestId("client-search").fill("Ndlovu"); await page.getByRole("button", { name: "Search", exact: true }).click(); return { from, to: await waitUrlChange(from) }; });
  await safe("clNonZeroToggle", async () => { await go(`${CL}?search=a&page=1`); const a = rel(); await page.getByTestId("non-zero-filter").click(); const b = await waitUrlChange(a); await page.getByTestId("non-zero-filter").click(); const c = await waitUrlChange(b); return { start: a, on: b, off: c }; });
  await safe("histDateFromDropsPage", async () => { await go(`${CL}/${K}?page=1`); const from = rel(); await page.locator("#filter-dateFrom").fill("2026-11-01"); return { from, to: await waitUrlChange(from) }; });
  await safe("histStatusLinkDropsPage", async () => { await go(`${CL}/${K}?page=1`); const from = rel(); await page.locator('[data-testid="status-filters"] a', { hasText: "Recorded" }).click(); return { from, to: await waitUrlChange(from) }; });
  await safe("histNextPrev", async () => {
    await go(`${CL}/${K}`); const a = rel();
    await page.locator("#main-content").getByRole("link", { name: "Next" }).click(); const b = await waitUrlChange(a);
    await page.locator("#main-content").getByRole("link", { name: "Previous" }).click(); const c = await waitUrlChange(b);
    return { start: a, afterNext: b, afterPrevious: c };
  });
  await safe("histPrintStatementEmpty", async () => { await go(`${CL}/${K}`); await page.getByTestId("print-statement-btn").click(); await page.waitForTimeout(400); return { message: await page.getByTestId("print-statement").innerText() }; });
  await safe("histBackLink", async () => { await go(`${CL}/${K}`); return { href: await page.getByTestId("back-link").getAttribute("href"), text: await page.getByTestId("back-link").innerText() }; });
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  for (const who of ["alice", "carol", "bob"]) {
    const st = await L.loginState(browser, who);
    await capture(browser, who, st);
    if (who === "alice") await interact(browser, st);
    console.log("captured", who);
  }
  // API cross-check (X2): the ledger card vs the history's running balances, Khumalo / Ndlovu / Acme
  const t = await L.idpToken("alice");
  out.api = {};
  for (const [k, c] of [["khumalo", K], ["ndlovu", N], ["acme", AC]]) {
    const card = (await L.apiGet(t, `/api/trust-accounts/${S.account.id}/client-ledgers/${c}`)).json;
    const h = (await L.apiGet(t, `/api/trust-accounts/${S.account.id}/client-ledgers/${c}/history?page=0&size=20&sort=transactionDate,desc`)).json;
    out.api[k] = { card: card && { balance: card.balance, investedBalance: card.investedBalance, totalDeposits: card.totalDeposits, totalPayments: card.totalPayments }, historyTotal: h && h.page && h.page.totalElements, historyPages: h && h.page && h.page.totalPages, page0: L.list(h).map((x) => ({ ref: x.reference.slice(0, 30), date: x.transactionDate, type: x.transactionType, status: x.status, amount: x.amount, runningBalance: x.runningBalance })) };
  }
  const txp = (await L.apiGet(t, `/api/trust-accounts/${S.account.id}/transactions?page=0&size=20&sort=transactionDate,desc`)).json;
  out.api.transactions = { totalElements: txp.page.totalElements, totalPages: txp.page.totalPages, pageSize: txp.page.size };
  out.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(L.RAW, "capture.json"), JSON.stringify(out));
  console.log("pages", Object.keys(out.pages).length, "console", cons.length, "interactions", JSON.stringify(out.interactions).slice(0, 1500));
})().catch((e) => { console.error("CAPTURE FAIL", e); process.exit(1); });
