// 655A OBS — the clients take-on commit engine observed in a real browser (e2e mock stack :3001).
// Usage: STEP=0|a|b|carol|c|d|e|f|g node obs.cjs   (state shared via state.json in this directory)
//   0      read the seeded client list (pick an existing client's email for the duplicate row)
//   a      upload -> map (CDD records location default) -> validate; cards vs row table
//   b      attestation as Alice (statement, FICA summary, checkbox gate, attest)
//   carol  read-only: Carol (member) opens the same batch — no Attest
//   c      commit dialog -> progress -> results (Mailpit count before/after)
//   d      created clients ACTIVE in the client list (2 spot checks)
//   e      Create portal access (Mailpit count before/after)
//   f      result CSV download
//   g      Compliance page dormancy check (candidate list)
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-655A/frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const BASE = 'http://localhost:3001';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
const FIXTURE = `${D}/fixtures/clients-20.csv`;
const STATE_FILE = `${D}/state.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

let page, currentLabel = 'init';
const consoleErrors = [];
const shot = async n => { await page.screenshot({ path: `${D}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };

async function mailpitTotal() {
  const r = await fetch('http://localhost:8026/api/v1/messages?limit=1');
  const j = await r.json();
  return { total: j.total, messages_count: j.messages_count };
}

async function login(user) {
  await page.context().clearCookies();
  await page.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await page.locator('#user-select').selectOption(user);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  L('[login]', user, page.url());
}

async function openBatch() {
  currentLabel = 'wizard';
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on&batch=${state.batch}`, { waitUntil: 'load' });
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
  L('[batch status]', await page.getByTestId('take-on-batch-status').innerText());
}

async function selectOption(trigger, optionName) {
  await trigger.click();
  await page.getByRole('option', { name: optionName, exact: true }).click();
}

const rowLocator = () => page.locator('table[data-testid="take-on-rows"] tbody tr[data-testid^="take-on-row-"]');

async function countRows() {
  await page.waitForFunction(() => document.querySelector('[data-testid="take-on-rows"]') || document.querySelector('[data-testid="take-on-rows-empty"]'), null, { timeout: 30000 });
  await page.waitForTimeout(800);
  if (await page.getByTestId('take-on-rows-empty').isVisible().catch(() => false)) return 0;
  return rowLocator().count();
}

async function readRows() {
  const out = [];
  const n = await rowLocator().count();
  for (let i = 0; i < n; i++) {
    const cells = await rowLocator().nth(i).locator('td').allInnerTexts();
    out.push(cells.map(c => c.replace(/\s+/g, ' ').trim()));
  }
  return out;
}

async function filterOptions() {
  await page.getByTestId('take-on-filter-status').click();
  const opts = await page.getByRole('option').allInnerTexts();
  await page.keyboard.press('Escape');
  return opts.map(o => o.trim());
}

async function step0() {
  await login('user_e2e_alice');
  currentLabel = 'clients-list';
  await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: 'load' });
  await page.locator('table').first().waitFor({ timeout: 60000 });
  const heads = await page.locator('table thead th').allInnerTexts();
  L('[client list headers]', heads);
  const rows = page.locator('table tbody tr');
  const n = await rows.count();
  const out = [];
  for (let i = 0; i < n; i++) out.push((await rows.nth(i).locator('td').allInnerTexts()).map(c => c.replace(/\s+/g, ' ').trim()));
  out.forEach(r => L('   ', r.join(' | ')));
  state.seededClients = out; saveState();
  await shot('s0-01-client-list-before');
  // Open the first client to read its email (the list may not show one).
  const link = page.locator('table tbody tr a').first();
  const name = (await link.innerText()).trim();
  await link.click();
  await page.waitForURL(/\/clients\/[0-9a-f-]{36}/, { timeout: 60000 });
  await page.waitForTimeout(2000);
  const body = await page.locator('main').innerText();
  const emails = [...new Set(body.match(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g) || [])];
  L('[existing client]', name, page.url(), 'emails on page:', emails);
  state.existingClient = { name, url: page.url(), emails }; saveState();
  await shot('s0-02-existing-client-detail');
  // Firm name as the org shows it (for the attestation statement check).
  currentLabel = 'settings-firm';
  await page.goto(`${BASE}/org/${SLUG}/settings/firm`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  const inputs = await page.locator('main input').evaluateAll(els => els.map(e => ({ name: e.name || e.id || e.getAttribute('aria-label'), value: e.value })));
  L('[settings/firm inputs]', inputs.filter(i => i.value).slice(0, 10));
  await shot('s0-03-settings-firm');
}

async function stepA() {
  await login('user_e2e_alice');
  currentLabel = 'take-on-tab';
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on`, { waitUntil: 'load' });
  await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-start-clients').click();
  await page.getByTestId('take-on-new-upload').waitFor();
  await page.getByTestId('take-on-batch-name').fill('OBS 655A clients (20 rows)');
  await page.getByTestId('take-on-file-input').setInputFiles(FIXTURE);
  await shot('a-01-upload-form');
  await page.getByTestId('take-on-upload-submit').click();
  await page.waitForFunction(() => location.search.includes('batch=') || document.querySelector('[data-testid="take-on-upload-warnings"]') || document.querySelector('[data-testid="take-on-upload-server-error"]'), null, { timeout: 60000 });
  if (await page.getByTestId('take-on-upload-server-error').isVisible().catch(() => false)) {
    L('[upload server error]', await page.getByTestId('take-on-upload-server-error').innerText());
    await shot('a-02-upload-error'); throw new Error('clients upload failed');
  }
  if (await page.getByTestId('take-on-upload-warnings').isVisible().catch(() => false)) {
    L('[upload warnings]', await page.getByTestId('take-on-upload-warnings').innerText());
    await shot('a-02-upload-warnings');
    await page.getByTestId('take-on-continue').click();
    await page.waitForURL(/batch=/, { timeout: 60000 });
  }
  state.batch = new URL(page.url()).searchParams.get('batch'); state.obsStart = state.obsStart || new Date().toISOString(); saveState();
  L('[batch]', state.batch);
  currentLabel = 'mapping';
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  const mapTriggers = page.locator('[data-testid^="take-on-map-"]');
  const mapped = {};
  for (let i = 0; i < await mapTriggers.count(); i++) {
    const t = mapTriggers.nth(i);
    mapped[(await t.getAttribute('data-testid')).replace('take-on-map-', '')] = (await t.innerText()).trim();
  }
  L('[suggested mapping]', mapped);
  state.suggestedMapping = mapped; saveState();
  // The file has no CDD records location column: the batch default fills it.
  await page.getByTestId('take-on-default-cddRecordsLocation').fill('Archive room B, Sandton office');
  // Dates in the fixture are ISO.
  const df = (await page.getByTestId('take-on-date-format').innerText()).trim();
  L('[date format shown]', df);
  if (!df.startsWith('2025-12-31')) await selectOption(page.getByTestId('take-on-date-format'), '2025-12-31 (yyyy-mm-dd)');
  await shot('a-03-mapping');
  if (await page.getByTestId('take-on-mapping-issues').isVisible().catch(() => false)) {
    L('[mapping issues]', await page.getByTestId('take-on-mapping-issues').innerText());
  }
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'validation';
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-validate').click();
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  await page.waitForTimeout(1500);
  L('[batch status]', await page.getByTestId('take-on-batch-status').innerText());
  const card = async id => Number((await page.getByTestId(`take-on-card-${id}`).innerText()).replace(/[^0-9]/g, '') || 'NaN');
  const cards = { total: await card('total'), valid: await card('valid'), error: await card('error'), duplicate: await card('duplicate'), blocks: await card('blocks') };
  L('[cards]', cards);
  const table = { all: await countRows() };
  const allRows = await readRows();
  L('[row table header]', await page.locator('table[data-testid="take-on-rows"] thead th').allInnerTexts());
  L('[rows (All statuses)]');
  allRows.forEach(r => L('   ', r.join(' | ')));
  await shot('a-04-validation-all');
  L('[status filter options]', await filterOptions());
  for (const [label, key] of [['Valid', 'valid'], ['Error', 'error']]) {
    await selectOption(page.getByTestId('take-on-filter-status'), label);
    table[key] = await countRows();
    if (key === 'error') { L('[error rows]'); (await readRows()).forEach(r => L('   ', r.join(' | '))); }
    await shot(`a-05-filter-${key}`);
  }
  await selectOption(page.getByTestId('take-on-filter-status'), 'All statuses');
  await countRows();
  // Duplicates = rows whose messages name an existing client (duplicate_of_id); count from the table.
  const dupRows = allRows.filter(r => /already exists|duplicate of an existing|matches an existing/i.test(r.join(' ')));
  table.duplicateFromMessages = dupRows.length;
  L('[table counts]', table);
  const eq = { total: cards.total === table.all, valid: cards.valid === table.valid, error: cards.error === table.error, duplicate: cards.duplicate === table.duplicateFromMessages };
  L('[cards==table]', eq);
  state.a = { cards, table, eq, allRows }; saveState();
}

async function stepB() {
  await login('user_e2e_alice');
  await openBatch();
  currentLabel = 'attestation';
  await page.getByTestId('take-on-to-attestation').click();
  await page.getByTestId('take-on-attestation').waitFor({ timeout: 60000 });
  const statement = await page.getByTestId('take-on-attestation-statement').innerText();
  L('[statement]', statement);
  L('[statement caption]', await page.locator('[data-testid="take-on-attestation"] figcaption').innerText());
  L('[defaults]', await page.getByTestId('take-on-attestation-defaults').innerText());
  L('[fica summary]', await page.getByTestId('take-on-attestation-fica-summary').innerText());
  const attestBtn = page.getByTestId('take-on-attest');
  const disabledBefore = await attestBtn.isDisabled();
  L('[Attest disabled before checkbox]', disabledBefore);
  await shot('b-01-attestation-unchecked');
  await page.getByTestId('take-on-attestation-accept').click();
  const disabledAfter = await attestBtn.isDisabled();
  L('[Attest disabled after checkbox]', disabledAfter);
  await shot('b-02-attestation-checked');
  await attestBtn.click();
  await page.getByTestId('take-on-attestation-live').waitFor({ timeout: 60000 });
  const live = await page.getByTestId('take-on-attestation-live').innerText();
  L('[live attestation]', live);
  await shot('b-03-attested');
  state.b = { statement, disabledBefore, disabledAfter, live }; saveState();
}

async function stepCarol() {
  await login('user_e2e_carol');
  currentLabel = 'carol-wizard';
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on&batch=${state.batch}`, { waitUntil: 'load' });
  await page.waitForTimeout(4000);
  const wizard = await page.getByTestId('take-on-wizard').count();
  const h1 = await page.locator('h1').allInnerTexts();
  const mainText = (await page.locator('main').innerText()).slice(0, 1500).replace(/\n/g, ' | ');
  L('[carol deep link]', { url: page.url(), wizardRendered: wizard > 0, h1 });
  L('[carol main text]', mainText);
  await shot('b-04-carol-deeplink');
  const res = { wizardRendered: wizard > 0, h1, attestButtons: 0, commitButtons: 0, unavailable: null };
  if (wizard > 0) {
    const toAtt = page.getByTestId('take-on-step-attestation');
    if (await toAtt.count()) { await toAtt.click().catch(() => {}); await page.waitForTimeout(1500); }
    res.attestButtons = await page.getByTestId('take-on-attest').count();
    res.commitButtons = await page.getByTestId('take-on-commit').count();
    res.unavailable = await page.getByTestId('take-on-attestation-unavailable').innerText().catch(() => null);
    await shot('b-05-carol-attestation');
  }
  L('[carol result]', res);
  state.carol = res; saveState();
}

async function stepC() {
  await login('user_e2e_alice');
  await openBatch();
  currentLabel = 'commit';
  await page.getByTestId('take-on-attestation').waitFor({ timeout: 60000 });
  L('[live attestation]', await page.getByTestId('take-on-attestation-live').innerText());
  const before = await mailpitTotal();
  L('[mailpit before commit]', before);
  await page.getByTestId('take-on-commit').click();
  await page.getByTestId('take-on-commit-dialog').waitFor();
  const title = await page.locator('[data-testid="take-on-commit-dialog"] h2').innerText().catch(async () => page.getByRole('alertdialog').getByRole('heading').innerText());
  const body = await page.getByTestId('take-on-commit-body').innerText();
  L('[commit dialog]', { title, body });
  await shot('c-01-commit-dialog');
  await page.getByTestId('take-on-commit-confirm').click();
  const progressSeen = [];
  const t0 = Date.now();
  while (Date.now() - t0 < 180000) {
    const p = await page.getByTestId('take-on-commit-progress').innerText().catch(() => null);
    if (p && progressSeen[progressSeen.length - 1] !== p) { progressSeen.push(p); L('[progress]', p, `+${Date.now() - t0}ms`); if (progressSeen.length === 1) await shot('c-02-committing'); }
    if (await page.getByTestId('take-on-result-cards').isVisible().catch(() => false)) break;
    await page.waitForTimeout(250);
  }
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 60000 });
  const fig = async id => (await page.getByTestId(id).innerText()).replace(/\s+/g, ' ').trim();
  const results = { created: await fig('take-on-result-created'), skipped: await fig('take-on-result-skipped'), failed: await fig('take-on-result-failed') };
  L('[results]', results);
  L('[batch status]', await page.getByTestId('take-on-batch-status').innerText());
  L('[completed at]', await page.getByTestId('take-on-completed-at').innerText().catch(() => null));
  await shot('c-03-results');
  await page.waitForTimeout(5000);
  const after = await mailpitTotal();
  L('[mailpit after commit (+5s)]', after);
  state.c = { before, after, title, body, progressSeen, results }; saveState();
}

async function stepD() {
  await login('user_e2e_alice');
  currentLabel = 'clients-list-after';
  const picks = (process.env.PICKS || '').split('|').filter(Boolean);
  await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: 'load' });
  await page.locator('table').first().waitFor({ timeout: 60000 });
  L('[client list rows after commit]', await page.locator('table tbody tr').count());
  await shot('d-01-client-list-after');
  state.d = {};
  for (const [i, name] of picks.entries()) {
    await page.goto(`${BASE}/org/${SLUG}/clients?search=${encodeURIComponent(name)}`, { waitUntil: 'load' });
    await page.locator('table').first().waitFor({ timeout: 60000 });
    await page.waitForTimeout(1000);
    const rows = [];
    const r = page.locator('table tbody tr');
    for (let k = 0; k < await r.count(); k++) rows.push((await r.nth(k).locator('td').allInnerTexts()).map(c => c.replace(/\s+/g, ' ').trim()).join(' | '));
    L(`[search ${name}]`, rows);
    await shot(`d-0${i + 2}-search-${i + 1}`);
    const link = page.locator('table tbody tr a', { hasText: name }).first();
    await link.click();
    await page.waitForURL(/\/clients\/[0-9a-f-]{36}/, { timeout: 60000 });
    await page.waitForTimeout(2000);
    const head = (await page.locator('main').innerText()).slice(0, 600).replace(/\n/g, ' | ');
    L(`[detail ${name}]`, page.url(), head);
    await shot(`d-0${i + 4}-detail-${i + 1}`);
    state.d[name] = { rows, url: page.url(), head };
  }
  saveState();
}

async function stepE() {
  await login('user_e2e_alice');
  await openBatch();
  currentLabel = 'portal';
  await page.getByTestId('take-on-results').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-portal').waitFor({ timeout: 60000 });
  L('[portal section]', await page.getByTestId('take-on-portal').innerText());
  const before = await mailpitTotal();
  L('[mailpit before portal access]', before);
  await shot('e-01-portal-before');
  await page.getByTestId('take-on-portal-access').click();
  await page.getByTestId('take-on-portal-result').or(page.getByTestId('take-on-portal-error')).waitFor({ timeout: 60000 });
  const result = await page.getByTestId('take-on-portal-result').innerText().catch(() => null);
  const error = await page.getByTestId('take-on-portal-error').innerText().catch(() => null);
  L('[portal result]', { result, error });
  await shot('e-02-portal-after');
  await page.waitForTimeout(5000);
  const after = await mailpitTotal();
  L('[mailpit after portal access (+5s)]', after);
  state.e = { before, after, result, error }; saveState();
}

async function stepF() {
  await login('user_e2e_alice');
  await openBatch();
  currentLabel = 'download';
  await page.getByTestId('take-on-result-download').waitFor({ timeout: 60000 });
  const [download] = await Promise.all([page.waitForEvent('download', { timeout: 60000 }), page.getByTestId('take-on-result-download').click()]);
  const name = download.suggestedFilename();
  const out = `${D}/result-download/${name}`;
  fs.mkdirSync(`${D}/result-download`, { recursive: true });
  await download.saveAs(out);
  L('[downloaded]', name, fs.statSync(out).size, 'bytes');
  await shot('f-01-after-download');
  // The Validation step's table shows the per-row outcomes after the commit.
  await page.getByTestId('take-on-step-validation').click();
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  await countRows();
  const rows = await readRows();
  L('[post-commit row table]'); rows.forEach(r => L('   ', r.join(' | ')));
  await shot('f-02-validation-outcomes');
  state.f = { name, out, rows }; saveState();
}

async function stepG() {
  await login('user_e2e_alice');
  currentLabel = 'compliance';
  await page.goto(`${BASE}/org/${SLUG}/compliance`, { waitUntil: 'load' });
  const btn = page.getByRole('button', { name: /check for dormant clients/i });
  await btn.waitFor({ timeout: 60000 });
  await shot('g-01-compliance');
  const resp = page.waitForResponse(r => r.request().method() === 'POST', { timeout: 60000 }).catch(() => null);
  await btn.click();
  await resp;
  await page.waitForFunction(() => !document.body.innerText.includes('Checking...'), null, { timeout: 60000 });
  await page.waitForTimeout(1500);
  const section = await page.locator('main').innerText();
  const idx = section.indexOf('Dormancy Check');
  const text = section.slice(idx, idx + 3000);
  L('[dormancy section]', text.replace(/\n/g, ' | '));
  await shot('g-02-dormancy-result');
  state.g = { text }; saveState();
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  const steps = { 0: step0, a: stepA, b: stepB, carol: stepCarol, c: stepC, d: stepD, e: stepE, f: stepF, g: stepG };
  L(`[step ${STEP}] start`, new Date().toISOString());
  try {
    if (!steps[STEP]) throw new Error('unknown STEP');
    await steps[STEP]();
  } catch (e) {
    L('[script error]', e.stack);
    await shot(`zz-failure-step-${STEP}`).catch(() => {});
    process.exitCode = 1;
  }
  L(`[step ${STEP}] end`, new Date().toISOString(), 'console/page errors:', consoleErrors.length);
  fs.appendFileSync(`${D}/obs-log.txt`, log.join('\n') + '\n');
  await browser.close();
})();
