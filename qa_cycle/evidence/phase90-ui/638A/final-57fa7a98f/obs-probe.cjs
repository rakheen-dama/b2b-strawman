// [final re-run at head 57fa7a98f] 638A OBS follow-up probe: (a) collapsed-rail hover tooltip with an explicit wait; (b) Ctrl+B in the
// Tiptap New Clause editor with navigator.platform spoofed to Win32 (ProseMirror reads it at load to
// decide Mod = Ctrl vs Cmd); (c) Meta+b / Ctrl+B inside a plain <input> (clause Title) on macOS.
// Read-only: the New Clause sheet is closed via its Close button, never saved.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-gate/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = __dirname;
const BASE = 'http://localhost:3001';
const out = {};
const state = async (page) => page.evaluate(() => document.querySelector('[data-slot="sidebar"]:not([data-mobile])')?.getAttribute('data-state'));

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

  // (a) hover tooltip
  {
    const ctx = await browser.newContext({ storageState: st, viewport: { width: 1440, height: 900 } });
    await ctx.addInitScript(() => localStorage.setItem('kazi-theme', 'light'));
    await ctx.addCookies([{ name: 'sidebar_state', value: 'false', url: BASE }]);
    const page = await ctx.newPage();
    await page.goto(`${BASE}/org/e2e-test-org/dashboard`, { waitUntil: 'load' });
    await page.locator('h1').first().waitFor();
    await page.waitForTimeout(1500);
    const link = page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Matters', exact: true });
    const b = await link.boundingBox();
    await page.mouse.move(b.x + b.width / 2, b.y + b.height / 2, { steps: 5 });
    await page.mouse.move(b.x + b.width / 2 + 2, b.y + b.height / 2, { steps: 3 });
    let tip = [];
    try { await page.locator('[data-slot="tooltip-content"]').first().waitFor({ state: 'visible', timeout: 3000 }); } catch (e) {}
    tip = await page.locator('[data-slot="tooltip-content"]').allTextContents();
    out.hover = { linkBox: b, tooltipTexts: tip, roleTooltip: await page.getByRole('tooltip').allTextContents(), activeElement: await page.evaluate(() => document.activeElement.tagName) };
    await page.screenshot({ path: `${OUT}/rail-tooltip-hover.png` });
    await ctx.close();
  }

  // (b) Win32 platform spoof: Ctrl+B inside Tiptap
  for (const plat of ['Win32', 'MacIntel']) {
    const ctx = await browser.newContext({ storageState: st, viewport: { width: 1440, height: 900 } });
    await ctx.addInitScript((p) => { Object.defineProperty(Navigator.prototype, 'platform', { get: () => p }); localStorage.setItem('kazi-theme', 'light'); }, plat);
    const page = await ctx.newPage();
    const logs = [];
    page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) logs.push(m.text().slice(0, 300)); });
    page.on('pageerror', (e) => logs.push(String(e).slice(0, 300)));
    await page.goto(`${BASE}/org/e2e-test-org/settings/documents?tab=clauses`, { waitUntil: 'load' });
    await page.locator('h1').first().waitFor();
    await page.waitForTimeout(1200);
    await page.getByRole('button', { name: 'New Clause' }).click();
    const dlg = page.getByRole('dialog');
    const pm = dlg.locator('.ProseMirror').first();
    await pm.waitFor({ timeout: 20000 });
    const r = { platform: await page.evaluate(() => navigator.platform) };
    await pm.click();
    await page.keyboard.type('Boldword');
    await page.keyboard.press(plat === 'Win32' ? 'Control+a' : 'Meta+a');
    for (const key of ['Control+b', 'Meta+b']) {
      const s0 = await state(page);
      await page.keyboard.press(key);
      await page.waitForTimeout(400);
      r[key] = { sidebarBefore: s0, sidebarAfter: await state(page), html: await pm.innerHTML() };
    }
    if (plat === 'MacIntel') {
      // plain input: Title field
      const title = dlg.locator('#clause-editor-title');
      await title.click();
      await title.fill('');
      for (const key of ['Meta+b', 'Control+b']) {
        const s0 = await state(page);
        await page.keyboard.press(key);
        await page.waitForTimeout(400);
        r[`titleInput ${key}`] = { sidebarBefore: s0, sidebarAfter: await state(page) };
      }
      await page.screenshot({ path: `${OUT}/tiptap-ctrl-b-mac.png` });
    }
    await dlg.getByRole('button', { name: 'Close', exact: true }).first().click();
    await page.waitForTimeout(500);
    r.dialogsAfterClose = await page.getByRole('dialog').count();
    r.console = logs;
    out[plat] = r;
    await ctx.close();
  }
  fs.writeFileSync(`${OUT}/obs-probe-report.json`, JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); fs.writeFileSync(`${OUT}/obs-probe-report.json`, JSON.stringify({ ...out, fatal: String(e) }, null, 2)); process.exit(1); });
