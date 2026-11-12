// 638B "after" captures — Playwright LIBRARY script (not MCP: the MCP Chrome profile is held by
// the user's live session). Base: ../before/before.cjs (same login, `kazi-theme` init script,
// waits load + visible h1 + 1.2s, never networkidle). Run: node after-67a073f7d/after.cjs
// Stack: E2E mock-auth built from worktree-epic-638B @ 67a073f7d.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-638B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const path = require('path');
const OUT = __dirname;
const BASE = 'http://localhost:3001';
const DASH = `${BASE}/org/e2e-test-org/dashboard`;
const PALETTE_KEY = process.platform === 'darwin' ? 'Meta+k' : 'Control+k';
const report = { head: '67a073f7d', branch: 'worktree-epic-638B', capturedAt: new Date().toISOString(), paletteKey: PALETTE_KEY, captures: [], darkTokenCheck: {} };

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

async function headerFacts(page) {
  return page.evaluate(() => {
    const box = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height), bottom: Math.round(r.bottom) }; };
    const headers = [...document.querySelectorAll('header, [role="banner"]')];
    const el = headers.find((h) => h.getBoundingClientRect().height > 0) || null;
    const cs = el ? getComputedStyle(el) : null;
    const controls = el ? [...el.querySelectorAll('button, a, [role="button"], input')].filter((c) => c.getBoundingClientRect().width > 0).map((c) => ({
      tag: c.tagName.toLowerCase(),
      name: (c.getAttribute('aria-label') || c.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 60),
    })) : [];
    // first rendered content inside #main-content (deepest-first leaf with a non-zero box), and the subscription banner if any
    const main = document.querySelector('#main-content');
    let firstContent = null;
    if (main) {
      const walker = document.createTreeWalker(main, NodeFilter.SHOW_ELEMENT);
      let n;
      while ((n = walker.nextNode())) {
        const r = n.getBoundingClientRect();
        if (r.width > 0 && r.height > 0 && n.children.length === 0 && getComputedStyle(n).visibility !== 'hidden') { firstContent = { tag: n.tagName.toLowerCase(), text: (n.textContent || '').trim().slice(0, 60), top: Math.round(r.top) }; break; }
      }
    }
    const banner = main ? [...main.querySelectorAll('[class*="banner"], [data-testid*="banner"], [role="alert"], [role="status"]')].find((b) => b.getBoundingClientRect().height > 0) : null;
    const h1 = document.querySelector('h1');
    return {
      headerCount: headers.length,
      headerTag: el ? el.tagName.toLowerCase() : null,
      headerRole: el ? (el.getAttribute('role') || (el.tagName === 'HEADER' ? 'banner (implicit)' : null)) : null,
      headerInsideMain: el ? !!el.closest('main') : null,
      headerBox: box(el),
      headerPosition: cs ? cs.position : null,
      headerTop: cs ? cs.top : null,
      headerZIndex: cs ? cs.zIndex : null,
      headerBorderRadius: cs ? cs.borderRadius : null,
      headerBorder: cs ? `${cs.borderTopWidth} ${cs.borderTopStyle} ${cs.borderTopColor}` : null,
      headerBoxShadow: cs ? cs.boxShadow : null,
      headerBackground: cs ? cs.backgroundColor : null,
      headerBackdropFilter: cs ? (cs.backdropFilter || cs.webkitBackdropFilter) : null,
      headerOverflow: cs ? cs.overflow : null,
      headerClass: el ? el.className : null,
      headerControls: controls,
      mainBox: box(main),
      mainFirstContent: firstContent,
      h1Top: h1 ? Math.round(h1.getBoundingClientRect().top) : null,
      subscriptionBanner: banner ? { text: banner.textContent.trim().slice(0, 80), box: box(banner) } : null,
      scrollY: Math.round(window.scrollY),
      scrollHeight: document.documentElement.scrollHeight,
      innerHeight: window.innerHeight,
      htmlClass: document.documentElement.className,
      kaziThemeLS: localStorage.getItem('kazi-theme'),
      mainCount: document.querySelectorAll('main').length,
      h1: h1?.textContent.trim() ?? null,
      bodyBackground: getComputedStyle(document.body).backgroundColor,
    };
  });
}

async function roleCounts(page) {
  return {
    searchButtonExact: await page.getByRole('button', { name: 'Search', exact: true }).count(),
    searchButtonRegex: await page.getByRole('button', { name: /search/i }).count(),
    searchButtonRegexNames: await page.getByRole('button', { name: /search/i }).evaluateAll((els) => els.map((e) => {
      const r = e.getBoundingClientRect();
      return { name: (e.getAttribute('aria-label') || e.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 80), inHeader: !!e.closest('header'), inSidebar: !!e.closest('[data-sidebar="sidebar"], aside, [data-slot="sidebar"]'), visible: r.width > 0 && r.height > 0, box: { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) } };
    })),
    searchCommandKExact: await page.getByRole('button', { name: 'Search, Command K', exact: true }).count(),
    bannerSearchCount: await page.getByRole('banner').getByRole('button', { name: 'Search, Command K' }).count(),
    toggleMenuCount: await page.getByRole('button', { name: 'Toggle menu' }).count(),
    dialogCount: await page.getByRole('dialog').count(),
  };
}

async function tabOrder(page, n = 9) {
  await page.getByRole('button', { name: 'Toggle menu' }).first().focus();
  const describe = () => page.evaluate(() => {
    const a = document.activeElement;
    if (!a) return null;
    return { tag: a.tagName.toLowerCase(), name: (a.getAttribute('aria-label') || a.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 60), href: a.getAttribute('href'), inHeader: !!a.closest('header') };
  });
  const seq = [await describe()];
  for (let i = 0; i < n; i++) { await page.keyboard.press('Tab'); seq.push(await describe()); }
  await page.evaluate(() => document.activeElement && document.activeElement.blur());
  return seq;
}

async function paletteInfo(page) {
  return page.evaluate(() => {
    const d = [...document.querySelectorAll('[role="dialog"]')].find((x) => x.querySelector('[cmdk-input], input'));
    if (!d) return null;
    const r = d.getBoundingClientRect();
    const cs = getComputedStyle(d);
    // the visible surface may be d itself or its first child (CommandDialog className lands on the inner div)
    const surfaces = [d, ...d.querySelectorAll(':scope > div')].map((el) => { const s = getComputedStyle(el); const b = el.getBoundingClientRect(); return { tag: el.tagName.toLowerCase(), cls: (el.className || '').toString().slice(0, 200), bg: s.backgroundColor, radius: s.borderRadius, shadow: s.boxShadow, box: { x: Math.round(b.x), y: Math.round(b.y), width: Math.round(b.width), height: Math.round(b.height) } }; });
    const headings = [...d.querySelectorAll('[cmdk-group-heading]')].map((h) => { const s = getComputedStyle(h); return { text: h.textContent.trim(), textTransform: s.textTransform, fontSize: s.fontSize, letterSpacing: s.letterSpacing }; });
    const hints = [...d.querySelectorAll('[aria-hidden="true"]')].filter((el) => el.querySelector('kbd'));
    const footer = hints[hints.length - 1] || null;
    const footerText = footer ? footer.textContent.replace(/\s+/g, ' ').trim() : null;
    const kbds = footer ? [...footer.querySelectorAll('kbd')].map((k) => k.textContent.trim()) : [];
    const nonKbdText = footer ? (() => { const c = footer.cloneNode(true); c.querySelectorAll('kbd').forEach((k) => k.remove()); return c.textContent.trim(); })() : null;
    const input = d.querySelector('input');
    return {
      box: { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) },
      dialogBg: cs.backgroundColor, dialogRadius: cs.borderRadius, dialogShadow: cs.boxShadow,
      surfaces,
      ariaLabel: d.getAttribute('aria-label'),
      inputPlaceholder: input?.getAttribute('placeholder') ?? null,
      groupHeadings: headings,
      hintFooter: footer ? { exists: true, ariaHidden: footer.getAttribute('aria-hidden'), insideListbox: !!footer.closest('[role="listbox"]'), text: footerText, kbds, nonKbdText, onlyGlyphs: kbds.every((k) => ['↑', '↓', '↵', 'Esc'].includes(k)) && nonKbdText === '' } : { exists: false },
      optionCount: d.querySelectorAll('[role="option"]').length,
    };
  });
}

async function record(name, page, viewport, theme, logs, extra = {}) {
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  const f = await headerFacts(page);
  const c = await roleCounts(page);
  const hydration = logs.filter((l) => /hydrat/i.test(l.text));
  const entry = { file: `${name}.png`, url: page.url(), viewport, theme, ...f, ...c, ...extra, console: [...logs], hydrationMessages: hydration };
  report.captures.push(entry);
  console.log('captured', name, 'header', f.headerPosition, f.headerTop, JSON.stringify(f.headerBox), 'scrollY', f.scrollY, 'search', c.searchButtonExact, c.searchButtonRegex, 'toggle', c.toggleMenuCount, 'logs', logs.length);
  return entry;
}

const openDialog = (page) => page.getByRole('dialog').first().waitFor({ state: 'visible', timeout: 5000 }).then(() => true).catch(() => false);
const dialogGone = (page) => page.getByRole('dialog').first().waitFor({ state: 'hidden', timeout: 5000 }).then(() => true).catch(() => false);

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

  // 1. header at scroll 0 — 1024/1440 x light/dark (+ tab order on 1440)
  for (const w of [[1024, 768], [1440, 900]]) {
    for (const theme of ['light', 'dark']) {
      const vp = { width: w[0], height: w[1] };
      const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
      await gotoShell(page, DASH);
      const e = await record(`header-${w[0]}-${theme}`, page, vp, theme, logs);
      e.noOverlapAtScroll0 = !!e.headerBox && !!e.mainBox && e.headerBox.bottom <= e.mainBox.y && (!e.mainFirstContent || e.headerBox.bottom <= e.mainFirstContent.top) && (!e.subscriptionBanner || e.headerBox.bottom <= e.subscriptionBanner.box.y);
      if (w[0] === 1440) e.tabOrderFromToggle = await tabOrder(page);
      await ctx.close();
    }
  }

  // 2. scrolled header — dashboard, max scroll
  for (const theme of ['light', 'dark']) {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, DASH);
    const pre = await headerFacts(page);
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await page.waitForTimeout(600);
    const e = await record(`header-scrolled-1440-${theme}`, page, vp, theme, logs, { headerBoxBeforeScroll: pre.headerBox, scrollRequested: 'max' });
    e.headerYDeltaFromScroll0 = e.headerBox && pre.headerBox ? e.headerBox.y - pre.headerBox.y : null;
    // what paints under the header: element at a point inside the header band (should be header descendant) — and content passes beneath it
    e.headerHitTest = await page.evaluate(() => { const h = document.querySelector('header'); const r = h.getBoundingClientRect(); const pts = [[r.x + r.width / 2, r.y + r.height / 2], [r.x + 40, r.y + r.height / 2]]; return pts.map(([x, y]) => { const el = document.elementFromPoint(x, y); return { x: Math.round(x), y: Math.round(y), inHeader: !!(el && el.closest('header')), tag: el ? el.tagName.toLowerCase() : null }; }); });
    e.contentBeneathHeader = await page.evaluate(() => { const h = document.querySelector('header').getBoundingClientRect(); const m = document.querySelector('#main-content').getBoundingClientRect(); return { mainTop: Math.round(m.top), headerBottom: Math.round(h.bottom), mainScrolledUnderHeader: m.top < h.top }; });
    await ctx.close();
  }

  // 3. palette via Meta+k — toggle + Esc behaviour
  for (const theme of ['light', 'dark']) {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, DASH);
    await page.locator('body').click({ position: { x: 700, y: 500 } }).catch(() => {});
    await page.keyboard.press(PALETTE_KEY);
    const opened = await openDialog(page);
    await page.waitForTimeout(500);
    const info = await paletteInfo(page);
    const e = await record(`palette-1440-${theme}`, page, vp, theme, logs, { paletteOpened: opened, paletteDialog: info });
    if (theme === 'light') {
      // ⌘K again → closes (toggle); ⌘K → reopen; Esc → closes
      await page.keyboard.press(PALETTE_KEY);
      e.metaKSecondPressClosed = await dialogGone(page);
      await page.keyboard.press(PALETTE_KEY);
      e.metaKReopened = await openDialog(page);
      await page.keyboard.press('Escape');
      e.escClosed = await dialogGone(page);
    }
    await ctx.close();
  }

  // 3b. palette via header Search button click
  {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, 'light');
    await gotoShell(page, DASH);
    const btn = page.getByRole('banner').getByRole('button', { name: 'Search, Command K' });
    const btnCount = await btn.count();
    const btnInfo = await btn.first().evaluate((b) => ({ ariaKeyshortcuts: b.getAttribute('aria-keyshortcuts'), text: b.textContent.trim(), box: (() => { const r = b.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) }; })() }));
    await btn.first().click();
    const opened = await openDialog(page);
    await page.waitForTimeout(500);
    const info = await paletteInfo(page);
    const e = await record('palette-search-button-1440-light', page, vp, 'light', logs, { headerSearchButtonCount: btnCount, headerSearchButton: btnInfo, paletteOpened: opened, paletteDialog: info });
    await page.keyboard.press('Escape');
    e.escClosed = await dialogGone(page);
    e.focusAfterEsc = await page.evaluate(() => { const a = document.activeElement; return a ? (a.getAttribute('aria-label') || a.tagName) : null; });
    await ctx.close();
  }

  // 4. mobile
  for (const theme of ['light', 'dark']) {
    const vp = { width: 390, height: 844 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, DASH);
    const e = await record(`mobile-390-${theme}`, page, vp, theme, logs);
    e.noOverlapAtScroll0 = !!e.headerBox && !!e.mainBox && e.headerBox.bottom <= e.mainBox.y && (!e.mainFirstContent || e.headerBox.bottom <= e.mainFirstContent.top);
    e.headerOverflowsViewport = await page.evaluate(() => { const h = document.querySelector('header'); return { headerScrollWidth: h.scrollWidth, headerClientWidth: h.clientWidth, docScrollWidth: document.documentElement.scrollWidth, controlsOutside: [...h.querySelectorAll('button, a')].filter((c) => { const r = c.getBoundingClientRect(); return r.width > 0 && (r.right > window.innerWidth || r.left < 0); }).map((c) => c.getAttribute('aria-label') || c.textContent.trim()) }; });
    if (theme === 'light') {
      const btn = page.getByRole('banner').getByRole('button', { name: 'Search, Command K' });
      e.mobileHeaderSearchVisible = await btn.first().isVisible().catch(() => false);
      await btn.first().click();
      const opened = await openDialog(page);
      await page.waitForTimeout(500);
      const info = await paletteInfo(page);
      await record('mobile-390-palette-light', page, vp, 'light', logs, { paletteOpened: opened, paletteDialog: info });
    }
    await ctx.close();
  }

  // 5. user menu (1440 light + dark); in light, click "Dark" and confirm <html> class switches
  for (const theme of ['light', 'dark']) {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, theme);
    await gotoShell(page, DASH);
    let trigger = page.locator('header').getByRole('button', { name: 'User menu' });
    let via = 'aria-label=User menu';
    if (!(await trigger.count())) { trigger = page.locator('header button').filter({ hasText: /^[A-Z?…]{1,3}$/ }); via = 'header button with initials text'; }
    const triggerName = (await trigger.count()) ? (await trigger.first().textContent()).trim() : null;
    await trigger.first().click();
    await page.waitForTimeout(700);
    const themeVisible = await page.getByText('Theme', { exact: true }).first().isVisible().catch(() => false);
    const themeRadios = await page.getByRole('radio').evaluateAll((els) => els.map((e) => ({ name: (e.getAttribute('aria-label') || e.textContent || '').trim(), checked: e.getAttribute('aria-checked') || e.getAttribute('data-state') }))).catch(() => []);
    const menuBox = await page.evaluate(() => { const t = [...document.querySelectorAll('header p')].find((p) => p.textContent.trim() === 'Theme'); const panel = t ? t.closest('div.absolute') : null; if (!panel) return null; const r = panel.getBoundingClientRect(); const h = document.querySelector('header').getBoundingClientRect(); const cx = r.x + r.width / 2, cy = r.bottom - 10; const el = document.elementFromPoint(cx, cy); return { box: { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) }, extendsBelowHeader: r.bottom > h.bottom, hitTestBottomInPanel: !!(el && panel.contains(el)) }; });
    const e = await record(`user-menu-1440-${theme}`, page, vp, theme, logs, { userMenuTriggerVia: via, userMenuTriggerText: triggerName, themeLabelVisible: themeVisible, themeRadios, userMenuPanel: menuBox });
    if (theme === 'light') {
      e.htmlClassBeforeDark = await page.evaluate(() => document.documentElement.className);
      await page.getByRole('radio', { name: 'Dark' }).click();
      await page.waitForTimeout(500);
      e.htmlClassAfterDark = await page.evaluate(() => document.documentElement.className);
      e.kaziThemeAfterDark = await page.evaluate(() => localStorage.getItem('kazi-theme'));
      e.themeSwitchedViaHeader = /\bdark\b/.test(e.htmlClassAfterDark) && !/\bdark\b/.test(e.htmlClassBeforeDark);
      e.headerBgAfterSwitch = await page.evaluate(() => getComputedStyle(document.querySelector('header')).backgroundColor);
    }
    await ctx.close();
  }

  // 6. bell opened while scrolled — must paint above page content
  {
    const vp = { width: 1440, height: 900 };
    const { ctx, page, logs } = await newCtx(browser, state, vp, 'light');
    await gotoShell(page, DASH);
    await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
    await page.waitForTimeout(600);
    await page.getByRole('banner').getByRole('button', { name: 'Notifications' }).click();
    await page.locator('header [role="dialog"][aria-label="Notifications"]').waitFor({ state: 'visible', timeout: 5000 }).catch(() => {});
    await page.waitForTimeout(900);
    const bell = await page.evaluate(() => {
      const d = document.querySelector('header [role="dialog"][aria-label="Notifications"]');
      if (!d) return { found: false };
      const r = d.getBoundingClientRect();
      const h = document.querySelector('header').getBoundingClientRect();
      const pts = [[r.x + r.width / 2, r.y + r.height / 2], [r.x + 20, r.bottom - 10], [r.right - 20, r.bottom - 10]].map(([x, y]) => { const el = document.elementFromPoint(x, y); return { x: Math.round(x), y: Math.round(y), inDropdown: !!(el && d.contains(el)), hitTag: el ? el.tagName.toLowerCase() : null }; });
      return { found: true, box: { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height), bottom: Math.round(r.bottom) }, headerBottom: Math.round(h.bottom), extendsBelowHeader: r.bottom > h.bottom, zIndex: getComputedStyle(d).zIndex, hitTests: pts, allPointsInDropdown: pts.every((p) => p.inDropdown), text: d.textContent.trim().replace(/\s+/g, ' ').slice(0, 120) };
    });
    await record('bell-1440-light', page, vp, 'light', logs, { bellDropdown: bell });
    await ctx.close();
  }

  // 7. dark token check — header bg and palette bg light vs dark
  {
    const out = {};
    for (const theme of ['light', 'dark']) {
      const vp = { width: 1440, height: 900 };
      const { ctx, page } = await newCtx(browser, state, vp, theme);
      await gotoShell(page, DASH);
      const headerBg = await page.evaluate(() => getComputedStyle(document.querySelector('header')).backgroundColor);
      const vars = await page.evaluate(() => { const s = getComputedStyle(document.documentElement); return { card: s.getPropertyValue('--card').trim(), popover: s.getPropertyValue('--popover').trim(), shadowOverlay: s.getPropertyValue('--shadow-overlay').trim(), htmlClass: document.documentElement.className }; });
      await page.keyboard.press(PALETTE_KEY);
      await openDialog(page);
      await page.waitForTimeout(400);
      const p = await paletteInfo(page);
      out[theme] = { headerBg, vars, paletteSurfaces: p ? p.surfaces.map((s) => ({ cls: s.cls.slice(0, 80), bg: s.bg })) : null };
      await ctx.close();
    }
    out.headerBgDiffers = out.light.headerBg !== out.dark.headerBg;
    const pb = (t) => (out[t].paletteSurfaces || []).map((s) => s.bg).join('|');
    out.paletteBgDiffers = pb('light') !== pb('dark');
    report.darkTokenCheck = out;
    console.log('dark check', JSON.stringify(out));
  }

  await browser.close();
  fs.writeFileSync(path.join(OUT, 'after-report.json'), JSON.stringify(report, null, 2));
  console.log('DONE');
})().catch((e) => { console.error('FAILED', e); fs.writeFileSync(path.join(OUT, 'after-report.partial.json'), JSON.stringify(report, null, 2)); process.exit(1); });
