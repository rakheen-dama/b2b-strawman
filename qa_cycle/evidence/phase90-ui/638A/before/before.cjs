const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-before/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = '/tmp/638A-before/shots';
const BASE = 'http://localhost:3001';
const DASH = `${BASE}/org/e2e-test-org/dashboard`;
const report = { head: '68cf12103', capturedAt: new Date().toISOString(), captures: [] };

async function newCtx(browser, state, viewport, theme) {
  const ctx = await browser.newContext({ storageState: state, viewport });
  if (theme) await ctx.addInitScript((t) => { try { localStorage.setItem('kazi-theme', t); } catch (e) {} }, theme);
  const page = await ctx.newPage();
  const logs = [];
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) logs.push({ type: m.type(), text: m.text().slice(0, 500) }); });
  page.on('pageerror', (e) => logs.push({ type: 'pageerror', text: String(e).slice(0, 500) }));
  return { ctx, page, logs };
}

async function gotoShell(page, url) {
  await page.goto(url, { waitUntil: 'load' });
  await page.locator('h1').first().waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForTimeout(1200);
}

async function facts(page) {
  return page.evaluate(() => {
    const box = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) }; };
    const navs = [...document.querySelectorAll('nav[aria-label="Main navigation"]')];
    const visibleNav = navs.find((n) => n.getBoundingClientRect().width > 0) || navs[0] || null;
    const container = visibleNav ? (visibleNav.closest('aside') || visibleNav.parentElement) : null;
    const zones = visibleNav ? [...visibleNav.querySelectorAll('button[aria-expanded]')].map((b) => ({ label: b.textContent.trim().replace(/\s+/g, ' '), expanded: b.getAttribute('aria-expanded') })) : [];
    const links = visibleNav ? [...visibleNav.querySelectorAll('a')].map((a) => a.textContent.trim().replace(/\s+/g, ' ')) : [];
    const current = visibleNav ? [...visibleNav.querySelectorAll('a[aria-current="page"]')].map((a) => ({ text: a.textContent.trim().replace(/\s+/g, ' '), href: a.getAttribute('href') })) : [];
    const toggles = [...document.querySelectorAll('button,[role="button"]')].filter((b) => (b.getAttribute('aria-label') || b.textContent || '').trim() === 'Toggle menu' || (b.querySelector('.sr-only') && b.querySelector('.sr-only').textContent.trim() === 'Toggle menu'));
    return {
      htmlClass: document.documentElement.className,
      kaziThemeLS: localStorage.getItem('kazi-theme'),
      mainNavCount: navs.length,
      mainNavVisibleCount: navs.filter((n) => n.getBoundingClientRect().width > 0).length,
      sidebarContainerTag: container ? container.tagName.toLowerCase() : null,
      sidebarContainerBox: box(container),
      navBox: box(visibleNav),
      mainContentBox: box(document.querySelector('#main-content')),
      mainContentTag: document.querySelector('#main-content')?.tagName.toLowerCase() ?? null,
      zones, links, ariaCurrent: current,
      mainCount: document.querySelectorAll('main').length,
      toggleMenuCountDom: toggles.length,
      h1: document.querySelector('h1')?.textContent.trim() ?? null,
    };
  });
}

async function record(name, page, viewport, theme, logs, extra = {}) {
  await page.screenshot({ path: `${OUT}/${name}.png` });
  const f = await facts(page);
  f.toggleMenuCountByRole = await page.getByRole('button', { name: 'Toggle menu' }).count();
  const hydration = logs.filter((l) => /hydrat/i.test(l.text));
  report.captures.push({ file: `${name}.png`, url: page.url(), viewport, theme, ...f, ...extra, console: [...logs], hydrationMessages: hydration });
  console.log('captured', name, page.url(), 'sidebar', JSON.stringify(f.sidebarContainerBox), 'main', JSON.stringify(f.mainContentBox), 'logs', logs.length);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  // login
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await lp.getByRole('button', { name: 'Sign In' }).click();
  await lp.waitForURL('**/org/e2e-test-org/dashboard', { timeout: 60000 });
  await lp.locator('h1').first().waitFor({ timeout: 30000 });
  const state = await lctx.storageState();
  await lctx.close();

  // 1. dashboard at 1024/1440 x light/dark
  for (const w of [[1024, 768], [1440, 900]]) {
    for (const theme of ['light', 'dark']) {
      const vp = { width: w[0], height: w[1] };
      const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
      await gotoShell(page, DASH);
      await record(`shell-${w[0]}-${theme}`, page, vp, theme, logs);
      await ctx.close();
    }
  }

  // 2 + 3. matters route + all zones at 1440 light
  {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, 'light');
    await gotoShell(page, DASH);
    const nav = page.getByRole('navigation', { name: 'Main navigation' });
    const mattersLink = nav.getByRole('link', { name: /^Matters$/ });
    let href = null;
    if (await mattersLink.count()) href = await mattersLink.first().getAttribute('href');
    report.mattersLinkHref = href;
    const target = href ? new URL(href, BASE).toString() : `${BASE}/org/e2e-test-org/projects`;
    logs.length = 0;
    await gotoShell(page, target);
    await record('shell-matters-1440-light', page, vp, 'light', logs);

    logs.length = 0;
    await gotoShell(page, DASH);
    const before = await facts(page);
    const closed = nav.locator('button[aria-expanded="false"]');
    let clicked = [];
    for (let i = 0; i < 20; i++) {
      const n = await closed.count();
      if (!n) break;
      const b = closed.first();
      clicked.push((await b.textContent()).trim());
      await b.click();
      await page.waitForTimeout(300);
    }
    await page.waitForTimeout(500);
    await record('shell-1440-light-all-zones', page, vp, 'light', logs, { zonesBeforeExpand: before.zones, zonesClicked: clicked });
    // reset zones UI state not required; leave
    await ctx.close();
  }

  // 4. mobile
  for (const theme of ['light', 'dark']) {
    const vp = { width: 390, height: 844 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, DASH);
    if (theme === 'light') await record('mobile-390-closed', page, vp, theme, logs, { state: 'closed' });
    const t = page.getByRole('button', { name: 'Toggle menu' });
    await t.first().click();
    await page.getByRole('dialog').first().waitFor({ state: 'visible', timeout: 10000 }).catch(() => {});
    await page.waitForTimeout(800);
    const dialogs = await page.getByRole('dialog').count();
    await record(theme === 'light' ? 'mobile-390-sheet-open' : 'mobile-390-sheet-open-dark', page, vp, theme, logs, { state: 'sheet-open', dialogCount: dialogs });
    await ctx.close();
  }

  await browser.close();
  fs.writeFileSync(`${OUT}/before-report.json`, JSON.stringify(report, null, 2));
  console.log('DONE');
})().catch((e) => { console.error('FAILED', e); fs.writeFileSync(`${OUT}/before-report.partial.json`, JSON.stringify(report, null, 2)); process.exit(1); });
