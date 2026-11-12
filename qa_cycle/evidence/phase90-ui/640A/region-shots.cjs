// 640A OBS (d78ce3ce5) supplement: row-money / row-risk region shots taken as full-page clips in document coordinates.
// obs.cjs's first run took them as element screenshots, which scroll the row under the sticky floating header (header
// overprinted the row-risk shot). This re-takes only those region shots + per-cell boxes; it does not touch the role flow.
// Usage: node region-shots.cjs <outDir> <playwrightTestModuleDir>
const OUT = process.argv[2];
const { chromium } = require(process.argv[3]);
const fs = require('fs');
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
const USERS = { alice: 'user_e2e_alice', bob: 'user_e2e_bob' };
(async () => {
  const browser = await chromium.launch();
  const out = [];
  try {
    for (const who of ['alice', 'bob']) for (const theme of ['light', 'dark']) {
      const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
      await ctx.addInitScript(t => { try { localStorage.setItem('kazi-theme', t); } catch (e) {} }, theme);
      const page = await ctx.newPage();
      const logs = [];
      page.on('console', m => { if (['error', 'warning'].includes(m.type())) logs.push(`[${m.type()}] ${m.text().slice(0, 300)}`); });
      await page.goto(BASE + '/mock-login');
      await page.locator('#user-select').selectOption(USERS[who]);
      await page.getByRole('button', { name: 'Sign In' }).click();
      await page.waitForURL(/\/dashboard/, { timeout: 30000 });
      await page.waitForLoadState('networkidle').catch(() => {});
      await page.waitForTimeout(1500);
      for (const [w, h] of [[1024, 768], [1440, 900]]) {
        await page.setViewportSize({ width: w, height: h });
        await page.goto(BASE + O + '/dashboard', { waitUntil: 'load' });
        await page.locator('#main-content [data-testid="home-page"]').waitFor({ timeout: 30000 });
        await page.waitForLoadState('networkidle').catch(() => {});
        await page.waitForTimeout(1500);
        const tag = `${who}-${w}-${theme}`;
        const shots = {};
        for (const [key, cell] of [['money', 'home-cell-unbilledWip'], ['risk', 'home-cell-ficaOutstanding']]) {
          if (key === 'risk' && who !== 'alice') continue;
          const row = page.locator(`#main-content [data-testid="${cell}"]`).locator('xpath=..');
          await page.evaluate(() => window.scrollTo(0, 0));
          const b = await row.evaluate(el => { const r = el.getBoundingClientRect(); return { x: r.left + scrollX, y: r.top + scrollY, width: r.width, height: r.height,
            cells: [...el.children].map(c => ({ cell: c.getAttribute('data-testid'), w: Math.round(c.getBoundingClientRect().width), x: Math.round(c.getBoundingClientRect().left), y: Math.round(c.getBoundingClientRect().top + scrollY) })) }; });
          const f = `row-${key}-${tag}.png`;
          await page.screenshot({ path: `${OUT}/${f}`, fullPage: true, clip: { x: Math.max(0, b.x - 4), y: Math.max(0, b.y - 4), width: b.width + 8, height: b.height + 8 } });
          shots[key] = { file: f, cells: b.cells };
        }
        out.push({ tag, shots, console: [...logs] });
        console.log(tag, JSON.stringify(shots));
        logs.length = 0;
      }
      await ctx.close();
    }
  } finally {
    fs.writeFileSync(`${OUT}/region-shots-report.json`, JSON.stringify({ capturedAt: new Date().toISOString(), head: 'd78ce3ce5', regions: out }, null, 2));
    await browser.close();
  }
})().catch(e => { console.error('REGION FAILED', e); process.exit(1); });
