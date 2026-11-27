// 668A OBS-after-fix (read-only): which block sets the My Work page height with "Available Tasks"
// collapsed (1440 light, Alice) — boxes of every block-level descendant of #main-content down to
// depth 4 with height ≥ 120 px, open vs collapsed. Usage: PHASE=after-fix node 22-collapse-layout.cjs → ../collapse-layout.json
const path = require("path");
const fs = require("fs");
const L = require("../../before/scripts/lib.cjs");
(async () => {
  const browser = await L.chromium.launch();
  const st = await L.loginState(browser, "alice");
  const ctx = await L.newCtx(browser, st, { width: 1440, height: 900, theme: "light", extra: L.EXTRA });
  const page = await ctx.newPage();
  const cons = [];
  L.watch(page, cons, { label: "collapse-layout" });
  await page.goto(`${L.BASE}/my-work?from=2026-11-23&to=2026-11-29`, { waitUntil: "load" });
  await page.waitForLoadState("networkidle").catch(() => {});
  await page.waitForTimeout(1000);
  const boxes = () => page.evaluate(() => {
    const res = [];
    const walk = (el, d, p) => { if (d > 4) return; [...el.children].forEach((c, i) => { const b = c.getBoundingClientRect(); if (b.height >= 120) res.push(`${p}${i} ${c.tagName.toLowerCase()}${c.dataset.testid ? "#" + c.dataset.testid : ""} x${Math.round(b.x)} y${Math.round(b.y + scrollY)} w${Math.round(b.width)} h${Math.round(b.height)} bottom${Math.round(b.bottom + scrollY)} "${c.innerText.replace(/\s+/g, " ").slice(0, 40)}"`); walk(c, d + 1, `${p}${i}.`); }); };
    walk(document.querySelector("#main-content"), 1, "");
    return { doc: document.documentElement.scrollHeight, boxes: res };
  });
  const out = { at: new Date().toISOString(), open: await boxes() };
  await page.locator("#main-content").getByRole("button", { name: /Available Tasks/ }).click();
  await page.waitForTimeout(1500);
  out.collapsed = await boxes();
  out.console = cons;
  await browser.close();
  fs.writeFileSync(path.join(__dirname, "..", "collapse-layout.json"), JSON.stringify(out));
  console.log(JSON.stringify(out, null, 1));
})().catch((e) => { console.error("FAIL", e); process.exit(1); });
