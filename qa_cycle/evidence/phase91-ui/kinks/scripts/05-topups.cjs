// 662A step 5: end-of-walk UI top-ups (orchestrator ruling D-14: allowed through the UI only, never SQL, for the
// empty detail pages). Runs only after S1–S8 are complete, so no earlier capture saw this data.
//   1. /compliance/requests/[id]: "New Request" dialog → first client, Access Request, a description → "Create Request".
//   2. /invoices/billing-runs/[id]: /invoices/billing-runs/new configure step (period 2026-11-01..2026-11-30) → "Next"
//      (creates the fee run; the wizard is not continued, so no fee notes are generated).
//   (K-10's 150-char deposit is NOT done: D-14 does not cover it; the reconciliation list stays skipped.)
// Then both detail pages are captured in S1 for all 8 combos (1024/1440 × light/dark × Alice/Carol) + dialog harvest.
// Usage (worktree root): BUDGET_S=480 CONC=4 NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/kinks/scripts/05-topups.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

const TF = path.join(L.RAW, "topups.json");
const T = L.readJson(TF, {});
const save = () => fs.writeFileSync(TF, JSON.stringify(T));
const UUIDRE = new RegExp(L.UUID);

(async () => {
  const browser = await L.chromium.launch();
  const token = await L.idpToken("alice");
  // ---- 1. data subject request
  if (!T.dsr) {
    const ctx = await L.ctxFor(browser, "alice", 1440, "light");
    const page = await ctx.newPage();
    const before = L.list(await L.api(token, "/api/data-requests")).length;
    await page.goto(L.BASE + "/compliance/requests", { waitUntil: "load" });
    await page.locator("#main-content h1").first().waitFor({ timeout: 20000 });
    await page.waitForTimeout(1500);
    await page.locator("#main-content").getByRole("button", { name: "New Request" }).click();
    const dlg = page.getByRole("dialog");
    await dlg.waitFor();
    await dlg.getByRole("button", { name: /Select a client/ }).waitFor({ timeout: 20000 });
    await dlg.getByRole("button", { name: /Select a client/ }).click();
    await dlg.locator('[cmdk-item], [role="option"]').first().click();
    await dlg.locator("#request-type").selectOption("ACCESS");
    await dlg.locator("#description").fill("662A top-up: a data subject request so the detail page can be walked (D-14).");
    const client = (await dlg.getByRole("button").first().innerText()).trim();
    await dlg.getByRole("button", { name: "Create Request" }).click();
    await dlg.waitFor({ state: "hidden", timeout: 30000 });
    await page.waitForTimeout(1000);
    const after = L.list(await L.api(token, "/api/data-requests"));
    const created = after.find((r) => /662A top-up/.test(r.description || "")) || after[0];
    T.dsr = { id: created && created.id, client, countBefore: before, countAfter: after.length, at: new Date().toISOString() };
    save();
    console.log("dsr", JSON.stringify(T.dsr));
    await ctx.close();
  }
  // ---- 2. fee run (configure step only)
  if (!T.billingRun) {
    const ctx = await L.ctxFor(browser, "alice", 1440, "light");
    const page = await ctx.newPage();
    await page.goto(L.BASE + "/invoices/billing-runs/new", { waitUntil: "load" });
    await page.locator("#periodFrom").waitFor({ timeout: 20000 });
    await page.waitForTimeout(1000);
    await page.locator("#periodFrom").fill("2026-11-01");
    await page.locator("#periodTo").fill("2026-11-30");
    await page.locator("#notes").fill("662A top-up (D-14): fee run for the detail-page walk");
    await page.locator("#main-content").getByRole("button", { name: "Next", exact: true }).click();
    await page.waitForTimeout(4000);
    const runs = L.list(await L.api(token, "/api/billing-runs"));
    const run = runs.find((r) => /662A top-up/.test(r.notes || "")) || runs[0];
    T.billingRun = { id: run && run.id, status: run && run.status, urlAfterNext: page.url().replace(L.APP, ""), runs: runs.length, at: new Date().toISOString() };
    save();
    console.log("billingRun", JSON.stringify(T.billingRun));
    await ctx.close();
  }
  // ---- captures: both detail pages, 8 combos, + dialog harvest
  const targets = [
    ["/compliance/requests/[id]", T.dsr && T.dsr.id],
    ["/invoices/billing-runs/[id]", T.billingRun && T.billingRun.id],
  ].filter(([, id]) => id && UUIDRE.test(id));
  const combos = [];
  for (const who of ["alice", "carol"]) for (const theme of ["light", "dark"]) for (const vp of [1024, 1440]) combos.push({ who, theme, vp });
  let left = 0;
  await L.pool(combos, +(process.env.CONC || 4), async ({ who, theme, vp }) => {
    const ctx = await L.ctxFor(browser, who, vp, theme);
    const page = await ctx.newPage();
    const sink = L.sinkFor(page);
    for (const [route, id] of targets) {
      if (L.overBudget()) {
        left++;
        continue;
      }
      const rec = await L.capture(page, sink, { set: "S1", route, url: route.replace(/\[id\]/, id), who, vp, theme, topup: true });
      if (rec.skipped) continue;
      if (!rec.skip) await L.harvestDialogs(page, sink, { pageKey: L.slugOf(route), foundOn: route, who, vp, theme, group: L.groupOf(route) });
    }
    await ctx.close();
  });
  await browser.close();
  console.log(left ? `REMAINING ${left}` : `ALL DONE 05-topups ${JSON.stringify(T)}`);
})().catch((e) => {
  console.error("FATAL", e);
  process.exit(1);
});
