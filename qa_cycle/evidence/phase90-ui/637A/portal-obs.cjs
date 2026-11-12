const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-637A/frontend/node_modules/@playwright/test');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-637A/qa_cycle/evidence/phase90-ui/637A/portal';
const PORTAL = 'http://localhost:3012', BACKEND = 'http://localhost:8081', ORG = 'e2e-test-org', EMAIL = 'contact@acme.example.com';
const CORS = { 'access-control-allow-origin': PORTAL, 'access-control-allow-credentials': 'true', 'access-control-allow-headers': '*', 'access-control-allow-methods': 'GET,POST,PUT,PATCH,DELETE,OPTIONS' };
(async () => {
  const link = await (await fetch(`${BACKEND}/portal/auth/request-link`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: EMAIL, orgId: ORG }) })).json();
  const token = link.magicLink.split('token=').pop().split('&')[0];
  const s = await (await fetch(`${BACKEND}/portal/auth/exchange`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ token, orgId: ORG }) })).json();
  if (!s.token) throw new Error('exchange failed ' + JSON.stringify(s));
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1024, height: 768 }, colorScheme: 'light' });
  // Test-harness CORS shim only: the e2e backend allows :3002, which the dev KC portal occupies.
  await ctx.route(`${BACKEND}/**`, async route => {
    if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers: CORS });
    const resp = await route.fetch({ headers: { ...route.request().headers(), origin: 'http://localhost:3002' } });
    return route.fulfill({ response: resp, headers: { ...resp.headers(), ...CORS } });
  });
  const page = await ctx.newPage();
  const logs = [];
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) logs.push(`[${m.type()}] ${page.url()} :: ${m.text().slice(0, 300)}`); });
  page.on('pageerror', e => logs.push(`[pageerror] ${page.url()} :: ${e.message.slice(0, 300)}`));
  await page.goto(PORTAL + '/login');
  await page.evaluate(x => { localStorage.setItem('portal_jwt', x.token); localStorage.setItem('portal_customer', JSON.stringify({ id: x.clientId, name: x.clientName, email: x.email, orgId: x.orgId })); }, { token: s.token, clientId: s.clientId, clientName: s.clientName, email: EMAIL, orgId: ORG });
  const report = [];
  for (const p of ['home', 'trust', 'invoices']) {
    await page.goto(`${PORTAL}/${p}`, { waitUntil: 'load', timeout: 90000 }); await page.waitForSelector('h1', { timeout: 60000 }).catch(() => {});
    await page.waitForTimeout(4000);
    await page.screenshot({ path: `${OUT}/${p}-1024-light.png` });
    const info = await page.evaluate(() => ({ h1: document.querySelector('h1')?.textContent?.trim(), bodyBg: getComputedStyle(document.body).backgroundColor, ring: getComputedStyle(document.documentElement).getPropertyValue('--ring').trim(), bg: getComputedStyle(document.documentElement).getPropertyValue('--background').trim() }));
    report.push({ page: p, url: page.url(), ...info });
  }
  require('fs').writeFileSync(`${OUT}/obs-report.json`, JSON.stringify({ report, console: logs, note: 'branch portal dev on :3012 against e2e backend :8081; Origin rewritten to :3002 and CORS headers added by a Playwright route (test harness only, no app change)' }, null, 2));
  console.log(JSON.stringify({ report, console: logs.slice(0, 15) }, null, 1));
  await browser.close();
})().catch(e => { console.error('OBS FAILED', e); process.exit(1); });
