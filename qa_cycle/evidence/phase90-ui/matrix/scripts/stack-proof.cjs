// 644B step 1: prove the restarted KC stack serves the 643A (trust balance trend) and 643B (billing
// trend) endpoints, through the browser as the owner. Also the dry run of common-kc.cjs.
const { chromium, ORG, MATRIX, session, open, shot, writeJson } = require("./common-kc.cjs");
(async () => {
  const browser = await chromium.launch(); const consoleLog = []; const out = [];
  try {
    const s = await session(browser, "thandi", { consoleSink: consoleLog });
    for (const [label, p, tid] of [["trust", "/trust-accounting", "trust-balance-trend"], ["profitability", "/reports/profitability", "billing-trend"]]) {
      await open(s, ORG + p, `[data-testid="${tid}"] svg.recharts-surface`, label);
      const card = await s.page.evaluate((t) => {
        const el = document.querySelector(`[data-testid="${t}"]`);
        return el ? { present: true, svg: !!el.querySelector("svg.recharts-surface"), text: el.innerText.replace(/\s+/g, " ").slice(0, 300) } : { present: false };
      }, tid);
      out.push({ label, card, ...(await shot(s, MATRIX, `stack-proof-${label}-1440-light.png`, { audit: false })) });
    }
    await s.ctx.close();
  } finally { writeJson(MATRIX, "stack-proof.json", { capturedAt: new Date().toISOString(), out, console: consoleLog }); await browser.close(); }
  console.log(JSON.stringify(out, null, 1));
})().catch((e) => { console.error("OBS FAILED", e); process.exit(1); });
