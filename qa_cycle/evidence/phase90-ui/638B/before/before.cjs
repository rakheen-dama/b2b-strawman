// 638B "before" captures — Playwright LIBRARY script (not MCP: the MCP Chrome profile is held by
// the user's live session). Run: node before/before.cjs  (stack: E2E mock-auth @ main 9d7d2b62e)
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-638B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const path = require('path');
const OUT = __dirname;
const BASE = 'http://localhost:3001';
const DASH = `${BASE}/org/e2e-test-org/dashboard`;
const PROJECTS = `${BASE}/org/e2e-test-org/projects`;
const PALETTE_KEY = process.platform === 'darwin' ? 'Meta+k' : 'Control+k';
const report = { head: '9d7d2b62e', branch: 'main', capturedAt: new Date().toISOString(), paletteKey: PALETTE_KEY, captures: [] };

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

// Header facts: <header> / [role=banner], else the container holding "Toggle menu".
async function headerFacts(page) {
  return page.evaluate(() => {
    const box = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) }; };
    const headers = [...document.querySelectorAll('header, [role="banner"]')];
    let el = headers.find((h) => h.getBoundingClientRect().height > 0) || null;
    let via = el ? 'header/[role=banner]' : null;
    if (!el) {
      const t = [...document.querySelectorAll('button')].find((b) => (b.getAttribute('aria-label') || b.textContent || '').trim().includes('Toggle menu'));
      el = t ? t.parentElement : null;
      via = el ? 'parent of Toggle menu' : null;
    }
    const cs = el ? getComputedStyle(el) : null;
    const controls = el ? [...el.querySelectorAll('button, a, [role="button"], input')].filter((c) => c.getBoundingClientRect().width > 0).map((c) => ({
      tag: c.tagName.toLowerCase(),
      name: (c.getAttribute('aria-label') || c.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 60),
    })) : [];
    return {
      headerFoundVia: via,
      headerCount: headers.length,
      headerTag: el ? el.tagName.toLowerCase() : null,
      headerRole: el ? (el.getAttribute('role') || (el.tagName === 'HEADER' ? 'banner (implicit)' : null)) : null,
      headerBox: box(el),
      headerPosition: cs ? cs.position : null,
      headerTop: cs ? cs.top : null,
      headerZIndex: cs ? cs.zIndex : null,
      headerBackground: cs ? cs.backgroundColor : null,
      headerBackdropFilter: cs ? (cs.backdropFilter || cs.webkitBackdropFilter) : null,
      headerClass: el ? el.className : null,
      headerControls: controls,
      scrollY: Math.round(window.scrollY),
      scrollHeight: document.documentElement.scrollHeight,
      innerHeight: window.innerHeight,
      htmlClass: document.documentElement.className,
      kaziThemeLS: localStorage.getItem('kazi-theme'),
      mainCount: document.querySelectorAll('main').length,
      h1: document.querySelector('h1')?.textContent.trim() ?? null,
    };
  });
}

async function roleCounts(page) {
  return {
    searchButtonExact: await page.getByRole('button', { name: 'Search', exact: true }).count(),
    searchButtonRegex: await page.getByRole('button', { name: /search/i }).count(),
    searchButtonRegexNames: await page.getByRole('button', { name: /search/i }).evaluateAll((els) => els.map((e) => {
      const r = e.getBoundingClientRect();
      return { name: (e.getAttribute('aria-label') || e.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 80), inHeader: !!e.closest('header'), inSidebar: !!e.closest('[data-sidebar="sidebar"], aside, [data-slot="sidebar"]'), box: { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) } };
    })),
    toggleMenuCount: await page.getByRole('button', { name: 'Toggle menu' }).count(),
    dialogCount: await page.getByRole('dialog').count(),
  };
}

async function tabOrder(page, n = 8) {
  const trigger = page.getByRole('button', { name: 'Toggle menu' }).first();
  await trigger.focus();
  const seq = [];
  const describe = () => page.evaluate(() => {
    const a = document.activeElement;
    if (!a) return null;
    const inHeader = !!a.closest('header');
    return { tag: a.tagName.toLowerCase(), name: (a.getAttribute('aria-label') || a.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 60), href: a.getAttribute('href'), inHeader };
  });
  seq.push(await describe());
  for (let i = 0; i < n; i++) { await page.keyboard.press('Tab'); seq.push(await describe()); }
  await page.evaluate(() => document.activeElement && document.activeElement.blur());
  return seq;
}

async function record(name, page, viewport, theme, logs, extra = {}) {
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  const f = await headerFacts(page);
  const c = await roleCounts(page);
  const hydration = logs.filter((l) => /hydrat/i.test(l.text));
  report.captures.push({ file: `${name}.png`, url: page.url(), viewport, theme, ...f, ...c, ...extra, console: [...logs], hydrationMessages: hydration });
  console.log('captured', name, 'header', f.headerTag, f.headerPosition, JSON.stringify(f.headerBox), 'scrollY', f.scrollY, 'search', c.searchButtonExact, c.searchButtonRegex, 'toggle', c.toggleMenuCount, 'logs', logs.length);
}

(async () => {
  const browser = await chromium.launch({ headless: true });
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await lp.getByRole('button', { name: 'Sign In' }).click();
  await lp.waitForURL('**/org/e2e-test-org/dashboard', { timeout: 60000 });
  await lp.locator('h1').first().waitFor({ timeout: 30000 });
  const state = await lctx.storageState();
  await lctx.close();

  // 1. header at scroll 0 — 1024/1440 x light/dark (+ tab order on 1440 light)
  for (const w of [[1024, 768], [1440, 900]]) {
    for (const theme of ['light', 'dark']) {
      const vp = { width: w[0], height: w[1] };
      const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
      await gotoShell(page, DASH);
      const extra = {};
      await record(`header-${w[0]}-${theme}`, page, vp, theme, logs, extra);
      if (w[0] === 1440) report.captures[report.captures.length - 1].tabOrderFromToggle = await tabOrder(page);
      await ctx.close();
    }
  }

  // 2. scrolled header — pick the longer of dashboard / projects
  const heights = {};
  {
    const { ctx, page } = await newCtx(browser, state, { width: 1440, height: 900 }, 'light');
    for (const [k, u] of [['dashboard', DASH], ['projects', PROJECTS]]) {
      await gotoShell(page, u);
      heights[k] = await page.evaluate(() => document.documentElement.scrollHeight);
    }
    await ctx.close();
  }
  const scrollTarget = heights.dashboard >= heights.projects ? DASH : PROJECTS;
  report.scrollHeights1440 = heights;
  report.scrollTarget = scrollTarget;
  for (const theme of ['light', 'dark']) {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, scrollTarget);
    const pre = await headerFacts(page);
    await page.evaluate(() => window.scrollTo(0, 600));
    await page.waitForTimeout(600);
    await record(`header-scrolled-1440-${theme}`, page, vp, theme, logs, { headerBoxBeforeScroll: pre.headerBox, scrollRequested: 600 });
    const last = report.captures[report.captures.length - 1];
    last.headerVisibleAfterScroll = !!last.headerBox && last.headerBox.y + last.headerBox.height > 0 && last.headerBox.y < vp.height;
    await ctx.close();
  }

  // 3. command palette
  for (const theme of ['light', 'dark']) {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, DASH);
    await page.locator('body').click({ position: { x: 700, y: 500 } }).catch(() => {});
    await page.keyboard.press(PALETTE_KEY);
    let opened = await page.getByRole('dialog').first().waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false);
    if (!opened) { await page.keyboard.press('Control+k'); opened = await page.getByRole('dialog').first().waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false); }
    await page.waitForTimeout(500);
    const dialogInfo = await page.evaluate(() => {
      const d = document.querySelector('[role="dialog"]');
      if (!d) return null;
      const r = d.getBoundingClientRect();
      const input = d.querySelector('input');
      return { box: { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) }, ariaLabel: d.getAttribute('aria-label'), inputPlaceholder: input?.getAttribute('placeholder') ?? null, text: d.textContent.trim().replace(/\s+/g, ' ').slice(0, 300) };
    });
    await record(`palette-1440-${theme}`, page, vp, theme, logs, { paletteOpened: opened, paletteDialog: dialogInfo });
    await ctx.close();
  }

  // 4. mobile
  for (const theme of ['light', 'dark']) {
    const vp = { width: 390, height: 844 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, DASH);
    await record(`mobile-390-${theme}`, page, vp, theme, logs);
    await ctx.close();
  }

  // 5. user menu (1440 light) — mock mode: initials button in the header's right cluster
  {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, 'light');
    await gotoShell(page, DASH);
    let trigger = page.locator('header').getByRole('button', { name: 'User menu' });
    let via = 'aria-label=User menu';
    if (!(await trigger.count())) {
      trigger = page.locator('header button').filter({ hasText: /^[A-Z?…]{1,3}$/ });
      via = 'header button with initials text';
    }
    const triggerName = (await trigger.count()) ? (await trigger.first().textContent()).trim() : null;
    await trigger.first().click();
    await page.waitForTimeout(700);
    const themeVisible = await page.getByText('Theme', { exact: true }).first().isVisible().catch(() => false);
    const themeRadios = await page.getByRole('radio').evaluateAll((els) => els.map((e) => (e.getAttribute('aria-label') || e.textContent || '').trim())).catch(() => []);
    await record('user-menu-1440-light', page, vp, 'light', logs, { userMenuTriggerVia: via, userMenuTriggerText: triggerName, themeLabelVisible: themeVisible, themeRadios });
    await ctx.close();
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'before-report.json'), JSON.stringify(report, null, 2));
  console.log('DONE');
})().catch((e) => { console.error('FAILED', e); fs.writeFileSync(path.join(OUT, 'before-report.partial.json'), JSON.stringify(report, null, 2)); process.exit(1); });
