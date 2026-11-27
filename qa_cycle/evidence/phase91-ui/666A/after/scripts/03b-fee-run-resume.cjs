// 666A OBS step 3b: resume the OBS fee run. 03-fee-run.cjs (executed once; not edited) created the run
// through step 1 (POST /api/billing-runs at 00:18:59Z, id af572249-…) and captured steps 1 and 2, then
// stopped: it looked the run up by `notes`, which GET /api/billing-runs does not return. This is 03
// copied with three changes:
//   (1) the run is found by L.RUN period + createdBy = Alice;
//   (2) step 1 is re-shot on a SEPARATE fresh /new page (same values typed, "Next" NOT clicked — no
//       second run is created), so the step-1 probes exist in this run's JSON;
//   (3) the wizard is resumed at step 2 through its own entry point /invoices/billing-runs/new?
//       billingRunId=<id> (billing-run-wizard.tsx: initial step 2 when billingRunId is given), then
//       steps 2 → 5 exactly as 03 (generate on step 4 mount, inline sheet → Escape, "Approve All &
//       Continue", step 5 "Send All" → alertdialog → Cancel; nothing sent).
// Refuses to run unless exactly one OBS run exists and it is still PREVIEW.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");

const out = { at: new Date().toISOString(), steps: [], runId: null, api: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const settle = async (p, ms = 700) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(ms); };
const setTheme = (p, th) => p.evaluate((t) => { const d = document.documentElement; d.classList.remove(t === "dark" ? "light" : "dark"); d.classList.add(t); d.style.colorScheme = t; }, th);
const probe = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__fee = (${P.FEE.toString()})`); return p.evaluate(P.COMPACT); };

async function capture(p, step, caption) {
  const rec = { step, caption, at: new Date().toISOString(), variants: {} };
  for (const [w, h] of [[1440, 900], [1024, 768]])
    for (const th of ["light", "dark"]) {
      await p.setViewportSize({ width: w, height: h });
      await setTheme(p, th);
      await p.waitForTimeout(300);
      const v = `${w}-${th}`;
      lab.label = `fee-run|${step}|${v}`;
      if (th === "light" || w === 1440) { const m = await probe(p); rec.variants[v] = { h1: m.h1, hOverflow: m.hOverflow, docHeight: m.docHeight, steps: m.fee.steps, tables: m.fee.tables.map((x) => ({ headers: x.headers, bodyRows: x.bodyRows, tableWidth: x.tableWidth, scroller: x.scroller, cut: x.cols.filter((c) => c.cutPx).map((c) => `${c.h}=${c.cutPx}`), firstCellLines: x.firstCellLines })), lowContrast: m.lowContrast.slice(0, 8), mainText: m.bodyTextHead }; }
      const key = `w__${step}-${v}`;
      const f = path.join(L.RAW, `${key}.png`);
      await p.screenshot({ path: f, fullPage: true });
      tiles.push([key, f, `Alice ${w}×${h} ${th} — New fee run, ${caption}`]);
    }
  await p.setViewportSize({ width: 1440, height: 900 });
  await setTheme(p, "light");
  rec.full = await p.evaluate(P.FULL);
  out.steps.push(rec);
  console.log(step, JSON.stringify({ h1: rec.variants["1440-light"].h1, steps: (rec.variants["1440-light"].steps || []).map((s) => `${s.n}:${s.label}:${s.badgeContrast}`), tables: rec.variants["1440-light"].tables.map((t) => `${t.headers.join("|")} rows${t.bodyRows} ${t.scroller ? t.scroller.clientWidth + "/" + t.scroller.scrollWidth : ""}`), t1024: rec.variants["1024-light"].tables.map((t) => `${t.scroller ? t.scroller.clientWidth + "/" + t.scroller.scrollWidth : "-"} cut:${t.cut}`), hOv1024: rec.variants["1024-light"].hOverflow }));
}

(async () => {
  const a = await L.idpToken("alice");
  const aliceId = (await L.apiGet(a, "/api/me/capabilities")).json.memberId;
  const pre = L.list((await L.apiGet(a, "/api/billing-runs?size=100")).json).filter((r) => r.periodFrom === L.RUN.periodFrom && r.periodTo === L.RUN.periodTo && r.createdBy === aliceId);
  if (pre.length !== 1 || pre[0].status !== "PREVIEW") throw new Error(`expected exactly one OBS run in PREVIEW, found ${JSON.stringify(pre.map((r) => r.status))}`);
  const run = pre[0];
  out.runId = run.id;
  out.api.afterCreate = run;
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  // (2) step 1 on a separate page; Next is never clicked here.
  const p1 = await ctx.newPage();
  L.watch(p1, consoleSink, lab);
  await p1.goto(`${L.BASE}/invoices/billing-runs/new`, { waitUntil: "load" });
  await settle(p1);
  await p1.locator("#periodFrom").fill(L.RUN.periodFrom);
  await p1.locator("#periodTo").fill(L.RUN.periodTo);
  await p1.locator("#notes").fill(L.RUN.notes);
  await capture(p1, "1-configure", `step 1 "Configure" filled (Period ${L.RUN.periodFrom} – ${L.RUN.periodTo}, Notes "${L.RUN.notes}"; there is no Name field) — re-shot, Next not clicked`);
  // Step 1 "Next" with an empty period: the validation list (fresh page, nothing typed).
  await p1.goto(`${L.BASE}/invoices/billing-runs/new`, { waitUntil: "load" });
  await settle(p1);
  await p1.getByRole("button", { name: "Next", exact: true }).click();
  await p1.waitForTimeout(500);
  out.validation = (await p1.locator("#main-content ul.list-disc").innerText().catch(() => "")).replace(/\s+/g, " ");
  { const f = path.join(L.RAW, "w__1b-configure-validation-1440-light.png"); await p1.screenshot({ path: f }); tiles.push(["w__1b-configure-validation-1440-light", f, `Alice 1440×900 light — step 1 "Next" with nothing typed: client validation "${out.validation}" (no run created)`]); }
  await p1.close();
  // (3) resume at step 2.
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  await p.goto(`${L.BASE}/invoices/billing-runs/new?billingRunId=${run.id}`, { waitUntil: "load" });
  await p.getByRole("heading", { level: 2, name: "Select Clients" }).waitFor({ timeout: 30000 });
  await settle(p);
  // Step 2
  await capture(p, "2-select-clients", `step 2 "Select Clients" (preview loaded; all selected by default)`);
  out.api.items2 = (await L.apiGet(a, `/api/billing-runs/${run.id}/items`)).json;
  await p.getByRole("button", { name: "Next", exact: true }).click();
  await p.getByRole("heading", { level: 2, name: "Review & Cherry-Pick" }).waitFor({ timeout: 30000 });
  await settle(p);
  // Step 3: expand the first client
  const firstSection = p.locator("#main-content button[aria-expanded]").first();
  await firstSection.click();
  await settle(p, 1500);
  await capture(p, "3-cherry-pick", `step 3 "Review & Cherry-Pick" with the first client expanded (its unbilled time / disbursement tables)`);
  await p.getByRole("button", { name: "Next", exact: true }).click();
  // Step 4: generation runs on mount
  await p.getByRole("button", { name: /Approve All & Continue/ }).waitFor({ timeout: 60000 });
  await p.locator("#main-content table").first().waitFor({ timeout: 60000 });
  await settle(p, 1500);
  out.api.afterGenerate = (await L.apiGet(a, `/api/billing-runs/${run.id}`)).json;
  out.api.items4 = (await L.apiGet(a, `/api/billing-runs/${run.id}/items`)).json;
  await capture(p, "4-review-drafts", `step 4 "Review Drafts" (draft fee notes generated on mount)`);
  // Inline editor sheet: open on the first row, shoot, Escape.
  await p.locator("#main-content table tbody tr").first().click();
  const sheet = p.getByRole("dialog");
  if (await sheet.waitFor({ timeout: 8000 }).then(() => true, () => false)) {
    await p.waitForTimeout(600);
    const f = path.join(L.RAW, "w__4b-review-drafts-sheet-1440-light.png");
    await p.screenshot({ path: f });
    tiles.push(["w__4b-review-drafts-sheet-1440-light", f, "Alice 1440×900 light — step 4: the first row's inline editor sheet (opened, then Escape — nothing saved)"]);
    out.sheet = { title: (await sheet.locator("h2").first().innerText().catch(() => "")).trim(), text: (await sheet.innerText()).replace(/\s+/g, " ").slice(0, 400) };
    await p.keyboard.press("Escape");
    await sheet.waitFor({ state: "hidden", timeout: 8000 }).catch(() => {});
  }
  await p.getByRole("button", { name: /Approve All & Continue/ }).click();
  const reached5 = await p.getByRole("button", { name: "Send All", exact: true }).waitFor({ timeout: 60000 }).then(() => true, () => false);
  await settle(p, 1200);
  if (!reached5) {
    out.approveError = (await p.locator("#main-content").innerText()).replace(/\s+/g, " ").slice(0, 600);
    await capture(p, "4c-approve-failed", "step 4 after \"Approve All & Continue\" did not reach step 5");
  }
  out.api.afterApprove = (await L.apiGet(a, `/api/billing-runs/${run.id}`)).json;
  out.api.items5 = (await L.apiGet(a, `/api/billing-runs/${run.id}/items`)).json;
  // Step 5
  if (reached5) {
  await capture(p, "5-send", `step 5 "Send" (after "Approve All & Continue"; Send All NOT clicked through)`);
  await p.getByRole("button", { name: "Send All", exact: true }).click();
  const ad = p.getByRole("alertdialog");
  await ad.waitFor({ timeout: 8000 });
  await p.waitForTimeout(500);
  out.confirm = (await ad.innerText()).replace(/\s+/g, " ");
  const f = path.join(L.RAW, "w__5b-send-confirm-1440-light.png");
  await p.screenshot({ path: f });
  tiles.push(["w__5b-send-confirm-1440-light", f, `Alice 1440×900 light — step 5: "Send All" → alertdialog "${out.confirm.slice(0, 120)}" → Cancel (nothing sent)`]);
  await ad.getByRole("button", { name: "Cancel", exact: true }).click();
  await ad.waitFor({ state: "hidden", timeout: 8000 });
  }
  out.api.final = (await L.apiGet(a, `/api/billing-runs/${run.id}`)).json;
  out.api.finalItems = (await L.apiGet(a, `/api/billing-runs/${run.id}/items`)).json;
  out.api.invoices = [];
  for (const it of out.api.finalItems || []) if (it.invoiceId) { const i = (await L.apiGet(a, `/api/invoices/${it.invoiceId}`)).json; out.api.invoices.push({ id: i.id, invoiceNumber: i.invoiceNumber, status: i.status, clientName: i.clientName, total: i.total }); }
  await ctx.close();
  await browser.close();
  out.console = consoleSink;
  out.tiles = tiles;
  fs.writeFileSync(path.join(L.RAW, "fee-run.json"), JSON.stringify(out));
  console.log("run", out.runId, JSON.stringify({ name: out.api.final.name, status: out.api.final.status, totals: [out.api.final.totalClients, out.api.final.totalInvoices, out.api.final.totalSent, out.api.final.totalFailed, out.api.final.totalAmount], items: (out.api.finalItems || []).map((i) => `${i.clientName}:${i.status}:${i.invoiceId}`), invoices: out.api.invoices, sheet: out.sheet, confirm: out.confirm }), "console", consoleSink.length, JSON.stringify(consoleSink.slice(0, 5)));
})();
