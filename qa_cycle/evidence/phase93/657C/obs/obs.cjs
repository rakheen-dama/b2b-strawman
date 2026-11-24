// 657C OBS — trust take-on sign-off and commit, report snapshot, portal recompute (e2e mock stack :3001).
// Usage: RUN=G|P STEP=<step> node obs.cjs   (state in state-<RUN>.json, log in obs-log-<RUN>.txt)
//   RUN=G  the §17.3.13 set on a new non-primary banking account G at 31 Oct 2026 (the 657B set-up)
//   RUN=P  the same set on the seeded primary account at its opened date (24 Nov 2026, mid-month
//          confirmed): every firm trust screen (ledgers, transactions, Record Deposit) shows the
//          primary account only, so the ledger views and the back-dated deposit are observed here.
//   accounts  Bob creates G (RUN=G), I3 and I4 in the UI
//   clients   Bob: clients take-on A, B, C → attest → commit → portal access
//   matters   Bob: matters take-on A1, B1, C1 → commit from Validation
//   trust     Bob: trust batch, the four corrected §17.3.13 files, the position → Validated; the
//             Sign-off step shows the status text and no sign-off control; Bob's Home has no widget
//   signoff   Alice: Home widget → sheet → review → declaration → accept → Sign off → signed state
//   commit    Bob: Sign-off step → Commit dialog (X3 trust sentence) → COMPLETED → report summary;
//             Mailpit count before/after
//   ledgers   Alice: the posted figures (UI screens of the primary account; REST GETs for G)
//   deposit   Bob: Record Deposit dated on / before the cutover on the taken-on primary → refused
//   portal    client A in the portal (:3002): matter A1's trust lines
// No SQL. REST only for read-only GETs (ids, batch state, figures) with the session's own token,
// and the Mailpit API.
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-657C/frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const F = `${D}/files`;
const BASE = 'http://localhost:3001';
const API = 'http://localhost:8081';
const PORTAL = 'http://localhost:3002';
const MAILPIT = 'http://localhost:8026';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
const RUN = process.env.RUN;
if (!['G', 'P'].includes(RUN)) throw new Error('RUN=G|P');
const PFX = RUN.toLowerCase();
const STATE_FILE = `${D}/state-${RUN}.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2) + '\n');
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

const PRIMARY_NAME = 'Mathebula & Partners Trust Account';
const ACCOUNTS = {
  G: { name: 'OBS 657C Trust banking G', number: '6570001', type: 'General' },
  I3: { name: 'OBS 657C s86(3) investment I3', number: '6573001', type: 'Investment', basis: 'Section 86(3) — the firm invests surplus trust money' },
  I4: { name: 'OBS 657C s86(4) investment I4', number: '6574001', type: 'Investment', basis: "Section 86(4) — invested on a client's instruction" },
};
const BANKING_NAME = RUN === 'G' ? ACCOUNTS.G.name : PRIMARY_NAME;
const CUTOVER = RUN === 'G' ? '2026-10-31' : '2026-11-24';
const CUTOVER_LONG = RUN === 'G' ? '31 October 2026' : '24 November 2026';
const MID_MONTH = RUN === 'P';
const POSITION = { bank: '418500.00', od: '5000.00', op: '20000.00', cashbook: '403500.00', fund: '1250.00', invested: '300000.00' };
const TRUST_SENTENCE = `The opening position of ${BANKING_NAME} at ${CUTOVER_LONG} will be posted and the account will not accept entries dated on or before that date.`;
const SIGNED_FROM_HOME = 'A partner or director signs off this take-on from their Home page.';
const SIGN_RE = /sign off|sign-off|withdraw|declaration|accept/i;

let page, context, browser, token, currentLabel = 'init';
const consoleErrors = [];
const shot = async n => { await page.screenshot({ path: `${D}/${PFX}-${n}.png`, fullPage: true }); L('[shot]', `${PFX}-${n}.png`); };
const norm = s => (s || '').replace(/[\s  ]+/g, ' ').trim();
const authHeaders = () => ({ Authorization: `Bearer ${token}` });
const vis = async (id, root = page) => root.getByTestId(id).first().isVisible().catch(() => false);
const txt = async (id, root = page) => ((await vis(id, root)) ? norm(await root.getByTestId(id).first().innerText()) : null);

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
  return { http, status: j?.status, allowedActions: j?.allowedActions, counts: j?.counts, validatedAt: j?.validatedAt, cutoverDate: j?.cutoverDate, position: j?.position, midMonthAcknowledged: j?.midMonthAcknowledged, signOffs: j?.signOffs, reportId: j?.reportId, trustAccountName: j?.trustAccountName, lastError: j?.lastError };
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
  const keys = RUN === 'G' ? ['G', 'I3', 'I4'] : ['I3', 'I4'];
  for (const k of keys) { currentLabel = `account-${k}`; await createAccount(k); }
  const { body: list } = await apiGet('/api/trust-accounts');
  state.accounts = list.map(a => ({ id: a.id, accountName: a.accountName, accountType: a.accountType, investmentBasis: a.investmentBasis, isPrimary: a.isPrimary, status: a.status, openedDate: a.openedDate, accountNumber: a.accountNumber }));
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
  if (trust) await shot('10-trust-upload-form');
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
  state.clientsBatch = await uploadFirst('clients', 'OBS 657C clients A, B, C', `${F}/clients-657b.csv`); saveState();
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
  let portal = null;
  if (await vis('take-on-portal-access')) {
    await page.getByTestId('take-on-portal-access').click();
    await page.getByTestId('take-on-portal-result').waitFor({ timeout: 60000 });
    portal = await txt('take-on-portal-result');
  }
  L('[clients portal access]', portal);
  await shot('03-clients-results');
  state.clients = { cards, results, portal }; saveState();
}

async function stepMatters() {
  await login('user_e2e_bob');
  currentLabel = 'matters';
  state.mattersBatch = await uploadFirst('matters', 'OBS 657C matters A1, B1, C1', `${F}/matters-657b.csv`); saveState();
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

async function stepTrust() {
  await login('user_e2e_bob');
  currentLabel = 'trust-upload';
  state.trustBatch = await uploadFirst('trust-position', `OBS 657C trust ${RUN} at ${CUTOVER}`, `${F}/trust-client-ledger.csv`,
    { account: BANKING_NAME, section: SECTION_LABEL['client-ledger'], cutover: CUTOVER });
  saveState();
  await openBatch(state.trustBatch);
  await page.getByTestId('take-on-step-upload').click();
  await page.getByTestId('take-on-files').waitFor({ timeout: 60000 });
  for (const [k, f] of Object.entries(FILES)) await addSource(k, f);
  await shot('11-trust-files-panel');
  currentLabel = 'trust-mapping';
  await mapAllSources();
  currentLabel = 'trust-validation';
  await page.getByTestId('take-on-position-form').waitFor({ timeout: 60000 });
  await fillPosition({ cutover: CUTOVER, ...POSITION, midMonth: MID_MONTH });
  await shot('12-position-entered');
  await savePositionAndWait();
  const v = {};
  v.status = await txt('take-on-batch-status');
  v.cards = (await vis('take-on-cards')) ? await readCards() : null;
  v.banner = await txt('take-on-trust-block-banner');
  v.accountErrors = await txt('take-on-account-errors');
  v.equation = await readEquation();
  v.flags = await txt('take-on-trust-flags');
  v.api = await getBatch(state.trustBatch);
  L('[trust validated]', v);
  await shot('13-validated');
  // Bob (admin, no SIGN_OFF_TRUST_TAKE_ON) continues to the Sign-off step
  currentLabel = 'trust-signoff-step-bob';
  const toSignOff = await vis('take-on-to-sign-off');
  L('[continue to sign-off visible]', toSignOff);
  await page.getByTestId('take-on-to-sign-off').click();
  await page.getByTestId('take-on-signoff-step').waitFor({ timeout: 30000 });
  await page.waitForTimeout(1500);
  const s = {};
  s.statusText = await txt('take-on-signoff-status');
  s.statusExact = s.statusText === SIGNED_FROM_HOME;
  const step = page.getByTestId('take-on-signoff-step');
  s.panelCount = await step.getByTestId('takeon-signoff-panel').count();
  s.acceptCount = await page.getByTestId('takeon-signoff-accept').count();
  s.submitCount = await page.getByTestId('takeon-signoff-submit').count();
  s.commitCount = await page.getByTestId('take-on-commit').count();
  s.revokeCount = await page.getByTestId('take-on-signoff-revoke').count();
  s.checkboxes = await step.locator('[role="checkbox"], input[type="checkbox"]').count();
  s.stepButtons = await step.locator('button:visible').evaluateAll(bs => bs.map(b => b.innerText.replace(/\s+/g, ' ').trim()).filter(Boolean));
  s.signLikeButtons = s.stepButtons.filter(b => SIGN_RE.test(b));
  s.api = await getBatch(state.trustBatch);
  L('[bob sign-off step]', s);
  await shot('14-bob-signoff-step');
  // Bob's Home: no sign-off widget
  await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: 'load' });
  await page.waitForTimeout(4000);
  s.bobHomeWidget = await page.getByTestId('take-on-sign-off-widget').count();
  s.bobHomeHasText = (await page.locator('main').innerText()).includes('Trust take-on awaiting your sign-off');
  L('[bob home] widget count', s.bobHomeWidget, 'text present', s.bobHomeHasText);
  await shot('15-bob-home');
  state.trust = { validated: v, bobSignOffStep: s }; saveState();
}

async function stepSignOff() {
  await login('user_e2e_alice');
  currentLabel = 'signoff-home';
  const widget = page.getByTestId('take-on-sign-off-widget');
  await widget.waitFor({ timeout: 60000 });
  const r = {};
  r.widgetText = norm(await widget.innerText());
  const item = page.getByTestId(`take-on-sign-off-item-${state.trustBatch}`);
  r.itemVisible = await item.isVisible().catch(() => false);
  r.itemText = r.itemVisible ? norm(await item.innerText()) : null;
  L('[alice home widget]', r.widgetText, '| item', r.itemText);
  await shot('20-alice-home-widget');
  await item.click();
  const sheet = page.getByTestId('takeon-signoff-sheet');
  await sheet.waitFor({ timeout: 30000 });
  await sheet.getByTestId('takeon-signoff-panel').waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  r.title = await txt('takeon-signoff-sheet-title', sheet);
  r.account = await txt('takeon-signoff-account', sheet);
  r.cutover = await txt('takeon-signoff-cutover', sheet);
  r.preparedBy = await txt('takeon-signoff-prepared-by', sheet);
  r.position = {};
  for (const k of ['bankBalance', 'outstandingDepositsTotal', 'outstandingPaymentsTotal', 'cashbookBalance', 'fundLedgerBalance', 'investedTotal']) r.position[k] = await txt(`takeon-signoff-position-${k}`, sheet);
  r.blocks = await txt('takeon-signoff-blocks', sheet);
  r.accountErrors = await txt('takeon-signoff-account-errors', sheet);
  r.equation = await readEquation(sheet);
  r.flags = await txt('takeon-signoff-flags', sheet);
  r.rows = await sheet.locator('[data-testid="takeon-signoff-rows"] tbody tr').evaluateAll(trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim()).join(' | ')));
  r.declaration = await txt('takeon-signoff-declaration', sheet);
  r.declarationVersion = await txt('takeon-signoff-declaration-version', sheet);
  r.submitDisabledBeforeAccept = await sheet.getByTestId('takeon-signoff-submit').isDisabled();
  r.apiBefore = await getBatch(state.trustBatch);
  const review = await apiGet(`/api/take-on/batches/${state.trustBatch}/sign-off-review?page=0&size=50`);
  r.reviewApi = { http: review.http, declaration: review.body?.declaration, allowedActions: review.body?.allowedActions, status: review.body?.status };
  r.declarationEqualsServer = r.declaration === norm(review.body?.declaration?.text);
  L('[alice sheet review]', r);
  await shot('21-alice-sheet-review');
  currentLabel = 'signoff-sign';
  await sheet.getByTestId('takeon-signoff-accept').click();
  r.submitEnabledAfterAccept = await sheet.getByTestId('takeon-signoff-submit').isEnabled();
  await sheet.getByTestId('takeon-signoff-submit').click();
  await sheet.getByTestId('takeon-signoff-live').waitFor({ timeout: 60000 });
  await page.waitForTimeout(1000);
  r.live = await txt('takeon-signoff-live', sheet);
  r.withdrawVisible = await sheet.getByTestId('takeon-signoff-withdraw').isVisible().catch(() => false);
  r.withdrawText = r.withdrawVisible ? norm(await sheet.getByTestId('takeon-signoff-withdraw').innerText()) : null;
  r.apiAfter = await getBatch(state.trustBatch);
  L('[alice signed]', { live: r.live, withdrawVisible: r.withdrawVisible, withdrawText: r.withdrawText, apiAfter: r.apiAfter });
  await shot('22-alice-sheet-signed');
  await sheet.getByTestId('takeon-signoff-live').screenshot({ path: `${D}/${PFX}-22b-signed-state.png` });
  // Close the sheet: the widget re-reads the queue
  await page.keyboard.press('Escape');
  await sheet.waitFor({ state: 'hidden', timeout: 15000 });
  await page.waitForTimeout(3000);
  r.widgetAfterClose = (await widget.isVisible().catch(() => false)) ? norm(await widget.innerText()) : null;
  L('[alice widget after close]', r.widgetAfterClose);
  await shot('23-alice-home-after-close');
  state.signoff = r; saveState();
}

async function stepCommit() {
  await login('user_e2e_bob');
  currentLabel = 'commit';
  const r = {};
  r.mailBefore = await mailpitTotal();
  L('[mailpit before]', r.mailBefore);
  await openBatch(state.trustBatch);
  await page.getByTestId('take-on-signoff-step').waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  r.statusText = await txt('take-on-signoff-status');
  r.revokeVisible = await vis('take-on-signoff-revoke');
  r.panelCount = await page.getByTestId('takeon-signoff-panel').count();
  r.apiBefore = await getBatch(state.trustBatch);
  L('[bob sign-off step after signing]', { statusText: r.statusText, revokeVisible: r.revokeVisible, panelCount: r.panelCount, api: r.apiBefore });
  await shot('30-bob-signoff-step-signed');
  await page.getByTestId('take-on-commit').click();
  const dialog = page.getByTestId('take-on-commit-dialog');
  await dialog.waitFor({ timeout: 15000 });
  await page.waitForTimeout(500);
  r.dialogTitle = norm(await dialog.getByRole('heading').first().innerText().catch(() => ''));
  r.dialogBody = await txt('take-on-commit-body', dialog);
  r.trustSentence = await txt('take-on-commit-trust', dialog);
  r.trustSentenceExact = r.trustSentence === TRUST_SENTENCE;
  L('[commit dialog]', { title: r.dialogTitle, body: r.dialogBody, trust: r.trustSentence, exact: r.trustSentenceExact, expected: TRUST_SENTENCE });
  await shot('31-commit-dialog');
  r.commitClickedAt = new Date().toISOString();
  await page.getByTestId('take-on-commit-confirm').click();
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 240000 });
  r.resultsAt = new Date().toISOString();
  r.results = { created: await txt('take-on-result-created'), skipped: await txt('take-on-result-skipped'), failed: await txt('take-on-result-failed'), completedAt: await txt('take-on-completed-at') };
  const summary = page.getByTestId('take-on-report-summary');
  await summary.waitFor({ timeout: 60000 }).catch(() => {});
  await page.getByTestId('take-on-report-position').waitFor({ timeout: 60000 }).catch(() => {});
  await page.waitForTimeout(1000);
  r.report = {};
  r.report.visible = await summary.isVisible().catch(() => false);
  r.report.header = r.report.visible ? norm(await summary.locator('p').first().innerText()) : null;
  r.report.position = await page.locator('[data-testid^="take-on-report-position-"]').evaluateAll(trs => trs.map(tr => ({ id: tr.getAttribute('data-testid'), cells: [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim()) })));
  r.report.creditors = await txt('take-on-report-creditors');
  r.report.signOff = await txt('take-on-report-signoff');
  r.report.flags = await txt('take-on-report-flags');
  r.report.error = await txt('take-on-report-error');
  r.apiAfter = await getBatch(state.trustBatch);
  if (r.apiAfter.reportId) {
    const rep = await apiGet(`/api/take-on/reports/${r.apiAfter.reportId}`);
    r.reportApi = { http: rep.http, generatedAt: rep.body?.generatedAt, differences: rep.body?.snapshot?.differences, previousSystem: rep.body?.snapshot?.previousSystem, kazi: rep.body?.snapshot?.kazi, signOff: rep.body?.snapshot?.signOff, flags: rep.body?.snapshot?.flags, creditorTotal: rep.body?.snapshot?.creditorList?.creditorTotal };
  }
  L('[results]', r.results, '| api', r.apiAfter);
  L('[report summary UI]', r.report);
  L('[report API]', r.reportApi);
  await shot('32-results-report');
  if (r.report.visible) await summary.screenshot({ path: `${D}/${PFX}-33-report-summary.png` });
  await page.waitForTimeout(10000);
  r.mailAfter = await mailpitTotal();
  L('[mailpit after]', r.mailAfter);
  state.commit = r; saveState();
}

// ---------------------------------------------------------------------------------------------
// The posted figures
const acct = name => (state.accounts || []).find(a => a.accountName === name);

async function stepLedgers() {
  await login('user_e2e_alice');
  currentLabel = 'ledgers';
  const r = { run: RUN };
  const { body: list } = await apiGet('/api/trust-accounts');
  const banking = list.find(a => a.accountName === BANKING_NAME);
  const i3 = list.find(a => a.accountName === ACCOUNTS.I3.name);
  const i4 = list.find(a => a.accountName === ACCOUNTS.I4.name);
  r.accounts = list.map(a => ({ id: a.id, name: a.accountName, primary: a.isPrimary, takeOnCutoverDate: a.takeOnCutoverDate, takeOnBatchId: a.takeOnBatchId }));
  // Read-only GETs (figures for the cross-check, both runs)
  const g = async p => { const x = await apiGet(p); return x.http === 200 ? x.body : { http: x.http, body: x.body }; };
  r.apiClientLedgers = await g(`/api/trust-accounts/${banking.id}/client-ledgers?size=50`);
  r.apiCreditors = await g(`/api/trust-accounts/${banking.id}/creditor-accounts`);
  r.apiTotal = await g(`/api/trust-accounts/${banking.id}/total-balance`);
  r.apiUnidentified = await g(`/api/trust-accounts/${banking.id}/unidentified-deposits?size=50`);
  r.apiTxns = await g(`/api/trust-accounts/${banking.id}/transactions?size=50`);
  r.apiTxnsI3 = await g(`/api/trust-accounts/${i3.id}/transactions?size=50`);
  r.apiTxnsI4 = await g(`/api/trust-accounts/${i4.id}/transactions?size=50`);
  r.apiIntegrity = await g(`/api/trust-accounts/${banking.id}/integrity`);
  r.apiInvestments = await g(`/api/trust-accounts/${banking.id}/investments`);
  L('[api accounts]', r.accounts);
  L('[api client-ledgers]', r.apiClientLedgers);
  L('[api creditor-accounts]', r.apiCreditors);
  L('[api total-balance]', r.apiTotal);
  L('[api unidentified]', r.apiUnidentified);
  L('[api txns banking]', (r.apiTxns.content || []).map(t => ({ type: t.transactionType, amount: t.amount, date: t.transactionDate, ref: t.reference, status: t.status, client: t.customerName, matterId: t.projectId })));
  L('[api txns I3]', (r.apiTxnsI3.content || []).map(t => ({ type: t.transactionType, amount: t.amount, date: t.transactionDate, ref: t.reference, status: t.status })));
  L('[api txns I4]', (r.apiTxnsI4.content || []).map(t => ({ type: t.transactionType, amount: t.amount, date: t.transactionDate, ref: t.reference, status: t.status })));
  L('[api integrity]', r.apiIntegrity);
  L('[api investments]', r.apiInvestments);
  // UI screens: every one of them binds to the primary banking account
  const screen = async (path, name, wait) => {
    await page.goto(`${BASE}/org/${SLUG}${path}`, { waitUntil: 'load' });
    if (wait) await page.getByTestId(wait).first().waitFor({ timeout: 60000 }).catch(() => {});
    await page.waitForTimeout(2500);
    const text = norm(await page.locator('main').innerText());
    await shot(name);
    return text;
  };
  r.uiDashboard = await screen('/trust-accounting', '40-trust-dashboard');
  r.uiClientLedgers = await screen('/trust-accounting/client-ledgers', '41-client-ledgers');
  r.uiClientLedgerRows = await page.locator('main table tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText.replace(/\s+/g, ' ').trim()));
  const clientA = (r.apiClientLedgers.content || []).find(l => /Amahle Zulu/.test(l.customerName || l.clientName || ''));
  if (clientA) {
    r.uiClientA = await screen(`/trust-accounting/client-ledgers/${clientA.customerId || clientA.clientId}`, '42-client-ledger-A');
    r.uiClientARows = await page.locator('main table tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText.replace(/\s+/g, ' ').trim()));
  }
  r.uiOtherCreditors = await screen('/trust-accounting/other-creditors', '43-other-creditors', 'creditor-account-cards');
  r.uiFundCard = await txt('creditor-FUND_INTEREST_AND_CHARGES');
  r.uiUnidentifiedCard = await txt('creditor-UNIDENTIFIED_DEPOSITS');
  r.uiUnidentifiedRows = await page.locator('[data-testid="unidentified-deposits-section"] tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText.replace(/\s+/g, ' ').trim()));
  r.uiFundRows = await page.locator('[data-testid="fund-ledger-section"] tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText.replace(/\s+/g, ' ').trim()));
  r.uiTransactions = await screen(`/trust-accounting/transactions?dateFrom=${CUTOVER}&dateTo=${CUTOVER}`, '44-transactions-at-cutover');
  r.uiTransactionRows = await page.locator('main tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText.replace(/\s+/g, ' ').trim()));
  r.uiInvestments = await screen('/trust-accounting/investments', '45-investments');
  r.uiInvestmentRows = await page.locator('[data-testid="investments-table"] tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText.replace(/\s+/g, ' ').trim()));
  L('[ui dashboard]', r.uiDashboard.slice(0, 1500));
  L('[ui client-ledger rows]', r.uiClientLedgerRows);
  L('[ui client A rows]', r.uiClientARows);
  L('[ui fund card]', r.uiFundCard, '| unidentified card', r.uiUnidentifiedCard);
  L('[ui unidentified rows]', r.uiUnidentifiedRows, '| fund rows', r.uiFundRows);
  L('[ui transactions at cutover rows]', r.uiTransactionRows);
  L('[ui investment rows]', r.uiInvestmentRows);
  state.ledgers = r; saveState();
}

async function stepDeposit() {
  await login('user_e2e_bob');
  currentLabel = 'deposit';
  const r = { attempts: [] };
  const dates = [CUTOVER, '2026-11-20'];
  for (const date of dates) {
    await page.goto(`${BASE}/org/${SLUG}/trust-accounting/transactions`, { waitUntil: 'load' });
    await page.getByTestId('record-transaction-button').waitFor({ timeout: 60000 });
    await page.getByTestId('record-transaction-button').click();
    await page.getByRole('menuitem', { name: 'Record Deposit', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Record Deposit' });
    await dialog.waitFor({ timeout: 15000 });
    await dialog.getByTestId('trust-deposit-customer-trigger').click();
    await page.getByRole('option', { name: /^Amahle Zulu(\s|$)/ }).click();
    const mt = dialog.getByTestId('trust-deposit-matter-trigger');
    await page.waitForFunction(() => { const b = document.querySelector('[data-testid="trust-deposit-matter-trigger"]'); return b && !b.disabled; }, null, { timeout: 15000 });
    await mt.click();
    await page.getByRole('option', { name: /Erf 101 Brooklyn/ }).click();
    await dialog.getByLabel('Amount', { exact: true }).fill('100.00');
    const ref = `OBS657C-BACKDATED-${date}`;
    await dialog.getByLabel('Reference', { exact: true }).fill(ref);
    await dialog.getByLabel('Transaction Date').fill(date);
    await dialog.getByRole('button', { name: 'Record Deposit', exact: true }).click();
    const err = dialog.locator('p.text-destructive');
    await err.waitFor({ timeout: 20000 }).catch(() => {});
    const a = { date, ref, error: (await err.isVisible().catch(() => false)) ? norm(await err.innerText()) : null, dialogStillOpen: await dialog.isVisible() };
    L('[deposit attempt]', a);
    await shot(`50-deposit-refused-${date}`);
    r.attempts.push(a);
    await page.keyboard.press('Escape');
  }
  const { body: list } = await apiGet('/api/trust-accounts');
  const banking = list.find(a => a.accountName === BANKING_NAME);
  const tx = await apiGet(`/api/trust-accounts/${banking.id}/transactions?size=100`);
  r.backdatedRowsInApi = (tx.body?.content || []).filter(t => (t.reference || '').startsWith('OBS657C-BACKDATED')).length;
  L('[deposit rows with the OBS reference in GET transactions]', r.backdatedRowsInApi);
  state.deposit = r; saveState();
}

async function stepPortal() {
  currentLabel = 'portal';
  const r = {};
  const email = 'amahle.zulu@example.test';
  await page.goto(`${PORTAL}/login?orgId=${SLUG}`, { waitUntil: 'load' });
  await page.locator('#email').waitFor({ timeout: 60000 });
  await page.locator('#email').fill(email);
  await page.locator('form button[type="submit"], form button').first().click();
  const link = page.locator(`a[href*="/auth/exchange"]`);
  await link.waitFor({ timeout: 30000 }).catch(() => {});
  r.magicLinkShown = await link.isVisible().catch(() => false);
  await shot('60-portal-login');
  if (!r.magicLinkShown) { r.loginText = norm(await page.locator('body').innerText()); L('[portal login]', r); state.portal = r; saveState(); return; }
  await link.click();
  // next dev runs the exchange effect twice (React StrictMode): the first call signs in, the
  // second finds the token used (401) and the page says "Login Failed". Wait, then go on.
  await page.waitForURL(u => !u.pathname.startsWith('/auth/exchange') && !u.pathname.startsWith('/login'), { timeout: 15000 }).catch(() => {});
  await page.waitForTimeout(2000);
  r.afterExchangeUrl = page.url();
  r.afterExchangeText = norm(await page.locator('body').innerText()).slice(0, 300);
  r.localStorageJwt = await page.evaluate(() => !!localStorage.getItem('portal_jwt'));
  L('[portal after exchange]', r.afterExchangeUrl, '|', r.afterExchangeText, '| portal_jwt set', r.localStorageJwt);
  await shot('60b-portal-after-exchange');
  await page.goto(`${PORTAL}/trust`, { waitUntil: 'load' });
  await page.locator('h1').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(4000);
  r.trustUrl = page.url();
  r.trustText = norm(await page.locator('main').innerText().catch(() => page.locator('body').innerText()));
  await shot('61-portal-trust');
  if (process.env.MATTER_A1 && !r.trustUrl.includes(process.env.MATTER_A1)) {
    await page.goto(`${PORTAL}/trust/${process.env.MATTER_A1}`, { waitUntil: 'load' });
    await page.locator('h1').first().waitFor({ timeout: 60000 });
    await page.waitForTimeout(4000);
    r.matterUrl = page.url();
    r.matterText = norm(await page.locator('main').innerText().catch(() => page.locator('body').innerText()));
    await shot('62-portal-trust-A1');
  }
  const t = r.matterText || r.trustText;
  r.broughtForwardCount = (t.match(/Balance brought forward — take-on/g) || []).length;
  L('[portal]', r);
  state.portal = r; saveState();
}

(async () => {
  const startedAt = new Date().toISOString();
  L(`[start ${RUN} ${STEP}]`, startedAt);
  browser = await chromium.launch();
  context = await browser.newContext({ viewport: { width: 1440, height: 1100 } });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  page.on('response', r => { if (r.status() >= 400) L(`[http ${r.status()} @${currentLabel}]`, r.request().method(), r.url()); });
  const steps = { accounts: stepAccounts, clients: stepClients, matters: stepMatters, trust: stepTrust, signoff: stepSignOff, commit: stepCommit, ledgers: stepLedgers, deposit: stepDeposit, portal: stepPortal };
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
  L(`[end ${RUN} ${STEP}]`, new Date().toISOString(), 'console/page errors:', consoleErrors.length);
  fs.appendFileSync(`${D}/obs-log-${RUN}.txt`, log.join('\n') + '\n');
  await browser.close();
})();
