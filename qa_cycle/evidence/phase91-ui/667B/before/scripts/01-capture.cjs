// 667B step 1: capture (read-only on the data — no reconciliation is created, completed or
// discarded; dialogs/menus/selects are opened and cancelled; the two report runs are reads).
// Reads the ids from before/seed.json (00-seed.cjs, run once). Re-run UNEDITED with PHASE=after for
// the OBS-after against the same data.
// Personas through /mock-login: alice (owner, MANAGE_TRUST), carol (member, VIEW_TRUST only).
// Variants 1024×768 / 1440×900 × light / dark (theme via localStorage `kazi-theme`, rail expanded,
// DPR 1, reduced motion, Africa/Johannesburg). Raw PNGs → /tmp/667B-$PHASE-raw/ (never committed);
// JSON → $RAW/capture.json, assembled by 02-assemble.cjs.
// Usage: PHASE=before|after node 01-capture.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const { probe } = require("./probe.cjs");

const S = L.readSeed();
const ID = S.ids;
const R = `${L.TA}/reconciliation`, NEW = `${R}/new`, ML = `${R}/lists/${ID.monthlyListId}`;
const INV = `${L.TA}/investments`, REP = `${L.TA}/reports`, OC = `${L.TA}/other-creditors`;
const V4 = ["1024-light", "1024-dark", "1440-light", "1440-dark"];
const L1 = ["1440-light"];
// key, url, {persona: variants}, full-page tile for the contact sheet
const TARGETS = [
  ["recon", R, { alice: V4, carol: V4 }, true],
  ["recon-p2", `${R}?page=1`, { alice: V4, carol: L1 }, true],
  ["recon-oor", `${R}?page=9`, { alice: L1, carol: L1 }, true],
  ["recon-new", NEW, { alice: V4, carol: V4 }, true],
  ["mlist", ML, { alice: V4, carol: V4 }, true],
  ["inv", INV, { alice: V4, carol: V4 }, true],
  ["inv-firm", `${INV}?investmentBasis=FIRM_DISCRETION`, { alice: V4, carol: L1 }, true],
  ["inv-client", `${INV}?investmentBasis=CLIENT_INSTRUCTION`, { alice: L1 }, true],
  ["reports", REP, { alice: V4, carol: V4 }, true],
  ["oc", OC, { alice: V4, carol: L1 }, true],
  ["landing", L.TA, { alice: L1, carol: L1 }, true],
];

const cons = [];
const out = { phase: L.PHASE, at: new Date().toISOString(), seedIds: ID, caps: {}, pages: {}, crops: {}, interactions: {} };
const settle = async (page, key) => {
  await page.waitForLoadState("networkidle").catch(() => {});
  if (key === "recon-new") await page.locator('[data-testid="account-select"] option').nth(1).waitFor({ state: "attached", timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(600);
};

async function crop(page, name, sel) {
  try {
    const loc = page.locator(sel).first();
    if (!(await loc.count())) return null;
    const f = path.join(L.RAW, `crop-${name}.png`);
    await loc.screenshot({ path: f });
    out.crops[name] = { file: f, sel };
    return f;
  } catch (e) { out.crops[name] = { error: e.message.split("\n")[0], sel }; return null; }
}

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
    for (const [key, url, per, shot] of TARGETS) {
      if (!(per[who] || []).includes(v)) continue;
      lbl.label = `${who} ${key} ${v}`;
      const resp = await page.goto(url, { waitUntil: "load" });
      await settle(page, key);
      const m = await page.evaluate(probe);
      m.httpStatus = resp ? resp.status() : null;
      (out.pages[key] = out.pages[key] || {})[`${who}/${v}`] = m;
      if (shot) await page.screenshot({ path: path.join(L.RAW, `${key}--${who}--${v}.png`), fullPage: true });
      // measured-kink crops (Alice): K-91 register card at 1024, K-76 step badges, K-36 report cards
      if (who === "alice" && key === "inv" && v.startsWith("1024")) await crop(page, `k91-${v}`, '[data-slot="card"]:has([data-testid="investments-table"]), [data-testid="investments-table"]');
      if (key === "recon-new" && v.startsWith("1440")) await crop(page, `k76-${who}-${v}`, '[data-testid="step-indicator"]');
      if (who === "alice" && key === "recon-new" && v === "1024-light") await crop(page, "k76-alice-1024-light", '[data-testid="step-indicator"]');
      if (who === "alice" && key === "recon-p2" && v === "1024-light") await crop(page, "recon-p2-1024-light", '[data-slot="card"]:has([data-testid="reconciliations-table"])');
      if (who === "alice" && key === "reports" && v === "1440-light") await crop(page, "k36-1440-light", '[data-testid="report-card-trust-receipts-payments"]');
    }
    await ctx.close();
  }
}

async function interact(browser, state, who, v) {
  const [w, theme] = v.split("-");
  const ctx = await L.newCtx(browser, state, { width: +w, height: w === "1024" ? 768 : 900, theme, extra: L.EXTRA });
  const page = await ctx.newPage();
  const lbl = { label: `${who} interactions ${v}` };
  L.watch(page, cons, lbl);
  const I = (out.interactions[`${who}/${v}`] = {});
  const go = async (u, key) => { await page.goto(u, { waitUntil: "load" }); await settle(page, key); };
  const rel = () => { const u = new URL(page.url()); return u.pathname + u.search; };
  const waitUrlChange = async (from, ms = 10000) => { const t0 = Date.now(); while (Date.now() - t0 < ms) { if (rel() !== from) break; await page.waitForTimeout(150); } await page.waitForLoadState("networkidle").catch(() => {}); await page.waitForTimeout(500); return rel(); };
  const safe = async (k, fn) => { try { I[k] = await fn(); } catch (e) { I[k] = { error: e.message.split("\n")[0] }; } };
  const h1 = async () => (await page.locator("#main-content h1").allInnerTexts()).map((s) => s.trim());
  const shot = async (name) => { const f = path.join(L.RAW, `int-${name}--${who}--${v}.png`); await page.screenshot({ path: f }); return f; };
  const full = v === "1440-light";

  // Discard dialog on the open statement: open → read → screenshot → Cancel (never confirmed)
  if (who === "alice") await safe("discardDialog", async () => {
    await go(R);
    const btn = page.getByTestId(`discard-statement-${ID.statementId}`);
    await btn.scrollIntoViewIfNeeded();
    const trigger = { text: (await btn.innerText()).trim(), cls: await btn.getAttribute("class") };
    await btn.click();
    const dlg = page.getByRole("dialog");
    await dlg.waitFor();
    await page.waitForTimeout(400);
    const d = await dlg.evaluate((x) => ({ text: x.innerText.replace(/\s+/g, " ").trim(), testids: [...x.querySelectorAll("[data-testid]")].map((e) => e.dataset.testid), buttons: [...x.querySelectorAll("button")].map((b) => `${b.innerText.trim()}${b.disabled ? " [disabled]" : ""}`), box: (() => { const r = x.getBoundingClientRect(); return [Math.round(r.left), Math.round(r.top), Math.round(r.width), Math.round(r.height)]; })() }));
    const file = await shot("discard-dialog");
    await dlg.getByRole("button", { name: "Cancel" }).click();
    await dlg.waitFor({ state: "hidden" });
    const stillOpen = await page.getByTestId(`discard-statement-${ID.statementId}`).count();
    return { trigger, ...d, file, cancelled: true, statementRowStillListed: stillOpen };
  });
  if (who === "alice") await safe("basisFilter", async () => {
    await go(INV);
    const trig = page.getByTestId("investment-basis-filter");
    const before = { url: rel(), text: (await trig.innerText()).trim(), box: await trig.boundingBox() };
    await trig.click();
    await page.getByRole("option").first().waitFor();
    await page.waitForTimeout(300);
    const options = await page.getByRole("option").allInnerTexts();
    const file = await shot("basis-filter-open");
    if (!full) { await page.keyboard.press("Escape"); return { before, options, file }; }
    await page.getByRole("option", { name: "Firm Discretion", exact: true }).click();
    const firm = await waitUrlChange(before.url);
    const firmEmpty = await page.getByTestId("empty-investments").count();
    await page.getByTestId("investment-basis-filter").click();
    await page.getByRole("option", { name: "Client Instruction", exact: true }).click();
    const client = await waitUrlChange(firm);
    await page.getByTestId("investment-basis-filter").click();
    await page.getByRole("option", { name: "All", exact: true }).click();
    const all = await waitUrlChange(client);
    return { before, options, file, toFirm: firm, firmEmptyCount: firmEmpty, toClient: client, toAll: all };
  });
  if (!full) { await ctx.close(); return; }
  await safe("reconNextPrev", async () => {
    await go(R); const a = rel();
    await page.locator("#main-content").getByRole("link", { name: "Next" }).click(); const b = await waitUrlChange(a);
    const p2 = await page.evaluate(probe);
    await page.locator("#main-content").getByRole("link", { name: "Previous" }).click(); const c = await waitUrlChange(b);
    return { start: a, afterNext: b, pagerOnPage2: p2.pager, rowsOnPage2: p2.tables[0] && p2.tables[0].rowCount, afterPrevious: c };
  });
  await safe("listLockedLink", async () => {
    await go(`${R}?page=1`);
    const link = page.locator(`[href$="/reconciliation/lists/${ID.monthlyListId}"]`).first();
    const info = { count: await page.locator(`[href$="/reconciliation/lists/${ID.monthlyListId}"]`).count(), href: await link.getAttribute("href"), text: (await link.innerText()).trim(), testid: await link.getAttribute("data-testid") };
    const from = rel(); await link.click(); const to = await waitUrlChange(from);
    return { ...info, from, to, h1: await h1() };
  });
  if (who === "alice") await safe("newReconButton", async () => {
    await go(R); const from = rel();
    await page.getByTestId("new-reconciliation-btn").click(); const to = await waitUrlChange(from);
    await page.getByRole("heading", { level: 1 }).first().waitFor();
    return { from, to, h1: await h1() };
  });
  await safe("wizardStep1", async () => {
    await go(NEW, "recon-new");
    const sel = page.getByTestId("account-select");
    return {
      accountOptions: (await sel.count()) ? await sel.locator("option").allInnerTexts() : null,
      periodEndMax: await page.getByTestId("period-end-input").getAttribute("max").catch(() => null),
      nextDisabled: await page.getByTestId("step-next-btn").isDisabled().catch(() => null),
      stepSelect: await page.getByTestId("step-select").count(),
      // K-95(a): every write the wizard makes needs MANAGE_TRUST — record what the member is offered
      fileInputs: await page.locator('input[type="file"]').count(),
    };
  });
  await safe("mlistBack", async () => {
    await go(ML);
    const back = page.locator("#main-content").getByRole("link", { name: /Reconciliation/ }).first();
    const href = await back.getAttribute("href"); const from = rel();
    await back.click(); const to = await waitUrlChange(from);
    return { href, from, to };
  });
  await safe("reportCardSection35", async () => {
    await go(REP);
    const card = page.getByTestId("report-card-section-35-data-pack");
    if (!(await card.count())) return { present: 0 };
    const from = rel(); await card.click(); const to = await waitUrlChange(from);
    await page.getByRole("heading", { level: 1 }).first().waitFor();
    return { from, to, h1: await h1() };
  });
  if (who === "alice") await safe("investmentRegisterRun", async () => {
    await go(REP);
    const from = rel(); await page.getByTestId("report-card-investment-register").click(); const to = await waitUrlChange(from);
    await page.getByRole("heading", { level: 1 }).first().waitFor();
    await page.getByRole("combobox", { name: /Trust Account/ }).click();
    await page.getByRole("option", { name: new RegExp(S.account.name.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")) }).click();
    await page.getByRole("button", { name: "Run Report" }).click();
    await page.getByRole("button", { name: "Export CSV" }).waitFor({ state: "visible", timeout: 20000 });
    await page.waitForTimeout(800);
    const table = page.getByRole("table").first();
    const headers = (await table.getByRole("columnheader").allTextContents()).map((s) => s.trim());
    const rows = await table.getByRole("row").count();
    const file = path.join(L.RAW, `int-investment-register-run--${who}--${v}.png`);
    await page.screenshot({ path: file, fullPage: true });
    return { from, to, h1: await h1(), headers, rowCount: rows, lpffRate: await table.getByRole("columnheader", { name: "LPFF Rate", exact: true }).count(), file };
  });
  await ctx.close();
}

(async () => {
  const browser = await L.chromium.launch();
  for (const who of ["alice", "carol"]) {
    const st = await L.loginState(browser, who);
    await capture(browser, who, st);
    await interact(browser, st, who, "1440-light");
    if (who === "alice") await interact(browser, st, who, "1024-dark");
    console.log("captured", who);
  }
  // API cross-check: the list's rows vs the endpoint (the page slices a plain array, 20 a page)
  const t = await L.idpToken("alice");
  const recs = L.list((await L.apiGet(t, `/api/trust-accounts/${ID.accountId}/reconciliations`)).json);
  const stmts = L.list((await L.apiGet(t, `/api/trust-accounts/${ID.accountId}/bank-statements`)).json);
  const invs = (await L.apiGet(t, `/api/trust-accounts/${ID.accountId}/investments?page=0&size=100&sort=depositDate,desc`)).json;
  out.api = {
    reconciliations: { total: recs.length, order: recs.map((r) => `${r.periodEnd} ${r.status}${r.isBalanced ? " balanced" : ""}${r.monthlyListId ? " list" : ""}`) },
    openStatements: stmts.filter((s) => s.status !== "RECONCILED").map((s) => ({ id: s.id, fileName: s.fileName, status: s.status })),
    investments: { total: invs.page && invs.page.totalElements, rows: L.list(invs).map((i) => ({ id: i.id, client: i.clientName, basis: i.investmentBasis, status: i.status, balance: i.balance })) },
  };
  out.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(L.RAW, "capture.json"), JSON.stringify(out));
  console.log("pages", Object.values(out.pages).reduce((n, x) => n + Object.keys(x).length, 0), "console", cons.length, "interactions", JSON.stringify(out.interactions).slice(0, 3000));
})().catch((e) => { console.error("CAPTURE FAIL", e); process.exit(1); });
