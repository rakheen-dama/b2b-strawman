// 638A OBS follow-up probe 2: (a) clean hover-tooltip capture (pointer approaches the icon horizontally
// from the canvas); (b) which zone triggers are inside the visible part of the sidebar scroll area
// (expanded 1024/1440, mobile Sheet 390), plus a scrolled mobile Sheet capture. Read-only.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-gate/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = __dirname;
const BASE = 'http://localhost:3001';
const DASH = `${BASE}/org/e2e-test-org/dashboard`;
const out = {};

const zoneVisibility = (page, scope) => page.evaluate((scope) => {
  const root = scope ? document.querySelector(scope) : document;
  const content = root.querySelector('[data-slot="sidebar-content"]');
  const cr = content.getBoundingClientRect();
  const nav = content.querySelector('nav[aria-label="Main navigation"]');
  return {
    content: { top: Math.round(cr.top), bottom: Math.round(cr.bottom), scrollHeight: content.scrollHeight, clientHeight: content.clientHeight, scrollTop: content.scrollTop },
    zones: [...nav.querySelectorAll('button[aria-expanded]')].map((b) => { const r = b.getBoundingClientRect(); return { label: b.textContent.trim(), top: Math.round(r.top), bottom: Math.round(r.bottom), fullyVisible: r.top >= cr.top && r.bottom <= cr.bottom }; }),
  };
}, scope);

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

  const open = async (vp, cookie) => {
    const ctx = await browser.newContext({ storageState: st, viewport: vp });
    await ctx.addInitScript(() => localStorage.setItem('kazi-theme', 'light'));
    if (cookie) await ctx.addCookies([{ name: 'sidebar_state', value: cookie, url: BASE }]);
    const page = await ctx.newPage();
    await page.goto(DASH, { waitUntil: 'load' });
    await page.locator('h1').first().waitFor();
    await page.waitForTimeout(1500);
    return { ctx, page };
  };

  // (a) hover
  {
    const { ctx, page } = await open({ width: 1440, height: 900 }, 'false');
    const link = page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Matters', exact: true });
    const b = await link.boundingBox();
    const y = b.y + b.height / 2;
    await page.mouse.move(600, y);
    await page.mouse.move(b.x + b.width / 2, y, { steps: 8 });
    await page.waitForTimeout(800);
    out.hover = { tooltip: await page.getByRole('tooltip').allTextContents(), visibleContents: await page.locator('[data-slot="tooltip-content"]:visible').allTextContents(), focused: await page.evaluate(() => document.activeElement.tagName) };
    await page.screenshot({ path: `${OUT}/rail-tooltip-hover.png` });
    await ctx.close();
  }

  // (b) zone visibility expanded desktop
  for (const vp of [{ width: 1024, height: 768 }, { width: 1440, height: 900 }]) {
    const { ctx, page } = await open(vp, 'true');
    out[`expanded-${vp.width}`] = await zoneVisibility(page, null);
    await ctx.close();
  }
  // mobile Sheet
  {
    const { ctx, page } = await open({ width: 390, height: 844 }, null);
    await page.getByRole('button', { name: 'Toggle menu', exact: true }).click();
    await page.getByRole('dialog', { name: 'Kazi' }).waitFor();
    await page.waitForTimeout(600);
    out['mobile-390-sheet'] = await zoneVisibility(page, '[role="dialog"]');
    await page.evaluate(() => { const c = document.querySelector('[role="dialog"] [data-slot="sidebar-content"]'); c.scrollTop = c.scrollHeight; });
    await page.waitForTimeout(400);
    out['mobile-390-sheet-scrolled'] = await zoneVisibility(page, '[role="dialog"]');
    await page.screenshot({ path: `${OUT}/mobile-390-sheet-scrolled-light.png` });
    await ctx.close();
  }
  fs.writeFileSync(`${OUT}/obs-probe2-report.json`, JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); fs.writeFileSync(`${OUT}/obs-probe2-report.json`, JSON.stringify({ ...out, fatal: String(e) }, null, 2)); process.exit(1); });
