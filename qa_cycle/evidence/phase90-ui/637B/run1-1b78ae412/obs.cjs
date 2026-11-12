// 637B OBS (task 637.12) — library-script fallback: the Playwright MCP browser profile is held by another live session.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-637B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-637B/qa_cycle/evidence/phase90-ui/637B';
const BASE = 'http://localhost:3001';
const HOME = '/org/e2e-test-org/dashboard';
const TOAST_PAGE = '/org/e2e-test-org/settings/firm?tab=data-protection';
const HYDRATION_RE = /Hydration|did not match|Text content does not match|server rendered HTML/i;

const report = { head: '1b78ae412', startedAt: new Date().toISOString(), obs1: {}, obs2: {}, obs3: {}, obs4: {}, obs5: {}, obs6: {}, console: [], httpErrors: [] };
let phase = 'setup';

function attach(page, tag) {
  page.on('console', (m) => {
    if (['error', 'warning'].includes(m.type())) report.console.push({ phase, page: tag, type: m.type(), url: page.url(), text: m.text().slice(0, 2000) });
  });
  page.on('pageerror', (e) => report.console.push({ phase, page: tag, type: 'pageerror', url: page.url(), text: String(e.message).slice(0, 2000) }));
  page.on('response', (r) => { if (r.status() >= 400) report.httpErrors.push({ phase, page: tag, status: r.status(), url: r.url(), pageUrl: page.url() }); });
}

async function ready(page, path, { h1 = true } = {}) {
  await page.goto(BASE + path, { waitUntil: 'load', timeout: 90000 });
  if (h1) await page.locator('h1').first().waitFor({ timeout: 45000 });
  await page.waitForTimeout(1200);
}
async function reloadReady(page) {
  await page.reload({ waitUntil: 'load', timeout: 90000 });
  await page.locator('h1').first().waitFor({ timeout: 45000 });
  await page.waitForTimeout(1200);
}

async function state(page) {
  return page.evaluate(() => {
    const group = document.querySelector('[aria-label="Theme"]');
    return {
      url: location.pathname + location.search,
      h1: document.querySelector('h1')?.textContent?.trim() ?? null,
      ls: localStorage.getItem('kazi-theme'),
      htmlClass: document.documentElement.className,
      hasDark: document.documentElement.classList.contains('dark'),
      htmlColorScheme: document.documentElement.style.colorScheme || null,
      bodyBg: getComputedStyle(document.body).backgroundColor,
      osPrefersDark: matchMedia('(prefers-color-scheme: dark)').matches,
      menuOpen: !!group,
      radios: group ? [...group.querySelectorAll('[role="radio"]')].map((r) => ({ label: r.textContent.trim(), ariaChecked: r.getAttribute('aria-checked'), dataState: r.getAttribute('data-state') })) : null,
      selected: group ? ([...group.querySelectorAll('[role="radio"][aria-checked="true"]')].map((r) => r.textContent.trim()).join(',') || null) : null,
    };
  });
}

async function openMenu(page) {
  if (await page.locator('[aria-label="Theme"] [role="radio"]').count()) return;
  await page.locator('header button.rounded-full').first().click();
  await page.locator('[aria-label="Theme"] [role="radio"]').first().waitFor({ timeout: 15000 });
  await page.waitForTimeout(300);
}

async function setStored(page, value) {
  await page.evaluate((v) => localStorage.setItem('kazi-theme', v), value);
}

const FOUC_INIT = () => {
  const samples = [];
  window.__fouc = samples;
  const rec = (ev) => {
    const el = document.documentElement;
    samples.push({ ev, t: Math.round(performance.now() * 10) / 10, readyState: document.readyState, htmlExists: !!el, dark: el ? el.classList.contains('dark') : null, cls: el ? el.className : null, bodyExists: !!document.body, bodyChildren: document.body ? document.body.childElementCount : 0 });
  };
  rec('init-script');
  document.addEventListener('readystatechange', () => rec('readystate:' + document.readyState));
  document.addEventListener('DOMContentLoaded', () => rec('DOMContentLoaded'));
  window.addEventListener('load', () => rec('load'));
  let n = 0;
  const loop = () => { n += 1; rec(n === 1 ? 'first-rAF' : 'rAF#' + n); if (n < 40) requestAnimationFrame(loop); };
  requestAnimationFrame(loop);
  new MutationObserver((muts) => {
    for (const m of muts) if (m.type === 'attributes' && m.target === document.documentElement) rec('mutation:html.class');
  }).observe(document, { subtree: true, attributes: true, attributeFilter: ['class'] });
  try {
    new PerformanceObserver((list) => { for (const e of list.getEntries()) rec('paint:' + e.name + '@' + e.startTime.toFixed(1)); }).observe({ type: 'paint', buffered: true });
  } catch (e) { /* ignore */ }
};

async function foucRun(ctx, stored, label, cacheDisabled = false) {
  phase = 'obs2-' + label;
  const page = await ctx.newPage();
  attach(page, 'fouc-' + label);
  await page.emulateMedia({ colorScheme: 'light' });
  await page.addInitScript(FOUC_INIT);
  await ready(page, HOME);
  await setStored(page, stored);
  const cdp = await ctx.newCDPSession(page);
  await cdp.send('Network.enable');
  await cdp.send('Network.setCacheDisabled', { cacheDisabled });
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 4 });
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 562.5, downloadThroughput: 180000, uploadThroughput: 84375 });
  const shots = [];
  const t0 = Date.now();
  await page.reload({ waitUntil: 'commit', timeout: 120000 });
  try { const f = `fouc-${label}-early-1-commit.png`; await page.screenshot({ path: `${OUT}/${f}`, timeout: 30000 }); shots.push({ f, msAfterReload: Date.now() - t0 }); } catch (e) { shots.push({ f: 'early-1', error: e.message.slice(0, 200) }); }
  try { await page.waitForLoadState('domcontentloaded', { timeout: 120000 }); const f = `fouc-${label}-early-2-dcl.png`; await page.screenshot({ path: `${OUT}/${f}`, timeout: 30000 }); shots.push({ f, msAfterReload: Date.now() - t0 }); } catch (e) { shots.push({ f: 'early-2', error: e.message.slice(0, 200) }); }
  await page.waitForLoadState('load', { timeout: 180000 });
  try { const f = `fouc-${label}-early-3-load.png`; await page.screenshot({ path: `${OUT}/${f}`, timeout: 30000 }); shots.push({ f, msAfterReload: Date.now() - t0 }); } catch (e) { shots.push({ f: 'early-3', error: e.message.slice(0, 200) }); }
  await page.locator('h1').first().waitFor({ timeout: 120000 });
  await page.waitForTimeout(2000);
  const samples = await page.evaluate(() => window.__fouc);
  await cdp.send('Emulation.setCPUThrottlingRate', { rate: 1 });
  await cdp.send('Network.emulateNetworkConditions', { offline: false, latency: 0, downloadThroughput: -1, uploadThroughput: -1 });
  const wantDark = stored === 'dark';
  const firstRaf = samples.find((s) => s.ev === 'first-rAF');
  const firstPaint = samples.find((s) => s.ev.startsWith('paint:first-paint'));
  const fromRaf = samples.filter((s) => firstRaf && s.t >= firstRaf.t);
  const mismatchesFromFirstRaf = fromRaf.filter((s) => s.dark !== wantDark);
  const anyMismatchWithBody = samples.filter((s) => s.bodyChildren > 0 && s.dark !== wantDark);
  const result = {
    stored, wantDark, cacheDisabled, msTotal: Date.now() - t0, sampleCount: samples.length,
    firstRaf, firstPaint, finalState: await state(page),
    mismatchesFromFirstRaf, anyMismatchWithBodyContent: anyMismatchWithBody,
    pass: !!firstRaf && mismatchesFromFirstRaf.length === 0 && anyMismatchWithBody.length === 0,
    shots, samples,
  };
  await page.close();
  return result;
}

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, colorScheme: 'light' });
  const page = await ctx.newPage();
  attach(page, 'main');
  await page.goto(BASE + '/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  await page.locator('h1').first().waitFor({ timeout: 45000 });

  // ---------- OBS 1: persistence ----------
  phase = 'obs1';
  await ready(page, HOME);
  report.obs1.initial = await state(page);
  // Dark first so the Light click is a real change (clicking the already-checked item is a Radix no-op).
  for (const choice of ['Dark', 'Light', 'System']) {
    const r = {};
    await ready(page, HOME);
    await openMenu(page);
    r.beforeClick = await state(page);
    await page.getByRole('radio', { name: choice }).click();
    await page.waitForTimeout(500);
    r.afterClick = await state(page); // menuOpen must still be true
    r.popoverStayedOpen = r.afterClick.menuOpen;
    await reloadReady(page);
    r.afterReloadMenuClosed = await state(page);
    await openMenu(page);
    r.afterReloadMenuOpen = await state(page);
    if (choice === 'System') {
      await page.emulateMedia({ colorScheme: 'dark' });
      await page.waitForTimeout(700);
      r.osDark = await state(page);
      await page.emulateMedia({ colorScheme: 'light' });
      await page.waitForTimeout(700);
      r.osLight = await state(page);
      await page.emulateMedia({ colorScheme: 'dark' });
      await reloadReady(page);
      await openMenu(page);
      r.osDarkAfterReload = await state(page);
      await page.emulateMedia({ colorScheme: 'light' });
      await page.waitForTimeout(700);
      r.osLightNoReload = await state(page);
    }
    report.obs1[choice] = r;
  }

  // ---------- OBS 2: FOUC on throttled reload ----------
  report.obs2.dark = await foucRun(ctx, 'dark', 'dark');
  report.obs2.light = await foucRun(ctx, 'light', 'light');
  report.obs2.darkColdCache = await foucRun(ctx, 'dark', 'dark-nocache', true);
  report.obs2.lightColdCache = await foucRun(ctx, 'light', 'light-nocache', true);

  // ---------- OBS 3: console on dashboard light + dark, incl. open user menu ----------
  for (const t of ['light', 'dark']) {
    phase = 'obs3-' + t;
    await page.emulateMedia({ colorScheme: 'light' });
    await setStored(page, t);
    const before = report.console.length;
    await ready(page, HOME);
    await page.waitForTimeout(2000);
    await openMenu(page);
    await page.waitForTimeout(1000);
    const s = await state(page);
    const msgs = report.console.slice(before);
    report.obs3[t] = { state: s, messages: msgs, hydration: msgs.filter((m) => HYDRATION_RE.test(m.text)) };
  }

  // ---------- OBS 5: toasts follow theme ----------
  async function toastRun(label, stored, os) {
    phase = 'obs5-' + label;
    await page.emulateMedia({ colorScheme: os });
    await setStored(page, stored);
    await ready(page, TOAST_PAGE);
    const btn = page.getByRole('button', { name: 'Export Register' });
    await btn.scrollIntoViewIfNeeded();
    await btn.click();
    const toast = page.locator('[data-sonner-toast]').first();
    await toast.waitFor({ timeout: 10000 });
    await page.waitForTimeout(600);
    const info = await page.evaluate(() => {
      const t = document.querySelector('[data-sonner-toaster]');
      const toastEl = document.querySelector('[data-sonner-toast]');
      return {
        toasterDataTheme: t?.getAttribute('data-theme') ?? null,
        toasterDataSonnerTheme: t?.getAttribute('data-sonner-theme') ?? null,
        toastText: toastEl?.textContent?.trim() ?? null,
        toastBg: toastEl ? getComputedStyle(toastEl).backgroundColor : null,
        toastColor: toastEl ? getComputedStyle(toastEl).color : null,
      };
    });
    const f = `toast-${label}.png`;
    await page.screenshot({ path: `${OUT}/${f}` });
    report.obs5[label] = { ...(await state(page)), ...info, screenshot: f };
    await page.emulateMedia({ colorScheme: 'light' });
  }
  await toastRun('dark', 'dark', 'light');
  await toastRun('light', 'light', 'light');
  await toastRun('system-osdark', 'system', 'dark');

  // ---------- OBS 6: dark "before" record ----------
  phase = 'obs6';
  await page.emulateMedia({ colorScheme: 'light' });
  for (const t of ['light', 'dark']) {
    await setStored(page, t);
    for (const [w, h] of [[1024, 768], [1440, 900]]) {
      await page.setViewportSize({ width: w, height: h });
      await ready(page, HOME);
      await page.waitForTimeout(1500);
      const f = `home-${w}-${t}.png`;
      await page.screenshot({ path: `${OUT}/${f}` });
      report.obs6[f] = await state(page);
    }
    await page.setViewportSize({ width: 1440, height: 900 });
    await ready(page, HOME);
    await openMenu(page);
    const f = `user-menu-${t}.png`;
    await page.screenshot({ path: `${OUT}/${f}` });
    report.obs6[f] = await state(page);
  }

  // ---------- OBS 4: forced light on non-/org/ routes (last: /mock-login is visited) ----------
  phase = 'obs4';
  await ready(page, HOME);
  await setStored(page, 'dark');
  await ready(page, HOME);
  report.obs4.themedBaseline = await state(page);
  // client-side navigation from a dark /org/ page to a non-/org/ link
  const link = page.locator('a[href="/platform-admin/access-requests"]').first();
  report.obs4.clientNav = { linkCount: await page.locator('a[href^="/"]:not([href^="/org/"])').count() };
  if (await link.count()) {
    await page.evaluate(() => { window.__noFullLoad = true; });
    await link.evaluate((a) => a.click());
    await page.waitForURL((u) => !u.pathname.startsWith('/org/'), { timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(2500);
    const s = await state(page);
    const stillSpa = await page.evaluate(() => window.__noFullLoad === true);
    await page.screenshot({ path: `${OUT}/forced-clientnav.png` });
    report.obs4.clientNav = { ...report.obs4.clientNav, href: '/platform-admin/access-requests', state: s, clientSideNavigation: stillSpa, screenshot: 'forced-clientnav.png' };
    await page.goBack({ waitUntil: 'load' }).catch(() => {});
    await page.waitForTimeout(2500);
    report.obs4.clientNav.afterBack = { ...(await state(page)), clientSideNavigation: await page.evaluate(() => window.__noFullLoad === true) };
  }
  for (const [name, path] of [['root', '/'], ['dashboard', '/dashboard'], ['mock-login', '/mock-login']]) {
    await page.goto(BASE + path, { waitUntil: 'load', timeout: 90000 });
    await page.waitForTimeout(2500);
    const f = `forced-${name}.png`;
    await page.screenshot({ path: `${OUT}/${f}` });
    report.obs4[name] = { requested: path, ...(await state(page)), screenshot: f };
  }

  report.finishedAt = new Date().toISOString();
  report.consoleSummary = {
    total: report.console.length,
    errors: report.console.filter((m) => m.type === 'error').length,
    warnings: report.console.filter((m) => m.type === 'warning').length,
    pageerrors: report.console.filter((m) => m.type === 'pageerror').length,
    hydration: report.console.filter((m) => HYDRATION_RE.test(m.text)).length,
  };
  fs.writeFileSync(`${OUT}/obs-report.json`, JSON.stringify(report, null, 2));
  const brief = (s) => s && `${s.url} ls=${s.ls} class="${s.htmlClass}" open=${s.menuOpen} sel=${s.selected} bg=${s.bodyBg}`;
  console.log('OBS1', JSON.stringify(Object.fromEntries(['Dark', 'Light', 'System'].map((c) => [c, { afterClick: brief(report.obs1[c].afterClick), reload: brief(report.obs1[c].afterReloadMenuOpen), osDark: brief(report.obs1[c].osDark), osLight: brief(report.obs1[c].osLight), osDarkReload: brief(report.obs1[c].osDarkAfterReload) }])), null, 1));
  for (const k of ['dark', 'light', 'darkColdCache', 'lightColdCache']) { const o = report.obs2[k]; console.log('OBS2', k, 'pass', o.pass, 'samples', o.sampleCount, 'firstRaf', JSON.stringify(o.firstRaf), 'firstPaint', JSON.stringify(o.firstPaint), 'mismatch', o.mismatchesFromFirstRaf.length, o.anyMismatchWithBodyContent.length, 'ms', o.msTotal, JSON.stringify(o.shots)); }
  console.log('OBS4', JSON.stringify({ base: brief(report.obs4.themedBaseline), clientNav: report.obs4.clientNav && { s: brief(report.obs4.clientNav.state), spa: report.obs4.clientNav.clientSideNavigation, back: brief(report.obs4.clientNav.afterBack), backSpa: report.obs4.clientNav.afterBack?.clientSideNavigation }, root: brief(report.obs4.root), dashboard: brief(report.obs4.dashboard), mockLogin: brief(report.obs4['mock-login']) }, null, 1));
  console.log('OBS5', JSON.stringify(Object.fromEntries(Object.entries(report.obs5).map(([k, v]) => [k, { theme: v.toasterDataTheme, sonnerTheme: v.toasterDataSonnerTheme, text: v.toastText, bg: v.toastBg, cls: v.htmlClass }]))));
  console.log('CONSOLE', JSON.stringify(report.consoleSummary), JSON.stringify(report.console, null, 1));
  console.log('HTTP>=400', JSON.stringify(report.httpErrors, null, 1));
  await browser.close();
})().catch((e) => { console.error('OBS FAILED', e); fs.writeFileSync(`${OUT}/obs-report.json`, JSON.stringify({ ...report, fatal: String(e && e.stack) }, null, 2)); process.exit(1); });
