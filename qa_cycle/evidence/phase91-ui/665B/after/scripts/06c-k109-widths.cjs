// 665B OBS-after step 6c (NEW, after-only, read-only; Alice light): the Processing Register header's
// title / description / action-column widths at 390, 1024, 1280, 1440 (K-109's second half: the
// register said the description squeezes to ≈ 90 px at 1024 before).
const path = require("path");
const fs = require("fs");
const L = require("./lib.cjs");
(async () => {
  const browser = await L.chromium.launch();
  const S = await L.loginState(browser, "alice");
  const R = {};
  for (const [w, h] of [[390, 844], [1024, 768], [1280, 800], [1440, 900]]) {
    const ctx = await L.newCtx(browser, S, { width: w, height: h, theme: "light", extra: L.EXTRA });
    const p = await ctx.newPage();
    await p.goto(`${L.BASE}/settings/firm?tab=data-protection`, { waitUntil: "load" });
    await p.waitForLoadState("networkidle").catch(() => {});
    R[w] = await p.evaluate(() => {
      const s = [...document.querySelectorAll('[data-slot="settings-section"]')].find((x) => (x.querySelector("h2") || {}).innerText === "Processing Register");
      const wd = (e) => (e ? Math.round(e.getBoundingClientRect().width) : null);
      const d = s.querySelector('[data-slot="card-description"]');
      return { header: wd(s.querySelector('[data-slot="card-header"]')), title: wd(s.querySelector("h2")), description: wd(d), descriptionLines: d ? Math.round(d.getBoundingClientRect().height / parseFloat(getComputedStyle(d).lineHeight)) : null, actionColumn: wd(s.querySelector('[data-slot="card-action"]')) };
    });
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(L.OUT, "k109-widths.json"), JSON.stringify(R) + "\n");
  console.log(JSON.stringify(R));
})();
