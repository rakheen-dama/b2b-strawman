// 665C OBS-after step 7b (after-only, read-only): the row Delete / Deactivate icon colour in dark and
// light. The eyeball of sheet-templates-alice.png (1440 dark) read the trash icons as grey; this
// records the computed colour of every button whose sr-only name starts with Delete / Deactivate on
// Templates / Checklists / Request templates (Alice, 1440), and a 3× crop of the Templates actions.
// Before, the class was `text-red-600 dark:text-red-400`; after, `text-destructive`.
// Writes ../destructive-probe.json (single line) + crop-templates-actions-dark-3x.png.
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");

(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const out = { at: new Date().toISOString(), probes: {} };
  for (const theme of ["light", "dark"]) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 3, storageState: S, ...L.EXTRA });
    await ctx.addCookies([{ name: "sidebar_state", value: "true", domain: "localhost", path: "/" }]);
    await ctx.addInitScript(L.INIT, theme);
    const p = await ctx.newPage();
    for (const t of ["templates", "checklists", "request-templates"]) {
      await p.goto(`${L.BASE}/settings/matters?tab=${t}`, { waitUntil: "load" });
      await p.waitForLoadState("networkidle").catch(() => {});
      await p.waitForTimeout(300);
      out.probes[`${t}|${theme}`] = await p.evaluate(() => [...document.querySelectorAll("#main-content button")].filter((b) => /^(Delete|Deactivate|Edit|Duplicate|Clone|View) /.test((b.querySelector(".sr-only") || {}).textContent || "")).slice(0, 4).map((b) => ({ name: b.querySelector(".sr-only").textContent.trim().split(" ")[0], color: getComputedStyle(b).color, svgStroke: b.querySelector("svg") ? getComputedStyle(b.querySelector("svg")).stroke : null, cls: b.className.slice(0, 80) })));
      if (t === "templates" && theme === "dark") {
        const row = p.locator("#main-content tbody tr").first();
        const box = await row.boundingBox();
        await p.screenshot({ path: path.join(L.OUT, "crop-templates-actions-dark-3x.png"), clip: { x: box.x + box.width - 160, y: box.y, width: 160, height: box.height } });
      }
    }
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "destructive-probe.json"), JSON.stringify(out) + "\n");
  for (const [k, v] of Object.entries(out.probes)) console.log(k, JSON.stringify(v.map((x) => `${x.name}:${x.color}`)));
})();
