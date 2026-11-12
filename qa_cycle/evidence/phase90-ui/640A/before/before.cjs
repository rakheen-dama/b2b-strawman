// 640A task 640.1 — "before" OBS of Home (/org/e2e-test-org/dashboard) on the :3001 mock stack built from main
// (e0e399b24-equivalent), light theme. Personas: Alice (owner), Bob (admin), Carol (member), and Carol re-assigned
// THROUGH THE UI (Team → member sheet, as Alice) to a custom role holding FINANCIAL_VISIBILITY (created through
// Settings → Firm → Roles if absent), then Carol restored to her original role through the same UI and verified.
// Playwright library, not MCP (the MCP Chrome profile is held by the user's live session).
// Per persona: data-audience on [data-testid=home-page], ordered home-cell-* testids, h1, count of browser requests
// whose URL contains "dashboard/kpis", console errors/warnings + hydration messages, full-page screenshot.
// Usage: node before.cjs <outDir> <playwrightTestModuleDir>
const OUT = process.argv[2];
const { chromium } = require(process.argv[3]);
const fs = require('fs');
const BASE = 'http://localhost:3001', O = '/org/e2e-test-org';
const ROLE_NAME = 'Finance viewer (640A OBS)';
const USERS = { alice: 'user_e2e_alice', bob: 'user_e2e_bob', carol: 'user_e2e_carol' };
const steps = [];
const step = (s) => { steps.push(`${new Date().toISOString()} ${s}`); console.log(s); };

async function login(browser, who, w = 1440, h = 900) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h } });
  await ctx.addInitScript(() => { try { localStorage.setItem('kazi-theme', 'light'); } catch (e) {} });
  const page = await ctx.newPage();
  const st = { logs: [], kpiReqs: [] };
  page.on('console', m => { if (['error', 'warning'].includes(m.type())) st.logs.push(`[${m.type()}] ${m.text().slice(0, 300)}`); });
  page.on('pageerror', e => st.logs.push(`[pageerror] ${e.message.slice(0, 300)}`));
  page.on('request', r => { if (/dashboard\/kpis/i.test(r.url())) st.kpiReqs.push(`${r.method()} ${r.url()}`); });
  await page.goto(BASE + '/mock-login');
  await page.locator('#user-select').selectOption(USERS[who]);
  await page.getByRole('button', { name: 'Sign In' }).click();
  await page.waitForURL(/\/dashboard/, { timeout: 30000 });
  return { ctx, page, st };
}

async function captureHome(sess, persona, w, file) {
  const { page, st } = sess;
  st.logs.length = 0; st.kpiReqs.length = 0;
  await page.goto(BASE + O + '/dashboard', { waitUntil: 'load' });
  await page.locator('#main-content [data-testid="home-page"]').waitFor({ timeout: 30000 });
  await page.waitForLoadState('networkidle').catch(() => {});
  await page.waitForTimeout(1500);
  await page.screenshot({ path: `${OUT}/${file}`, fullPage: true });
  const d = await page.evaluate(() => {
    const hp = document.querySelector('#main-content [data-testid="home-page"]');
    return {
      homePageCount: document.querySelectorAll('[data-testid="home-page"]').length,
      audience: hp?.getAttribute('data-audience') ?? null,
      cells: [...hp.querySelectorAll('[data-testid^="home-cell-"]')].map(c => c.getAttribute('data-testid')),
      rows: [...hp.children].filter(ch => ch.querySelector(':scope > [data-testid^="home-cell-"]'))
        .map(r => [...r.children].map(c => `${c.getAttribute('data-testid')}{${c.className.split(' ').filter(k => /col-span/.test(k)).join(' ')}}`)),
      h1: [...document.querySelectorAll('h1')].map(h => h.textContent.trim()),
      htmlClass: document.documentElement.className,
    };
  });
  const rec = { persona, viewport: w, file, ...d, cellCount: d.cells.length, kpisRequests: st.kpiReqs.length, kpisRequestUrls: [...st.kpiReqs],
    console: [...st.logs], hydrationMessages: st.logs.filter(l => /hydrat|did not match|server rendered|mismatch/i.test(l)) };
  step(`HOME ${persona}@${w}: audience=${rec.audience} cells(${rec.cellCount})=${rec.cells.join(',')} h1=${JSON.stringify(rec.h1)} kpis=${rec.kpisRequests} console=${rec.console.length}`);
  return rec;
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
  const report = { capturedAt: new Date().toISOString(), head: process.env.HEAD_SHA, roleName: ROLE_NAME, personas: [], roleFlow: {} };
  try {
    // 1-3: system personas
    for (const who of ['alice', 'bob', 'carol']) {
      const s = await login(browser, who);
      report.personas.push(await captureHome(s, who, 1440, `home-${who}-1440-light.png`));
      if (who === 'alice') {
        await s.page.setViewportSize({ width: 1024, height: 768 });
        report.personas.push(await captureHome(s, who, 1024, `home-alice-1024-light.png`));
      }
      await s.ctx.close();
    }

    // 4: role flow as Alice
    const a = await login(browser, 'alice');
    await a.page.goto(BASE + O + '/settings/firm?tab=roles', { waitUntil: 'load' });
    await a.page.getByRole('heading', { name: 'Custom Roles' }).waitFor({ timeout: 30000 });
    await a.page.screenshot({ path: `${OUT}/roles-before.png`, fullPage: true });
    const exists = await a.page.getByRole('heading', { name: ROLE_NAME, exact: true }).count() > 0
      || await a.page.getByText(ROLE_NAME, { exact: true }).count() > 0;
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

    let restoreErr = null;
    try {
      const c = await login(browser, 'carol');
      report.personas.push(await captureHome(c, 'carol-finance', 1440, 'home-carol-finance-1440-light.png'));
      await c.ctx.close();
    } catch (e) { restoreErr = e; step(`capture carol-finance FAILED: ${e.message}`); }

    // restore
    const pre = await openCarolSheet(a.page, 'carol-prerestore');
    await assignRole(a.page, pre.sheet, orig.roleValue, 'carol-restore');
    const rest = await openCarolSheet(a.page, 'carol-restored');
    await a.page.keyboard.press('Escape');
    await a.page.waitForTimeout(500);
    await a.page.reload({ waitUntil: 'load' });
    await a.page.locator('[data-testid^="member-row-"]').first().waitFor();
    await a.page.screenshot({ path: `${OUT}/team-carol-restored-final.png`, fullPage: true });
    report.roleFlow.restored = { ...rest, sheet: undefined };
    report.roleFlow.restoreVerified = rest.badge === orig.badge && rest.roleValue === orig.roleValue
      && !rest.overrideCount && JSON.stringify(rest.checkedCaps) === JSON.stringify(orig.checkedCaps);
    step(`Carol restored: badge=${rest.badge} sheetRole=${rest.roleValue} caps=${JSON.stringify(rest.checkedCaps)} verified=${report.roleFlow.restoreVerified}`);
    await a.page.goto(BASE + O + '/settings/firm?tab=roles', { waitUntil: 'load' });
    await a.page.getByRole('heading', { name: 'Custom Roles' }).waitFor({ timeout: 30000 });
    await a.page.screenshot({ path: `${OUT}/roles-final.png`, fullPage: true });
    await a.ctx.close();

    // Carol's Home after restore (should match persona 3)
    const c2 = await login(browser, 'carol');
    report.personas.push(await captureHome(c2, 'carol-restored', 1440, 'home-carol-restored-1440-light.png'));
    await c2.ctx.close();
    if (restoreErr) throw restoreErr;
  } finally {
    report.steps = steps;
    fs.writeFileSync(`${OUT}/before-report.json`, JSON.stringify(report, null, 2));
    await browser.close();
  }
})().catch(e => { console.error('OBS FAILED', e); process.exit(1); });
