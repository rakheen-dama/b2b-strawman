// 643C OBS shared helpers (Node @playwright/test chromium, headless).
const { chromium } = require("/Users/rakheendama/Projects/2026/worktree-epic-643C/frontend/node_modules/@playwright/test");
const path = require("path");
const BASE = "http://localhost:3001", API = "http://localhost:8081", SLUG = "e2e-test-org";
const PROF = `${BASE}/org/${SLUG}/reports/profitability`, REPORTS = `${BASE}/org/${SLUG}/reports`;
const OUT = path.join(__dirname, "..", "..", "final-fix");
async function session(browser, { theme = "light", viewport = { width: 1440, height: 900 }, reducedMotion = "reduce", consoleSink, acceptDownloads = false } = {}) {
  const ctx = await browser.newContext({ viewport, colorScheme: theme, reducedMotion, timezoneId: "Africa/Johannesburg", acceptDownloads });
  await ctx.addInitScript((t) => { try { localStorage.setItem("kazi-theme", t); } catch {} }, theme);
  const page = await ctx.newPage();
  const tag = `${theme}/${viewport.width}`;
  if (consoleSink) {
    page.on("console", (m) => consoleSink.push({ session: tag, url: page.url().replace(BASE, ""), type: m.type(), text: m.text().slice(0, 300) }));
    page.on("pageerror", (e) => consoleSink.push({ session: tag, url: page.url().replace(BASE, ""), type: "pageerror", text: String(e).slice(0, 300) }));
  }
  await page.goto(`${BASE}/mock-login`, { waitUntil: "load" });
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 60000 });
  await page.locator("h1").first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(800);
  const token = (await ctx.cookies()).find((c) => c.name === "mock-auth-token").value;
  const get = async (p) => { const r = await ctx.request.get(API + p, { headers: { Authorization: `Bearer ${token}` } }); let b = null; try { b = await r.json(); } catch {} return { status: r.status(), body: b }; };
  return { ctx, page, get };
}
async function open(page, url, waitSel) {
  const resp = await page.goto(url, { waitUntil: "load" });
  await page.locator("h1").first().waitFor({ timeout: 60000 });
  if (waitSel) await page.locator(waitSel).first().waitFor({ timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(2500);
  return resp ? resp.status() : null;
}
// Card by its title text (CardTitle is data-slot="card-title").
const cardSel = (title) => `[data-slot="card"]:has([data-slot="card-title"]:text-is("${title}"))`;
module.exports = { chromium, BASE, API, SLUG, PROF, REPORTS, OUT, session, open, cardSel };
