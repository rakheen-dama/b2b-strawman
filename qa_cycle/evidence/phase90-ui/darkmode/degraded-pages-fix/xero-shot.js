// Locate the "Connect to Xero" button on the connections page and capture it (e2e :3001). Usage: node xero-shot.js <dark|light>
const { chromium } = require(process.env.PW);
const theme = process.argv[2] || 'dark', D = __dirname;
(async () => {
  const b = await chromium.launch(); const ctx = await b.newContext({ viewport: { width: 1440, height: 1000 }, colorScheme: theme }); const p = await ctx.newPage();
  await p.goto('http://localhost:3001/mock-login', { waitUntil: 'load' }); await p.getByRole('button', { name: /sign in/i }).click();
  await p.waitForURL(/\/org\//, { timeout: 60000 }); await p.waitForLoadState('load'); await p.waitForTimeout(4000);
  await p.evaluate(t => localStorage.setItem('kazi-theme', t), theme);
  await p.goto('http://localhost:3001/org/e2e-test-org/settings/connections?tab=xero', { waitUntil: 'load' }); await p.waitForTimeout(3000);
  const info = await p.evaluate(() => { const btn = [...document.querySelectorAll('button')].find(x => /connect to xero/i.test(x.textContent)); if (!btn) return { found: false }; const r = btn.getBoundingClientRect(); const s = getComputedStyle(btn); let hiddenBy = null, e = btn; while (e) { const cs = getComputedStyle(e); if (cs.display === 'none' || cs.visibility === 'hidden') { hiddenBy = e.tagName + '.' + (e.className || '').toString().slice(0, 60); break; } e = e.parentElement; } return { found: true, rect: [r.x, r.y, r.width, r.height].map(Math.round), display: s.display, hiddenBy, inViewport: r.y >= 0 && r.y + r.height <= innerHeight }; });
  console.log(theme, JSON.stringify(info));
  const btn = p.getByRole('button', { name: /connect to xero/i });
  if (info.found && !info.hiddenBy) { await btn.scrollIntoViewIfNeeded(); await btn.locator('xpath=ancestor::*[contains(@class,"rounded")][1]').screenshot({ path: `${D}/after-xero-connect-card-${theme}.png` }).catch(async () => btn.screenshot({ path: `${D}/after-xero-connect-card-${theme}.png` })); console.log('captured'); }
  await b.close();
})();
