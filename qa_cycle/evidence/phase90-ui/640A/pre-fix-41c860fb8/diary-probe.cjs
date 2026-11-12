// 640A OBS supplement — SYNTHETIC diary-column probe. The seeded tenant has NO diary rows today for any persona
// ("Nothing in the diary today"), so the reviewer's 1–2-row two-column question cannot be observed on product data.
// This probe injects N placeholder <li> rows (client-side DOM only; no server/data change) into the real Home diary
// CardContent (which carries the 640A @container / @4xl:[&>ul]:columns-2 classes) and measures the CSS outcome.
// It shows how the shipped CSS lays out N rows; it is NOT an observation of real DiaryEventRow markup.
// Usage: node diary-probe.cjs <outDir> <playwrightTestModuleDir>
const OUT = process.argv[2];
const { chromium } = require(process.argv[3]);
const fs = require('fs');
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  await ctx.addInitScript(() => { try { localStorage.setItem('kazi-theme', 'light'); } catch (e) {} });
  const page = await ctx.newPage();
  await page.goto(BASE + '/mock-login');
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(2000);
  const out = [];
  for (const [w, h] of [[1440, 900], [1024, 768]]) {
    await page.setViewportSize({ width: w, height: h });
    await page.goto(BASE + O + '/dashboard', { waitUntil: 'load' });
    await page.locator('#main-content [data-testid="home-cell-diary"]').waitFor({ timeout: 30000 });
    await page.waitForTimeout(1000);
    for (const n of [1, 2, 5]) {
      const r = await page.evaluate((n) => {
        const content = document.querySelector('#main-content [data-testid="home-cell-diary"] [data-testid="todays-diary"]');
        content.querySelectorAll(':scope > ul, :scope > p').forEach(e => e.remove());
        const ul = document.createElement('ul'); ul.className = 'space-y-0.5';
        for (let i = 0; i < n; i++) {
          const li = document.createElement('li');
          li.innerHTML = `<a class="flex items-center gap-3 rounded-md px-2 py-1.5 text-sm hover:bg-slate-50"><span class="font-mono text-xs text-slate-500">0${9 + i}:00</span><span>SYNTHETIC row ${i + 1} — placeholder diary event</span></a>`;
          ul.appendChild(li);
        }
        content.prepend(ul);
        const cs = getComputedStyle(content);
        const lis = [...ul.children];
        return {
          rows: n,
          contentBoxWidth: Math.round(content.getBoundingClientRect().width - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight)),
          ulColumnCount: getComputedStyle(ul).columnCount,
          ulColumnGap: getComputedStyle(ul).columnGap,
          liBoxes: lis.map(li => { const b = li.getBoundingClientRect(); return { x: Math.round(b.left), y: Math.round(b.top), w: Math.round(b.width) }; }),
          distinctColumnsX: [...new Set(lis.map(li => Math.round(li.getBoundingClientRect().left)))],
        };
      }, n);
      const file = `diary-synthetic-${n}rows-${w}.png`;
      await page.locator('#main-content [data-testid="home-cell-diary"]').screenshot({ path: `${OUT}/${file}` });
      out.push({ viewport: w, file, ...r });
      console.log(JSON.stringify({ viewport: w, ...r }));
    }
  }
  fs.writeFileSync(`${OUT}/diary-probe-report.json`, JSON.stringify({ synthetic: true, capturedAt: new Date().toISOString(), results: out }, null, 2));
  await browser.close();
})().catch(e => { console.error('PROBE FAILED', e); process.exit(1); });
