// 668A OBS-after-fix (read-only, CSS injection only): does the A-3 column change (Logged now shown in
// My Tasks at 1440 / 1024 dark) explain the collapsed-height delta vs OBS-after? 1440 light and
// 1024 dark, Alice: doc height + tasks-panel height open / collapsed, as shipped and with the
// Logged column (6th) hidden again. Usage: PHASE=after-fix node 23-collapse-proto.cjs → ../collapse-proto.json
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");
(async () => {
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const out = { at: new Date().toISOString(), runs: {}, console: [] };
  for (const [w, theme] of [[1440, "light"], [1024, "dark"]]) for (const variant of ["asShipped", "loggedHidden"]) {
    const ctx = await L.newCtx(browser, st, { width: w, height: w <= 1024 ? 768 : 900, theme, extra: L.EXTRA });
    const page = await ctx.newPage();
    L.watch(page, out.console, { label: `collapse-proto ${w}-${theme} ${variant}` });
    await page.goto(`${L.BASE}/my-work?from=2026-11-23&to=2026-11-29`, { waitUntil: "load" });
    await page.waitForLoadState("networkidle").catch(() => {});
    await page.waitForTimeout(1000);
    if (variant === "loggedHidden") { await page.addStyleTag({ content: "[data-testid=tasks-panel] table th:nth-child(6),[data-testid=tasks-panel] table td:nth-child(6){display:none!important}" }); await page.waitForTimeout(300); }
    const m = () => page.evaluate(() => { const tp = document.querySelector('[data-testid="tasks-panel"]'); const ew = document.querySelector("#extended-widgets"); const rows = [...tp.querySelectorAll("table")].map((t) => [...t.querySelectorAll("tbody tr")].map((r) => Math.round(r.getBoundingClientRect().height)).join(",")); return { doc: document.documentElement.scrollHeight, tasksPanelH: Math.round(tp.getBoundingClientRect().height), extendedH: ew && Math.round(ew.getBoundingClientRect().height), rowHeights: rows }; });
    const r = { open: await m() };
    await page.locator("#main-content").getByRole("button", { name: /Available Tasks/ }).click();
    await page.waitForTimeout(1000);
    r.collapsed = await m();
    out.runs[`${w}-${theme} ${variant}`] = r;
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(__dirname, "..", "collapse-proto.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
})().catch((e) => { console.error("FAIL", e); process.exit(1); });
