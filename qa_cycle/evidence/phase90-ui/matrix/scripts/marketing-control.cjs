// 644B task 644.4 (marketing): control for marketing-stored-dark-1440.png. The marketing hero is a
// dark-designed section in both themes, so "still light" is proven by comparison: load "/" with
// (a) no stored preference + OS light and (b) kazi-theme=dark stored + OS dark, and compare the
// <html> class, body background and the screenshots byte-for-byte (viewport and full page).
const path = require("path");
const fs = require("fs");
const { chromium, BASE, MATRIX, writeJson } = require("./common-kc.cjs");

async function load(browser, stored, scheme, tag) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: scheme, reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" });
  if (stored) await ctx.addInitScript((t) => { try { localStorage.setItem("kazi-theme", t); } catch {} }, stored);
  const page = await ctx.newPage(); const logs = [];
  page.on("console", (m) => { if (["error", "warning"].includes(m.type()) || /hydrat/i.test(m.text())) logs.push({ type: m.type(), text: m.text().slice(0, 300) }); });
  page.on("pageerror", (e) => logs.push({ type: "pageerror", text: String(e).slice(0, 300) }));
  await page.goto(`${BASE}/`, { waitUntil: "load", timeout: 120000 });
  await page.locator("h1").first().waitFor({ timeout: 60000 });
  await page.mouse.move(1, 1);
  await page.waitForTimeout(3000);
  const vp = path.join(MATRIX, `marketing-${tag}-1440.png`), full = path.join(MATRIX, `marketing-${tag}-1440-full.png`);
  await page.screenshot({ path: vp }); await page.screenshot({ path: full, fullPage: true });
  const facts = await page.evaluate(() => ({ htmlClass: document.documentElement.className, colorScheme: document.documentElement.style.colorScheme, stored: localStorage.getItem("kazi-theme"), bodyBg: getComputedStyle(document.body).backgroundColor, h1: document.querySelector("h1")?.textContent?.trim() }));
  await ctx.close();
  return { tag, stored, scheme, ...facts, console: logs, vp: fs.readFileSync(vp), full: fs.readFileSync(full) };
}

(async () => {
  const browser = await chromium.launch();
  try {
    const a = await load(browser, null, "light", "control-no-pref");
    const b = await load(browser, "dark", "dark", "stored-dark-rerun");
    const strip = ({ vp, full, ...r }) => r;
    const out = { capturedAt: new Date().toISOString(), control: strip(a), storedDark: strip(b), viewportIdentical: a.vp.equals(b.vp), fullPageIdentical: a.full.equals(b.full) };
    writeJson(MATRIX, "marketing-report.json", out);
    console.log(JSON.stringify(out, null, 1));
  } finally { await browser.close(); }
})().catch((e) => { console.error("OBS FAILED", e); process.exit(1); });
