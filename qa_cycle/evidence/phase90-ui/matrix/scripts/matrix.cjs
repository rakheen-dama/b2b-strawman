// 644B task 644.4: exit observation matrix as the owner (Thandi) on the KC dev stack.
// 7 surfaces × {1024, 1440} × {light, dark} = 28 viewport shots, + full-page Home/profitability at
// 1440, + matter-detail tabs (time, trust, activity) at 1440 light and dark, + marketing "/" with a
// stored dark preference (must still paint light).
const { chromium, BASE, ORG, MATRIX, session, setTheme, setWidth, open, shot, writeJson } = require("./common-kc.cjs");
const MATTER = "9181f514-1742-43c5-b446-99e1d41f220e"; // Estate Late Peter Moroka
const CLIENT = "713a1f9a-9cd4-406e-920e-507ac449c98a"; // Moroka Family Trust
const SURFACES = [
  ["home", "/dashboard", '[data-testid="home-page"]'],
  ["matters", "/matters", "table"],
  ["matter-detail", `/matters/${MATTER}`, '[data-testid="matter-header-card"]'],
  ["clients", "/clients", "table"],
  ["client-detail", `/clients/${CLIENT}`, "h1"],
  ["trust", "/trust-accounting", '[data-testid="trust-balance-trend"] svg.recharts-surface'],
  ["profitability", "/reports/profitability", '[data-testid="billing-trend"] svg.recharts-surface'],
];
const FULL = new Set(["home", "profitability"]);
const selectedTab = (page) => page.evaluate(() => [...document.querySelectorAll('[role="tab"][aria-selected="true"], [role="tab"][data-state="active"]')].map((t) => t.textContent.trim()));

(async () => {
  const browser = await chromium.launch(); const consoleLog = []; const report = []; const t0 = Date.now();
  try {
    const s = await session(browser, "thandi", { consoleSink: consoleLog });
    for (const theme of ["light", "dark"]) {
      await setTheme(s, theme);
      for (const width of [1024, 1440]) {
        await setWidth(s, width);
        for (const [name, p, waitSel] of SURFACES) {
          const label = `${name}-${width}-${theme}`;
          const status = await open(s, ORG + p, waitSel, label);
          const r = { name, persona: "owner-thandi", theme, width, status, ...(await shot(s, MATRIX, `${label}.png`)) };
          if (name === "matter-detail") r.tabs = await selectedTab(s.page);
          report.push(r);
          if (width === 1440 && FULL.has(name)) report.push({ name: `${name}-full`, persona: "owner-thandi", theme, width, status, ...(await shot(s, MATRIX, `${label}-full.png`, { fullPage: true, audit: false })) });
          console.log(label, status, r.dark, `${Math.round((Date.now() - t0) / 1000)}s`);
        }
      }
      // Matter detail: three more tabs visited (Overview is the default above) at 1440.
      await setWidth(s, 1440);
      for (const tab of ["time", "trust", "activity"]) {
        const label = `matter-detail-tab-${tab}-1440-${theme}`;
        const status = await open(s, `${ORG}/matters/${MATTER}?tab=${tab}`, '[role="tabpanel"]', label);
        report.push({ name: `matter-detail-tab-${tab}`, persona: "owner-thandi", theme, width: 1440, status, tabs: await selectedTab(s.page), ...(await shot(s, MATRIX, `${label}.png`)) });
        console.log(label, status);
      }
    }
    await s.ctx.close();

    // Marketing "/" with a stored dark preference and an OS dark preference: must still paint light.
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: "dark", reducedMotion: "reduce", timezoneId: "Africa/Johannesburg" });
    await ctx.addInitScript(() => { try { localStorage.setItem("kazi-theme", "dark"); } catch {} });
    const page = await ctx.newPage(); const m = { page, state: { who: "anonymous", theme: "stored-dark", width: 1440, label: "marketing" } };
    page.on("console", (x) => { if (x.type() === "error" || x.type() === "warning" || /hydrat/i.test(x.text())) consoleLog.push({ label: "marketing-stored-dark-1440", who: "anonymous", theme: "stored-dark", width: 1440, url: page.url().replace(BASE, ""), type: x.type(), text: x.text().slice(0, 400) }); });
    page.on("pageerror", (e) => consoleLog.push({ label: "marketing-stored-dark-1440", who: "anonymous", theme: "stored-dark", width: 1440, url: page.url().replace(BASE, ""), type: "pageerror", text: String(e).slice(0, 400) }));
    const status = await open(m, `${BASE}/`, null, "marketing");
    report.push({ name: "marketing", persona: "anonymous", theme: "stored-dark", width: 1440, status, ...(await shot(m, MATRIX, "marketing-stored-dark-1440.png")) });
    await ctx.close();
  } finally {
    writeJson(MATRIX, "obs-report.json", { capturedAt: new Date().toISOString(), report });
    writeJson(MATRIX, "console.json", consoleLog);
    await browser.close();
  }
  console.log("DONE", report.length, "captures", consoleLog.length, "console entries");
})().catch((e) => { console.error("OBS FAILED", e); process.exit(1); });
