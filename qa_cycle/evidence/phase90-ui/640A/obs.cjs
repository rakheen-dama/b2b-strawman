// 640A after-OBS RE-RUN on fixed head d78ce3ce5 (PR #1767): Home (/org/e2e-test-org/dashboard) on the :3001 mock stack
// built from d78ce3ce5 (copied from pre-fix-41c860fb8/obs.cjs; adds per-cell boxes, truncation scan, row region shots,
// diary data-columns),
// seeded by E2E-LEGAL (legal-lifecycle + legal-depth-ii). Playwright library, not MCP (MCP Chrome profile is held by
// the user's live session). Personas: Alice (owner), Bob (admin), Carol (member), and Carol re-assigned THROUGH THE UI
// (Settings → Firm → Roles "New Role" with only Financial Visibility; Team → member sheet) to that custom role, then
// restored to Member through the same UI and verified (badge after reload + sheet). No SQL, no REST writes.
// Matrix: 4 personas × 1024x768 / 1440x900 × light / dark = 16 captures.
// Per capture: data-audience, ordered home-cell-* list, rows, h1, page-header, the 4 KPI StatCards (label/value/delta/
// hint/state), KPI grid layout (tiles per row), sparkline a11y (DOM + CDP AX tree + ariaSnapshot), diary layout,
// console errors/warnings + hydration, horizontal overflow, browser requests to dashboard/kpis; full-page + kpis shots.
// Usage: node obs.cjs <outDir> <playwrightTestModuleDir>
const OUT = process.argv[2];
const { chromium } = require(process.argv[3]);
const fs = require('fs');
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
const ROLE_NAME = 'Finance viewer (640A OBS)';
const USERS = { alice: 'user_e2e_alice', bob: 'user_e2e_bob', carol: 'user_e2e_carol' };
const SIZES = [[1024, 768], [1440, 900]];
const THEMES = ['light', 'dark'];
const steps = [];
const step = (s) => { steps.push(`${new Date().toISOString()} ${s}`); console.log(s); };

async function login(browser, who, theme = 'light', w = 1440, h = 900) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h } });
  await ctx.addInitScript(t => { try { localStorage.setItem('kazi-theme', t); } catch (e) {} }, theme);
  const page = await ctx.newPage();
  const st = { logs: [], kpiReqs: [] };
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) st.logs.push(`[${m.type()}] ${m.text().slice(0, 300)}`); });
  page.on('pageerror', e => st.logs.push(`[pageerror] ${e.message.slice(0, 300)}`));
  page.on('request', r => { if (/dashboard\/kpis/i.test(r.url())) st.kpiReqs.push(`${r.method()} ${r.url()}`); });
  await page.goto(BASE + '/mock-login');
  await page.locator('#user-select').selectOption(USERS[who]);
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  // Settle the post-login page (avoids the before-run harness race that aborted the mock-IDP /userinfo fetch).
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(2000);
  return { ctx, page, st };
}

async function axSparkline(page) {
  // CDP full AX tree: find role=img nodes, report computed name + description.
  const cdp = await page.context().newCDPSession(page);
  const { nodes } = await cdp.send('Accessibility.getFullAXTree');
  await cdp.detach();
  return nodes.filter(n => ['image', 'img'].includes(n.role?.value))
    .map(n => ({ role: n.role?.value, name: n.name?.value ?? null, description: n.description?.value ?? null, ignored: !!n.ignored }))
    .filter(n => /hours logged/i.test(n.name || ''));
}

async function captureHome(sess, persona, w, h, theme) {
  const { page, st } = sess;
  await page.setViewportSize({ width: w, height: h });
  st.logs.length = 0; st.kpiReqs.length = 0;
  await page.goto(BASE + O + '/dashboard', { waitUntil: 'load' });
  await page.locator('#main-content [data-testid="home-page"]').waitFor({ timeout: 30000 });
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(1500);
  const tag = `${persona}-${w}-${theme}`;
  const file = `home-${tag}.png`;
  await page.screenshot({ path: `${OUT}/${file}`, fullPage: true });
  const kpiCell = page.locator('#main-content [data-testid="home-cell-kpis"]');
  let kpiFile = null, kpiAria = null, diaryFile = null;
  if (await kpiCell.count()) {
    kpiFile = `kpis-${tag}.png`;
    await kpiCell.screenshot({ path: `${OUT}/${kpiFile}` });
    kpiAria = await kpiCell.ariaSnapshot().catch(e => `ERR ${e.message}`);
  }
  const regionFiles = {};
  const rowOf = (cell) => page.locator(`#main-content [data-testid="${cell}"]`).locator('xpath=..');
  const pbase = persona.replace(/-.*/, '');
  if (['alice', 'bob'].includes(pbase) && await page.locator('#main-content [data-testid="home-cell-unbilledWip"]').count()) {
    regionFiles.money = `row-money-${tag}.png`;
    await rowOf('home-cell-unbilledWip').screenshot({ path: `${OUT}/${regionFiles.money}` }); // superseded by region-shots.cjs (sticky header overprint)
  }
  if (pbase === 'alice' && await page.locator('#main-content [data-testid="home-cell-ficaOutstanding"]').count()) {
    regionFiles.risk = `row-risk-${tag}.png`;
    await rowOf('home-cell-ficaOutstanding').screenshot({ path: `${OUT}/${regionFiles.risk}` }); // superseded by region-shots.cjs
  }
  const diaryCell = page.locator('#main-content [data-testid="home-cell-diary"]');
  if (w === 1440 && await diaryCell.count()) {
    diaryFile = `diary-${tag}.png`;
    await diaryCell.screenshot({ path: `${OUT}/${diaryFile}` });
  }
  const d = await page.evaluate(() => {
    const hp = document.querySelector('#main-content [data-testid="home-page"]');
    const txt = (el) => el ? el.textContent.replace(/\s+/g, ' ').trim() : null;
    const kc = hp.querySelector('[data-testid="home-cell-kpis"]');
    let kpi = null;
    if (kc) {
      const cards = [...kc.querySelectorAll('[data-slot="stat-card"]')];
      const tops = cards.map(c => Math.round(c.getBoundingClientRect().top));
      const rowsMap = {}; tops.forEach(t => { rowsMap[t] = (rowsMap[t] || 0) + 1; });
      const grid = kc.querySelector('[data-slot="kpi-row"] > div.grid');
      kpi = {
        periodLabel: txt(kc.querySelector('[data-slot="kpi-row"] > p')),
        kpiRowWidth: Math.round(kc.querySelector('[data-slot="kpi-row"]')?.getBoundingClientRect().width ?? 0),
        gridTemplateColumns: grid ? getComputedStyle(grid).gridTemplateColumns : null,
        tilesPerRow: Object.keys(rowsMap).sort((a, b) => a - b).map(k => rowsMap[k]),
        tiles: cards.map(c => {
          const svg = c.querySelector('[data-slot="stat-card-sparkline"] svg');
          const descId = svg?.getAttribute('aria-describedby');
          const desc = descId ? document.getElementById(descId) : null;
          let hiddenAncestor = null;
          for (let el = svg; el; el = el.parentElement) if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') { hiddenAncestor = el.getAttribute('data-slot') || el.tagName; break; }
          const valueEl = c.querySelector('[data-slot="stat-card-value"]');
          const deltaEl = c.querySelector('[data-slot="stat-card-delta"]');
          const r = c.getBoundingClientRect();
          return {
            label: txt(c.querySelector('[data-slot="stat-card-label"]')),
            state: c.getAttribute('data-state'),
            valueVisible: valueEl ? [...valueEl.childNodes].filter(n => !(n.classList && n.classList.contains('sr-only'))).map(n => n.textContent).join('').trim() : null,
            valueSr: txt(valueEl?.querySelector('.sr-only')),
            deltaFigure: txt(deltaEl?.querySelector('[data-slot="stat-card-delta-figure"]')),
            deltaTone: deltaEl?.getAttribute('data-tone') ?? null,
            deltaSr: txt(deltaEl?.querySelector('.sr-only')),
            deltaVisible: deltaEl ? txt(deltaEl).replace(txt(deltaEl.querySelector('.sr-only')) || '', '').trim() : null,
            hint: txt(c.querySelector('[data-slot="stat-card-hint"]')),
            errorStatus: txt(c.querySelector('[role="status"]')),
            box: { x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height) },
            sparkline: svg ? {
              role: svg.getAttribute('role'), ariaLabel: svg.getAttribute('aria-label'), describedById: descId,
              describedByText: txt(desc), describedByIsSrOnly: desc ? desc.classList.contains('sr-only') : null,
              ariaHiddenAncestor: hiddenAncestor, dataEmpty: svg.getAttribute('data-empty'),
              box: { w: Math.round(svg.getBoundingClientRect().width), h: Math.round(svg.getBoundingClientRect().height) },
              lineStroke: svg.querySelector('[data-slot="sparkline-trend-line"]') ? getComputedStyle(svg.querySelector('[data-slot="sparkline-trend-line"]')).stroke : null,
            } : null,
          };
        }),
      };
    }
    const dc = hp.querySelector('[data-testid="home-cell-diary"]');
    let diary = null;
    if (dc) {
      const content = dc.querySelector('[data-testid="todays-diary"]');
      const ul = content?.querySelector('ul');
      const lis = ul ? [...ul.children] : [];
      const xs = [...new Set(lis.map(li => Math.round(li.getBoundingClientRect().left)))];
      const cs = content ? getComputedStyle(content) : null;
      diary = {
        contentBoxWidth: content ? Math.round(content.getBoundingClientRect().width - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight)) : null,
        containerType: cs?.containerType ?? null,
        rowCount: lis.length,
        ulColumnCount: ul ? getComputedStyle(ul).columnCount : null,
        dataColumns: ul ? ul.getAttribute('data-columns') : null,
        ulClass: ul ? ul.className : null,
        distinctColumnsX: xs,
        rows: lis.map(li => ({ text: txt(li).slice(0, 80), x: Math.round(li.getBoundingClientRect().left), y: Math.round(li.getBoundingClientRect().top), w: Math.round(li.getBoundingClientRect().width) })),
        emptyText: ul ? null : txt(content?.querySelector('p')),
        overdueBadge: txt(content?.querySelector('[data-testid="todays-diary-overdue"]')),
        ulHeight: ul ? Math.round(ul.getBoundingClientRect().height) : null,
      };
    }
    const ph = document.querySelectorAll('#main-content [data-testid="page-header"]');
    const phEl = ph[0];
    return {
      homePageCount: document.querySelectorAll('[data-testid="home-page"]').length,
      audience: hp?.getAttribute('data-audience') ?? null,
      cells: [...hp.querySelectorAll('[data-testid^="home-cell-"]')].map(c => c.getAttribute('data-testid')),
      rows: [...hp.children].filter(ch => ch.querySelector(':scope > [data-testid^="home-cell-"]'))
        .map(r => [...r.children].map(c => `${c.getAttribute('data-testid')}{${c.className.split(' ').filter(k => /col-span/.test(k)).join(' ')}}`)),
      cellBoxes: [...hp.querySelectorAll('[data-testid^="home-cell-"]')].map(c => {
        const r = c.getBoundingClientRect();
        // Truncation scan: leaf-ish elements whose content overflows their box (ellipsis/clip) inside the cell.
        const trunc = [...c.querySelectorAll('*')].filter(el => {
          if (el.closest('svg') || el.classList.contains('sr-only') || !el.textContent.trim()) return false;
          const cs = getComputedStyle(el);
          if (cs.display === 'none' || cs.visibility === 'hidden') return false;
          const clips = /hidden|clip/.test(cs.overflowX) || cs.textOverflow === 'ellipsis';
          return clips && el.scrollWidth > el.clientWidth + 1 && el.clientWidth > 0;
        }).map(el => ({ text: el.textContent.replace(/\s+/g, ' ').trim().slice(0, 60), clientW: el.clientWidth, scrollW: el.scrollWidth }));
        return { cell: c.getAttribute('data-testid'), x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height),
          spans: c.className.split(' ').filter(k => /col-span/.test(k)).join(' '), truncatedCount: trunc.length, truncated: trunc.slice(0, 25) };
      }),
      h1: [...document.querySelectorAll('h1')].map(h => ({ text: h.textContent.trim(), inMain: !!h.closest('#main-content'), slot: h.getAttribute('data-slot'), color: getComputedStyle(h).color, visible: h.getBoundingClientRect().height > 0 })),
      pageHeader: phEl ? {
        countInMain: ph.length,
        description: txt(phEl.querySelector('[data-slot="page-header-description"]')),
        descriptionColor: phEl.querySelector('[data-slot="page-header-description"]') ? getComputedStyle(phEl.querySelector('[data-slot="page-header-description"]')).color : null,
        box: (() => { const r = phEl.getBoundingClientRect(); return { x: Math.round(r.left), y: Math.round(r.top), w: Math.round(r.width), h: Math.round(r.height) }; })(),
      } : null,
      htmlClass: document.documentElement.className,
      bodyBg: getComputedStyle(document.body).backgroundColor,
      overflowX: { scrollWidth: document.documentElement.scrollWidth, clientWidth: document.documentElement.clientWidth, overflow: document.documentElement.scrollWidth > document.documentElement.clientWidth },
      kpi, diary,
    };
  });
  const ax = d.kpi ? await axSparkline(page) : [];
  const rec = { persona, viewport: `${w}x${h}`, width: w, theme, file, kpiFile, diaryFile, regionFiles, ...d, cellCount: d.cells.length,
    kpiAriaSnapshot: kpiAria, sparklineAx: ax,
    kpisRequests: st.kpiReqs.length, kpisRequestUrls: [...st.kpiReqs],
    console: [...st.logs], hydrationMessages: st.logs.filter(l => /hydrat|did not match|server rendered|mismatch/i.test(l)) };
  const kv = d.kpi ? d.kpi.tiles.map(t => `${t.label}=${t.valueVisible}${t.deltaFigure ? `(${t.deltaFigure},${t.deltaTone})` : ''}${t.hint ? `[${t.hint}]` : ''}`).join('; ') : 'NO KPIS';
  step(`HOME ${tag}: audience=${rec.audience} cells(${rec.cellCount})=${rec.cells.join(',')} h1=${JSON.stringify(d.h1.map(x => x.text))} kpi{${kv}} perRow=${JSON.stringify(d.kpi?.tilesPerRow)} diary=${d.diary ? `${d.diary.rowCount}rows data-columns=${d.diary.dataColumns} cols=${d.diary.ulColumnCount} xs=${d.diary.distinctColumnsX} empty=${d.diary.emptyText}` : '-'} boxes=${d.cellBoxes.map(b => `${b.cell.replace('home-cell-', '')}:${b.w}${b.truncatedCount ? `/t${b.truncatedCount}` : ''}`).join(',')} overflow=${d.overflowX.overflow} kpisReq=${rec.kpisRequests} console=${rec.console.length}`);
  return rec;
}

async function captureMatrix(browser, who, persona, report) {
  for (const theme of THEMES) {
    const s = await login(browser, who, theme);
    for (const [w, h] of SIZES) report.captures.push(await captureHome(s, persona, w, h, theme));
    await s.ctx.close();
  }
}

async function openCarolSheet(page, tag) {
  await page.goto(BASE + O + '/team', { waitUntil: 'load' });
  const row = page.locator('[data-testid^="member-row-"]').filter({ hasText: 'Carol' });
  await row.first().waitFor({ timeout: 30000 });
  if (await row.count() !== 1) throw new Error(`expected 1 Carol row, got ${await row.count()}`);
  const testid = await row.getAttribute('data-testid');
  const badge = (await row.locator('[data-testid="member-role-badge"]').textContent()).trim();
  const overrideCount = await row.locator('[data-testid="override-count"]').count() ? (await row.locator('[data-testid="override-count"]').textContent()).trim() : null;
  await page.screenshot({ path: `${OUT}/team-${tag}.png`, fullPage: true });
  await row.click();
  const sheet = page.locator('[data-slot="sheet-content"]');
  await sheet.waitFor({ timeout: 15000 });
  await sheet.getByText('Loading capabilities...').waitFor({ state: 'detached', timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(800);
  const roleValue = (await sheet.getByRole('combobox').textContent()).trim();
  const checkedCaps = await sheet.locator('button[role="checkbox"][data-state="checked"]').evaluateAll(els => els.map(e => e.getAttribute('aria-label')));
  const overrideMarks = await sheet.locator('span.text-teal-600, span.text-destructive').allTextContents();
  await page.screenshot({ path: `${OUT}/team-${tag}-sheet.png` });
  return { testid, badge, overrideCount, roleValue, checkedCaps, overrideMarks, sheet };
}

async function assignRole(page, sheet, roleName, tag) {
  await sheet.getByRole('combobox').click();
  await page.getByRole('option', { name: roleName, exact: true }).click();
  await page.waitForTimeout(400);
  const checkedCaps = await sheet.locator('button[role="checkbox"][data-state="checked"]').evaluateAll(els => els.map(e => e.getAttribute('aria-label')));
  await page.screenshot({ path: `${OUT}/team-${tag}-selected.png` });
  await sheet.getByRole('button', { name: 'Save' }).click();
  await sheet.waitFor({ state: 'detached', timeout: 20000 });
  await page.waitForTimeout(1500);
  return checkedCaps;
}

(async () => {
  const browser = await chromium.launch();
  const report = { capturedAt: new Date().toISOString(), head: process.env.HEAD_SHA, roleName: ROLE_NAME, captures: [], roleFlow: {} };
  let a = null;
  try {
    await captureMatrix(browser, 'alice', 'alice', report);
    await captureMatrix(browser, 'bob', 'bob', report);
    await captureMatrix(browser, 'carol', 'carol', report);

    // Role flow as Alice
    a = await login(browser, 'alice');
    await a.page.goto(BASE + O + '/settings/firm?tab=roles', { waitUntil: 'load' });
    await a.page.getByRole('heading', { name: 'Custom Roles' }).waitFor({ timeout: 30000 });
    await a.page.screenshot({ path: `${OUT}/roles-before.png`, fullPage: true });
    const exists = await a.page.getByText(ROLE_NAME, { exact: true }).count() > 0;
    report.roleFlow.roleExistedBefore = exists;
    if (!exists) {
      await a.page.getByRole('button', { name: 'New Role' }).click();
      const dlg = a.page.getByRole('dialog');
      await dlg.waitFor();
      await dlg.locator('#role-name').fill(ROLE_NAME);
      await dlg.locator('#role-description').fill('640A before/after OBS persona: Financial Visibility only');
      await dlg.getByRole('checkbox', { name: 'Financial Visibility' }).click();
      await a.page.screenshot({ path: `${OUT}/roles-create-dialog.png` });
      await dlg.getByRole('button', { name: 'Create' }).click();
      await dlg.waitFor({ state: 'detached', timeout: 20000 });
      await a.page.waitForTimeout(1500);
      step(`created custom role "${ROLE_NAME}"`);
    }
    await a.page.goto(BASE + O + '/settings/firm?tab=roles', { waitUntil: 'load' });
    await a.page.getByRole('heading', { name: 'Custom Roles' }).waitFor({ timeout: 30000 });
    await a.page.screenshot({ path: `${OUT}/roles-after-create.png`, fullPage: true });
    report.roleFlow.roleVisibleAfterCreate = await a.page.getByText(ROLE_NAME, { exact: true }).count() > 0;

    const orig = await openCarolSheet(a.page, 'carol-original');
    report.roleFlow.original = { ...orig, sheet: undefined };
    step(`Carol original: badge=${orig.badge} sheetRole=${orig.roleValue} overrides=${orig.overrideCount} marks=${JSON.stringify(orig.overrideMarks)} caps=${JSON.stringify(orig.checkedCaps)}`);
    if (orig.overrideCount || orig.overrideMarks.length) throw new Error('Carol has capability overrides — a role change would reset them; aborting before any change');
    if (orig.roleValue === ROLE_NAME) throw new Error('Carol already on the OBS role — original role unknown; aborting');
    report.roleFlow.assignedCapsShown = await assignRole(a.page, orig.sheet, ROLE_NAME, 'carol-assign');
    const after = await openCarolSheet(a.page, 'carol-finance');
    report.roleFlow.afterAssign = { ...after, sheet: undefined };
    step(`Carol after assign: badge=${after.badge} sheetRole=${after.roleValue} caps=${JSON.stringify(after.checkedCaps)}`);
    await a.page.keyboard.press('Escape');

    let fvErr = null;
    try { await captureMatrix(browser, 'carol', 'carolfv', report); }
    catch (e) { fvErr = e; step(`capture carolfv FAILED: ${e.message}`); }

    // Restore (always attempted)
    const pre = await openCarolSheet(a.page, 'carol-prerestore');
    await assignRole(a.page, pre.sheet, orig.roleValue, 'carol-restore');
    const rest = await openCarolSheet(a.page, 'carol-restored');
    await a.page.keyboard.press('Escape');
    await a.page.waitForTimeout(500);
    await a.page.reload({ waitUntil: 'load' });
    const row = a.page.locator('[data-testid^="member-row-"]').filter({ hasText: 'Carol' });
    await row.first().waitFor();
    const badgeAfterReload = (await row.locator('[data-testid="member-role-badge"]').textContent()).trim();
    await a.page.screenshot({ path: `${OUT}/team-carol-restored-final.png`, fullPage: true });
    report.roleFlow.restored = { ...rest, sheet: undefined, badgeAfterReload };
    report.roleFlow.restoreVerified = rest.badge === orig.badge && badgeAfterReload === orig.badge && rest.roleValue === orig.roleValue
      && !rest.overrideCount && JSON.stringify(rest.checkedCaps) === JSON.stringify(orig.checkedCaps);
    step(`Carol restored: badge=${rest.badge} badgeAfterReload=${badgeAfterReload} sheetRole=${rest.roleValue} caps=${JSON.stringify(rest.checkedCaps)} verified=${report.roleFlow.restoreVerified}`);
    await a.page.goto(BASE + O + '/settings/firm?tab=roles', { waitUntil: 'load' });
    await a.page.getByRole('heading', { name: 'Custom Roles' }).waitFor({ timeout: 30000 });
    await a.page.screenshot({ path: `${OUT}/roles-final.png`, fullPage: true });
    await a.ctx.close(); a = null;

    // Carol's Home after restore (should match the member captures)
    const c2 = await login(browser, 'carol');
    report.restoredCheck = await captureHome(c2, 'carol-restored', 1440, 900, 'light');
    await c2.ctx.close();
    if (fvErr) throw fvErr;
  } finally {
    report.steps = steps;
    fs.writeFileSync(`${OUT}/obs-report.json`, JSON.stringify(report, null, 2));
    await browser.close();
  }
})().catch(e => { console.error('OBS FAILED', e); process.exit(1); });
