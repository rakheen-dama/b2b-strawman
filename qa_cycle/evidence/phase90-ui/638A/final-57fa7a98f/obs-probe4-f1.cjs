// [final re-run at head 57fa7a98f] 638A OBS — NEW probe (not a copy of an earlier script): the F1 matrix.
// Each editor case opens a fresh New Clause sheet, types "Boldword", selects all, presses ONE key, records
// the editor HTML, the DOM selection, the sidebar data-state, the sidebar_state cookie and whether the keydown
// ended up defaultPrevented, then closes the sheet with its Close button (never "Create Clause").
// Body cases: sheet closed, focus on BODY, press the key, record the same facts.
// Platforms:
//   mac        — headless Chromium on this Mac, no spoof.
//   win-np     — navigator.platform spoofed to Win32 via Navigator.prototype getter (the obs-probe.cjs method).
//                navigator.userAgentData is NOT spoofed by this method.
//   win-cdp    — consistent Windows emulation via CDP Emulation.setUserAgentOverride (platform 'Win32',
//                Windows UA string, userAgentMetadata.platform 'Windows').
// Read-only: UI state only.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-gate/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = __dirname;
const BASE = 'http://localhost:3001';
const CLAUSES = `${BASE}/org/e2e-test-org/settings/documents?tab=clauses`;
const out = { head: '57fa7a98f8c1bfc1a8167cad37f56368d727800f', capturedAt: new Date().toISOString() };

const KEYLOG = () => {
  window.__keys = [];
  window.addEventListener('keydown', (e) => {
    if (e.key !== 'b' && e.key !== 'B') return;
    const rec = { key: e.key, ctrl: e.ctrlKey, meta: e.metaKey, target: e.target && e.target.tagName };
    window.__keys.push(rec);
    setTimeout(() => { rec.defaultPreventedAfterDispatch = e.defaultPrevented; }, 0);
  });
};

(async () => {
  const browser = await chromium.launch({ headless: true });
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await lp.getByRole('button', { name: 'Sign In' }).click();
  await lp.waitForURL('**/org/e2e-test-org/dashboard', { timeout: 60000 });
  const st = await lctx.storageState();
  st.cookies = st.cookies.filter((c) => c.name !== 'sidebar_state');
  const realUA = await lp.evaluate(() => navigator.userAgent);
  await lctx.close();

  for (const plat of ['mac', 'win-np', 'win-cdp']) {
    const ctx = await browser.newContext({ storageState: st, viewport: { width: 1440, height: 900 } });
    await ctx.addCookies([{ name: 'sidebar_state', value: 'true', url: BASE }]);
    await ctx.addInitScript(() => { try { localStorage.setItem('kazi-theme', 'light'); } catch (e) {} });
    await ctx.addInitScript(KEYLOG);
    if (plat === 'win-np') await ctx.addInitScript(() => { Object.defineProperty(Navigator.prototype, 'platform', { get: () => 'Win32' }); });
    const page = await ctx.newPage();
    if (plat === 'win-cdp') {
      const cdp = await ctx.newCDPSession(page);
      const major = (realUA.match(/Chrome\/(\d+)/) || [])[1] || '0';
      await cdp.send('Emulation.setUserAgentOverride', {
        userAgent: realUA.replace(/\(Macintosh;[^)]*\)/, '(Windows NT 10.0; Win64; x64)'),
        platform: 'Win32',
        userAgentMetadata: { brands: [{ brand: 'Chromium', version: major }], platform: 'Windows', platformVersion: '10.0.0', architecture: 'x86', model: '', mobile: false },
      });
    }
    const logs = [];
    page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) logs.push(m.text().slice(0, 300)); });
    page.on('pageerror', (e) => logs.push(String(e).slice(0, 300)));
    const sb = () => page.evaluate(() => document.querySelector('[data-slot="sidebar"]:not([data-mobile])')?.getAttribute('data-state'));
    const cookie = async () => ((await ctx.cookies(BASE)).find((c) => c.name === 'sidebar_state') || {}).value ?? null;
    const lastKey = () => page.evaluate(() => window.__keys[window.__keys.length - 1] || null);

    await page.goto(CLAUSES, { waitUntil: 'load' });
    await page.locator('h1').first().waitFor({ timeout: 30000 });
    await page.waitForTimeout(1200);
    const r = {
      env: await page.evaluate(() => ({ platform: navigator.platform, uaDataPlatform: navigator.userAgentData ? navigator.userAgentData.platform : null, ua: navigator.userAgent })),
      editor: {}, body: {},
    };

    for (const key of ['Control+b', 'Meta+b']) {
      await page.getByRole('button', { name: 'New Clause' }).click();
      const dlg = page.getByRole('dialog');
      const pm = dlg.locator('.ProseMirror').first();
      await pm.waitFor({ timeout: 20000 });
      await pm.click();
      await page.keyboard.type('Boldword');
      await pm.evaluate((el) => { const s = window.getSelection(); s.removeAllRanges(); const rg = document.createRange(); rg.selectNodeContents(el); s.addRange(rg); });
      await page.waitForTimeout(150);
      const sel = () => page.evaluate(() => { const s = window.getSelection(); return { text: s.toString(), collapsed: s.isCollapsed, anchorOffset: s.anchorOffset, focusOffset: s.focusOffset }; });
      const before = { sidebar: await sb(), cookie: await cookie(), html: await pm.innerHTML(), selection: await sel() };
      await page.keyboard.press(key);
      await page.waitForTimeout(400);
      const after = { sidebar: await sb(), cookie: await cookie(), html: await pm.innerHTML(), selection: await sel(), keydown: await lastKey(), activeElementIsEditor: await pm.evaluate((el) => el === document.activeElement || el.contains(document.activeElement)) };
      r.editor[key] = { before, after, toggled: before.sidebar !== after.sidebar, bolded: /<strong>/.test(after.html) && !/<strong>/.test(before.html) };
      if (key === 'Control+b') await page.screenshot({ path: `${OUT}/f1-${plat}-editor-ctrl-b.png` });
      await dlg.getByRole('button', { name: 'Close', exact: true }).first().click();
      await page.waitForTimeout(600);
      r.editor[key].dialogsAfterClose = await page.getByRole('dialog').count();
      // restore expanded if the press toggled it
      if (after.sidebar !== 'expanded') { await page.getByRole('button', { name: 'Toggle menu', exact: true }).click(); await page.waitForTimeout(400); }
    }

    for (const key of ['Control+b', 'Meta+b']) {
      await page.evaluate(() => { if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur(); });
      const before = { sidebar: await sb(), cookie: await cookie(), activeElement: await page.evaluate(() => document.activeElement.tagName) };
      await page.keyboard.press(key);
      await page.waitForTimeout(400);
      const after = { sidebar: await sb(), cookie: await cookie(), keydown: await lastKey() };
      r.body[key] = { before, after, toggled: before.sidebar !== after.sidebar };
      if (key === 'Control+b') await page.screenshot({ path: `${OUT}/f1-${plat}-body-ctrl-b.png` });
      if (after.sidebar !== 'expanded') { await page.getByRole('button', { name: 'Toggle menu', exact: true }).click(); await page.waitForTimeout(400); }
    }
    r.console = logs;
    out[plat] = r;
    fs.writeFileSync(`${OUT}/obs-probe4-f1-report.json`, JSON.stringify(out, null, 2));
    await ctx.close();
  }
  fs.writeFileSync(`${OUT}/obs-probe4-f1-report.json`, JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 1));
  await browser.close();
})().catch((e) => { console.error('FATAL', e); fs.writeFileSync(`${OUT}/obs-probe4-f1-report.json`, JSON.stringify({ ...out, fatal: String(e) }, null, 2)); process.exit(1); });
