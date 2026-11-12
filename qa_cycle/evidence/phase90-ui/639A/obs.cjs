// 639A own OBS (task 639.7): three sampled EmptyState importers at 1024/1440 x light/dark on the fresh :3001 mock stack.
// Playwright library (the MCP Chrome profile is held by the user's live session — same deviation as 637A..638B).
// Samples chosen by probe.cjs (routes rendering [data-slot="empty-state"] on the seed); the matters filtered-empty view is a
// hand-rolled div, not EmptyState (brief A8), so it is not a sample.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-639A/frontend/node_modules/@playwright/test');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-639A/qa_cycle/evidence/phase90-ui/639A';
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
const samples = [
  ['settings-tags', O + '/settings/matters?tab=tags', 'empty settings list'],
  ['my-work', O + '/my-work', 'empty page-level lists'],
  ['profitability', O + '/reports/profitability', 'empty report'],
];
const sizes = [[1024, 768], [1440, 900]];
(async () => {
  const browser = await chromium.launch();
  const report = [];
  for (const theme of ['light', 'dark']) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } });
    await ctx.addInitScript(t => { try { localStorage.setItem('kazi-theme', t); } catch (e) {} }, theme);
    const page = await ctx.newPage();
    let logs = [];
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) logs.push(`[${m.type()}] ${m.text().slice(0, 300)}`); });
    page.on('pageerror', e => logs.push(`[pageerror] ${e.message.slice(0, 300)}`));
    await page.goto(BASE + '/mock-login');
    await page.getByRole('button', { name: 'Sign In' }).click();
    await page.waitForURL(/\/dashboard/, { timeout: 30000 });
    for (const [name, path, what] of samples) {
      for (const [w, h] of sizes) {
        logs = [];
        await page.setViewportSize({ width: w, height: h });
        await page.goto(BASE + path, { waitUntil: 'networkidle' });
        await page.waitForSelector('[data-slot="empty-state"]', { timeout: 30000 });
        await page.waitForTimeout(1200);
        const es = page.locator('[data-slot="empty-state"]').first();
        await es.scrollIntoViewIfNeeded();
        const file = `${name}-${w}-${theme}.png`;
        await page.screenshot({ path: `${OUT}/${file}` });
        await es.screenshot({ path: `${OUT}/${name}-${w}-${theme}-emptystate.png` });
        const info = await page.evaluate(() => {
          const all = [...document.querySelectorAll('[data-slot="empty-state"]')];
          const e = all[0], tile = e.querySelector('[data-slot="empty-state-icon"]'), h2 = e.querySelector('h2'), desc = e.querySelector('p');
          const cs = x => x ? getComputedStyle(x) : null;
          return {
            h1: document.querySelector('h1')?.textContent?.trim(), h1Count: document.querySelectorAll('h1').length,
            htmlClass: document.documentElement.className,
            emptyStates: all.map(x => ({ variant: x.dataset.variant, title: x.querySelector('h2,p')?.textContent?.trim(), actions: [...x.querySelectorAll('a,button')].map(a => a.textContent.trim()) })),
            iconTile: tile ? { w: tile.getBoundingClientRect().width, h: tile.getBoundingClientRect().height, bg: cs(tile).backgroundColor, radius: cs(tile).borderRadius } : null,
            iconSvgAriaHidden: e.querySelector('svg')?.getAttribute('aria-hidden'),
            title: h2 ? { tag: 'h2', font: cs(h2).fontFamily.split(',')[0], weight: cs(h2).fontWeight, size: cs(h2).fontSize, color: cs(h2).color } : null,
            description: desc ? { maxWidth: cs(desc).maxWidth, color: cs(desc).color } : null,
            bodyBg: cs(document.body).backgroundColor,
          };
        });
        const hydration = logs.filter(l => /hydrat|did not match|server rendered/i.test(l));
        report.push({ name, what, theme, w, h, url: page.url().replace(BASE, ''), file, ...info, console: logs, hydrationMessages: hydration });
      }
    }
    await ctx.close();
  }
  require('fs').writeFileSync(`${OUT}/obs-report.json`, JSON.stringify({ capturedAt: new Date().toISOString(), report }, null, 2));
  console.log(report.map(r => `${r.name}@${r.w}/${r.theme}: h1=${r.h1}(${r.h1Count}) html=${r.htmlClass} es=${JSON.stringify(r.emptyStates.map(e => e.variant + ':' + e.title))} tile=${r.iconTile && r.iconTile.w + 'x' + r.iconTile.h + ' ' + r.iconTile.bg} title=${r.title && r.title.font + '/' + r.title.weight} console=${r.console.length} hydr=${r.hydrationMessages.length}`).join('\n'));
  await browser.close();
})().catch(e => { console.error('OBS FAILED', e); process.exit(1); });
