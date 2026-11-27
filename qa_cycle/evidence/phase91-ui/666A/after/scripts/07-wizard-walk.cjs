// 666A OBS-after step 7 (NEW): walk the New fee run wizard on the kit step tables, through the UI as
// Alice, and leave no run behind. Steps 2-5 need a run in PREVIEW and the only OBS run is COMPLETED
// (POST /preview refuses a non-PREVIEW run, BillingRunSelectionService.java:84), so a TEMPORARY run is
// made with a period no other run uses (W_PERIOD) and cancelled through the detail page's "Cancel Run"
// (a PREVIEW run is deleted on cancel, BillingRunLifecycleService.java:113-119). Steps 4-5 are NOT
// entered: step 4 generates draft fee notes on mount, which cancelling would not remove.
//   1 Configure: Next with nothing typed (validation), then W_PERIOD → Next (creates the run)
//   2 Select Clients: shoot 1440/1024 × light/dark (theme class swap, as 03b); toggle the row
//     checkbox off/on and "Select all clients" off/on, recording aria-checked + the summary bar
//   3 Review & Cherry-Pick: expand the first client; toggle the first "Include …" checkbox off/on; shoot
//   4 Fee runs list (PREVIEW banner) + the run's detail (PREVIEW) at 1440 light, 1024 light
//   5 detail → "Cancel Run" → alertdialog (shot) → "Cancel Run"; GET confirms the run is gone
// The finally block re-tries the UI cancel if the run still exists. Writes ../wizard-walk.json +
// ../sheet-wizard-walk.png.
// Usage: PHASE=after node 07-wizard-walk.cjs
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
const P = require("./probes.cjs");
const A = require("./probes-after.cjs");

const W_PERIOD = { periodFrom: "2026-11-02", periodTo: "2026-11-27" };
const out = { at: new Date().toISOString(), period: W_PERIOD, steps: [], toggles: [], times: {} };
const tiles = [];
const consoleSink = [];
const lab = { label: "" };
const settle = async (p, ms = 700) => { await p.waitForLoadState("networkidle").catch(() => {}); await p.waitForTimeout(ms); };
const setTheme = (p, th) => p.evaluate((t) => { const d = document.documentElement; d.classList.remove(t === "dark" ? "light" : "dark"); d.classList.add(t); d.style.colorScheme = t; }, th);
const after = async (p) => { await p.evaluate(P.HELPERS, ""); await p.evaluate(P.HELPERS2); await p.evaluate(`window.__after = (${A.AFTER.toString()})`); return p.evaluate(() => window.__after()); };
const shot = async (p, key, caption, opt = { fullPage: true }) => { const f = path.join(L.RAW, `${key}.png`); await p.screenshot({ path: f, ...opt }); tiles.push([caption, f]); };
const find = async (t, aliceId) => L.list((await L.apiGet(t, "/api/billing-runs?size=100")).json).filter((r) => r.periodFrom === W_PERIOD.periodFrom && r.periodTo === W_PERIOD.periodTo && r.createdBy === aliceId);
const topCrumb = (p) => p.evaluate(() => { const h = document.querySelector("header"); return h ? h.innerText.replace(/\s+/g, " ").trim().slice(0, 160) : null; });

async function capture(p, step, caption) {
  const rec = { step, caption, variants: {} };
  for (const [w, h] of [[1440, 900], [1024, 768]])
    for (const th of ["light", "dark"]) {
      await p.setViewportSize({ width: w, height: h });
      await setTheme(p, th);
      await p.waitForTimeout(300);
      const m = await after(p);
      rec.variants[`${w}-${th}`] = { h1: m.header.h1Text, h1Count: m.header.h1Count, steps: m.steps.map((s) => ({ n: s.n, label: s.label, badge: s.badge.ratio, fg: s.badge.fg, bg: s.badge.bg, labelRatio: s.label_.ratio, labelLines: s.labelLines })), tables: m.tables.map((x) => ({ slots: x.slots, headers: x.headersVisible, headLines: x.headLines, tableWidth: x.tableWidth, scroller: x.scroller && { clientWidth: x.scroller.clientWidth, scrollWidth: x.scroller.scrollWidth }, rows: x.rowHeights, firstCellLines: x.firstCellLines, checkboxes: x.checkboxes, cells: x.cellTexts.slice(0, 4), clipped: x.clippedCells, cursor: x.rowCursor })), empties: m.empties, lowContrast: m.lowContrast.slice(0, 6).map((x) => `${x.text} ${x.ratio}`), dark: th === "dark" ? m.dark : undefined };
      await shot(p, `ww__${step}-${w}-${th}`, `Alice ${w}×${h} ${th} — ${caption}`);
    }
  await p.setViewportSize({ width: 1440, height: 900 });
  await setTheme(p, "light");
  out.steps.push(rec);
  const v = rec.variants["1440-light"], d = rec.variants["1440-dark"];
  console.log(step, JSON.stringify({ steps: v.steps.map((s) => `${s.n}:${s.badge}/${d.steps[+s.n - 1] ? d.steps[+s.n - 1].badge : "-"}`), tables: v.tables.map((x) => `${x.headers.join("|")} ${x.scroller ? x.scroller.clientWidth + "/" + x.scroller.scrollWidth : ""} slots ${x.slots.join(">")}`), t1024: rec.variants["1024-light"].tables.map((x) => `${x.scroller ? x.scroller.clientWidth + "/" + x.scroller.scrollWidth : ""}`) }));
}
const cbState = (p) => p.evaluate(() => [...document.querySelectorAll('#main-content [role="checkbox"]')].map((c) => `${c.getAttribute("aria-label")}=${c.getAttribute("aria-checked") || c.dataset.state}`));
const summary = (p) => p.evaluate(() => { const e = [...document.querySelectorAll("#main-content p, #main-content span, #main-content div")].find((x) => /selected|included/.test(x.textContent) && x.children.length < 3 && x.textContent.length < 120); return e ? e.textContent.replace(/\s+/g, " ").trim() : null; });

(async () => {
  const t = await L.idpToken("alice");
  const aliceId = (await L.apiGet(t, "/api/me/capabilities")).json.memberId;
  if ((await find(t, aliceId)).length) throw new Error("a run with the walk period already exists — not creating another");
  out.unbilledInPeriod = await L.unbilled(t, { ...W_PERIOD, currency: "ZAR" });
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, S, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const p = await ctx.newPage();
  L.watch(p, consoleSink, lab);
  let runId = null;
  try {
    lab.label = "07|configure";
    await p.goto(`${L.BASE}/invoices/billing-runs/new`, { waitUntil: "load" });
    await settle(p);
    out.breadcrumbNew = await topCrumb(p);
    await p.getByRole("button", { name: "Next", exact: true }).click();
    await p.waitForTimeout(500);
    out.validation = (await p.locator("#main-content ul.list-disc").innerText().catch(() => "")).replace(/\s+/g, " ");
    await shot(p, "ww__1b-validation-1440-light", `Alice 1440 light — step 1 Next with nothing typed: "${out.validation}"`, {});
    await p.locator("#periodFrom").fill(W_PERIOD.periodFrom);
    await p.locator("#periodTo").fill(W_PERIOD.periodTo);
    await capture(p, "1-configure", `step 1 "Configure fee run" filled (${W_PERIOD.periodFrom} – ${W_PERIOD.periodTo}), before Next`);
    out.times.create = new Date().toISOString();
    await p.getByRole("button", { name: "Next", exact: true }).click();
    await p.getByRole("heading", { level: 2, name: "Select Clients" }).waitFor({ timeout: 30000 });
    await settle(p, 1200);
    const made = await find(t, aliceId);
    runId = made[0] && made[0].id;
    out.tempRun = made.map((r) => ({ id: r.id, status: r.status, name: r.name }));
    lab.label = "07|select-clients";
    await capture(p, "2-select-clients", `step 2 "Select Clients" on the temporary run (all selected by default)`);
    const rowCb = p.locator('#main-content [role="checkbox"]').nth(1);
    const allCb = p.getByRole("checkbox", { name: "Select all clients" });
    for (const [what, loc] of [["row off", rowCb], ["row on", rowCb], ["select-all off", allCb], ["select-all on", allCb]]) {
      await loc.click();
      await p.waitForTimeout(300);
      out.toggles.push({ step: 2, action: what, checkboxes: await cbState(p), summary: await summary(p), nextDisabled: await p.getByRole("button", { name: "Next", exact: true }).isDisabled() });
      if (what === "select-all off") await shot(p, "ww__2b-none-selected-1440-light", `Alice 1440 light — step 2 after "Select all clients" unticked: ${JSON.stringify(out.toggles[out.toggles.length - 1].checkboxes)}`, {});
    }
    await p.getByRole("button", { name: "Next", exact: true }).click();
    await p.getByRole("heading", { level: 2, name: "Review & Cherry-Pick" }).waitFor({ timeout: 30000 });
    await settle(p);
    lab.label = "07|cherry-pick";
    await p.locator("#main-content button[aria-expanded]").first().click();
    await settle(p, 1500);
    const inc = p.locator('#main-content [role="checkbox"][aria-label^="Include"]').first();
    out.cherryCheckboxes = await cbState(p);
    if (await inc.count()) {
      for (const what of ["include off", "include on"]) {
        await inc.click();
        await settle(p, 800);
        out.toggles.push({ step: 3, action: what, checkboxes: await cbState(p), summary: await summary(p) });
      }
    }
    await capture(p, "3-cherry-pick", `step 3 "Review & Cherry-Pick", first client expanded (Include toggled off and back on)`);
    // Fee runs list (banner) + detail in PREVIEW
    for (const [w, h] of [[1440, 900], [1024, 768]]) {
      await p.setViewportSize({ width: w, height: h });
      lab.label = `07|list-preview|${w}`;
      await p.goto(`${L.BASE}/invoices/billing-runs`, { waitUntil: "load" });
      await settle(p);
      const m = await after(p);
      out[`listPreview${w}`] = { banner: await p.evaluate(() => { const b = [...document.querySelectorAll("#main-content div")].find((d) => /in progress/.test(d.textContent) && d.textContent.length < 300 && d.querySelector("a")); return b ? b.innerText.replace(/\s+/g, " ") : null; }), table: m.tables.map((x) => ({ rows: x.rowHeights, links: x.links.map((l) => `${l.text} (${l.lines} lines)`), sc: x.scroller && `${x.scroller.clientWidth}/${x.scroller.scrollWidth}` })) };
      await shot(p, `ww__4-list-preview-${w}-light`, `Alice ${w} light — Fee runs list with the temporary run in PREVIEW: banner "${out[`listPreview${w}`].banner}"`);
      lab.label = `07|detail-preview|${w}`;
      await p.goto(`${L.BASE}/invoices/billing-runs/${runId}`, { waitUntil: "load" });
      await settle(p);
      const d = await after(p);
      out[`detailPreview${w}`] = { h1: d.header.h1Text, h1Count: d.header.h1Count, crumb: await topCrumb(p), desc: d.header.description, toolbar: d.header.toolbar, actions: d.header.actions.map((x) => x.text), cards: d.stat.map((s) => `${s.label}=${s.value} spill${s.valueSpillPx}`), grid: d.statGrid && `${d.statGrid.cols}c ${d.statGrid.width}`, items: d.tables.map((x) => ({ headers: x.headersVisible, rows: x.cellTexts, sc: x.scroller && `${x.scroller.clientWidth}/${x.scroller.scrollWidth}` })), empties: d.empties };
      await shot(p, `ww__5-detail-preview-${w}-light`, `Alice ${w} light — the temporary run's detail (PREVIEW): h1 "${d.header.h1Text}"`);
    }
    await p.setViewportSize({ width: 1440, height: 900 });
  } finally {
    const left = runId ? (await find(t, aliceId)).filter((r) => r.id === runId) : await find(t, aliceId);
    for (const r of left) {
      lab.label = "07|cancel";
      await p.goto(`${L.BASE}/invoices/billing-runs/${r.id}`, { waitUntil: "load" });
      await settle(p);
      await p.locator("#main-content").getByRole("button", { name: "Cancel Run", exact: true }).click();
      const ad = p.getByRole("alertdialog");
      await ad.waitFor({ timeout: 8000 });
      await p.waitForTimeout(400);
      out.cancelDialog = (await ad.innerText()).replace(/\s+/g, " ");
      await shot(p, "ww__6-cancel-dialog-1440-light", `Alice 1440 light — "Cancel Run" alertdialog: "${out.cancelDialog}"`, {});
      await ad.getByRole("button", { name: "Cancel Run", exact: true }).click();
      await ad.waitFor({ state: "hidden", timeout: 15000 }).catch(() => {});
      await settle(p, 1500);
      out.times.cancel = new Date().toISOString();
      out.afterCancelUrl = p.url().replace(L.BASE, "").replace(/[0-9a-f-]{36}/g, "{id}");
      out.afterCancelText = (await p.locator("#main-content").innerText().catch(() => "")).replace(/\s+/g, " ").slice(0, 200);
      await shot(p, "ww__7-after-cancel-1440-light", `Alice 1440 light — after confirming Cancel Run: ${out.afterCancelUrl}`, {});
    }
    out.remaining = (await find(t, aliceId)).map((r) => `${r.id}:${r.status}`);
    out.runsAfter = L.list((await L.apiGet(t, "/api/billing-runs?size=100")).json).map((r) => `${r.id}:${r.status}`);
    out.console = consoleSink;
    fs.writeFileSync(path.join(L.OUT, "wizard-walk.json"), JSON.stringify(out) + "\n");
    await L.sheet(browser, path.join(L.OUT, "sheet-wizard-walk.png"), `666A after — New fee run wizard walked on a temporary run (${W_PERIOD.periodFrom} – ${W_PERIOD.periodTo}), cancelled at the end; steps 1-3 at 1440/1024 × light/dark, the list banner and detail in PREVIEW, the cancel dialog`, tiles, 4, 440);
    await browser.close();
    console.log(JSON.stringify({ temp: out.tempRun, toggles: out.toggles.map((x) => `${x.step}:${x.action}:${x.checkboxes.join(",")}:${x.summary}:${x.nextDisabled}`), validation: out.validation, cancel: out.cancelDialog, remaining: out.remaining, runsAfter: out.runsAfter, times: out.times, list: out.listPreview1440, detail1024: out.detailPreview1024, crumb: out.breadcrumbNew, console: consoleSink.length }));
  }
})();
