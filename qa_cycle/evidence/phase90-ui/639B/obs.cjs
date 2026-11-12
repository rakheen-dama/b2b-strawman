// 639B own OBS (task 639.13): the My Work "Time Breakdown" donut re-pointed onto the kit DonutChart,
// at 1024x768 / 1440x900 x light / dark on the fresh :3001 mock stack (after E2E-LEGAL seeded time entries), as Alice (owner).
// Playwright library, not MCP (the MCP Chrome profile is held by the user's live session — same deviation as 637A..639A).
// Per surface: panel screenshot; hover tooltip on the largest sector; keyboard (Tab to the recharts accessibilityLayer
// surface, ArrowRight) — records whether a tooltip appears; "View as table" mounts exactly one <table> and unmounts on close;
// console errors/warnings + hydration messages. Extra pass: prefers-reduced-motion: reduce (hydration-safe hook check).
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-639B/frontend/node_modules/@playwright/test');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-639B/qa_cycle/evidence/phase90-ui/639B';
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
const sizes = [[1024, 768], [1440, 900]];
const runs = [['light', 'no-preference'], ['dark', 'no-preference'], ['light', 'reduce']];
(async () => {
  const browser = await chromium.launch();
  const report = [];
  for (const [theme, motion] of runs) {
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, reducedMotion: motion });
    await ctx.addInitScript(t => { try { localStorage.setItem('kazi-theme', t); } catch (e) {} }, theme);
    const page = await ctx.newPage();
    let logs = [];
    page.on('console', m => { if (['error', 'warning'].includes(m.type())) logs.push(`[${m.type()}] ${m.text().slice(0, 300)}`); });
    page.on('pageerror', e => logs.push(`[pageerror] ${e.message.slice(0, 300)}`));
    await page.goto(BASE + '/mock-login');
    await page.getByRole('button', { name: 'Sign In' }).click();
    await page.waitForURL(/\/dashboard/, { timeout: 30000 });
    for (const [w, h] of (motion === 'reduce' ? [[1440, 900]] : sizes)) {
      logs = [];
      await page.setViewportSize({ width: w, height: h });
      await page.goto(BASE + O + '/my-work', { waitUntil: 'load' });
      const panel = page.locator('[data-testid="time-activity-panel"]');
      await panel.waitFor({ timeout: 30000 });
      await page.waitForSelector('[data-testid="time-activity-panel"] .recharts-pie-sector', { timeout: 30000 });
      await page.waitForTimeout(1500);
      await panel.scrollIntoViewIfNeeded();
      const tag = `${w}-${theme}${motion === 'reduce' ? '-reduced-motion' : ''}`;
      await panel.screenshot({ path: `${OUT}/my-work-donut-${tag}.png` });
      if (motion !== 'reduce') await page.screenshot({ path: `${OUT}/my-work-page-${tag}.png` });
      const base = await page.evaluate(() => {
        const p = document.querySelector('[data-testid="time-activity-panel"]');
        const fig = p.closest('[role="figure"]') || p.querySelector('[role="figure"]') || (p.getAttribute('role') === 'figure' ? p : null);
        const sectors = [...p.querySelectorAll('.recharts-pie-sector path, .recharts-pie-sector')].filter(x => x.tagName === 'path').map(x => x.getAttribute('fill'));
        const cs = x => getComputedStyle(x);
        return {
          htmlClass: document.documentElement.className,
          figureRole: fig ? fig.getAttribute('role') : null, figureName: fig ? fig.getAttribute('aria-label') : null,
          srSummary: [...p.querySelectorAll('.sr-only')].map(s => s.textContent.trim()).filter(Boolean),
          sectorFills: sectors, centre: p.querySelector('[data-slot="donut-center"], .absolute')?.innerText?.trim(),
          list: [...p.querySelectorAll('.tabular-nums')].map(s => s.textContent.trim()),
          tablesInPanel: p.querySelectorAll('table').length, tablesOnPage: document.querySelectorAll('table').length,
          cardBg: cs(p).backgroundColor, bodyBg: cs(document.body).backgroundColor,
          accessibilityLayerTabIndex: p.querySelector('.recharts-surface, .recharts-wrapper [tabindex]')?.getAttribute('tabindex') ?? p.querySelector('[tabindex="0"]')?.className?.toString().slice(0, 60) ?? null,
        };
      });
      // hover tooltip on the first (largest) sector
      // hover a point ON the largest sector's arc (its bbox centre falls in the donut hole)
      const pt = await page.evaluate(() => { const path = document.querySelector('[data-testid="time-activity-panel"] .recharts-pie-sector path'); const L = path.getTotalLength(); const q = path.getPointAtLength(L * 0.25); const m = path.getScreenCTM(); return { x: q.x * m.a + q.y * m.c + m.e, y: q.x * m.b + q.y * m.d + m.f }; });
      await page.mouse.move(pt.x, pt.y, { steps: 5 });
      await page.waitForTimeout(600);
      const hoverTip = await page.evaluate(() => (() => { const t = document.querySelector('[data-testid="time-activity-panel"] .recharts-tooltip-wrapper'); return t && getComputedStyle(t).visibility !== 'hidden' ? t.innerText.trim() : null; })());
      await panel.screenshot({ path: `${OUT}/my-work-donut-${tag}-hover.png` });
      await page.mouse.move(0, 0); await page.waitForTimeout(400);
      // keyboard: focus the accessibilityLayer surface and press ArrowRight twice
      const focusable = panel.locator('.recharts-wrapper [tabindex="0"], svg.recharts-surface[tabindex="0"]').first();
      let keyboard = { focusable: await focusable.count() > 0, tooltipAfterArrow: null };
      if (keyboard.focusable) {
        await focusable.focus(); await page.keyboard.press('ArrowRight'); await page.waitForTimeout(400); await page.keyboard.press('ArrowRight'); await page.waitForTimeout(500);
        keyboard.activeElement = await page.evaluate(() => document.activeElement?.tagName + '.' + (document.activeElement?.getAttribute('class') || '').slice(0, 40));
        keyboard.tooltipAfterArrow = await page.evaluate(() => { const t = document.querySelector('[data-testid="time-activity-panel"] .recharts-tooltip-wrapper'); return t && getComputedStyle(t).visibility !== 'hidden' ? t.innerText.trim() : null; });
        await panel.screenshot({ path: `${OUT}/my-work-donut-${tag}-keyboard.png` });
        await page.keyboard.press('Escape'); await focusable.blur?.();
      }
      // View as table
      const toggle = panel.getByRole('button', { name: /view as table/i });
      await toggle.click(); await page.waitForTimeout(500);
      const open = await page.evaluate(() => { const p = document.querySelector('[data-testid="time-activity-panel"]'); const t = p.querySelector('table'); return { tablesInPanel: p.querySelectorAll('table').length, tablesOnPage: document.querySelectorAll('table').length, rows: t ? [...t.querySelectorAll('tr')].map(r => [...r.children].map(c => c.textContent.trim()).join(' | ')) : [] }; });
      await panel.screenshot({ path: `${OUT}/my-work-donut-${tag}-table.png` });
      const hideBtn = panel.getByRole('button', { name: /hide table|view as chart|hide/i });
      if (await hideBtn.count()) await hideBtn.first().click(); else await toggle.click();
      await page.waitForTimeout(400);
      const closedTables = await page.evaluate(() => document.querySelector('[data-testid="time-activity-panel"]').querySelectorAll('table').length);
      const hydration = logs.filter(l => /hydrat|did not match|server rendered|mismatch/i.test(l));
      report.push({ theme, motion, w, h, ...base, hoverTooltip: hoverTip, keyboard, tableOpen: open, tablesInPanelAfterClose: closedTables, console: logs, hydrationMessages: hydration });
    }
    await ctx.close();
  }
  require('fs').writeFileSync(`${OUT}/obs-report.json`, JSON.stringify({ capturedAt: new Date().toISOString(), head: process.env.HEAD_SHA, report }, null, 2));
  console.log(report.map(r => `${r.w}/${r.theme}/${r.motion}: html=${r.htmlClass} fig=${r.figureRole}:${r.figureName} fills=${r.sectorFills.join(',')} centre=${JSON.stringify(r.centre)} tables(default panel/page)=${r.tablesInPanel}/${r.tablesOnPage} hover=${JSON.stringify(r.hoverTooltip)} kb=${JSON.stringify(r.keyboard)} tableOpen=${r.tableOpen.tablesInPanel}/${r.tableOpen.tablesOnPage} rows=${r.tableOpen.rows.length} afterClose=${r.tablesInPanelAfterClose} console=${r.console.length} hydr=${r.hydrationMessages.length}`).join('\n'));
  await browser.close();
})().catch(e => { console.error('OBS FAILED', e); process.exit(1); });
