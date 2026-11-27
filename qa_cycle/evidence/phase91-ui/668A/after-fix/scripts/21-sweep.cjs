// 668A OBS-after-fix sweep (read-only): Home /dashboard agenda (Alice + Carol, 1024/1440 × light/dark:
// 1 main#main-content, 1 h1, agenda testids, console) and the My Work "Available Tasks" collapse
// height over time (the replay's 400 ms sample moved by more than the R1 data delta), with the
// heights of the tasks panel and the This Week card. Re-uses ../../before/scripts/lib.cjs (unedited).
// Usage: PHASE=after-fix node 21-sweep.cjs → ../sweep.json + sheet-home-<who>.png
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");
const B = L.BASE;
const OUT = path.resolve(__dirname, "..");
const out = { at: new Date().toISOString(), home: {}, collapse: {}, console: [] };
const cons = out.console;
async function go(page, url) {
  await page.goto(url, { waitUntil: "load" });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.locator("#main-content h1").first().waitFor({ timeout: 20000 }).catch(() => {});
  await page.waitForTimeout(700);
}
(async () => {
  const browser = await L.chromium.launch();
  for (const who of ["alice", "carol"]) {
    const st = await L.loginState(browser, who);
    const files = [];
    for (const v of ["1024-light", "1024-dark", "1440-light", "1440-dark"]) {
      const [w, theme] = v.split("-");
      const ctx = await L.newCtx(browser, st, { width: +w, height: +w <= 1024 ? 768 : 900, theme, extra: L.EXTRA });
      const page = await ctx.newPage();
      L.watch(page, cons, { label: `sweep home ${who} ${v}` });
      await go(page, `${B}/dashboard`);
      const f = path.join(L.RAW, `sweep-home--${who}--${v}.png`);
      await page.screenshot({ path: f, fullPage: true });
      files.push([v, f]);
      out.home[`${who}/${v}`] = await page.evaluate(() => ({
        main: document.querySelectorAll("main#main-content").length, h1: [...document.querySelectorAll("#main-content h1")].map((h) => h.innerText.trim()),
        agenda: ["todays-diary", "time-progress-today", "next-deadline"].map((t) => `${t}=${document.querySelectorAll(`[data-testid="${t}"]`).length}`).join(" "),
        diaryText: (document.querySelector('[data-testid="todays-diary"]') || { innerText: "" }).innerText.replace(/\s+/g, " ").slice(0, 120),
        docOverflow: document.documentElement.scrollWidth - innerWidth,
      }));
      await ctx.close();
    }
    await L.sheet(browser, path.join(OUT, `sheet-home-${who}.png`), `668A OBS-after-fix — Home /dashboard — ${who}`, files, 2, 720);
    if (who === "alice") {
      const ctx = await L.newCtx(browser, st, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
      const page = await ctx.newPage();
      L.watch(page, cons, { label: "sweep collapse" });
      await go(page, `${B}/my-work?from=2026-11-23&to=2026-11-29`);
      const m = () => page.evaluate(() => {
        const card = [...document.querySelectorAll("#main-content [data-slot=card]")].find((c) => /This Week/.test(c.innerText));
        const tp = document.querySelector('[data-testid="tasks-panel"]');
        return { doc: document.documentElement.scrollHeight, tasksPanelH: tp && Math.round(tp.getBoundingClientRect().height), thisWeekCardH: card && Math.round(card.getBoundingClientRect().height) };
      });
      const C = out.collapse;
      C.open = await m();
      const toggle = page.locator("#main-content").getByRole("button", { name: /Available Tasks/ });
      await toggle.click();
      for (const t of [400, 1000, 2500]) { await page.waitForTimeout(t === 400 ? 400 : t === 1000 ? 600 : 1500); C[`collapsed@${t}ms`] = await m(); }
      await toggle.click(); await page.waitForTimeout(1500); C.reopened = await m();
      await ctx.close();
    }
  }
  await browser.close();
  fs.writeFileSync(path.join(OUT, "sweep.json"), JSON.stringify(out));
  console.log(JSON.stringify(out).slice(0, 3000));
})().catch((e) => { console.error("SWEEP FAIL", e); process.exit(1); });
