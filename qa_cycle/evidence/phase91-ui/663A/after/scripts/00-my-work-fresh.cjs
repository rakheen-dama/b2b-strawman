// 663A OBS-after step 0 (orchestrator): on the FRESH stack built from the slice head, before any
// suite or data creation, measure the /my-work page-level "no tasks" EmptyState (my-work/page.tsx:200,
// now variant="compact"; F-663A-2 / D-21) at 1024x768 and 1440x900 x light/dark, Alice and Carol.
// Read-only (page views only). Raw tiles -> .tmp/663A-after-raw/fresh; JSON -> ../my-work-fresh.json.
// Usage (worktree root): NODE_OPTIONS="" node qa_cycle/evidence/phase91-ui/663A/after/scripts/00-my-work-fresh.cjs
const path = require("path");
const fs = require("fs");
const C = require("../../../661B/walk/scripts/common.cjs");
const RAW = path.join(C.W, ".tmp/663A-after-raw/fresh");
fs.mkdirSync(RAW, { recursive: true });
const OUT = path.join(__dirname, "..", "my-work-fresh.json");
const VP = { 1024: { width: 1024, height: 768 }, 1440: { width: 1440, height: 900 } };
(async () => {
  const browser = await C.chromium.launch();
  const rows = [];
  for (const who of ["alice", "carol"]) {
    const st = await C.loginState(browser, who);
    for (const w of [1024, 1440]) for (const theme of ["light", "dark"]) {
      const ctx = await C.newCtx(browser, st, { ...VP[w], theme });
      const p = await ctx.newPage();
      const errs = [];
      p.on("console", (m) => { if (m.type() === "error" || /hydrat/i.test(m.text())) errs.push(m.text().slice(0, 300)); });
      await p.goto(`${C.APP}/org/${C.ORG}/my-work`, { waitUntil: "load" });
      await p.locator("#main-content h1").first().waitFor({ timeout: 30000 });
      await p.waitForTimeout(2500);
      const boxes = await p.evaluate(() => [...document.querySelectorAll('#main-content [data-slot="empty-state"]')].map((el) => {
        const r = el.getBoundingClientRect();
        const title = el.querySelector("h2,h3,p");
        return { variant: el.getAttribute("data-variant"), h: Math.round(r.height * 10) / 10, w: Math.round(r.width), titleTag: title && title.tagName, title: title && title.textContent.trim().slice(0, 80) };
      }));
      const file = path.join(RAW, `${who}-${w}-${theme}.png`);
      await p.screenshot({ path: file, fullPage: true });
      rows.push({ who, w, theme, boxes, consoleErrors: errs });
      await ctx.close();
    }
  }
  fs.writeFileSync(OUT, JSON.stringify(rows));
  const tiles = rows.map((r) => [`${r.who} ${r.w} ${r.theme}`, path.join(RAW, `${r.who}-${r.w}-${r.theme}.png`)]);
  await C.sheet(browser, path.join(__dirname, "..", "sheet-my-work-fresh.png"), "663A after — /my-work no-tasks empty (fresh stack) — rows: alice, carol; cols 1024/1440 x light/dark", tiles, 4, 720);
  await browser.close();
  console.log(JSON.stringify(rows));
})().catch((e) => { console.error(e); process.exit(1); });
