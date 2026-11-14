// Phase 90 dark-mode debt D-3/D-4/D-5 probe (e2e mock stack :3001). Usage: node probe.js <label> <dark|light>
// Every colour is composited as painted (1x1 sRGB canvas). Per page it reports:
//  D-3: each visible native <select>/<textarea>/<input> field's surface vs the card and border vs the card;
//  D-4: muted text (old pair, or text-muted-foreground) contrast — min over the page;
//  D-5: the Connect to Xero button text vs its fill.
const { chromium } = require(process.env.PW);
const fs = require('fs');
const D = __dirname, label = process.argv[2] || 'run', theme = process.argv[3] || 'dark';
const BASE = 'http://localhost:3001/org/e2e-test-org';
const PAGES = [
  ['matters-new', '/matters/new'], ['conflict-check', '/conflict-check'], ['legal-disbursements', '/legal/disbursements'],
  ['legal-adverse-parties', '/legal/adverse-parties'], ['settings-documents-templates-new', '/settings/documents/templates/new'],
  ['settings-matters-checklists-new', '/settings/matters/checklists/new'], ['settings-matters-templates-id', null],
  ['settings-matters--checklists', '/settings/matters?tab=checklists'], ['settings-fees--tariffs', '/settings/fees?tab=tariffs'],
  ['settings-firm--general', '/settings/firm?tab=general'], ['legal-disbursements-id', null],
  ['settings-connections--xero', '/settings/connections?tab=xero'],
];
const DIALOGS = [ // [name, page, button name regex]
  ['dialog-adverse-party', '/legal/adverse-parties', /add (adverse )?party|new (adverse )?party/i],
  ['dialog-new-disbursement', '/legal/disbursements', /new disbursement|add disbursement|record disbursement/i],
];
const MEASURE = () => {
  const paint = layers => { const cv = document.createElement('canvas'); cv.width = cv.height = 1; const g = cv.getContext('2d', { colorSpace: 'srgb' }); layers.forEach(c => { g.fillStyle = c; g.fillRect(0, 0, 1, 1); }); const [r, gg, b] = g.getImageData(0, 0, 1, 1).data; return [r, gg, b]; };
  const lum = rgb => rgb.map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }).reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0);
  const ratio = (a, b) => { const [hi, lo] = [lum(a), lum(b)].sort((x, y) => y - x); return Number(((hi + 0.05) / (lo + 0.05)).toFixed(2)); };
  const opaque = c => c && !/rgba\(0, 0, 0, 0\)|transparent/.test(c);
  const layersBehind = el => { const out = []; let e = el.parentElement; while (e) { const c = getComputedStyle(e).backgroundColor; if (opaque(c)) out.unshift(c); e = e.parentElement; } return out.length ? out : ['rgb(255,255,255)']; };
  const visible = el => { const r = el.getBoundingClientRect(); const s = getComputedStyle(el); return r.width > 0 && r.height > 0 && s.visibility !== 'hidden' && s.display !== 'none'; };
  const fields = [...document.querySelectorAll('select, textarea')].filter(visible).map(el => {
    const under = layersBehind(el), card = paint(under);
    const own = getComputedStyle(el).backgroundColor, surface = paint([...under, own]);
    const border = paint([...under, getComputedStyle(el).borderTopColor]);
    return { tag: el.tagName.toLowerCase(), name: el.getAttribute('name') || el.id || el.getAttribute('aria-label') || '', cls: el.className, surfaceVsCard: ratio(surface, card), borderVsCard: ratio(border, card), hasFill: opaque(own) };
  });
  const isMuted = el => { const c = typeof el.className === 'string' ? el.className.split(/\s+/) : []; return (c.includes('text-slate-400') && c.includes('dark:text-slate-600')) || c.includes('text-muted-foreground'); };
  const muted = [...document.querySelectorAll('body *')].filter(el => visible(el) && isMuted(el) && el.textContent.trim() && [...el.childNodes].some(n => n.nodeType === 3 && n.textContent.trim())).map(el => {
    const under = layersBehind(el); const bg = paint([...under, getComputedStyle(el).backgroundColor]); const fg = paint([...under, getComputedStyle(el).backgroundColor, getComputedStyle(el).color]);
    return { text: el.textContent.trim().slice(0, 40), oldPair: el.className.includes('dark:text-slate-600'), contrast: ratio(fg, bg) };
  });
  const xeroBtn = [...document.querySelectorAll('button')].find(b => /connect to xero/i.test(b.textContent));
  let xero = null;
  if (xeroBtn) { const under = layersBehind(xeroBtn); const fill = paint([...under, getComputedStyle(xeroBtn).backgroundColor]); const fg = paint([...under, getComputedStyle(xeroBtn).backgroundColor, getComputedStyle(xeroBtn).color]); xero = { fill, fg, contrast: ratio(fg, fill) }; }
  const min = a => a.length ? Math.min(...a) : null;
  return { htmlDark: document.documentElement.classList.contains('dark'), fields, fieldMinBorder: min(fields.map(f => f.borderVsCard)), fieldMinSurface: min(fields.map(f => f.surfaceVsCard)), mutedOld: muted.filter(m => m.oldPair), mutedMin: min(muted.map(m => m.contrast)), mutedOldMin: min(muted.filter(m => m.oldPair).map(m => m.contrast)), xero };
};
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(`${page.url()} :: ${e.message}`));
  page.on('console', m => { if (m.type() === 'error') errs.push(`${page.url()} :: ${m.text()}`); });
  await page.goto('http://localhost:3001/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\//, { timeout: 60000 });
  await page.waitForLoadState('load'); await page.waitForTimeout(4000);
  await page.evaluate(t => localStorage.setItem('kazi-theme', t), theme);
  const firstHref = async (listPath, re) => { await page.goto(BASE + listPath, { waitUntil: 'load' }); await page.waitForTimeout(2500); return page.evaluate(r => { const a = [...document.querySelectorAll('a[href]')].map(x => x.getAttribute('href')).find(h => new RegExp(r).test(h)); return a || null; }, re.source); };
  const out = { label, theme, at: new Date().toISOString(), pages: {}, dialogs: {} };
  for (const [name, p] of PAGES) {
    let route = p;
    if (name === 'settings-matters-templates-id') route = await firstHref('/settings/matters?tab=templates', /\/settings\/matters\/templates\/[0-9a-f-]{8,}/);
    if (name === 'legal-disbursements-id') route = await firstHref('/legal/disbursements', /\/legal\/disbursements\/[0-9a-f-]{8,}/);
    if (!route) { out.pages[name] = { skipped: 'no instance found in e2e data' }; continue; }
    const url = route.startsWith('http') ? route : (route.startsWith('/org/') ? 'http://localhost:3001' + route : BASE + route);
    await page.goto(url, { waitUntil: 'load' }); await page.waitForTimeout(3000);
    out.pages[name] = { url: page.url().replace('http://localhost:3001', ''), ...(await page.evaluate(MEASURE)) };
    await page.screenshot({ path: `${D}/${label}-${name}-1440-${theme}.png`, fullPage: false });
  }
  for (const [name, p, re] of DIALOGS) {
    await page.goto(BASE + p, { waitUntil: 'load' }); await page.waitForTimeout(2500);
    const btn = page.getByRole('button', { name: re }).first();
    if (!(await btn.count())) { out.dialogs[name] = { skipped: 'trigger not found' }; continue; }
    await btn.click(); await page.waitForTimeout(1500);
    out.dialogs[name] = await page.evaluate(MEASURE);
    await page.screenshot({ path: `${D}/${label}-${name}-1440-${theme}.png` });
    await page.keyboard.press('Escape');
  }
  out.errors = errs;
  fs.writeFileSync(`${D}/${label}-${theme}-probe.json`, JSON.stringify(out, null, 2));
  for (const [k, v] of [...Object.entries(out.pages), ...Object.entries(out.dialogs)]) {
    if (v.skipped) { console.log(`${k.padEnd(34)} SKIPPED (${v.skipped})`); continue; }
    console.log(`${k.padEnd(34)} fields=${v.fields.length} minBorder=${v.fieldMinBorder} minSurface=${v.fieldMinSurface} | mutedOldPair=${v.mutedOld.length} min=${v.mutedOldMin} | mutedMin=${v.mutedMin}${v.xero ? ` | xero=${v.xero.contrast}` : ''}`);
  }
  console.log('errors:', errs.length);
  await browser.close();
})().catch(e => { console.error(e.stack); process.exit(1); });
