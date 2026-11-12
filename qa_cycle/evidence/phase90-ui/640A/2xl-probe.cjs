const { chromium } = require(process.argv[3]); const OUT = process.argv[2]; const fs = require('fs');
(async () => { const b = await chromium.launch(); const res = [];
  for (const [who, w] of [['user_e2e_alice', 1600], ['user_e2e_alice', 1535]]) {
    const ctx = await b.newContext({ viewport: { width: w, height: 900 } }); const p = await ctx.newPage(); const logs = [];
    p.on('console', m => { if (['error','warning'].includes(m.type())) logs.push(m.text().slice(0,200)); });
    await p.goto('http://localhost:3001/mock-login'); await p.locator('#user-select').selectOption(who);
    await p.getByRole('button', { name: 'Sign In' }).click(); await p.waitForURL(/\/dashboard/, { timeout: 30000 });
    await p.waitForLoadState('networkidle').catch(() => {}); await p.waitForTimeout(1500);
    const boxes = await p.evaluate(() => [...document.querySelectorAll('#main-content [data-testid^="home-cell-"]')].map(c => { const r = c.getBoundingClientRect(); return `${c.dataset.testid.replace('home-cell-','')}:${Math.round(r.width)}@${Math.round(r.left)},${Math.round(r.top + scrollY)}`; }));
    if (w === 1600) await p.screenshot({ path: `${OUT}/home-alice-1600-light-2xl-probe.png`, fullPage: true });
    res.push({ width: w, boxes, console: logs }); console.log(w, boxes.join(' '), 'console', logs.length); await ctx.close(); }
  fs.writeFileSync(`${OUT}/2xl-probe-report.json`, JSON.stringify({ head: 'd78ce3ce5', capturedAt: new Date().toISOString(), res }, null, 2)); await b.close(); })();
