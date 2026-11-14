// Dark-mode contrast probe for /my-work (e2e mock stack :3001). Usage: node obs.js <label>
const { chromium } = require(process.env.PW);
const fs = require('fs');
const D = __dirname, label = process.argv[2] || 'run', theme = process.argv[3] || 'dark';
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme });
  const page = await ctx.newPage();
  const errs = [];
  page.on('pageerror', e => errs.push(e.message));
  page.on('console', m => { if (m.type() === 'error') errs.push(page.url() + ' :: ' + m.text()); });
  await page.goto('http://localhost:3001/mock-login', { waitUntil: 'load' });
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\//, { timeout: 60000 });
  await page.waitForLoadState('load'); await page.waitForTimeout(4000);
  await page.evaluate(t => localStorage.setItem('kazi-theme', t), theme);
  await page.goto('http://localhost:3001/org/e2e-test-org/my-work', { waitUntil: 'load' });
  await page.getByTestId('time-progress-today').waitFor({ timeout: 60000 });
  await page.getByTestId('weekly-rhythm-strip').waitFor({ timeout: 60000 });
  const probe = () => page.evaluate(() => {
    const lum = c => { const m = c.match(/-?[\d.]+/g).map(Number); if (c.startsWith('lab') || c.startsWith('oklab') === false && c.startsWith('lab')) {} if (c.startsWith('lab(')) { const L = m[0]; return L > 8 ? ((L + 16) / 116) ** 3 : L / 903.3; } if (c.startsWith('oklch(') || c.startsWith('oklab(')) { const L = m[0] > 1 ? m[0] / 100 : m[0]; return L ** 3; } const [r, g, b] = m.slice(0, 3).map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }); return 0.2126 * r + 0.7152 * g + 0.0722 * b; };
    const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return ((x + 0.05) / (y + 0.05)).toFixed(2); };
    const bgOf = el => { while (el) { const c = getComputedStyle(el).backgroundColor; if (c && !/rgba\(0, 0, 0, 0\)|transparent/.test(c)) return c; el = el.parentElement; } return 'rgb(255,255,255)'; };
    const dark = document.documentElement.classList.contains('dark');
    const time = document.querySelector('[data-testid="time-progress-today"] span.font-mono');
    const strip = document.querySelector('[data-testid="weekly-rhythm-strip"]');
    const total = strip.querySelector('p.font-mono');
    const track = document.querySelector('[data-testid="time-progress-today"] .rounded-full');
    const bar = strip.querySelector('[data-testid="rhythm-day-0"] .absolute');
    const divider = total.parentElement;
    const card = bgOf(time.parentElement);
    return {
      htmlDark: dark, cardBg: card,
      timeToday: { text: time.textContent, color: getComputedStyle(time).color, contrast: ratio(getComputedStyle(time).color, bgOf(time)) },
      weeklyTotal: { text: total.textContent, color: getComputedStyle(total).color, contrast: ratio(getComputedStyle(total).color, bgOf(total)) },
      timeTrack: { bg: getComputedStyle(track).backgroundColor, vsCard: ratio(getComputedStyle(track).backgroundColor, card) },
      dayBarTrack: { bg: getComputedStyle(bar).backgroundColor, vsCard: ratio(getComputedStyle(bar).backgroundColor, card) },
      divider: { border: getComputedStyle(divider).borderLeftColor, vsCard: ratio(getComputedStyle(divider).borderLeftColor, card) },
      daysRemaining: (() => { const el = [...document.querySelectorAll('[data-testid="next-deadline"] span')].find(e => /d remaining$/.test(e.textContent)); return el ? { text: el.textContent, color: getComputedStyle(el).color, contrast: ratio(getComputedStyle(el).color, bgOf(el)) } : 'not shown (no upcoming deadline in data)'; })(),
    };
  });
  const out = { label, theme, at: new Date().toISOString(), resting: await probe() };
  await page.screenshot({ path: `${D}/${label}-my-work-1440-${theme}.png`, fullPage: true });
  await page.getByTestId('weekly-rhythm-strip').screenshot({ path: `${D}/${label}-rhythm-strip-${theme}.png` });
  // Selected-day state (click day 0) and hover (day 1)
  await page.getByTestId('rhythm-day-0').click();
  await page.getByTestId('rhythm-day-1').hover();
  await page.waitForTimeout(300);
  out.selectedAndHover = await page.evaluate(() => {
    const d0 = document.querySelector('[data-testid="rhythm-day-0"]'), d1 = document.querySelector('[data-testid="rhythm-day-1"]');
    const lbl = d0.querySelector('span');
    const lum = c => { const m = c.match(/-?[\d.]+/g).map(Number); if (c.startsWith('lab(')) { const L = m[0]; return L > 8 ? ((L + 16) / 116) ** 3 : L / 903.3; } if (c.startsWith('oklch(') || c.startsWith('oklab(')) { const L = m[0] > 1 ? m[0] / 100 : m[0]; return L ** 3; } const [r, g, b] = m.slice(0, 3).map(v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; }); return 0.2126 * r + 0.7152 * g + 0.0722 * b; }; const ratio = (a, b) => { const [x, y] = [lum(a), lum(b)].sort((p, q) => q - p); return ((x + 0.05) / (y + 0.05)).toFixed(2); }; const sb = getComputedStyle(d0).backgroundColor, lc = getComputedStyle(lbl).color; return { selectedBg: sb, selectedLabelColor: lc, selectedLabelContrast: ratio(lc, sb), hoverBg: getComputedStyle(d1).backgroundColor };
  });
  await page.getByTestId('weekly-rhythm-strip').screenshot({ path: `${D}/${label}-rhythm-strip-selected-${theme}.png` });
  out.errors = errs;
  fs.writeFileSync(`${D}/${label}-${theme}-probe.json`, JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  await browser.close();
})().catch(e => { console.error(e.stack); process.exit(1); });
