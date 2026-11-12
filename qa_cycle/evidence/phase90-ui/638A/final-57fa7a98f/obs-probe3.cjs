// [final re-run at head 57fa7a98f] 638A OBS follow-up probe 3: is the /matters table's right-edge clipping caused by the shell (main 1184 vs
// before 1200) or by the richer seed data? Measures the table's scroll container at 1440 and 1456 (main=1200),
// expanded and collapsed. Read-only.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-gate/frontend/node_modules/@playwright/test');
const fs = require('fs');
const BASE = 'http://localhost:3001';
(async () => {
  const browser = await chromium.launch({ headless: true });
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await lp.getByRole('button', { name: 'Sign In' }).click();
  await lp.waitForURL('**/org/e2e-test-org/dashboard', { timeout: 60000 });
  const st = await lctx.storageState();
  st.cookies = st.cookies.filter((c) => c.name !== 'sidebar_state');
  await lctx.close();
  const out = [];
  for (const [w, mode] of [[1440, 'true'], [1456, 'true'], [1440, 'false'], [1600, 'true']]) {
    const ctx = await browser.newContext({ storageState: st, viewport: { width: w, height: 900 } });
    await ctx.addCookies([{ name: 'sidebar_state', value: mode, url: BASE }]);
    const page = await ctx.newPage();
    await page.goto(`${BASE}/org/e2e-test-org/matters`, { waitUntil: 'load' });
    await page.locator('table').first().waitFor();
    await page.waitForTimeout(1200);
    const m = await page.evaluate(() => {
      const t = document.querySelector('table');
      let s = t.parentElement; while (s && s.scrollWidth <= s.clientWidth && s !== document.body) s = s.parentElement;
      const sc = t.parentElement;
      return { main: Math.round(document.querySelector('#main-content').getBoundingClientRect().width), tableW: Math.round(t.getBoundingClientRect().width), wrapper: { cls: sc.className.slice(0, 80), scrollWidth: sc.scrollWidth, clientWidth: sc.clientWidth, overflowX: getComputedStyle(sc).overflowX }, docScrollWidth: document.documentElement.scrollWidth };
    });
    out.push({ viewport: w, sidebar: mode === 'true' ? 'expanded' : 'collapsed', ...m });
    await ctx.close();
  }
  fs.writeFileSync(`${__dirname}/obs-probe3-report.json`, JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); process.exit(1); });
