// Weekly-rhythm strip label contrast on /my-work (e2e mock stack :3001). Usage: node labels.js <label> <dark|light>
// Colours are composited as painted: each label and everything behind it is drawn on a 1x1 sRGB canvas and read back.
const { chromium } = require(process.env.PW);
const fs = require('fs');
const D = __dirname, label = process.argv[2] || 'run', theme = process.argv[3] || 'light';
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errs.push(m.text()); });
  await page.goto('http://localhost:3001/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\//, { timeout: 60000 });
  await page.waitForLoadState('load'); await page.waitForTimeout(4000); // let mock-auth userinfo finish before navigating
  await page.evaluate(t => localStorage.setItem('kazi-theme', t), theme);
  await page.goto('http://localhost:3001/org/e2e-test-org/my-work', { waitUntil: 'load' });
  await page.getByTestId('weekly-rhythm-strip').waitFor({ timeout: 60000 });
  const measure = () => page.evaluate(() => {
    const paint = layers => { const cv = document.createElement('canvas'); cv.width = cv.height = 1; const g = cv.getContext('2d', { colorSpace: 'srgb' }); layers.forEach(c => { g.fillStyle = c; g.fillRect(0, 0, 1, 1); }); const [r, gg, b] = g.getImageData(0, 0, 1, 1).data; return [r, gg, b]; };
    const lum = rgb => rgb.map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }).reduce((s, v, i) => s + v * [0.2126, 0.7152, 0.0722][i], 0);
    const stack = el => { const layers = []; let e = el; while (e) { const c = getComputedStyle(e).backgroundColor; if (c && !/rgba\(0, 0, 0, 0\)|transparent/.test(c)) layers.unshift(c); e = e.parentElement; } return layers.length ? layers : ['rgb(255,255,255)']; };
    const m = el => { const bgLayers = stack(el); const bg = paint(bgLayers), fg = paint([...bgLayers, getComputedStyle(el).color]); const [hi, lo] = [lum(bg), lum(fg)].sort((x, y) => y - x); return { text: el.textContent.trim(), cls: el.className, color: getComputedStyle(el).color, bgRgb: bg, fgRgb: fg, contrast: Number(((hi + 0.05) / (lo + 0.05)).toFixed(2)) }; };
    const strip = document.querySelector('[data-testid="weekly-rhythm-strip"]');
    const days = [...strip.querySelectorAll('[data-testid^="rhythm-day-"]')].map(b => ({ pressed: b.getAttribute('aria-pressed'), ...m(b.querySelector('span')) }));
    const thisWeek = [...strip.querySelectorAll('p')].find(p => p.textContent.trim() === 'this week');
    return { htmlDark: document.documentElement.classList.contains('dark'), days, thisWeek: m(thisWeek) };
  });
  const out = { label, theme, at: new Date().toISOString(), resting: await measure() };
  await page.getByTestId('weekly-rhythm-strip').screenshot({ path: `${D}/${label}-strip-${theme}.png` });
  await page.getByTestId('rhythm-day-0').click(); await page.mouse.move(0, 0); await page.waitForTimeout(300);
  out.day0Selected = (await measure()).days[0];
  await page.getByTestId('weekly-rhythm-strip').screenshot({ path: `${D}/${label}-strip-selected-${theme}.png` });
  out.errors = errs;
  fs.writeFileSync(`${D}/${label}-${theme}-labels.json`, JSON.stringify(out, null, 2));
  const r = out.resting, today = r.days.find(d => /teal/.test(d.cls)), plain = r.days.filter(d => !/teal/.test(d.cls));
  console.log(`${label} ${theme} dark=${r.htmlDark} | unselected day labels: ${[...new Set(plain.map(d => d.contrast))].join('/')} | today (teal): ${today ? today.contrast : 'n/a'} | "this week": ${r.thisWeek.contrast} | selected MON: ${out.day0Selected.contrast} | errors=${errs.length}`);
  await browser.close();
})().catch(e => { console.error(e.stack); process.exit(1); });
