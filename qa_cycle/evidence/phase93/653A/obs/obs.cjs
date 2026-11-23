// 653A OBS — real-browser observation of the take-on foundation (e2e mock stack :3001).
// Usage: STEP=1|2|3|4 node obs.cjs   (state shared via state.json in this directory)
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-653A/frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const BASE = 'http://localhost:3001';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
// Step 2 inputs (2a = the given fixture, header on row 3; 2b = header-row-1 variant of the same workbook)
const DEBTORS_FILE = process.env.DEBTORS_FILE || '/tmp/653obs/debtors-two-sheets.xlsx';
const HEADER_ROW = process.env.HEADER_ROW || '3';
const TAG = process.env.TAG || '';
const STATE_FILE = `${D}/state.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

let page, currentLabel = 'init';
const consoleErrors = [];
const shot = async n => { await page.screenshot({ path: `${D}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };

async function login(user) {
  await page.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await page.locator('#user-select').selectOption(user);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  L('[login]', user, page.url());
}

async function openTakeOnTabViaUi() {
  currentLabel = 'settings-matters';
  await page.goto(`${BASE}/org/${SLUG}/settings/matters`, { waitUntil: 'load' });
  await page.getByRole('tablist').first().waitFor({ timeout: 60000 });
  const tabs = await page.getByRole('tab').allInnerTexts();
  L('[matters tabs]', tabs);
  const takeOn = page.getByRole('tab', { name: 'Take-on', exact: true });
  const visible = (await takeOn.count()) > 0;
  L('[take-on tab visible]', visible);
  return { tabs, visible, takeOn };
}

async function selectOption(trigger, optionName) {
  await trigger.click();
  await page.getByRole('option', { name: optionName, exact: true }).click();
}

const rowLocator = () => page.locator('table[data-testid="take-on-rows"] tbody tr[data-testid^="take-on-row-"]');

async function countRows() {
  // wait until either the table or the empty notice is rendered for the current filter
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

async function cardsVsTable(prefix) {
  const card = async id => Number((await page.getByTestId(`take-on-card-${id}`).innerText()).replace(/[^0-9]/g, '') || 'NaN');
  const cards = { total: await card('total'), valid: await card('valid'), error: await card('error'), duplicate: await card('duplicate'), blocks: await card('blocks') };
  L(`[${prefix} cards]`, cards);
  const table = {};
  table.all = await countRows();
  const allRows = await readRows();
  L(`[${prefix} rows (All statuses)]`);
  allRows.forEach(r => L('   ', r.join(' | ')));
  await shot(`${prefix}-validation-all`);
  for (const [label, key] of [['Pending', 'pending'], ['Valid', 'valid'], ['Error', 'error']]) {
    await selectOption(page.getByTestId('take-on-filter-status'), label);
    table[key] = await countRows();
    await shot(`${prefix}-filter-${key}`);
  }
  await selectOption(page.getByTestId('take-on-filter-status'), 'All statuses');
  await page.getByTestId('take-on-filter-blocks').click();
  table.blocksOnly = await countRows();
  await shot(`${prefix}-filter-blocks-only`);
  await page.getByTestId('take-on-filter-blocks').click();
  await countRows();
  L(`[${prefix} table counts]`, table);
  const eq = {
    total: cards.total === table.all,
    valid: cards.valid === table.valid,
    error: cards.error === table.error,
    blocks: cards.blocks === table.blocksOnly,
    pendingZero: table.pending === 0,
  };
  L(`[${prefix} cards==table]`, eq);
  return { cards, table, eq, allRows };
}

async function step1() {
  await login('user_e2e_alice');
  const { visible, takeOn } = await openTakeOnTabViaUi();
  if (!visible) throw new Error('Take-on tab not visible for Alice');
  await takeOn.click();
  await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
  L('[url after tab click]', page.url());
  await shot('s1-01-alice-take-on-tab');
  currentLabel = 'take-on-tab';
  await page.getByTestId('take-on-start-clients').click();
  await page.getByTestId('take-on-new-upload').waitFor();
  await page.getByTestId('take-on-batch-name').fill('OBS 653A clients (semicolon BOM)');
  await page.getByTestId('take-on-file-input').setInputFiles('/tmp/653obs/clients-semicolon-bom.csv');
  await shot('s1-02-clients-upload-form');
  await page.getByTestId('take-on-upload-submit').click();
  await page.waitForFunction(() => location.search.includes('batch=') || document.querySelector('[data-testid="take-on-upload-warnings"]') || document.querySelector('[data-testid="take-on-upload-server-error"]'), null, { timeout: 60000 });
  if (await page.getByTestId('take-on-upload-server-error').isVisible().catch(() => false)) {
    L('[upload server error]', await page.getByTestId('take-on-upload-server-error').innerText());
    await shot('s1-03-upload-error'); throw new Error('clients upload failed');
  }
  if (await page.getByTestId('take-on-upload-warnings').isVisible().catch(() => false)) {
    L('[upload warnings]', await page.getByTestId('take-on-upload-warnings').innerText());
    await shot('s1-03-upload-warnings');
    await page.getByTestId('take-on-continue').click();
    await page.waitForURL(/batch=/, { timeout: 60000 });
  }
  state.clientsBatch = new URL(page.url()).searchParams.get('batch'); saveState();
  L('[clients batch]', state.clientsBatch);
  currentLabel = 'clients-wizard';
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  await shot('s1-04-clients-mapping');
  // Headers as the reader saw them: the Sheet step's preview table.
  await page.getByTestId('take-on-change-sheet').click();
  await page.getByTestId('take-on-preview').waitFor({ timeout: 60000 });
  const headers = await page.locator('[data-testid="take-on-preview"] thead th').allInnerTexts();
  L('[clients preview headers]', headers);
  L('[first header]', JSON.stringify(headers[0]), 'codepoints', [...(headers[0] || '')].slice(0, 3).map(c => c.codePointAt(0).toString(16)));
  const firstRow = await page.locator('[data-testid="take-on-preview"] tbody tr').first().locator('td').allInnerTexts();
  L('[clients preview first row]', firstRow);
  await shot('s1-05-clients-preview-headers');
  await page.getByTestId('take-on-step-mapping').click();
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  const mapTriggers = page.locator('[data-testid^="take-on-map-"]');
  const mapped = {};
  for (let i = 0; i < await mapTriggers.count(); i++) {
    const t = mapTriggers.nth(i);
    mapped[(await t.getAttribute('data-testid')).replace('take-on-map-', '')] = (await t.innerText()).trim();
  }
  L('[clients suggested mapping]', mapped);
  if (await page.getByTestId('take-on-mapping-issues').isVisible().catch(() => false)) {
    L('[mapping issues]', await page.getByTestId('take-on-mapping-issues').innerText());
  }
  await page.getByTestId('take-on-save-mapping').click();
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-validate').click();
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  L('[clients batch status]', await page.getByTestId('take-on-batch-status').innerText());
  state.step1 = await cardsVsTable('s1-clients'); saveState();
}

async function step2() {
  await login('user_e2e_alice');
  const { visible, takeOn } = await openTakeOnTabViaUi();
  if (!visible) throw new Error('Take-on tab not visible for Alice');
  await takeOn.click();
  await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
  currentLabel = 'take-on-tab';
  await page.getByTestId('take-on-start-debtor-balances').click();
  await page.getByTestId('take-on-new-upload').waitFor();
  await page.getByTestId('take-on-cutover-date').fill('2026-10-31');
  await page.getByTestId('take-on-batch-name').fill('OBS 653A debtors (two sheets)');
  await page.getByTestId('take-on-file-input').setInputFiles(DEBTORS_FILE);
  await shot(`s2${TAG}-` + '01-debtors-upload-form');
  await page.getByTestId('take-on-upload-submit').click();
  await page.waitForFunction(() => location.search.includes('batch=') || document.querySelector('[data-testid="take-on-upload-warnings"]') || document.querySelector('[data-testid="take-on-upload-server-error"]'), null, { timeout: 60000 });
  if (await page.getByTestId('take-on-upload-server-error').isVisible().catch(() => false)) {
    L('[upload server error]', await page.getByTestId('take-on-upload-server-error').innerText());
    await shot(`s2${TAG}-` + '02-upload-error'); throw new Error('debtors upload failed');
  }
  if (await page.getByTestId('take-on-upload-warnings').isVisible().catch(() => false)) {
    L('[upload warnings]', await page.getByTestId('take-on-upload-warnings').innerText());
    await shot(`s2${TAG}-` + '02-upload-warnings');
    await page.getByTestId('take-on-continue').click();
    await page.waitForURL(/batch=/, { timeout: 60000 });
  }
  state.debtorsBatch = new URL(page.url()).searchParams.get('batch'); saveState();
  L('[debtors batch]', state.debtorsBatch);
  currentLabel = 'debtors-wizard';
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
  L('[cutover shown]', await page.getByTestId('take-on-batch-cutover').innerText().catch(() => '(none)'));
  await page.getByTestId('take-on-sheet-picker').waitFor({ timeout: 60000 });
  const radios = page.locator('[data-testid^="take-on-sheet-"][role="radio"]');
  const sheets = [];
  for (let i = 0; i < await radios.count(); i++) {
    const tid = await radios.nth(i).getAttribute('data-testid');
    const idx = tid.replace('take-on-sheet-', '');
    sheets.push({ idx, name: await page.locator(`label[for="take-on-sheet-${idx}"]`).innerText(), hiddenBadge: await page.getByTestId(`take-on-sheet-hidden-${idx}`).count() > 0, checked: await radios.nth(i).getAttribute('aria-checked') });
  }
  L('[sheet options]', sheets);
  L('[default header row]', await page.getByTestId('take-on-header-row').inputValue());
  await shot(`s2${TAG}-` + '03-debtors-sheet-picker-initial');
  const debtors = sheets.find(s => s.name === 'Debtors');
  await page.getByTestId(`take-on-sheet-${debtors.idx}`).click();
  await page.getByTestId('take-on-header-row').fill(HEADER_ROW);
  await page.getByTestId('take-on-apply-sheet').click();
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  await shot(`s2${TAG}-` + '04-debtors-mapping');
  await page.getByTestId('take-on-change-sheet').click();
  await page.getByTestId('take-on-preview').waitFor({ timeout: 60000 });
  L('[debtors preview headers]', await page.locator('[data-testid="take-on-preview"] thead th').allInnerTexts());
  const prows = page.locator('[data-testid="take-on-preview"] tbody tr');
  for (let i = 0; i < await prows.count(); i++) L('   preview', (await prows.nth(i).locator('td').allInnerTexts()).join(' | '));
  await shot(`s2${TAG}-` + '05-debtors-sheet-after-apply');
  await page.getByTestId('take-on-step-mapping').click();
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  const mapTriggers = page.locator('[data-testid^="take-on-map-"]');
  const mapped = {};
  for (let i = 0; i < await mapTriggers.count(); i++) {
    const t = mapTriggers.nth(i);
    mapped[(await t.getAttribute('data-testid')).replace('take-on-map-', '')] = (await t.innerText()).trim();
  }
  L('[debtors suggested mapping]', mapped);
  if (await page.getByTestId('take-on-mapping-issues').isVisible().catch(() => false)) {
    L('[mapping issues]', await page.getByTestId('take-on-mapping-issues').innerText());
  }
  await page.getByTestId('take-on-save-mapping').click();
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-validate').click();
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  state.step2 = await cardsVsTable(`s2${TAG}-debtors`); saveState();
}

async function step3() {
  for (const [user, tag] of [['user_e2e_bob', 'bob'], ['user_e2e_carol', 'carol']]) {
    const ctx = page.context();
    await ctx.clearCookies();
    await login(user);
    const { tabs, visible, takeOn } = await openTakeOnTabViaUi();
    state[`${tag}Tabs`] = tabs; state[`${tag}TabVisible`] = visible;
    await shot(`s3-${tag}-matters-tabs`);
    if (visible) {
      await takeOn.click();
      await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
      L(`[${tag} take-on tab]`, (await page.getByTestId('take-on-tab').innerText()).slice(0, 400).replace(/\n/g, ' | '));
      await shot(`s3-${tag}-take-on-tab`);
    }
    currentLabel = `${tag}-deeplink`;
    await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on`, { waitUntil: 'load' });
    await page.waitForTimeout(3000);
    const selected = await page.getByRole('tab', { selected: true }).allInnerTexts().catch(() => []);
    const hasTakeOn = await page.getByTestId('take-on-tab').count();
    const h1 = await page.locator('h1').allInnerTexts();
    L(`[${tag} deep link ?tab=take-on]`, { url: page.url(), selectedTab: selected, takeOnRendered: hasTakeOn > 0, h1 });
    state[`${tag}DeepLink`] = { url: page.url(), selectedTab: selected, takeOnRendered: hasTakeOn > 0, h1 };
    await shot(`s3-${tag}-deeplink-take-on`);
    saveState();
  }
}

async function step4() {
  await login('user_e2e_alice');
  currentLabel = 'debtors-wizard-cancel';
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on&batch=${state.debtorsBatch}`, { waitUntil: 'load' });
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
  L('[before cancel status]', await page.getByTestId('take-on-batch-status').innerText());
  await shot('s4-01-debtors-before-cancel');
  await page.getByTestId('take-on-cancel').click();
  await page.getByRole('alertdialog').waitFor();
  await shot('s4-02-cancel-confirm-dialog');
  await page.getByTestId('take-on-cancel-confirm').click();
  await page.getByTestId('take-on-cancelled').waitFor({ timeout: 60000 });
  L('[after cancel status]', await page.getByTestId('take-on-batch-status').innerText());
  L('[cancelled notice]', await page.getByTestId('take-on-cancelled').innerText());
  await shot('s4-03-debtors-cancelled');
  await page.reload({ waitUntil: 'load' });
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
  L('[after reload status]', await page.getByTestId('take-on-batch-status').innerText());
  await shot('s4-04-debtors-cancelled-reload');
  await page.getByTestId('take-on-back').click();
  await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
  L('[history]', (await page.getByTestId('take-on-tab').innerText()).replace(/\n/g, ' | ').slice(0, 1200));
  await shot('s4-05-history-after-cancel');
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  try {
    if (STEP === '1') await step1();
    else if (STEP === '2') await step2();
    else if (STEP === '3') await step3();
    else if (STEP === '4') await step4();
    else throw new Error('STEP must be 1..4');
  } catch (e) {
    L('[script error]', e.stack);
    await shot(`s${STEP}${TAG}-zz-failure`).catch(() => {});
    process.exitCode = 1;
  }
  L(`[summary step ${STEP}] console/page errors:`, consoleErrors.length);
  fs.writeFileSync(`${D}/obs-step${STEP}${TAG}-log.txt`, log.join('\n') + '\n');
  await browser.close();
})();
