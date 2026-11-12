// 639D own OBS: the Home "Debtors" widget's per-row aging meter re-pointed onto the kit StackedMeter
// (palette="ordinal-aging"), at 1024x768 / 1440x900 x light / dark on the :3001 mock stack (frontend image built
// from faa96b0a8, data seeded by E2E-LEGAL legal-lifecycle + legal-depth-ii), as Alice (owner).
// Playwright library, not MCP (the MCP Chrome profile is held by the user's live session — same deviation as 637A..639B).
// Per surface: meter count, each meter's aria-label + resolved aria-describedby summary, computed fill of the track and
// every segment rect, gap lines, the --aging-* / status / track CSS var values (aging != status), legend dots,
// console errors/warnings + hydration messages; widget + viewport screenshots.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-639D/frontend/node_modules/@playwright/test');
const OUT = '/Users/rakheendama/Projects/2026/worktree-epic-639D/qa_cycle/evidence/phase90-ui/639D';
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
const sizes = [[1024, 768], [1440, 900]];
const VARS = ['--aging-current', '--aging-d30', '--aging-d60', '--aging-d90plus', '--success', '--warning', '--danger', '--muted', '--card'];
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
    for (const [w, h] of sizes) {
      logs = [];
      await page.setViewportSize({ width: w, height: h });
      await page.goto(BASE + O + '/dashboard', { waitUntil: 'load' });
      await page.locator('h1').first().waitFor({ timeout: 30000 });
      // Scoped to #main-content: on the first load after sign-in a second, transient copy of the widget was seen
      // (strict-mode violation, gone after settle) — counts at first sight and after settle are recorded.
      const widget = page.locator('#main-content [data-testid="debtors-widget"]');
      await widget.waitFor({ timeout: 30000 });
      const widgetCountAtFirstSight = await page.locator('[data-testid="debtors-widget"]').count();
      await page.waitForTimeout(1500);
      const widgetCountAfterSettle = await page.locator('[data-testid="debtors-widget"]').count();
      await widget.scrollIntoViewIfNeeded();
      const tag = `${w}-${theme}`;
      await widget.screenshot({ path: `${OUT}/debtors-${tag}.png` });
      await page.screenshot({ path: `${OUT}/home-${tag}.png` });
      const data = await page.evaluate((VARS) => {
        const root = getComputedStyle(document.documentElement);
        const cssVars = Object.fromEntries(VARS.map(v => [v, root.getPropertyValue(v).trim()]));
        const wd = document.querySelector('#main-content [data-testid="debtors-widget"]');
        const svgs = [...wd.querySelectorAll('svg[role="img"]')];
        const meters = svgs.map(svg => {
          const descId = svg.getAttribute('aria-describedby');
          const desc = descId ? document.getElementById(descId) : null;
          const track = svg.querySelector('[data-slot="stacked-meter-track"]');
          const r = svg.getBoundingClientRect();
          return {
            ariaLabel: svg.getAttribute('aria-label'),
            describedById: descId,
            describedByText: desc ? desc.textContent.trim() : null,
            describedByIsSrOnly: desc ? desc.classList.contains('sr-only') : null,
            dataEmpty: svg.getAttribute('data-empty'),
            box: { w: Math.round(r.width), h: Math.round(r.height) },
            track: track ? { fillAttr: track.getAttribute('fill'), fillComputed: getComputedStyle(track).fill } : null,
            segments: [...svg.querySelectorAll('rect[data-segment]')].map(s => ({
              key: s.getAttribute('data-segment'), x: s.getAttribute('x'), width: s.getAttribute('width'),
              fillAttr: s.getAttribute('fill'), fillComputed: getComputedStyle(s).fill,
              title: s.querySelector('title')?.textContent,
            })),
            gaps: [...svg.querySelectorAll('[data-slot="stacked-meter-gap"]')].map(g => ({ x: g.getAttribute('x1'), strokeComputed: getComputedStyle(g).stroke })),
          };
        });
        const legend = wd.querySelector('ul[aria-label="Aging legend"]');
        const legendItems = legend ? [...legend.querySelectorAll('li')].map(li => {
          const dot = li.querySelector('span[aria-hidden="true"]');
          return { text: li.textContent.trim(), dot: !!dot, dotBg: dot ? getComputedStyle(dot).backgroundColor : null };
        }) : [];
        const rows = [...wd.querySelectorAll('ul[aria-label="Largest debtors"] > li')].map(li => li.innerText.replace(/\s+/g, ' ').trim());
        const wr = wd.getBoundingClientRect();
        return {
          htmlClass: document.documentElement.className,
          cssVars, headline: wd.querySelector('[data-testid="debtors-headline"]')?.textContent.trim() ?? null,
          widgetText: wd.innerText.replace(/\s+/g, ' ').trim().slice(0, 400),
          widgetBox: { w: Math.round(wr.width), h: Math.round(wr.height) },
          widgetOverflowX: wd.scrollWidth > wd.clientWidth,
          meterCount: svgs.length, meters, legendItems, rows,
        };
      }, VARS);
      const hydration = logs.filter(l => /hydrat|did not match|server rendered|mismatch/i.test(l));
      report.push({ theme, w, h, widgetCountAtFirstSight, widgetCountAfterSettle, ...data, console: logs, hydrationMessages: hydration });
    }
    await ctx.close();
  }
  require('fs').writeFileSync(`${OUT}/obs-report.json`, JSON.stringify({ capturedAt: new Date().toISOString(), head: process.env.HEAD_SHA, report }, null, 2));
  console.log(report.map(r => `${r.w}/${r.theme}: html=${r.htmlClass} widgets=${r.widgetCountAtFirstSight}->${r.widgetCountAfterSettle} headline=${r.headline} meters=${r.meterCount} legend=${r.legendItems.length} console=${r.console.length} hydr=${r.hydrationMessages.length}`).join('\n'));
  await browser.close();
})().catch(e => { console.error('OBS FAILED', e); process.exit(1); });
