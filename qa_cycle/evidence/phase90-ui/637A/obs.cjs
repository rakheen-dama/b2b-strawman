const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-637A/frontend/node_modules/@playwright/test');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-637A/qa_cycle/evidence/phase90-ui/637A';
const BASE = 'http://localhost:3001';
const pages = [
  ['home', '/org/e2e-test-org/dashboard'],
  ['matters', '/org/e2e-test-org/matters'],
  ['trust', '/org/e2e-test-org/trust-accounting'],
];
const sizes = [[1024, 768], [1440, 900]];
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' });
  const page = await ctx.newPage();
  const logs = [];
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) logs.push(`[${m.type()}] ${page.url()} :: ${m.text().slice(0, 300)}`); });
  page.on('pageerror', e => logs.push(`[pageerror] ${page.url()} :: ${e.message.slice(0, 300)}`));
  await page.goto(BASE + '/mock-login');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  const report = [];
  for (const [name, path] of pages) {
    for (const [w, h] of sizes) {
      await page.setViewportSize({ width: w, height: h });
      await page.goto(BASE + path, { waitUntil: 'networkidle' });
      await page.waitForTimeout(1500);
      const file = `${OUT}/${name}-${w}-light.png`;
      await page.screenshot({ path: file });
      const info = await page.evaluate(() => {
        const cs = getComputedStyle(document.documentElement);
        const body = getComputedStyle(document.body);
        const aside = document.querySelector('aside, nav');
        return {
          title: document.title,
          h1: document.querySelector('h1')?.textContent?.trim(),
          htmlClass: document.documentElement.className,
          bodyBg: body.backgroundColor,
          tokenBackground: cs.getPropertyValue('--background').trim(),
          tokenCard: cs.getPropertyValue('--card').trim(),
          tokenRing: cs.getPropertyValue('--ring').trim(),
          tokenChart1: cs.getPropertyValue('--chart-1').trim(),
          tokenAging90: cs.getPropertyValue('--aging-d90plus').trim(),
          sidebarBg: aside ? getComputedStyle(aside).backgroundColor : null,
        };
      });
      report.push({ name, w, h, url: page.url(), file: file.replace(OUT + '/', ''), ...info });
    }
  }
  require('fs').writeFileSync(`${OUT}/obs-report.json`, JSON.stringify({ report, console: logs }, null, 2));
  console.log(JSON.stringify({ report: report.map(r => `${r.name}@${r.w}: ${r.url} h1=${r.h1} bodyBg=${r.bodyBg} ring=${r.tokenRing} sidebar=${r.sidebarBg}`), consoleCount: logs.length, console: logs.slice(0, 15) }, null, 1));
  await browser.close();
})().catch(e => { console.error('OBS FAILED', e); process.exit(1); });
