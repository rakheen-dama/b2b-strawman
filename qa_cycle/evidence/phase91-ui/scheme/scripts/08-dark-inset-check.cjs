// found.md F-661A-3 evidence: candidate A's `variant="inset"` is a component prop, not a token, so
// the inset card (SidebarInset m-2 / rounded-xl / shadow-sm) also appears in DARK mode — unlike every
// token of the candidate blocks, which are :not(.dark)-scoped. Shoots Home at 1440×900, rail
// expanded, theme dark (localStorage kazi-theme=dark, ADR-371), with ?scheme=today and ?scheme=a1,
// and probes the shell geometry. Output: <evidence>/found/dark-home-1440-<scheme>.png.
// Usage: node 08-dark-inset-check.cjs <prototype frontend dir> <evidence dir ($EV/scheme)>
"use strict";
const fs = require("node:fs");
const path = require("node:path");

const [, , feDir, evDir] = process.argv;
const { chromium } = require(require.resolve("@playwright/test", { paths: [path.resolve(feDir)] }));
const BASE = "http://localhost:3001";
const out = path.join(evDir, "found");
fs.mkdirSync(out, { recursive: true });

(async () => {
  const browser = await chromium.launch({ headless: true });
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: "reduce" });
  await ctx.addInitScript(() => {
    try {
      window.localStorage.setItem("kazi-theme", "dark");
    } catch {}
  });
  await ctx.addCookies([{ name: "sidebar_state", value: "true", domain: "localhost", path: "/" }]);
  const page = await ctx.newPage();
  try {
    await page.goto(`${BASE}/mock-login`);
    await page.getByRole("button", { name: "Sign In" }).click();
    await page.waitForURL(/\/org\/[^/]+\//, { timeout: 60_000 });
    for (const scheme of ["today", "a1"]) {
      await page.goto(`${BASE}/org/e2e-test-org/dashboard?scheme=${scheme}`, { waitUntil: "load" });
      await page.locator("#main-content").waitFor({ state: "visible" });
      await page.waitForFunction(() => document.documentElement.classList.contains("dark"));
      await page.waitForLoadState("networkidle", { timeout: 15_000 }).catch(() => {});
      await page.waitForTimeout(1500);
      const probe = await page.evaluate(() => {
        const inset = document.querySelector('[data-slot="sidebar-inset"]');
        const r = inset.getBoundingClientRect();
        const cs = getComputedStyle(inset);
        return {
          htmlClass: document.documentElement.className,
          attr: document.documentElement.getAttribute("data-scheme-candidate"),
          variant: document.querySelector('[data-slot="sidebar"][data-variant]')?.getAttribute("data-variant"),
          insetRect: { x: r.x, y: r.y, width: r.width },
          insetRadius: cs.borderTopLeftRadius,
          insetShadow: cs.boxShadow,
          wrapperBg: getComputedStyle(document.querySelector('[data-slot="sidebar-wrapper"]')).backgroundColor,
          railPaint: getComputedStyle(document.querySelector('[data-slot="sidebar-inner"]')).backgroundColor,
        };
      });
      const file = path.join(out, `dark-home-1440-${scheme}.png`);
      await page.screenshot({ path: file });
      console.log(scheme, path.relative(evDir, file), JSON.stringify(probe));
    }
  } finally {
    await browser.close();
  }
})().catch((e) => {
  console.error("FAILED:", e);
  process.exit(1);
});
