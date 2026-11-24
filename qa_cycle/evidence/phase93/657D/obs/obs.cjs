// 657D OBS — take-on report files, the first post-cutover month, reports over the cutover
// (e2e mock stack :3001, stack 3, head 4a8bdbbe8).
// Usage: STEP=<step> [T=G|H|P] node obs.cjs   (state in state.json, log appended to obs-log.txt)
//   accounts   Bob creates G (General, not primary), H (General, not primary), I3, I4 in the UI
//   clients    Bob: clients take-on A, B, C → attest → commit → portal access
//   matters    Bob: matters take-on A1, B1, C1 → commit
//   trust      T=G|H|P  Bob: trust batch → files → mapping → position → Validated
//   signoff    T=…  Alice: Home widget → sheet → accept → Sign off
//   commit     T=…  Bob: Sign-off step → Commit → Results (+ "Preparing…" watch, downloads on T=G)
//   reportcard Alice: Trust Reports → "Take-on reports" card → download G's PDF and CSV
//   reconrefuse  Bob: wizard on G, a statement wholly on/before C → refused (422 text)
//   recong     Bob: wizard on G, the October statement → auto-match → record interest + fee →
//              calculate → Complete → Rule 54.15 list locked
//   reconh     Bob: wizard on H, a statement spanning C → import warning, EXCLUDED line,
//              OPENING_DIFFERS_FROM_TAKE_ON flag
//   rp         Alice: Receipts & Payments on G over Sep–Oct (contains C) and over October alone
//   trend      Alice: Trust dashboard (binds to the primary P): trend + "Taken on at …"
// No SQL writes. REST only for read-only GETs with the session's own token, and the Mailpit API.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-657D/frontend/node_modules/@playwright/test');
const fs = require('fs');
const crypto = require('crypto');
const D = __dirname;
const F = `${D}/files`;
const DL = `${D}/downloads`;
const BASE = 'http://localhost:3001';
const API = 'http://localhost:8081';
const MAILPIT = 'http://localhost:8026';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
const T = process.env.T || 'G';
const STATE_FILE = `${D}/state.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2) + '\n');
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

const PRIMARY_NAME = 'Mathebula & Partners Trust Account';
const ACCOUNTS = {
  G: { name: 'OBS 657D Trust banking G', number: '6570001', type: 'General' },
  H: { name: 'OBS 657D Trust banking H', number: '6575001', type: 'General' },
  I3: { name: 'OBS 657D s86(3) investment I3', number: '6573001', type: 'Investment', basis: 'Section 86(3) — the firm invests surplus trust money' },
  I4: { name: 'OBS 657D s86(4) investment I4', number: '6574001', type: 'Investment', basis: "Section 86(4) — invested on a client's instruction" },
};
// The three take-ons. G: the §17.3.13 set at 30 September 2026 (C = the month end two months
// back, so the first post-cutover month, October, is wholly in the past). H: one client balance
// at 30 September (the spanning-statement / opening-differs checks). P: the seeded primary at its
// opened date 24 November 2026 (the only date it allows; the trend binds to the primary).
const TRUSTS = {
  G: {
    account: ACCOUNTS.G.name, cutover: '2026-09-30', long: '30 September 2026', midMonth: false,
    ledger: `${F}/trust-client-ledger.csv`,
    extra: { 'unidentified-deposits': `${F}/trust-unidentified-deposits.csv`, investments: `${F}/trust-investments.csv`, 'outstanding-items': `${F}/trust-outstanding-items.csv` },
    position: { bank: '418500.00', od: '5000.00', op: '20000.00', cashbook: '403500.00', fund: '1250.00', invested: '300000.00' },
  },
  H: {
    account: ACCOUNTS.H.name, cutover: '2026-09-30', long: '30 September 2026', midMonth: false,
    ledger: `${F}/trust-client-ledger-H.csv`, extra: {},
    position: { bank: '50000.00', od: '0.00', op: '0.00', cashbook: '50000.00', fund: '0.00', invested: '0.00' },
  },
  P: {
    account: PRIMARY_NAME, cutover: '2026-11-24', long: '24 November 2026', midMonth: true,
    ledger: `${F}/trust-client-ledger-P.csv`, extra: {},
    position: { bank: '10000.00', od: '0.00', op: '0.00', cashbook: '10000.00', fund: '0.00', invested: '0.00' },
  },
};
const TR = TRUSTS[T];

let page, context, browser, token, currentLabel = 'init';
const consoleErrors = [];
const shot = async n => { await page.screenshot({ path: `${D}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };
const norm = s => (s || '').replace(/[\s  ]+/g, ' ').trim();
const authHeaders = () => ({ Authorization: `Bearer ${token}` });
const vis = async (id, root = page) => root.getByTestId(id).first().isVisible().catch(() => false);
const txt = async (id, root = page) => ((await vis(id, root)) ? norm(await root.getByTestId(id).first().innerText()) : null);
const sha256 = p => crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');

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

async function apiGet(path) {
  const r = await page.request.get(`${API}${path}`, { headers: authHeaders() });
  let body = null;
  try { body = await r.json(); } catch { body = await r.text().catch(() => null); }
  return { http: r.status(), body };
}

async function getBatch(id) {
  const { http, body: j } = await apiGet(`/api/take-on/batches/${id}`);
  return { http, status: j?.status, allowedActions: j?.allowedActions, counts: j?.counts, cutoverDate: j?.cutoverDate, reportId: j?.reportId, trustAccountName: j?.trustAccountName, lastError: j?.lastError };
}

async function mailpitTotal() {
  const r = await page.request.get(`${MAILPIT}/api/v1/messages?limit=1`);
  const j = await r.json();
  return { at: new Date().toISOString(), total: j.total, latest: j.messages?.[0] ? { id: j.messages[0].ID, subject: j.messages[0].Subject, created: j.messages[0].Created } : null };
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
  L('[mapping] suggested', suggested);
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
  await shot(`01-create-account-${key}`);
  await dialog.getByRole('button', { name: 'Create Account' }).click();
  await dialog.waitFor({ state: 'hidden', timeout: 20000 });
  await page.reload();
  await section.waitFor({ timeout: 60000 });
  await page.getByTestId('trust-accounts-section').getByText(a.name).first().waitFor({ timeout: 15000 });
  L('[account created]', a.name);
}

async function stepAccounts() {
  await login('user_e2e_bob');
  for (const k of ['G', 'H', 'I3', 'I4']) { currentLabel = `account-${k}`; await createAccount(k); }
  const { body: list } = await apiGet('/api/trust-accounts');
  state.accounts = list.map(a => ({ id: a.id, accountName: a.accountName, accountType: a.accountType, isPrimary: a.isPrimary, status: a.status, openedDate: a.openedDate, accountNumber: a.accountNumber }));
  L('[trust accounts]', state.accounts);
  await shot('02-trust-accounts');
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
  await login('user_e2e_bob');
  currentLabel = 'clients';
  state.clientsBatch = await uploadFirst('clients', 'OBS 657D clients A, B, C', `${F}/clients-657b.csv`); saveState();
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
  await shot('03-clients-results');
  state.clients = { cards, results }; saveState();
}

async function stepMatters() {
  await login('user_e2e_bob');
  currentLabel = 'matters';
  state.mattersBatch = await uploadFirst('matters', 'OBS 657D matters A1, B1, C1', `${F}/matters-657b.csv`); saveState();
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
  await shot('04-matters-results');
  state.matters = { cards, results }; saveState();
}

// ---------------------------------------------------------------------------------------------
// Trust position (one take-on per T)
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
const EQ_ROWS = ['eq1', 'items', 'eq2', 'investments', 'deficit'];

async function addSource(kebab, file) {
  await pick(page.getByTestId('take-on-add-source-section'), SECTION_LABEL[kebab]);
  await page.getByTestId('take-on-add-source-file').setInputFiles(file);
  await page.getByTestId('take-on-add-source').click();
  await page.locator(`[data-testid="take-on-trust-section-${kebab}"][data-uploaded="true"]`).waitFor({ timeout: 60000 });
  L('[source added]', kebab);
}

async function mapAllSources() {
  await page.getByTestId('take-on-files-next').click();
  for (let i = 0; i < 6; i++) {
    await page.getByTestId('take-on-mapping').or(page.getByTestId('take-on-validation')).first().waitFor({ timeout: 60000 });
    if (await page.getByTestId('take-on-validation').isVisible().catch(() => false)) return;
    await page.waitForTimeout(800);
    const pressed = page.locator('[data-testid^="take-on-source-tab-"][aria-pressed="true"]');
    let kebab;
    if (await pressed.count()) kebab = (await pressed.getAttribute('data-testid')).replace('take-on-source-tab-', '');
    else kebab = 'client-ledger';
    L('[mapping source]', kebab);
    const targets = { ...TARGETS[kebab] };
    for (const k of Object.keys(targets)) if (!(await page.getByTestId(`take-on-map-${k}`).count())) delete targets[k];
    await mapAndSave(targets);
    await page.waitForTimeout(2500);
  }
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
}

async function fillPosition(p) {
  const set = async (id, v) => { const el = page.getByTestId(id); await el.fill(''); await el.fill(v); };
  if (p.cutover) await set('take-on-position-cutover-date', p.cutover);
  await set('take-on-position-bank-balance', p.bank);
  await set('take-on-position-outstanding-deposits', p.od);
  await set('take-on-position-outstanding-payments', p.op);
  await set('take-on-position-cashbook', p.cashbook);
  await set('take-on-position-fund-ledger', p.fund);
  await set('take-on-position-invested-total', p.invested);
  if (p.midMonth !== undefined && (await page.getByTestId('take-on-position-mid-month').count())) {
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
  await page.waitForTimeout(2500);
}

async function readEquation(root = page) {
  const eq = {};
  for (const id of EQ_ROWS) {
    if (!(await vis(`take-on-equation-${id}`, root))) { eq[id] = null; continue; }
    const row = root.getByTestId(`take-on-equation-${id}`);
    eq[id] = { holds: await row.getAttribute('data-holds'), difference: await txt(`take-on-equation-${id}-difference`, root) };
  }
  return eq;
}

const tstate = () => { state.trust = state.trust || {}; state.trust[T] = state.trust[T] || {}; return state.trust[T]; };

async function stepTrust() {
  await login('user_e2e_bob');
  currentLabel = `trust-${T}`;
  const s = tstate();
  s.batch = await uploadFirst('trust-position', `OBS 657D trust ${T} at ${TR.cutover}`, TR.ledger,
    { account: TR.account, section: SECTION_LABEL['client-ledger'], cutover: TR.cutover });
  saveState();
  await openBatch(s.batch);
  await page.getByTestId('take-on-step-upload').click();
  await page.getByTestId('take-on-files').waitFor({ timeout: 60000 });
  for (const [k, f] of Object.entries(TR.extra)) await addSource(k, f);
  await shot(`${T.toLowerCase()}-11-trust-files`);
  await mapAllSources();
  await page.getByTestId('take-on-position-form').waitFor({ timeout: 60000 });
  await fillPosition({ cutover: TR.cutover, ...TR.position, midMonth: TR.midMonth });
  await savePositionAndWait();
  const v = {};
  v.status = await txt('take-on-batch-status');
  v.cards = (await vis('take-on-cards')) ? await readCards() : null;
  v.banner = await txt('take-on-trust-block-banner');
  v.accountErrors = await txt('take-on-account-errors');
  v.equation = await readEquation();
  v.flags = await txt('take-on-trust-flags');
  v.api = await getBatch(s.batch);
  L(`[trust ${T} validated]`, v);
  await shot(`${T.toLowerCase()}-13-validated`);
  s.validated = v; saveState();
}

async function stepSignOff() {
  await login('user_e2e_alice');
  currentLabel = `signoff-${T}`;
  const s = tstate();
  const widget = page.getByTestId('take-on-sign-off-widget').first();
  await widget.waitFor({ timeout: 60000 });
  await page.waitForTimeout(3000);
  const item = page.getByTestId(`take-on-sign-off-item-${s.batch}`).first();
  await item.waitFor({ timeout: 30000 });
  const r = { itemText: norm(await item.innerText()) };
  r.widgetCount = await page.getByTestId('take-on-sign-off-widget').count();
  r.widgetVisible = await page.getByTestId('take-on-sign-off-widget').evaluateAll(ws => ws.map(w => !!(w.offsetWidth || w.offsetHeight)));
  await shot(`${T.toLowerCase()}-20-alice-home`);
  await item.click();
  const sheet = page.getByTestId('takeon-signoff-sheet');
  await sheet.waitFor({ timeout: 30000 });
  await sheet.getByTestId('takeon-signoff-panel').waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  r.title = await txt('takeon-signoff-sheet-title', sheet);
  await sheet.getByTestId('takeon-signoff-accept').click();
  await sheet.getByTestId('takeon-signoff-submit').click();
  await sheet.getByTestId('takeon-signoff-live').waitFor({ timeout: 60000 });
  r.live = await txt('takeon-signoff-live', sheet);
  r.api = await getBatch(s.batch);
  L(`[alice signed ${T}]`, r);
  await shot(`${T.toLowerCase()}-22-alice-signed`);
  s.signoff = r; saveState();
}

// Downloads one report file through a button; returns { file, sha256, size, head }.
async function downloadVia(button, label) {
  fs.mkdirSync(DL, { recursive: true });
  const [dl] = await Promise.all([page.waitForEvent('download', { timeout: 60000 }), button.click()]);
  const name = dl.suggestedFilename();
  const target = `${DL}/${label}-${name}`;
  await dl.saveAs(target);
  const bytes = fs.readFileSync(target);
  const r = { suggestedFilename: name, file: target.replace(D + '/', ''), size: bytes.length, sha256: sha256(target), head: bytes.subarray(0, 8).toString('latin1') };
  L('[download]', label, r);
  return r;
}

async function stepCommit() {
  await login('user_e2e_bob');
  currentLabel = `commit-${T}`;
  const s = tstate();
  const r = {};
  r.mailBefore = await mailpitTotal();
  L('[mailpit before commit]', r.mailBefore);
  await openBatch(s.batch);
  await page.getByTestId('take-on-signoff-step').waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  await page.getByTestId('take-on-commit').click();
  const dialog = page.getByTestId('take-on-commit-dialog');
  await dialog.waitFor({ timeout: 15000 });
  r.trustSentence = await txt('take-on-commit-trust', dialog);
  r.commitClickedAt = new Date().toISOString();
  await page.getByTestId('take-on-commit-confirm').click();
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 240000 });
  r.resultsAt = new Date().toISOString();
  r.results = { created: await txt('take-on-result-created'), skipped: await txt('take-on-result-skipped'), failed: await txt('take-on-result-failed') };
  // Watch the downloads block: "Preparing the report files…" while the drain renders, then the buttons
  r.filesWatch = [];
  const t0 = Date.now();
  let preparingShot = false;
  while (Date.now() - t0 < 90000) {
    const preparing = await vis('take-on-report-files-preparing');
    const pdf = await vis('take-on-report-download-pdf');
    const csv = await vis('take-on-report-download-csv');
    const failed = await vis('take-on-report-files-failed');
    const last = r.filesWatch.at(-1);
    if (!last || last.preparing !== preparing || last.pdf !== pdf || last.csv !== csv || last.failed !== failed) {
      r.filesWatch.push({ atMs: Date.now() - t0, at: new Date().toISOString(), preparing, preparingText: preparing ? await txt('take-on-report-files-preparing') : null, pdf, csv, failed });
      if (preparing && !preparingShot) { await page.getByTestId('take-on-report-downloads').screenshot({ path: `${D}/${T.toLowerCase()}-32a-report-files-preparing.png` }); preparingShot = true; }
    }
    if ((pdf && csv) || failed) break;
    await page.waitForTimeout(250);
  }
  L('[report files watch]', r.filesWatch);
  await page.getByTestId('take-on-report-summary').waitFor({ timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(1000);
  r.api = await getBatch(s.batch);
  s.reportId = r.api.reportId;
  await shot(`${T.toLowerCase()}-32-results-report`);
  if (T === 'G' && (await vis('take-on-report-download-pdf'))) {
    currentLabel = 'download-results';
    r.downloads = {
      pdf: await downloadVia(page.getByTestId('take-on-report-download-pdf'), 'results-step'),
      csv: await downloadVia(page.getByTestId('take-on-report-download-csv'), 'results-step'),
    };
    r.downloadError = await txt('take-on-report-download-error');
    await page.getByTestId('take-on-report-downloads').screenshot({ path: `${D}/g-33-report-downloads.png` });
  }
  if (s.reportId) {
    const rep = await apiGet(`/api/take-on/reports/${s.reportId}`);
    r.reportApiFiles = rep.body?.files;
    L('[report API files]', r.reportApiFiles);
  }
  await page.waitForTimeout(8000);
  r.mailAfter = await mailpitTotal();
  L('[results]', r.results, '| api', r.api);
  L('[mailpit after commit]', r.mailAfter);
  s.commit = r; saveState();
}

async function stepReportCard() {
  await login('user_e2e_alice');
  currentLabel = 'reportcard';
  const r = {};
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reports`, { waitUntil: 'load' });
  const card = page.getByTestId('trust-take-on-reports');
  await card.waitFor({ timeout: 60000 });
  r.cardText = norm(await card.innerText());
  const reportId = state.trust.G.reportId;
  const item = page.getByTestId(`trust-take-on-report-${reportId}`);
  r.itemText = norm(await item.innerText());
  L('[take-on reports card]', r.cardText);
  await shot('50-trust-reports-take-on-card');
  r.downloads = {
    pdf: await downloadVia(item.getByTestId('take-on-report-download-pdf'), 'reports-card'),
    csv: await downloadVia(item.getByTestId('take-on-report-download-csv'), 'reports-card'),
  };
  r.downloadError = await txt('take-on-report-download-error', item);
  const list = await apiGet('/api/take-on/reports');
  r.listApi = { http: list.http, body: list.body };
  const rep = await apiGet(`/api/take-on/reports/${reportId}`);
  r.reportApiFiles = rep.body?.files;
  L('[report API files]', r.reportApiFiles);
  state.reportCard = r; saveState();
}

// ---------------------------------------------------------------------------------------------
// Reconciliation wizard
const acctLabel = k => `${ACCOUNTS[k].name} (Standard Bank - ${ACCOUNTS[k].number})`;

async function wizardUpload(key, periodEnd, file, tag) {
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting/reconciliation/new`, { waitUntil: 'load' });
  await page.getByTestId('account-select').waitFor({ timeout: 60000 });
  await page.waitForFunction(() => document.querySelectorAll('[data-testid="account-select"] option').length > 0, null, { timeout: 30000 });
  const options = await page.locator('[data-testid="account-select"] option').allInnerTexts();
  await page.getByTestId('account-select').selectOption({ label: acctLabel(key) });
  await page.getByTestId('period-end-input').fill(periodEnd);
  const r = { accountOptions: options, selected: acctLabel(key), periodEnd, file: file.replace(D + '/', '') };
  await shot(`${tag}-01-select`);
  await page.getByTestId('step-next-btn').click();
  await page.getByTestId('bank-statement-upload').waitFor({ timeout: 30000 });
  await page.getByTestId('file-input').setInputFiles(file);
  await page.getByTestId('upload-result').or(page.getByTestId('upload-error')).first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1000);
  r.uploadError = await txt('upload-error');
  r.uploadResult = await txt('upload-result');
  r.wizardError = await txt('reconciliation-error');
  r.warnings = await txt('statement-import-warnings');
  r.warningsCount = await page.getByTestId('statement-import-warnings').count();
  await shot(`${tag}-02-upload`);
  L(`[${tag} upload]`, r);
  return r;
}

async function readLines() {
  return page.locator('[data-testid^="bank-line-"]').evaluateAll(ds => ds.map(d => d.innerText.replace(/\s+/g, ' ').trim()));
}

async function readSummary() {
  const s = {};
  s.bankCashbook = await txt('comparison-bank-cashbook');
  s.cashbookCreditors = await txt('comparison-cashbook-creditors');
  s.creditors = await txt('creditor-breakdown');
  s.unresolved = await txt('unresolved-line-count');
  s.excludedTotal = await txt('excluded-lines-total');
  s.flags = await txt('reconciliation-integrity-flags');
  s.flagCodes = await page.locator('[data-testid^="reconciliation-flag-"]').evaluateAll(ls => ls.map(l => l.getAttribute('data-testid').replace('reconciliation-flag-', '')));
  s.balancedText = norm(await page.getByTestId('balance-summary').innerText()).match(/Reconciliation is (not )?balanced/)?.[0] ?? null;
  s.completeEnabled = await page.getByTestId('complete-reconciliation-btn').isEnabled().catch(() => null);
  return s;
}

async function autoMatchAndReview(tag) {
  await page.getByTestId('step-next-btn').click();
  await page.getByTestId('auto-match-btn').waitFor({ timeout: 30000 });
  await page.getByTestId('auto-match-btn').click();
  await page.getByTestId('auto-match-result').waitFor({ timeout: 60000 });
  const autoMatch = await txt('auto-match-result');
  await shot(`${tag}-03-auto-match`);
  await page.getByTestId('step-next-btn').click();
  await page.getByTestId('balance-summary').waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  return autoMatch;
}

async function recordLine(text, classification, charge) {
  const line = page.locator('[data-testid^="bank-line-"]').filter({ hasText: text }).first();
  await line.click();
  const btn = line.locator('[data-testid^="record-btn-"]');
  await btn.waitFor({ timeout: 15000 });
  await btn.click();
  const dialog = page.getByTestId('record-from-statement-dialog');
  await dialog.waitFor({ timeout: 15000 });
  const choices = await dialog.getByTestId('classification-radio').innerText();
  await dialog.locator(`#classification-${classification}`).click();
  if (charge) {
    await dialog.locator('#charge-recoverable-yes').click();
    await dialog.getByTestId('charge-vat-input').fill('0');
  }
  await shot(`g-r-record-${classification}`);
  await dialog.getByTestId('record-submit').click();
  await dialog.waitFor({ state: 'hidden', timeout: 30000 });
  await line.locator('[data-testid^="recorded-badge-"]').waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500);
  const r = { text, classification, choices: norm(choices), badge: norm(await line.locator('[data-testid^="recorded-badge-"]').innerText()) };
  L('[recorded]', r);
  return r;
}

async function stepReconRefuse() {
  await login('user_e2e_alice');
  currentLabel = 'recon-refuse-G';
  const r = await wizardUpload('G', '2026-09-30', `${F}/statement-G-september-wholly-before-C.csv`, 'g-r0-refused');
  const g = state.accounts.find(a => a.accountName === ACCOUNTS.G.name);
  const st = await apiGet(`/api/trust-accounts/${g.id}/bank-statements`);
  r.statementsAfterApi = { http: st.http, count: Array.isArray(st.body) ? st.body.length : st.body?.content?.length ?? null };
  L('[G statements after refused import]', r.statementsAfterApi);
  state.reconRefuse = r; saveState();
}

async function stepReconG() {
  await login('user_e2e_alice');
  currentLabel = 'recon-G';
  const r = {};
  r.mailBefore = await mailpitTotal();
  L('[mailpit before reconcile]', r.mailBefore);
  r.upload = await wizardUpload('G', '2026-10-31', `${F}/statement-G-october.csv`, 'g-r1');
  if (r.upload.uploadError) throw new Error('upload refused: ' + r.upload.uploadError);
  r.autoMatch = await autoMatchAndReview('g-r1');
  r.linesAfterAutoMatch = await readLines();
  r.summaryBeforeRecording = await readSummary();
  L('[G after auto-match]', r.autoMatch, r.linesAfterAutoMatch, r.summaryBeforeRecording);
  await shot('g-r1-04-review-after-auto-match');
  r.recordInterest = await recordLine('INTEREST', 'FUND_INTEREST');
  r.recordFee = await recordLine('SERVICE FEE', 'BANK_CHARGE', true);
  r.linesAfterRecording = await readLines();
  r.summary = await readSummary();
  L('[G review summary]', r.summary);
  L('[G lines]', r.linesAfterRecording);
  await shot('g-r1-05-review-balanced');
  await page.getByTestId('balance-summary').screenshot({ path: `${D}/g-r1-05b-balance-summary.png` });
  await page.getByTestId('complete-reconciliation-btn').click();
  await page.getByTestId('step-complete').or(page.getByTestId('reconciliation-error')).first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1000);
  r.completeError = await txt('reconciliation-error');
  r.complete = await txt('step-complete');
  r.listLocked = await txt('monthly-list-locked');
  r.listNotLocked = await txt('monthly-list-not-locked');
  L('[G complete]', { complete: r.complete, listLocked: r.listLocked, listNotLocked: r.listNotLocked, error: r.completeError });
  await shot('g-r1-06-complete');
  if (await vis('view-locked-list')) {
    await page.getByTestId('view-locked-list').click();
    await page.waitForLoadState('load');
    await page.waitForTimeout(3000);
    r.lockedListUrl = page.url();
    r.lockedListText = norm(await page.locator('main').innerText()).slice(0, 2500);
    L('[G locked list]', r.lockedListUrl, r.lockedListText);
    await shot('g-r1-07-locked-list');
  }
  await page.waitForTimeout(5000);
  r.mailAfter = await mailpitTotal();
  L('[mailpit after reconcile]', r.mailAfter);
  state.reconG = r; saveState();
}

async function stepReconH() {
  await login('user_e2e_alice');
  currentLabel = 'recon-H';
  const r = {};
  r.upload = await wizardUpload('H', '2026-10-31', `${F}/statement-H-spanning-C.csv`, 'h-r1');
  if (r.upload.uploadError) throw new Error('upload refused: ' + r.upload.uploadError);
  if (await vis('statement-import-warnings')) await page.getByTestId('statement-import-warnings').screenshot({ path: `${D}/h-r1-02b-import-warning.png` });
  r.autoMatch = await autoMatchAndReview('h-r1');
  r.lines = await readLines();
  r.summary = await readSummary();
  r.openingFlag = await txt('reconciliation-flag-OPENING_DIFFERS_FROM_TAKE_ON');
  L('[H review]', r);
  await shot('h-r1-04-review-flags');
  if (await vis('reconciliation-flag-OPENING_DIFFERS_FROM_TAKE_ON')) await page.getByTestId('reconciliation-integrity-flags').screenshot({ path: `${D}/h-r1-04b-opening-differs-flag.png` });
  state.reconH = r; saveState();
}

// ---------------------------------------------------------------------------------------------
// Receipts & Payments over the cutover
async function runRp(accountName, from, to, tag) {
  await page.goto(`${BASE}/org/${SLUG}/reports/trust-receipts-payments`, { waitUntil: 'load' });
  const combo = page.locator('#param-trust_account_id');
  await combo.waitFor({ timeout: 60000 });
  await combo.click();
  const opt = page.getByRole('option').filter({ hasText: accountName }).first();
  await opt.waitFor({ timeout: 30000 });
  await opt.click();
  await page.locator('#param-dateFrom').fill(from);
  await page.locator('#param-dateTo').fill(to);
  await page.getByRole('button', { name: 'Run Report' }).click();
  await page.locator('main table tbody tr').first().or(page.getByText('No data found for the selected parameters.')).first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  const r = { accountName, from, to };
  r.columns = await page.locator('main table thead th').allInnerTexts();
  r.rows = await page.locator('main table tbody tr').evaluateAll(trs => trs.map(tr => ({ bf: tr.getAttribute('data-testid') === 'report-row-brought-forward', cells: [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim()) })));
  r.bfCount = await page.getByTestId('report-row-brought-forward').count();
  r.bfText = r.bfCount ? norm(await page.getByTestId('report-row-brought-forward').first().innerText()) : null;
  r.footnote = await txt('report-footnote-brought-forward');
  r.summaryCards = await page.locator('main div.lg\\:grid-cols-4').last().innerText().then(norm).catch(() => null);
  L(`[R&P ${tag}]`, r);
  await shot(`rp-${tag}`);
  return r;
}

async function stepRp() {
  await login('user_e2e_alice');
  currentLabel = 'rp';
  state.rp = {
    sepOct: await runRp(ACCOUNTS.G.name, '2026-09-01', '2026-10-31', 'g-2026-09-01_2026-10-31'),
    oct: await runRp(ACCOUNTS.G.name, '2026-10-01', '2026-10-31', 'g-2026-10-01_2026-10-31'),
  };
  saveState();
}

// ---------------------------------------------------------------------------------------------
// Trust trend (the dashboard binds to the primary account)
async function stepTrend() {
  await login('user_e2e_alice');
  currentLabel = 'trend';
  const r = {};
  await page.goto(`${BASE}/org/${SLUG}/trust-accounting`, { waitUntil: 'load' });
  const chart = page.getByTestId('trust-balance-trend');
  await chart.first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(4000);
  r.takenOn = await txt('trust-balance-trend-taken-on');
  r.chartText = norm(await chart.first().innerText());
  r.xTicks = await page.locator('[data-testid="trust-balance-trend"] .recharts-xAxis .recharts-cartesian-axis-tick-value').allInnerTexts().catch(() => []);
  await shot('60-trust-dashboard-trend');
  await chart.first().screenshot({ path: `${D}/60b-trust-balance-trend.png` });
  const { body: list } = await apiGet('/api/trust-accounts');
  for (const name of [PRIMARY_NAME, ACCOUNTS.G.name, ACCOUNTS.H.name]) {
    const a = list.find(x => x.accountName === name);
    const t = await apiGet(`/api/dashboard/trust-balance-trend?accountId=${a.id}&months=12`);
    r[`api:${name}`] = t.body;
  }
  L('[trend]', r);
  state.trend = r; saveState();
}

(async () => {
  const startedAt = new Date().toISOString();
  L(`[start ${STEP} T=${T}]`, startedAt);
  browser = await chromium.launch();
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 }, acceptDownloads: true });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  page.on('response', r => { if (r.status() >= 400 || r.status() === 202) L(`[http ${r.status()} @${currentLabel}]`, r.request().method(), r.url()); });
  const steps = { accounts: stepAccounts, clients: stepClients, matters: stepMatters, trust: stepTrust, signoff: stepSignOff, commit: stepCommit, reportcard: stepReportCard, reconrefuse: stepReconRefuse, recong: stepReconG, reconh: stepReconH, rp: stepRp, trend: stepTrend };
  try {
    if (!steps[STEP]) throw new Error(`unknown STEP ${STEP}`);
    await steps[STEP]();
  } catch (e) {
    L('[script error]', e.stack);
    await shot(`zz-failure-${STEP}-${T}`).catch(() => {});
    process.exitCode = 1;
  }
  state.consoleErrors = state.consoleErrors || {};
  state.consoleErrors[`${STEP}-${T}`] = consoleErrors;
  saveState();
  L(`[end ${STEP} T=${T}]`, new Date().toISOString(), 'console/page errors:', consoleErrors.length);
  fs.appendFileSync(`${D}/obs-log.txt`, log.join('\n') + '\n');
  await browser.close();
})();
