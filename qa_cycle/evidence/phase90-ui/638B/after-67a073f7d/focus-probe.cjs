// Follow-up probe (after after.cjs reported focusAfterEsc = BODY): where does focus land after the
// palette closes, for each way of opening it? Sidebar pill is unchanged by 638B, so it is the control.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-638B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const path = require('path');
const BASE = 'http://localhost:3001';
const DASH = `${BASE}/org/e2e-test-org/dashboard`;
const out = { head: '67a073f7d', capturedAt: new Date().toISOString(), cases: [] };

(async () => {
  const browser = await chromium.launch({ headless: true });
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await lp.getByRole('button', { name: 'Sign In' }).click();
  await lp.waitForURL('**/org/e2e-test-org/dashboard', { timeout: 60000 });
  const state = await lctx.storageState();
  await lctx.close();
  const active = (page) => page.evaluate(() => { const a = document.activeElement; return a ? { tag: a.tagName.toLowerCase(), name: (a.getAttribute('aria-label') || a.textContent || '').trim().slice(0, 40), inHeader: !!a.closest('header') } : null; });

  const cases = [
    ['header Search click', (p) => p.getByRole('banner').getByRole('button', { name: 'Search, Command K' }).click()],
    ['sidebar pill click', (p) => p.locator('[data-sidebar="sidebar"], [data-slot="sidebar"]').getByRole('button', { name: 'Search, Command K' }).first().click()],
    ['header Search keyboard (focus + Enter)', async (p) => { await p.getByRole('banner').getByRole('button', { name: 'Search, Command K' }).focus(); await p.keyboard.press('Enter'); }],
    ['Meta+k with Notifications focused', async (p) => { await p.getByRole('banner').getByRole('button', { name: 'Notifications' }).focus(); await p.keyboard.press('Meta+k'); }],
  ];
  for (const [label, open] of cases) {
    const ctx = await browser.newContext({ storageState: state, viewport: { width: 1440, height: 900 } });
    const page = await ctx.newPage();
    await page.goto(DASH, { waitUntil: 'load' });
    await page.locator('h1').first().waitFor({ state: 'visible', timeout: 30000 });
    await page.waitForTimeout(1200);
    await open(page);
    await page.getByRole('dialog').first().waitFor({ state: 'visible', timeout: 5000 });
    await page.waitForTimeout(400);
    const whileOpen = await active(page);
    await page.keyboard.press('Escape');
    await page.getByRole('dialog').first().waitFor({ state: 'hidden', timeout: 5000 });
    const at0 = await active(page);
    await page.waitForTimeout(800);
    const at800 = await active(page);
    out.cases.push({ label, whileOpen, focusRightAfterClose: at0, focusAfter800ms: at800 });
    console.log(label, JSON.stringify(whileOpen), '->', JSON.stringify(at0), JSON.stringify(at800));
    await ctx.close();
  }
  await browser.close();
  fs.writeFileSync(path.join(__dirname, 'focus-probe.json'), JSON.stringify(out, null, 2));
})().catch((e) => { console.error('FAILED', e); process.exit(1); });
