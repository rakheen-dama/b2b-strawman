// 639A OBS sample discovery: which routes render the EmptyState primitive (data-slot="empty-state") on the seeded :3001 stack.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-639A/frontend/node_modules/@playwright/test');
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
const routes = ['/invoices/billing-runs', '/compliance/requests', '/invoices/unbilled', '/settings/matters?tab=tags', '/settings/matters?tab=custom-fields',
  '/settings/documents?tab=templates', '/settings/connections?tab=packs', '/my-work', '/diary', '/notifications', '/reports/profitability', '/reports', '/invoices', '/clients', '/matters'];
(async () => {
  const browser = await chromium.launch();
  const page = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
  await page.goto(BASE + '/mock-login'); await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  const out = [];
  for (const r of routes) {
    await page.goto(BASE + O + r, { waitUntil: 'networkidle' }).catch(() => {}); await page.waitForTimeout(800);
    out.push({ r, url: page.url().replace(BASE, ''), h1: await page.locator('h1').first().textContent().catch(() => null),
      empties: await page.$$eval('[data-slot="empty-state"]', els => els.map(e => `${e.dataset.variant}: ${e.querySelector('h2,p')?.textContent?.trim()}`)) });
  }
  console.log(JSON.stringify(out, null, 1)); await browser.close();
})().catch(e => { console.error('PROBE FAILED', e); process.exit(1); });
