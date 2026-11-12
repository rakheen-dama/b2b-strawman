// 638A orchestrator probe (final head 57fa7a98f): active-bar colour (D8) and separator inset (D15).
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-gate/frontend/node_modules/@playwright/test');
const fs = require('fs'); const path = require('path');
const OUT = __dirname; const BASE = 'http://localhost:3001';
(async () => {
  const browser = await chromium.launch(); const report = { head: '57fa7a98f', runs: [] };
  for (const theme of ['light', 'dark']) for (const state of ['true', 'false']) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await ctx.addInitScript(t => { try { localStorage.setItem('kazi-theme', t); } catch (e) {} }, theme);
    const page = await ctx.newPage(); const console_ = [];
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) console_.push(m.type() + ': ' + m.text()); });
    await page.goto(BASE + '/mock-login'); await page.getByRole('button', { name: 'Sign In' }).click();
    await page.waitForURL('**/org/e2e-test-org/dashboard');
    // v2: let the post-login dashboard finish loading before the cookie+reload, so its in-flight userinfo fetch is not aborted (v1 artefact)
    await page.waitForLoadState('load'); await page.locator('h1').first().waitFor(); await page.waitForTimeout(1500); console_.length = 0;
    await ctx.addCookies([{ name: 'sidebar_state', value: state, url: BASE }]);
    await page.goto(BASE + '/org/e2e-test-org/dashboard', { waitUntil: 'load' }); await page.locator('h1').first().waitFor();
    await page.waitForTimeout(800);
    const r = await page.evaluate(() => {
      const nav = document.querySelector('nav[aria-label="Main navigation"]');
      const container = document.querySelector('[data-slot="sidebar-container"]');
      const cb = container.getBoundingClientRect();
      const active = nav.querySelector('a[aria-current="page"]');
      const before = active ? getComputedStyle(active, '::before') : null;
      const seps = [...document.querySelectorAll('[data-slot="sidebar-container"] [data-slot="sidebar-separator"]')].filter(s => s.getBoundingClientRect().width > 0)
        .map(s => { const b = s.getBoundingClientRect(); return { left: Math.round(b.left), right: Math.round(b.right), width: Math.round(b.width), insideContainer: b.left >= cb.left && b.right <= cb.right, inNav: nav.contains(s) }; });
      return { containerLeft: cb.left, containerRight: cb.right, containerWidth: cb.width,
        active: active ? { text: active.textContent.trim(), dataActive: active.getAttribute('data-active'), barBg: before.backgroundColor, barWidth: before.width, barContent: before.content } : null,
        separators: seps };
    });
    const teal500 = await page.evaluate(() => { const d = document.createElement('div'); d.style.color = 'var(--teal-500)'; document.body.appendChild(d); const c = getComputedStyle(d).color; d.remove(); return c; });
    await page.screenshot({ path: path.join(OUT, `bar-separators-1440-${theme}-${state === 'true' ? 'expanded' : 'collapsed'}.png`) });
    report.runs.push({ theme, sidebar_state: state, teal500, ...r, console: console_ });
    await ctx.close();
  }
  await browser.close(); fs.writeFileSync(path.join(OUT, 'obs-probe5-report.json'), JSON.stringify(report, null, 2)); console.log(JSON.stringify(report.runs.map(x => ({ theme: x.theme, state: x.sidebar_state, teal500: x.teal500, bar: x.active, seps: x.separators.length, allInside: x.separators.every(s => s.insideContainer), widths: [...new Set(x.separators.map(s => s.width))], cw: x.containerWidth, console: x.console.length })), null, 1));
})().catch(e => { console.error(e); process.exit(1); });
