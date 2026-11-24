// 657B OBS — trust opening position: validation, live equation indicator, hard blocks (e2e mock stack :3001).
// Usage: STEP=<step> node obs.cjs   (state shared via state.json in this directory)
//   accounts  create G (General, not primary), I3 (Investment, s86(3)) and I4 (Investment, s86(4)) in the UI
//   clients   clients take-on: A, B, C (previous-system IDs) → map → dry run → attest → commit
//   matters   matters take-on: A1, B1, C1 for A, B, C → map → dry run → commit from Validation
//   debit     trust batch on G at 31 Oct 2026: four §17.3.13 files with client C at −R500,00, the §17.3.13 position
//   r10       corrected client file (C R49 500,00), cashbook R403 510,00 (off by R10,00)
//   fix       cashbook R403 500,00 — the batch goes back to Mapped on the save, the dry run re-runs (D-183, OP-5)
//   acctcheck cutover 30 Oct 2026 without the mid-month tick → account error (D-191); then back to 31 Oct
// No SQL. REST only for batch GETs (allowedActions/status) with the browser session's own token.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-657B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const F = `${D}/files`;
const BASE = 'http://localhost:3001';
const API = 'http://localhost:8081';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
const STATE_FILE = `${D}/state.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2) + '\n');
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

const ACCOUNTS = {
  G: { name: 'OBS 657B Trust banking G', number: '6570001', type: 'General' },
  I3: { name: 'OBS 657B s86(3) investment I3', number: '6573001', type: 'Investment', basis: 'Section 86(3) — the firm invests surplus trust money' },
  I4: { name: 'OBS 657B s86(4) investment I4', number: '6574001', type: 'Investment', basis: "Section 86(4) — invested on a client's instruction" },
};
const CUTOVER = '2026-10-31';
const POSITION = { bank: '418500.00', od: '5000.00', op: '20000.00', cashbook: '403500.00', fund: '1250.00', invested: '300000.00' };

// X3 (§17.10) texts, built as the backend builds them (IntegrityMessages.rand: U+00A0 thousands separator).
const NB = ' ';
const X3 = {
  debitC: "This client's matter is in debit (R-500,00) at the cutover date. No trust creditor may be in debit (Rule 54.14.9). The firm must immediately report a debit balance to the Council in writing, with the reason and proof that it was rectified (Rule 54.14.11). Correct it in the previous system, then upload the corrected file.",
  eq2Debit: `The trust creditors (R353${NB}500,00) do not equal the cashbook balance at cutover (R403${NB}500,00): difference R50${NB}000,00. Every creditor group must be included and must agree to the cent.`,
  eq1R10: `Bank balance R418${NB}500,00 + outstanding deposits R5${NB}000,00 − outstanding payments R20${NB}000,00 is R403${NB}500,00, not the cashbook balance R403${NB}510,00.`,
  eq2R10: `The trust creditors (R403${NB}500,00) do not equal the cashbook balance at cutover (R403${NB}510,00): difference R10,00. Every creditor group must be included and must agree to the cent.`,
  monthEnd: 'The cutover date is not a month end. Confirm a mid-month cutover, and reconcile the previous system to this date.',
};
const OVERRIDE_RE = /override|overrid|acknowledge|dismiss|ignore|continue anyway|proceed|sign off|sign-off|commit/i;

let page, context, token, currentLabel = 'init';
const consoleErrors = [];
const shot = async n => { await page.screenshot({ path: `${D}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };
const norm = s => (s || '').replace(/[\s  ]+/g, ' ').trim();
const authHeaders = () => ({ Authorization: `Bearer ${token}` });

async function login(user) {
  await context.clearCookies();
  await page.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await page.locator('#user-select').selectOption(user);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  const c = (await context.cookies()).find(x => x.name === 'mock-auth-token');
  if (!c) throw new Error('no mock-auth-token cookie after login');
  token = c.value;
  L('[login]', user, page.url());
}

async function getBatch(id) {
  const r = await page.request.get(`${API}/api/take-on/batches/${id}`, { headers: authHeaders() });
  const j = await r.json();
  return { http: r.status(), status: j.status, allowedActions: j.allowedActions, counts: j.counts, validatedAt: j.validatedAt, cutoverDate: j.cutoverDate, position: j.position, midMonthAcknowledged: j.midMonthAcknowledged, summary: j.summary };
}

async function pick(trigger, optionName) {
  await trigger.click();
  const opt = page.getByRole('option', { name: optionName, exact: true });
  await opt.waitFor({ timeout: 15000 });
  await page.waitForTimeout(300); // Radix open animation
  await opt.click();
}

async function openTakeOnTab() {
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on`, { waitUntil: 'load' });
  await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
}

async function openBatch(id) {
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on&batch=${id}`, { waitUntil: 'load' });
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
}

async function ensureIsoDates() {
  const df = (await page.getByTestId('take-on-date-format').innerText()).trim();
  if (!df.startsWith('2025-12-31')) await pick(page.getByTestId('take-on-date-format'), '2025-12-31 (yyyy-mm-dd)');
}

/** Map every field of the current source to the header given (forced, whatever was suggested). */
async function mapAndSave(targets) {
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  await page.waitForTimeout(800);
  const suggested = {};
  for (const [key, header] of Object.entries(targets)) {
    const t = page.getByTestId(`take-on-map-${key}`);
    const now = (await t.innerText()).trim();
    suggested[key] = now;
    if (now !== header) await pick(t, header);
  }
  await ensureIsoDates();
  const issues = await page.getByTestId('take-on-mapping-issues').innerText().catch(() => null);
  if (issues) L('[mapping issues]', norm(issues));
  L('[mapping] suggested', suggested, '→ forced', targets);
  await page.getByTestId('take-on-save-mapping').click();
  return suggested;
}

async function createAccount(key) {
  const a = ACCOUNTS[key];
  await page.goto(`${BASE}/org/${SLUG}/settings/fees?tab=trust-accounting`, { waitUntil: 'load' });
  const section = page.getByTestId('trust-accounts-section');
  await section.waitFor({ timeout: 60000 });
  if ((await section.innerText()).includes(a.name)) { L('[account exists]', a.name); return; }
  await page.getByTestId('add-trust-account-button').click();
  const dialog = page.getByTestId('create-trust-account-dialog');
  await dialog.waitFor();
  await dialog.getByLabel('Account Name', { exact: true }).fill(a.name);
  await dialog.getByLabel('Bank Name', { exact: true }).fill('Standard Bank');
  await dialog.getByLabel('Branch Code', { exact: true }).fill('051001');
  await dialog.getByLabel('Account Number', { exact: true }).fill(a.number);
  await pick(dialog.getByTestId('trust-account-type-select'), a.type);
  if (a.basis) {
    await dialog.getByTestId('trust-account-basis-select').click();
    await page.waitForTimeout(300);
    await page.getByRole('option').filter({ hasText: a.basis }).click();
  }
  await dialog.getByLabel('Opened Date', { exact: true }).fill('2026-01-01');
  const primary = dialog.getByRole('checkbox', { name: 'Set as primary trust account' });
  if (await primary.isChecked()) await primary.click();
  await shot(`o-01-create-account-${key}`);
  await dialog.getByRole('button', { name: 'Create Account' }).click();
  await dialog.waitFor({ state: 'hidden', timeout: 20000 });
  await page.reload();
  await section.waitFor({ timeout: 60000 });
  await page.getByTestId('trust-accounts-section').getByText(a.name).first().waitFor({ timeout: 15000 });
  L('[account created]', a.name);
}

async function stepAccounts() {
  await login('user_e2e_alice');
  for (const k of ['G', 'I3', 'I4']) { currentLabel = `account-${k}`; await createAccount(k); }
  const r = await page.request.get(`${API}/api/trust-accounts`, { headers: authHeaders() });
  const list = await r.json();
  state.accounts = list.map(a => ({ id: a.id, accountName: a.accountName, accountType: a.accountType, investmentBasis: a.investmentBasis, isPrimary: a.isPrimary, status: a.status, openedDate: a.openedDate, accountNumber: a.accountNumber }));
  L('[trust accounts]', state.accounts);
  await shot('o-02-trust-accounts');
  saveState();
}

async function uploadFirst(datasetKebab, batchName, file, trust) {
  await openTakeOnTab();
  await page.getByTestId(`take-on-start-${datasetKebab}`).click();
  await page.getByTestId('take-on-new-upload').waitFor();
  if (trust) {
    await pick(page.getByTestId('take-on-trust-account'), trust.account);
    await pick(page.getByTestId('take-on-section'), trust.section);
    await page.getByTestId('take-on-cutover-date').fill(trust.cutover);
  }
  await page.getByTestId('take-on-batch-name').fill(batchName);
  await page.getByTestId('take-on-file-input').setInputFiles(file);
  if (trust) await shot('o-10-trust-upload-form');
  await page.getByTestId('take-on-upload-submit').click();
  await page.waitForFunction(() => location.search.includes('batch=') || document.querySelector('[data-testid="take-on-upload-warnings"]') || document.querySelector('[data-testid="take-on-upload-server-error"]'), null, { timeout: 60000 });
  if (await page.getByTestId('take-on-upload-server-error').isVisible().catch(() => false)) {
    throw new Error('upload failed: ' + (await page.getByTestId('take-on-upload-server-error').innerText()));
  }
  if (await page.getByTestId('take-on-upload-warnings').isVisible().catch(() => false)) {
    L('[upload warnings]', norm(await page.getByTestId('take-on-upload-warnings').innerText()));
    await page.getByTestId('take-on-continue').click();
    await page.waitForURL(/batch=/, { timeout: 60000 });
  }
  const batch = new URL(page.url()).searchParams.get('batch');
  L('[batch]', datasetKebab, batch);
  return batch;
}

async function readCards() {
  const card = async id => Number((await page.getByTestId(`take-on-card-${id}`).innerText()).replace(/[^0-9]/g, '') || 'NaN');
  return { total: await card('total'), valid: await card('valid'), error: await card('error'), duplicate: await card('duplicate'), blocks: await card('blocks') };
}

async function stepClients() {
  await login('user_e2e_alice');
  currentLabel = 'clients';
  state.clientsBatch = await uploadFirst('clients', 'OBS 657B clients A, B, C', `${F}/clients-657b.csv`); saveState();
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  await ensureIsoDates();
  await page.getByTestId('take-on-save-mapping').click();
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  if (await page.getByTestId('take-on-validate').isVisible().catch(() => false)) await page.getByTestId('take-on-validate').click();
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  const cards = await readCards();
  L('[clients cards]', cards);
  await page.getByTestId('take-on-to-attestation').click();
  await page.getByTestId('take-on-attestation').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-attestation-accept').click();
  await page.getByTestId('take-on-attest').click();
  await page.getByTestId('take-on-attestation-live').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-commit').click();
  await page.getByTestId('take-on-commit-dialog').waitFor();
  await page.getByTestId('take-on-commit-confirm').click();
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 180000 });
  const results = { created: norm(await page.getByTestId('take-on-result-created').innerText()), failed: norm(await page.getByTestId('take-on-result-failed').innerText()) };
  L('[clients results]', results);
  await shot('o-03-clients-results');
  state.clients = { cards, results }; saveState();
}

async function stepMatters() {
  await login('user_e2e_alice');
  currentLabel = 'matters';
  state.mattersBatch = await uploadFirst('matters', 'OBS 657B matters A1, B1, C1', `${F}/matters-657b.csv`); saveState();
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  await ensureIsoDates();
  await page.getByTestId('take-on-save-mapping').click();
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  if (await page.getByTestId('take-on-validate').isVisible().catch(() => false)) await page.getByTestId('take-on-validate').click();
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  const cards = await readCards();
  L('[matters cards]', cards);
  await page.getByTestId('take-on-commit').click();
  await page.getByTestId('take-on-commit-dialog').waitFor();
  await page.getByTestId('take-on-commit-confirm').click();
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 180000 });
  const results = { created: norm(await page.getByTestId('take-on-result-created').innerText()), failed: norm(await page.getByTestId('take-on-result-failed').innerText()) };
  L('[matters results]', results);
  await shot('o-04-matters-results');
  state.matters = { cards, results }; saveState();
}

// ---------------------------------------------------------------------------------------------
// Trust position
const SECTION_LABEL = {
  'client-ledger': 'Client and matter balances',
  'unidentified-deposits': 'Unidentified deposits',
  investments: 'Investments',
  'outstanding-items': 'Items outstanding at cutover',
};
const TARGETS = {
  'client-ledger': { clientRef: 'Client reference', matterRef: 'Matter reference', balance: 'Balance at cutover', sourceRef: 'Previous system ID' },
  'unidentified-deposits': { receivedDate: 'Date received', amount: 'Amount', bankReference: 'Bank reference', description: 'Description' },
  investments: {
    investmentAccount: 'Investment account (number or name)', basis: 'Basis', institution: 'Institution', accountNumber: 'Account number',
    principal: 'Principal at cutover', interestRate: 'Interest rate', depositDate: 'Original deposit date', clientRef: 'Client reference',
    matterRef: 'Matter reference', instructionReference: 'Instruction reference', instructionDate: 'Instruction date',
    endorsementConfirmed: 'Endorsed under section 86(4)', accruedInterest: 'Accrued interest not capitalised',
    fundAmount: "Legal Practitioners' Fidelity Fund amount held", balancePerConfirmation: 'Balance per confirmation',
  },
  'outstanding-items': { kind: 'Item type', originalDate: 'Original date', amount: 'Amount', reference: 'Reference', clientRef: 'Client reference', matterRef: 'Matter reference' },
};
const FILES = {
  'unidentified-deposits': `${F}/trust-unidentified-deposits.csv`,
  investments: `${F}/trust-investments.csv`,
  'outstanding-items': `${F}/trust-outstanding-items.csv`,
};
const EQ_ROWS = ['eq1', 'items', 'eq2', 'investments', 'deficit'];

async function addSource(kebab, file) {
  await pick(page.getByTestId('take-on-add-source-section'), SECTION_LABEL[kebab]);
  await page.getByTestId('take-on-add-source-file').setInputFiles(file);
  await page.getByTestId('take-on-add-source').click();
  await page.locator(`[data-testid="take-on-trust-section-${kebab}"][data-uploaded="true"]`).waitFor({ timeout: 60000 });
  L('[source added]', kebab);
}

/** From the files panel: Next, then map every unmapped file in turn until the Validation step shows. */
async function mapAllSources() {
  await page.getByTestId('take-on-files-next').click();
  for (let i = 0; i < 6; i++) {
    await page.getByTestId('take-on-mapping').or(page.getByTestId('take-on-validation')).first().waitFor({ timeout: 60000 });
    if (await page.getByTestId('take-on-validation').isVisible().catch(() => false)) return;
    await page.waitForTimeout(800);
    const pressed = page.locator('[data-testid^="take-on-source-tab-"][aria-pressed="true"]');
    let kebab;
    if (await pressed.count()) kebab = (await pressed.getAttribute('data-testid')).replace('take-on-source-tab-', '');
    else kebab = 'client-ledger'; // one file only: no tabs
    L('[mapping source]', kebab);
    const targets = { ...TARGETS[kebab] };
    for (const k of Object.keys(targets)) if (!(await page.getByTestId(`take-on-map-${k}`).count())) { L('[no field on screen]', k); delete targets[k]; }
    state.mapping = state.mapping || {};
    state.mapping[kebab] = await mapAndSave(targets);
    await page.waitForTimeout(2500);
  }
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
}

async function fillPosition(p) {
  const set = async (id, v) => { const el = page.getByTestId(id); await el.fill(''); await el.fill(v); };
  if (p.cutover) await set('take-on-position-cutover-date', p.cutover);
  if (p.bank !== undefined) await set('take-on-position-bank-balance', p.bank);
  if (p.od !== undefined) await set('take-on-position-outstanding-deposits', p.od);
  if (p.op !== undefined) await set('take-on-position-outstanding-payments', p.op);
  if (p.cashbook !== undefined) await set('take-on-position-cashbook', p.cashbook);
  if (p.fund !== undefined) await set('take-on-position-fund-ledger', p.fund);
  if (p.invested !== undefined) await set('take-on-position-invested-total', p.invested);
  if (p.midMonth !== undefined) {
    const cb = page.getByTestId('take-on-position-mid-month');
    const checked = (await cb.getAttribute('data-state')) === 'checked';
    if (checked !== p.midMonth) await cb.click();
  }
}

async function savePositionAndWait() {
  await page.getByTestId('take-on-position-save').click();
  await page.waitForTimeout(500);
  await page.waitForFunction(() => {
    const b = document.querySelector('[data-testid="take-on-position-save"]');
    return b && !b.disabled && b.textContent.includes('Save position');
  }, null, { timeout: 120000 });
  await page.waitForTimeout(2500); // rows SWR refetch after the new batch
}

async function readTrust() {
  const vis = async id => page.getByTestId(id).isVisible().catch(() => false);
  const txt = async id => ((await vis(id)) ? await page.getByTestId(id).innerText() : null);
  const r = {};
  r.status = norm(await page.getByTestId('take-on-batch-status').innerText());
  r.cutoverShown = norm(await txt('take-on-batch-cutover'));
  r.cards = (await vis('take-on-cards')) ? await readCards() : null;
  r.positionError = await txt('take-on-position-error');
  r.accountErrors = await txt('take-on-account-errors');
  r.accountErrorItems = await page.locator('[data-testid="take-on-account-errors"] li').allInnerTexts();
  r.banner = await txt('take-on-trust-block-banner');
  r.blockItems = await page.locator('[data-testid^="take-on-trust-block-"][data-testid$="0"], [data-testid^="take-on-trust-block-"]').evaluateAll(es => es.filter(e => /^take-on-trust-block-\d+$/.test(e.getAttribute('data-testid'))).map(e => e.innerText));
  r.rowBlocks = await txt('take-on-trust-row-blocks');
  r.bannerControls = (await vis('take-on-trust-block-banner'))
    ? await page.getByTestId('take-on-trust-block-banner').locator('button, input, select, textarea, a, [role="button"], [role="checkbox"], [role="switch"]').count()
    : null;
  r.equationShown = await vis('take-on-equation');
  r.equation = {};
  for (const id of EQ_ROWS) {
    if (!(await vis(`take-on-equation-${id}`))) { r.equation[id] = null; continue; }
    const row = page.getByTestId(`take-on-equation-${id}`);
    r.equation[id] = { holds: await row.getAttribute('data-holds'), difference: norm(await page.getByTestId(`take-on-equation-${id}-difference`).innerText()), text: norm(await row.innerText()) };
  }
  r.flags = norm(await txt('take-on-trust-flags'));
  r.rows = await page.locator('table[data-testid="take-on-rows"] tbody tr').evaluateAll(trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim()).join(' | ')));
  r.rowMessagesRaw = await page.locator('table[data-testid="take-on-rows"] tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText));
  r.buttons = await page.getByTestId('take-on-wizard').locator('button:visible').evaluateAll(bs => bs.map(b => b.innerText.replace(/\s+/g, ' ').trim()).filter(Boolean));
  r.overrideLikeButtons = r.buttons.filter(b => OVERRIDE_RE.test(b));
  r.commitControls = await page.getByTestId('take-on-commit').count();
  r.checkboxesOutsideForm = await page.getByTestId('take-on-validation').locator('[role="checkbox"]').evaluateAll(cs => cs.map(c => c.getAttribute('data-testid')));
  r.api = await getBatch(state.trustBatch);
  return r;
}

function compareBlocks(r, expected) {
  const out = {};
  for (const [k, v] of Object.entries(expected)) {
    out[k] = { exactInBanner: r.blockItems.includes(v), exactInRows: r.rowMessagesRaw.some(t => t.includes(v)), exactInApiBlocks: (r.api.summary?.blocks || []).some(b => b.message === v) };
  }
  return out;
}

function logTrust(tag, r) {
  L(`[${tag}] status`, r.status, '| cutover', r.cutoverShown, '| cards', r.cards);
  L(`[${tag}] api`, { http: r.api.http, status: r.api.status, allowedActions: r.api.allowedActions, counts: r.api.counts, validatedAt: r.api.validatedAt, position: r.api.position, midMonthAcknowledged: r.api.midMonthAcknowledged });
  L(`[${tag}] api summary.blocks`, r.api.summary?.blocks, '| accountErrors', r.api.summary?.accountErrors, '| flags', r.api.summary?.flags, '| warnings', r.api.summary?.warnings);
  L(`[${tag}] api summary.equation`, r.api.summary?.equation);
  L(`[${tag}] account errors (UI)`, r.accountErrorItems);
  L(`[${tag}] banner`, norm(r.banner), '| block items', r.blockItems, '| row-blocks', norm(r.rowBlocks), '| controls inside banner', r.bannerControls);
  L(`[${tag}] equation shown`, r.equationShown);
  for (const id of EQ_ROWS) L(`[${tag}]   ${id}`, r.equation[id]);
  L(`[${tag}] flags`, r.flags);
  r.rows.forEach(x => L(`[${tag}]   row`, x));
  L(`[${tag}] wizard buttons`, r.buttons, '| override-like', r.overrideLikeButtons, '| take-on-commit count', r.commitControls, '| checkboxes', r.checkboxesOutsideForm);
}

async function stepDebit() {
  await login('user_e2e_alice');
  currentLabel = 'trust-upload';
  state.trustBatch = await uploadFirst('trust-position', 'OBS 657B trust G at 31 Oct 2026', `${F}/trust-client-ledger-debit.csv`,
    { account: ACCOUNTS.G.name, section: SECTION_LABEL['client-ledger'], cutover: CUTOVER });
  saveState();
  await openBatch(state.trustBatch);
  await page.getByTestId('take-on-step-upload').click();
  await page.getByTestId('take-on-files').waitFor({ timeout: 60000 });
  for (const [k, f] of Object.entries(FILES)) await addSource(k, f);
  const sections = {};
  for (const k of Object.keys(SECTION_LABEL)) sections[k] = await page.getByTestId(`take-on-trust-section-${k}`).getAttribute('data-uploaded');
  L('[trust sections data-uploaded]', sections);
  await shot('o-11-trust-files-panel');
  currentLabel = 'trust-mapping';
  await mapAllSources();
  currentLabel = 'trust-validation-debit';
  await page.getByTestId('take-on-position-form').waitFor({ timeout: 60000 });
  L('[position form cutover prefill]', await page.getByTestId('take-on-position-cutover-date').inputValue());
  await fillPosition({ cutover: CUTOVER, ...POSITION, midMonth: false });
  await shot('o-12-debit-position-entered');
  await savePositionAndWait();
  const r = await readTrust();
  logTrust('debit', r);
  r.x3 = compareBlocks(r, { debitC: X3.debitC, eq2Debit: X3.eq2Debit });
  L('[debit X3 compare]', r.x3);
  await shot('o-13-debit-validation');
  if (r.banner !== null) await page.getByTestId('take-on-trust-block-banner').screenshot({ path: `${D}/o-14-debit-block-banner.png` });
  await page.locator('table[data-testid="take-on-rows"]').screenshot({ path: `${D}/o-15-debit-rows.png` }).catch(() => {});
  state.debit = r; saveState();
}

async function stepR10() {
  await login('user_e2e_alice');
  currentLabel = 'trust-replace-client-file';
  await openBatch(state.trustBatch);
  await page.getByTestId('take-on-step-upload').click();
  await page.getByTestId('take-on-files').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-remove-source-client-ledger').click();
  await page.locator('[data-testid="take-on-trust-section-client-ledger"][data-uploaded="false"]').waitFor({ timeout: 60000 });
  await addSource('client-ledger', `${F}/trust-client-ledger.csv`);
  await mapAllSources();
  currentLabel = 'trust-validation-r10';
  await page.getByTestId('take-on-position-form').waitFor({ timeout: 60000 });
  const before = await getBatch(state.trustBatch);
  L('[r10 before save] api status', before.status, 'position', before.position);
  await fillPosition({ cashbook: '403510.00' });
  await savePositionAndWait();
  const r = await readTrust();
  logTrust('r10', r);
  r.x3 = compareBlocks(r, { eq1R10: X3.eq1R10, eq2R10: X3.eq2R10 });
  L('[r10 X3 compare]', r.x3);
  await shot('o-20-r10-validation');
  if (r.banner !== null) await page.getByTestId('take-on-trust-block-banner').screenshot({ path: `${D}/o-21-r10-block-banner.png` });
  if (r.equationShown) await page.getByTestId('take-on-equation').screenshot({ path: `${D}/o-22-r10-indicator.png` });
  state.r10 = r; saveState();
}

async function stepFix() {
  await login('user_e2e_alice');
  currentLabel = 'trust-fix';
  await openBatch(state.trustBatch);
  await page.getByTestId('take-on-position-form').waitFor({ timeout: 60000 });
  const before = await getBatch(state.trustBatch);
  L('[fix before] api status', before.status, 'validatedAt', before.validatedAt);
  // Hold the dry-run server action (body ["<batchId>"]) for 6 s so the batch's Mapped state can be read in between.
  let hold = true;
  const actions = [];
  await page.route(`${BASE}/org/**`, async route => {
    const req = route.request();
    if (req.method() === 'POST' && req.headers()['next-action']) {
      const body = req.postData() || '';
      actions.push({ at: new Date().toISOString(), body: body.slice(0, 200) });
      if (hold && body === JSON.stringify([state.trustBatch])) {
        hold = false;
        L('[holding the validate server action 6 s]', new Date().toISOString());
        await new Promise(res => setTimeout(res, 6000));
      }
    }
    await route.continue();
  });
  const actionResponses = [];
  page.on('response', async resp => {
    const req = resp.request();
    if (req.method() === 'POST' && req.headers()['next-action']) {
      const t = await resp.text().catch(() => '');
      const m = t.match(/"status":"(UPLOADED|MAPPED|VALIDATED|COMMITTING|COMPLETED|CANCELLED)"/);
      actionResponses.push({ at: new Date().toISOString(), batchStatusInResponse: m ? m[1] : null, bytes: t.length });
    }
  });
  await fillPosition({ cashbook: POSITION.cashbook });
  await page.getByTestId('take-on-position-save').click();
  let mapped = null;
  for (let i = 0; i < 20; i++) {
    const b = await getBatch(state.trustBatch);
    if (b.status === 'MAPPED') { mapped = { at: new Date().toISOString(), status: b.status, cashbook: b.position?.cashbookBalance, validatedAt: b.validatedAt, summary: b.summary, allowedActions: b.allowedActions }; break; }
    await page.waitForTimeout(300);
  }
  L('[fix batch GET while the dry run is held]', mapped);
  await shot('o-30-fix-saving-batch-mapped');
  await page.waitForFunction(() => {
    const b = document.querySelector('[data-testid="take-on-position-save"]');
    return b && !b.disabled && b.textContent.includes('Save position');
  }, null, { timeout: 120000 });
  await page.waitForTimeout(2500);
  const r = await readTrust();
  logTrust('fix', r);
  L('[fix server actions]', actions);
  L('[fix server action responses]', actionResponses);
  r.mappedDuringSave = mapped; r.beforeValidatedAt = before.validatedAt; r.actions = actions; r.actionResponses = actionResponses;
  L('[fix validatedAt before → after]', before.validatedAt, '→', r.api.validatedAt);
  await shot('o-31-fix-validated');
  if (r.equationShown) await page.getByTestId('take-on-equation').screenshot({ path: `${D}/o-32-fix-indicator.png` });
  state.fix = r; saveState();
}

async function stepAcctCheck() {
  await login('user_e2e_alice');
  currentLabel = 'trust-account-check';
  await openBatch(state.trustBatch);
  await page.getByTestId('take-on-position-form').waitFor({ timeout: 60000 });
  await fillPosition({ cutover: '2026-10-30', midMonth: false });
  await savePositionAndWait();
  const r = await readTrust();
  logTrust('acctcheck', r);
  r.monthEndExact = r.accountErrorItems.includes(X3.monthEnd);
  L('[acctcheck month-end text exact]', r.monthEndExact);
  await shot('o-40-account-check-not-month-end');
  currentLabel = 'trust-account-check-restore';
  await fillPosition({ cutover: CUTOVER, midMonth: false });
  await savePositionAndWait();
  const back = await readTrust();
  logTrust('restored', back);
  await shot('o-41-restored-31-oct-validated');
  state.acctcheck = r; state.restored = back; saveState();
}

(async () => {
  const startedAt = new Date().toISOString();
  L(`[start ${STEP}]`, startedAt);
  const browser = await chromium.launch();
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  page.on('response', r => { if (r.status() >= 400) L(`[http ${r.status()} @${currentLabel}]`, r.request().method(), r.url()); });
  const steps = { accounts: stepAccounts, clients: stepClients, matters: stepMatters, debit: stepDebit, r10: stepR10, fix: stepFix, acctcheck: stepAcctCheck };
  try {
    if (!steps[STEP]) throw new Error(`unknown STEP ${STEP}`);
    await steps[STEP]();
  } catch (e) {
    L('[script error]', e.stack);
    await shot(`zz-failure-${STEP}`).catch(() => {});
    process.exitCode = 1;
  }
  state.consoleErrors = state.consoleErrors || {};
  state.consoleErrors[STEP] = consoleErrors;
  saveState();
  L(`[end ${STEP}]`, new Date().toISOString(), 'console/page errors:', consoleErrors.length);
  fs.appendFileSync(`${D}/obs-log.txt`, log.join('\n') + '\n');
  await browser.close();
})();
