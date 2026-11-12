// 638A OBS (task 638.7): new shadcn sidebar shell on the mock-auth e2e stack (head e25c38f11).
// Playwright LIBRARY (headless Chromium) — the Playwright MCP profile is held by the user's live session.
// Read-only: navigation + UI-only state (sidebar_state cookie, zone toggles, kazi-theme, mobile Sheet,
// an unsaved New Clause editor that is closed without saving).
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-638A-gate/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = __dirname;
const BASE = 'http://localhost:3001';
const DASH = `${BASE}/org/e2e-test-org/dashboard`;
const MATTERS = `${BASE}/org/e2e-test-org/matters`;
const CLAUSES = `${BASE}/org/e2e-test-org/settings/documents?tab=clauses`;
const report = { head: 'e25c38f110004ef67941ce77dbe7db746234fa7d', capturedAt: new Date().toISOString(), obs: {}, pages: [] };
const allLogs = [];
const save = () => fs.writeFileSync(`${OUT}/obs-report.json`, JSON.stringify(report, null, 2));

async function newCtx(browser, state, viewport, theme, sidebar) {
  const ctx = await browser.newContext({ storageState: state, viewport });
  if (theme) await ctx.addInitScript((t) => { try { localStorage.setItem('kazi-theme', t); } catch (e) {} }, theme);
  if (sidebar) await ctx.addCookies([{ name: 'sidebar_state', value: sidebar, url: BASE }]);
  const page = await ctx.newPage();
  const logs = [];
  const tag = () => page.url();
  page.on('console', (m) => { if (['error', 'warning'].includes(m.type())) { const l = { url: tag(), type: m.type(), text: m.text().slice(0, 600) }; logs.push(l); allLogs.push(l); } });
  page.on('pageerror', (e) => { const l = { url: tag(), type: 'pageerror', text: String(e).slice(0, 600) }; logs.push(l); allLogs.push(l); });
  return { ctx, page, logs };
}

async function gotoShell(page, url) {
  await page.goto(url, { waitUntil: 'load' });
  await page.locator('h1').first().waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForTimeout(1200);
}

async function cookieVal(ctx) {
  const c = (await ctx.cookies(BASE)).find((x) => x.name === 'sidebar_state');
  return c ? c.value : null;
}

async function facts(page) {
  const f = await page.evaluate(() => {
    const box = (el) => { if (!el) return null; const r = el.getBoundingClientRect(); return { x: Math.round(r.x), y: Math.round(r.y), width: Math.round(r.width), height: Math.round(r.height) }; };
    const sb = document.querySelector('[data-slot="sidebar"]:not([data-mobile])');
    const container = document.querySelector('[data-slot="sidebar-container"]');
    const gap = document.querySelector('[data-slot="sidebar-gap"]');
    const nav = [...document.querySelectorAll('nav[aria-label="Main navigation"]')];
    const vNav = nav.find((n) => n.getBoundingClientRect().width > 0) || nav[0];
    const zones = vNav ? [...vNav.querySelectorAll('button[aria-expanded]')].map((b) => ({ label: b.textContent.trim(), expanded: b.getAttribute('aria-expanded'), display: getComputedStyle(b).display, visible: b.getBoundingClientRect().width > 0 })) : [];
    const current = vNav ? [...vNav.querySelectorAll('a[aria-current="page"]')].map((a) => ({ text: a.textContent.trim(), href: a.getAttribute('href') })) : [];
    const content = document.querySelector('[data-slot="sidebar-content"]');
    return {
      htmlClass: document.documentElement.className,
      kaziThemeLS: localStorage.getItem('kazi-theme'),
      sidebarDataState: sb ? sb.getAttribute('data-state') : null,
      sidebarDataCollapsible: sb ? sb.getAttribute('data-collapsible') : null,
      sidebarContainerBox: box(container),
      sidebarGapWidth: gap ? Math.round(gap.getBoundingClientRect().width) : null,
      sidebarBg: container ? getComputedStyle(container.firstElementChild).backgroundColor : null,
      sidebarBorderRight: container ? `${getComputedStyle(container).borderRightWidth} ${getComputedStyle(container).borderRightColor}` : null,
      mainBg: document.querySelector('#main-content') ? getComputedStyle(document.querySelector('#main-content')).backgroundColor : null,
      mainContentBox: box(document.querySelector('#main-content')),
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
      innerWidth: window.innerWidth,
      mainCount: document.querySelectorAll('main').length,
      mainNavDomCount: nav.length,
      zones,
      ariaCurrent: current,
      separatorsInNav: vNav ? vNav.querySelectorAll('[data-slot="sidebar-separator"]').length : null,
      contentScroll: content ? { scrollHeight: content.scrollHeight, clientHeight: content.clientHeight, overflowY: getComputedStyle(content).overflowY } : null,
      h1: document.querySelector('h1')?.textContent.trim() ?? null,
    };
  });
  const navLoc = page.getByRole('navigation', { name: 'Main navigation' });
  f.mainNavByRoleCount = await navLoc.count();
  f.links = f.mainNavByRoleCount ? (await navLoc.first().getByRole('link').evaluateAll((as) => as.map((a) => a.textContent.trim()))) : [];
  f.linkCount = f.links.length;
  f.toggleMenuByRole = await page.getByRole('button', { name: 'Toggle menu', exact: true }).count();
  f.noHorizontalOverflow = f.scrollWidth <= f.innerWidth;
  return f;
}

async function record(name, page, extra = {}) {
  if (name) await page.screenshot({ path: `${OUT}/${name}.png` });
  const f = await facts(page);
  report.pages.push({ file: name ? `${name}.png` : null, url: page.url(), ...extra, ...f });
  console.log('captured', name, page.url(), f.sidebarDataState, JSON.stringify(f.sidebarContainerBox), 'main', JSON.stringify(f.mainContentBox), 'links', f.linkCount);
  return f;
}

const SAMPLER = () => {
  window.__sb = [];
  const t0 = performance.now();
  const sample = (src) => {
    const c = document.querySelector('[data-slot="sidebar-container"]');
    const s = document.querySelector('[data-slot="sidebar"]:not([data-mobile])');
    if (!c) return;
    const w = Math.round(c.getBoundingClientRect().width * 10) / 10;
    const st = s ? s.getAttribute('data-state') : null;
    const last = window.__sb[window.__sb.length - 1];
    if (!last || last.w !== w || last.st !== st) window.__sb.push({ t: Math.round(performance.now() - t0), src, w, st, rs: document.readyState });
    window.__sbN = (window.__sbN || 0) + 1;
  };
  new MutationObserver(() => sample('mo')).observe(document, { childList: true, subtree: true, attributes: true, attributeFilter: ['data-state', 'style', 'class'] });
  const loop = () => { sample('raf'); if (performance.now() - t0 < 4000) requestAnimationFrame(loop); };
  requestAnimationFrame(loop);
};

(async () => {
  const browser = await chromium.launch({ headless: true });
  const lctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const lp = await lctx.newPage();
  await lp.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await lp.getByRole('button', { name: 'Sign In' }).click();
  await lp.waitForURL('**/org/e2e-test-org/dashboard', { timeout: 60000 });
  await lp.locator('h1').first().waitFor({ timeout: 30000 });
  const state = await lctx.storageState();
  state.cookies = state.cookies.filter((c) => c.name !== 'sidebar_state');
  await lctx.close();

  // ---- 1/2. shell grid
  const grid = [];
  for (const [w, h] of [[1024, 768], [1440, 900]]) for (const theme of ['light', 'dark']) for (const mode of ['expanded', 'collapsed']) {
    const { ctx, page } = await newCtx(browser, state, { width: w, height: h }, theme, mode === 'collapsed' ? 'false' : 'true');
    await gotoShell(page, DASH);
    const f = await record(`shell-${w}-${theme}-${mode}`, page, { viewport: { width: w, height: h }, theme, mode });
    grid.push({ file: `shell-${w}-${theme}-${mode}.png`, sidebarWidth: f.sidebarContainerBox?.width, dataState: f.sidebarDataState, mainX: f.mainContentBox?.x, mainWidth: f.mainContentBox?.width, scrollWidth: f.scrollWidth, innerWidth: f.innerWidth, mainCount: f.mainCount, toggleMenuByRole: f.toggleMenuByRole, zones: f.zones.filter((z) => z.visible).map((z) => `${z.label}=${z.expanded}`), linkCount: f.linkCount, ariaCurrent: f.ariaCurrent.map((a) => a.text), htmlClass: f.htmlClass });
    await ctx.close();
  }
  report.obs.o1_grid = grid;
  const before = JSON.parse(fs.readFileSync(`${OUT}/before/before-report.json`, 'utf8')).captures.find((c) => c.file === 'shell-1440-light.png');
  const nowExp = report.pages.find((p) => p.file === 'shell-1440-light-expanded.png');
  report.obs.o2_contract = {
    beforeZones: before.zones.map((z) => `${z.label}=${z.expanded}`),
    nowZones: nowExp.zones.map((z) => `${z.label}=${z.expanded}`),
    zonesEqual: JSON.stringify(before.zones.map((z) => `${z.label}=${z.expanded}`)) === JSON.stringify(nowExp.zones.map((z) => `${z.label}=${z.expanded}`)),
    beforeLinks: before.links, nowLinks: nowExp.links,
    linksEqual: JSON.stringify(before.links) === JSON.stringify(nowExp.links),
    beforeAriaCurrent: before.ariaCurrent, nowAriaCurrent: nowExp.ariaCurrent,
  };
  save();

  // ---- 3. cookie persistence + no width shift
  {
    const o3 = {};
    const { ctx, page } = await newCtx(browser, state, { width: 1440, height: 900 }, 'light', null);
    await gotoShell(page, DASH);
    o3.cookieBeforeToggle = await cookieVal(ctx);
    o3.stateBeforeToggle = (await facts(page)).sidebarDataState;
    await page.getByRole('button', { name: 'Toggle menu', exact: true }).click();
    await page.waitForTimeout(500);
    o3.afterClick = { cookie: await cookieVal(ctx), state: (await facts(page)).sidebarDataState, width: (await facts(page)).sidebarContainerBox.width };
    await page.addInitScript(SAMPLER);
    const cdp = await ctx.newCDPSession(page);
    for (const [label, throttle] of [['collapsed-normal', 1], ['collapsed-cpu6x', 6]]) {
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: throttle });
      await page.reload({ waitUntil: 'load' });
      await page.locator('h1').first().waitFor({ timeout: 30000 });
      await page.waitForTimeout(4500);
      const samples = await page.evaluate(() => ({ samples: window.__sb, n: window.__sbN }));
      o3[label] = { cookie: await cookieVal(ctx), ...samples, afterLoad: (await facts(page)).sidebarContainerBox.width, firstWidth: samples.samples[0]?.w, distinctWidths: [...new Set(samples.samples.map((s) => s.w))] };
    }
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 });
    // expand again via button, then reload
    await page.getByRole('button', { name: 'Toggle menu', exact: true }).click();
    await page.waitForTimeout(500);
    o3.afterSecondClick = { cookie: await cookieVal(ctx), state: (await facts(page)).sidebarDataState };
    for (const [label, throttle] of [['expanded-normal', 1], ['expanded-cpu6x', 6]]) {
      await cdp.send('Emulation.setCPUThrottlingRate', { rate: throttle });
      await page.reload({ waitUntil: 'load' });
      await page.locator('h1').first().waitFor({ timeout: 30000 });
      await page.waitForTimeout(4500);
      const samples = await page.evaluate(() => ({ samples: window.__sb, n: window.__sbN }));
      o3[label] = { cookie: await cookieVal(ctx), ...samples, afterLoad: (await facts(page)).sidebarContainerBox.width, firstWidth: samples.samples[0]?.w, distinctWidths: [...new Set(samples.samples.map((s) => s.w))] };
    }
    await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 });
    report.obs.o3_persistence = o3;
    save();

    // ---- 4. Ctrl/Meta+B on body
    const o4 = [];
    await gotoShell(page, DASH);
    for (const key of ['Control+b', 'Control+b', 'Meta+b', 'Meta+b']) {
      await page.evaluate(() => { if (document.activeElement && document.activeElement !== document.body) document.activeElement.blur(); });
      const beforeSt = (await facts(page)).sidebarDataState;
      await page.keyboard.press(key);
      await page.waitForTimeout(400);
      const f = await facts(page);
      o4.push({ key, activeElement: await page.evaluate(() => document.activeElement.tagName), before: beforeSt, after: f.sidebarDataState, width: f.sidebarContainerBox.width, cookie: await cookieVal(ctx) });
    }
    report.obs.o4_shortcut = o4;
    save();

    // ---- 5. ⌘B inside Tiptap (New Clause sheet, never saved)
    const o5 = {};
    try {
      await gotoShell(page, CLAUSES);
      o5.url = page.url();
      await page.getByRole('button', { name: 'New Clause' }).click();
      const dlg = page.getByRole('dialog');
      await dlg.waitFor({ timeout: 15000 });
      const pm = dlg.locator('.ProseMirror').first();
      await pm.waitFor({ timeout: 20000 });
      await pm.click();
      await page.keyboard.type('Boldword');
      await page.keyboard.press('ControlOrMeta+a');
      const st0 = (await facts(page)).sidebarDataState;
      const ck0 = await cookieVal(ctx);
      await page.keyboard.press('Meta+b');
      await page.waitForTimeout(400);
      o5.metaB = { sidebarBefore: st0, sidebarAfter: (await facts(page)).sidebarDataState, cookieBefore: ck0, cookieAfter: await cookieVal(ctx), html: await pm.innerHTML(), hasStrong: (await pm.locator('strong').count()) > 0 };
      await page.screenshot({ path: `${OUT}/tiptap-meta-b.png` });
      const st1 = (await facts(page)).sidebarDataState;
      await page.keyboard.press('Control+b');
      await page.waitForTimeout(400);
      o5.ctrlB = { sidebarBefore: st1, sidebarAfter: (await facts(page)).sidebarDataState, cookieAfter: await cookieVal(ctx), html: await pm.innerHTML(), hasStrong: (await pm.locator('strong').count()) > 0 };
      o5.platform = await page.evaluate(() => navigator.platform);
      await dlg.getByRole('button', { name: 'Close', exact: true }).first().click();
      await page.waitForTimeout(600);
      o5.dialogOpenAfterClose = await page.getByRole('dialog').count();
      o5.noSaveClicked = true;
    } catch (e) { o5.error = String(e).slice(0, 500); }
    report.obs.o5_tiptap = o5;
    save();
    await ctx.close();
  }

  // ---- 6. collapsed icon rail
  {
    const o6 = {};
    const { ctx, page } = await newCtx(browser, state, { width: 1440, height: 900 }, 'light', 'false');
    await gotoShell(page, DASH);
    const f = await facts(page);
    o6.dataState = f.sidebarDataState; o6.linkCount = f.linkCount; o6.links = f.links;
    o6.zoneTriggers = f.zones; o6.separatorsInNav = f.separatorsInNav; o6.contentScroll = f.contentScroll;
    // focusability of triggers: Tab through the whole sidebar and see whether any trigger receives focus
    const seen = [];
    await page.evaluate(() => document.activeElement && document.activeElement.blur());
    for (let i = 0; i < 45; i++) {
      await page.keyboard.press('Tab');
      seen.push(await page.evaluate(() => { const a = document.activeElement; return { tag: a.tagName, txt: (a.getAttribute('aria-label') || a.textContent || '').trim().slice(0, 40), trigger: a.hasAttribute('aria-expanded') && !!a.closest('nav[aria-label="Main navigation"]'), inNav: !!a.closest('nav[aria-label="Main navigation"]') }; }));
    }
    o6.tabSeenTriggerFocused = seen.some((s) => s.trigger);
    o6.tabNavStops = seen.filter((s) => s.inNav).length;
    // tooltip on hover
    const links = page.getByRole('navigation', { name: 'Main navigation' }).getByRole('link');
    await page.evaluate(() => document.activeElement && document.activeElement.blur());
    await links.nth(3).hover();
    await page.waitForTimeout(500);
    o6.hoverTooltip = await page.locator('[data-slot="tooltip-content"]').allTextContents();
    await page.screenshot({ path: `${OUT}/rail-tooltip-hover.png` });
    await page.mouse.move(900, 500);
    await page.waitForTimeout(300);
    await links.nth(0).focus();
    await page.waitForTimeout(200);
    await page.keyboard.press('Tab');
    await page.waitForTimeout(500);
    o6.focusTarget = await page.evaluate(() => document.activeElement.textContent.trim());
    o6.focusTooltip = await page.locator('[data-slot="tooltip-content"]').allTextContents();
    await page.screenshot({ path: `${OUT}/rail-tooltip-focus.png` });
    await page.evaluate(() => document.activeElement && document.activeElement.blur());
    await page.mouse.move(900, 500);
    await page.evaluate(() => { const c = document.querySelector('[data-slot="sidebar-content"]'); c.scrollTop = c.scrollHeight; });
    await page.waitForTimeout(400);
    o6.scrollTopAfter = await page.evaluate(() => document.querySelector('[data-slot="sidebar-content"]').scrollTop);
    await page.screenshot({ path: `${OUT}/rail-1440-collapsed-bottom.png` });
    report.obs.o6_rail = o6;
    save();
    await ctx.close();
  }

  // ---- 7. keyboard expanded
  {
    const o7 = {};
    const { ctx, page } = await newCtx(browser, state, { width: 1440, height: 900 }, 'light', 'true');
    await gotoShell(page, DASH);
    const seq = [];
    for (let i = 0; i < 22; i++) {
      await page.keyboard.press('Tab');
      seq.push(await page.evaluate(() => { const a = document.activeElement; return { tag: a.tagName.toLowerCase(), role: a.getAttribute('role') || (a.tagName === 'A' ? 'link' : a.tagName === 'BUTTON' ? 'button' : a.tagName.toLowerCase()), name: (a.getAttribute('aria-label') || a.textContent || '').trim().replace(/\s+/g, ' ').slice(0, 50), expanded: a.getAttribute('aria-expanded'), inNav: !!a.closest('nav[aria-label="Main navigation"]') }; }));
    }
    o7.focusSequence = seq;
    const nav = page.getByRole('navigation', { name: 'Main navigation' });
    const fees = nav.getByRole('button', { name: 'Fees' });
    await fees.focus();
    o7.feesBefore = await fees.getAttribute('aria-expanded');
    await page.keyboard.press('Enter');
    await page.waitForTimeout(400);
    o7.feesAfterEnter = await fees.getAttribute('aria-expanded');
    o7.linksAfterEnter = await nav.getByRole('link').evaluateAll((as) => as.map((a) => a.textContent.trim()));
    await page.screenshot({ path: `${OUT}/keyboard-zone-toggle.png` });
    await page.keyboard.press('Space');
    await page.waitForTimeout(400);
    o7.feesAfterSpace = await fees.getAttribute('aria-expanded');
    o7.linkCountAfterSpace = await nav.getByRole('link').count();
    o7.sidebarStateUnchanged = (await facts(page)).sidebarDataState;
    report.obs.o7_keyboard = o7;
    save();
    await ctx.close();
  }

  // ---- 8. mobile
  {
    const o8 = {};
    for (const theme of ['light', 'dark']) {
      const r = {};
      const { ctx, page } = await newCtx(browser, state, { width: 390, height: 844 }, theme, null);
      await gotoShell(page, DASH);
      const f0 = await record(`mobile-390-closed-${theme}`, page, { viewport: { width: 390, height: 844 }, theme, mode: 'mobile-closed' });
      r.closed = { mainNavByRole: f0.mainNavByRoleCount, mainNavDom: f0.mainNavDomCount, toggleMenuByRole: f0.toggleMenuByRole, mainBox: f0.mainContentBox, scrollWidth: f0.scrollWidth, htmlClass: f0.htmlClass };
      await page.getByRole('button', { name: 'Toggle menu', exact: true }).click();
      const dlg = page.getByRole('dialog', { name: 'Kazi' });
      await dlg.waitFor({ timeout: 10000 });
      await page.waitForTimeout(600);
      await page.screenshot({ path: `${OUT}/mobile-390-sheet-${theme}.png` });
      const closeBtn = dlg.getByRole('button', { name: 'Close', exact: true });
      r.sheet = {
        dialogNamedKazi: await dlg.count(),
        dialogsTotal: await page.getByRole('dialog').count(),
        dialogBox: await dlg.boundingBox(),
        closeButtonCount: await closeBtn.count(),
        closeButtonVisible: await closeBtn.isVisible(),
        closeButtonBox: await closeBtn.boundingBox(),
        mainNavByRole: await page.getByRole('navigation', { name: 'Main navigation' }).count(),
        mainNavDom: await page.evaluate(() => document.querySelectorAll('nav[aria-label="Main navigation"]').length),
        links: await dlg.getByRole('navigation', { name: 'Main navigation' }).getByRole('link').evaluateAll((as) => as.map((a) => a.textContent.trim())),
      };
      if (theme === 'light') {
        await dlg.getByRole('navigation', { name: 'Main navigation' }).getByRole('link', { name: 'Matters', exact: true }).click();
        await page.waitForURL('**/org/e2e-test-org/matters', { timeout: 30000 });
        await page.locator('h1').first().waitFor({ timeout: 30000 });
        await page.waitForTimeout(1000);
        r.afterNav = { url: page.url(), dialogCount: await page.getByRole('dialog').count(), h1: await page.locator('h1').first().textContent() };
        await page.screenshot({ path: `${OUT}/mobile-390-after-nav.png` });
      } else {
        await closeBtn.click();
        await page.waitForTimeout(500);
        r.afterClose = { dialogCount: await page.getByRole('dialog').count() };
      }
      o8[theme] = r;
      await ctx.close();
    }
    report.obs.o8_mobile = o8;
    save();
  }

  // ---- 9. matters
  {
    const { ctx, page } = await newCtx(browser, state, { width: 1440, height: 900 }, 'light', 'true');
    await gotoShell(page, MATTERS);
    const f = await record('matters-1440-light', page, { viewport: { width: 1440, height: 900 }, theme: 'light', mode: 'expanded' });
    report.obs.o9_matters = { h1: f.h1, ariaCurrent: f.ariaCurrent, sidebarWidth: f.sidebarContainerBox.width, mainBox: f.mainContentBox, scrollWidth: f.scrollWidth };
    await ctx.close();
  }

  report.obs.o10_console = { total: allLogs.length, hydration: allLogs.filter((l) => /hydrat/i.test(l.text)), all: allLogs };
  save();
  await browser.close();
  console.log('DONE logs=', allLogs.length);
})().catch((e) => { console.error('FATAL', e); report.fatal = String(e); save(); process.exit(1); });
