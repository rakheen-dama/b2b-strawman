// 658A OBS — debtor opening balances: brought-forward fee notes issued quietly, observed in a real
// browser (e2e mock stack :3001). Modelled on 656A's obs.cjs (fixed mainText raw-enum scan).
// Usage: STEP=<step> node obs.cjs   (state shared via state.json in this dir)
//   clients   clients take-on (6 clients D-001..D-006; D-006 without an address) → attest → commit → portal access
//   matters   matters take-on (2 matters TO-D-2001 / TO-D-2003) → commit from Validation
//   validate  debtors take-on (11 rows), cutover 2026-10-31 on the upload step → map → validate; cards,
//             debtor columns, messages, raw-enum scan, error filter
//   commit    commit the debtors batch from Validation; results; Mailpit before/after
//   feenotes  fee-note list (badge per row), details (badge, actions, payment link), ordinary fee note
//   notify    Alice + Bob notifications page and bell
//   approve   approve the seeded ordinary draft fee note (Acme Corp) in the UI → its number
//   reports   Fee Note Aging report (as of today) + Collections debtor book
//   void      void PS-1003 in the UI; status, audit trail; Mailpit
//   reimport  re-upload the same debtors file into a new batch and validate (not committed; cancelled)
//   carol     Carol (member): the Take-on tab
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-658A/frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const BASE = 'http://localhost:3001';
const MAILPIT = 'http://localhost:8026';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
const FX = `${D}/fixtures`;
const OUT = D;
const STATE_FILE = `${OUT}/state.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

const CUTOVER = '2026-10-31';
// Expected texts (backend DebtorBalancesDatasetHandler / TrustPositionMessages / MattersDatasetHandler @ 0d08e820f,
// frontend validation-step.tsx). Dates as TrustPositionMessages.date writes them ("d MMMM yyyy").
const EXPECT = {
  unknownClient: 'No client matches D-099. Take the client on first, or correct the reference.',
  amountZero: 'The amount must be more than R0,00.',
  afterCutover: 'The date (10 November 2026) is after the cutover date (31 October 2026). Take on only what the previous system held at the cutover date.',
  matterNotClients: 'Matter TO-D-2001 is not a matter of client D-003.',
  numberExists1: 'Fee-note number INV-0001 already exists in Kazi',
  dueDefaulted: "No due date was given. It is set to 19 November 2026, the issue date plus the firm's payment terms of 30 days.",
  continuesSeq: "Kazi's own fee-note numbers will continue after INV-0007.",
  detailsMissing: 'This client has no billing address or email in Kazi. The brought-forward fee note is taken on as it is; add them before generating a new fee note for this client.',
  duplicate: 'Matches a brought-forward fee note already in Kazi',
  numberExistsVoided: 'Fee-note number PS-1003 already exists in Kazi',
};

// Raw-enum scan: any SCREAMING_SNAKE token, or a bare coded value that has a label.
const RAW_ENUM_RE = /\b[A-Z]{2,}(?:_[A-Z]{2,})+\b|\b(?:SENT|DRAFT|APPROVED|VOID|PAID|PARTIALLY|ACTIVE|ERROR|VALID|VALIDATED|CREATED|COMPLETED|MANUAL|INVOICE|DEBTOR|CLIENTS|MATTERS|ZAR_)\b/g;
const rawEnums = text => [...new Set((text.match(RAW_ENUM_RE) || []))];
// Text nodes joined with spaces (656A run-note fix): plain textContent glues adjacent elements.
const mainText = async () => page.locator('main').evaluate(m => { const w = document.createTreeWalker(m, NodeFilter.SHOW_TEXT, { acceptNode: n => (n.parentElement && n.parentElement.closest('script,style') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT) }); const out = []; while (w.nextNode()) out.push(w.currentNode.nodeValue); return out.join(' '); });

let page, context, currentLabel = 'init';
const consoleErrors = [];
const network = [];
const shot = async n => { await page.screenshot({ path: `${OUT}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };
const norm = s => (s || '').replace(/[\s  ]+/g, ' ').trim();

async function mailpitTotal() {
  const r = await fetch(`${MAILPIT}/api/v1/messages?limit=1`);
  const j = await r.json();
  return { at: new Date().toISOString(), total: j.total, latest: j.messages?.[0] ? { subject: j.messages[0].Subject, created: j.messages[0].Created } : null };
}

async function login(user) {
  await context.clearCookies();
  await page.goto(`${BASE}/mock-login`, { waitUntil: 'load' });
  await page.locator('#user-select').selectOption(user);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/org\/.*\/dashboard/, { timeout: 60000 });
  L('[login]', user, page.url());
}

async function pick(trigger, optionName) {
  await trigger.click();
  const opt = page.getByRole('option', { name: optionName, exact: true });
  await opt.waitFor({ timeout: 15000 });
  await page.waitForTimeout(300);
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

async function upload(datasetKebab, batchName, fixture, cutover) {
  await openTakeOnTab();
  await page.getByTestId(`take-on-start-${datasetKebab}`).click();
  await page.getByTestId('take-on-new-upload').waitFor();
  if (cutover) await page.getByTestId('take-on-cutover-date').first().fill(cutover);
  await page.getByTestId('take-on-batch-name').fill(batchName);
  await page.getByTestId('take-on-file-input').setInputFiles(fixture);
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

async function readMapping() {
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  const mapTriggers = page.locator('[data-testid^="take-on-map-"]');
  const mapped = {};
  for (let i = 0; i < await mapTriggers.count(); i++) {
    const t = mapTriggers.nth(i);
    mapped[(await t.getAttribute('data-testid')).replace('take-on-map-', '')] = norm(await t.innerText());
  }
  return mapped;
}

async function ensureIsoDates() {
  const df = (await page.getByTestId('take-on-date-format').innerText()).trim();
  if (!df.startsWith('2025-12-31')) await pick(page.getByTestId('take-on-date-format'), '2025-12-31 (yyyy-mm-dd)');
}

async function validateAndRead() {
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  if (await page.getByTestId('take-on-validate').isVisible().catch(() => false)) {
    await page.getByTestId('take-on-validate').click();
  }
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  await page.waitForTimeout(2000);
  return readCardsAndRows();
}

async function readCardsAndRows() {
  const card = async id => Number((await page.getByTestId(`take-on-card-${id}`).innerText()).replace(/[^0-9]/g, '') || 'NaN');
  const cards = { total: await card('total'), valid: await card('valid'), error: await card('error'), duplicate: await card('duplicate'), blocks: await card('blocks') };
  const heads = await page.locator('table[data-testid="take-on-rows"] thead th').evaluateAll(ths => ths.map(th => th.innerText.replace(/\s+/g, ' ').trim()));
  const rows = await page.locator('table[data-testid="take-on-rows"] tbody tr[data-testid^="take-on-row-"]').evaluateAll(trs => trs.map(tr => {
    const n = tr.getAttribute('data-testid').replace('take-on-row-', '');
    const q = s => { const e = tr.querySelector(`[data-testid="take-on-row-${n}-${s}"]`); return e ? e.innerText.replace(/\s+/g, ' ').trim() : null; };
    const st = tr.querySelector(`[data-testid="take-on-row-status-${n}"]`);
    const dup = tr.querySelector(`[data-testid="take-on-row-duplicate-${n}"]`);
    const tds = [...tr.querySelectorAll('td')];
    const msgs = tds.length ? [...tds[tds.length - 1].querySelectorAll('li')].map(li => li.innerText.replace(/\s+/g, ' ').trim()) : [];
    return {
      n, status: st ? st.innerText.replace(/\s+/g, ' ').trim() : null,
      feeNoteNumber: q('feeNoteNumber'), client: q('client'), issueDate: q('issueDate'), dueDate: q('dueDate'),
      dueDefault: q('due-date-default'), outstanding: q('outstandingAmount'), clientDetails: q('clientDetails'),
      duplicate: dup ? dup.innerText.trim() : null, messages: msgs,
      cells: tds.map(td => td.innerText.replace(/\s+/g, ' ').trim()),
    };
  }));
  return { cards, heads, rows };
}

async function commitFromWizard(label) {
  await page.getByTestId('take-on-commit').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-commit').click();
  await page.getByTestId('take-on-commit-dialog').waitFor();
  const body = norm(await page.getByTestId('take-on-commit-body').innerText());
  const title = norm(await page.getByTestId('take-on-commit-dialog').locator('h2').first().innerText().catch(() => null));
  L(`[${label} commit dialog]`, { title, body });
  return { title, body };
}

async function readResults() {
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 180000 });
  const fig = async id => norm(await page.getByTestId(id).innerText().catch(() => null));
  return { created: await fig('take-on-result-created'), skipped: await fig('take-on-result-skipped'), failed: await fig('take-on-result-failed'), batchStatus: await fig('take-on-batch-status') };
}

// ---------------------------------------------------------------------------------------------
async function stepClients() {
  await login('user_e2e_alice');
  currentLabel = 'clients-upload';
  state.clientsBatch = await upload('clients', 'OBS 658A clients (6 rows)', `${FX}/clients-658a.csv`); saveState();
  const mapped = await readMapping();
  L('[clients suggested mapping]', mapped);
  await ensureIsoDates();
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'clients-validation';
  const v = await validateAndRead();
  L('[clients cards]', v.cards);
  v.rows.forEach(r => L('   ', r.cells.join(' | ')));
  await shot('a-01-clients-validation');
  currentLabel = 'clients-attestation';
  await page.getByTestId('take-on-to-attestation').click();
  await page.getByTestId('take-on-attestation').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-attestation-accept').click();
  await page.getByTestId('take-on-attest').click();
  await page.getByTestId('take-on-attestation-live').waitFor({ timeout: 60000 });
  currentLabel = 'clients-commit';
  const dialog = await commitFromWizard('clients');
  await page.getByTestId('take-on-commit-confirm').click();
  const results = await readResults();
  L('[clients results]', results);
  currentLabel = 'clients-portal-access';
  const before = await mailpitTotal();
  await page.getByTestId('take-on-portal-access').click();
  await page.getByTestId('take-on-portal-result').or(page.getByTestId('take-on-portal-error')).waitFor({ timeout: 60000 });
  const portalResult = await page.getByTestId('take-on-portal-result').innerText().catch(() => null);
  await page.waitForTimeout(5000);
  const after = await mailpitTotal();
  L('[portal access]', { portalResult, mailpitBefore: before.total, mailpitAfter: after.total });
  await shot('a-02-clients-results');
  state.clients = { mapped, cards: v.cards, dialog, results, portalResult, mailpitBefore: before, mailpitAfter: after }; saveState();
}

async function stepMatters() {
  await login('user_e2e_alice');
  currentLabel = 'matters-upload';
  state.mattersBatch = await upload('matters', 'OBS 658A matters (2 rows)', `${FX}/matters-658a.csv`); saveState();
  const mapped = await readMapping();
  L('[matters suggested mapping]', mapped);
  await ensureIsoDates();
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'matters-validation';
  const v = await validateAndRead();
  L('[matters cards]', v.cards);
  v.rows.forEach(r => L('   ', r.cells.join(' | ')));
  currentLabel = 'matters-commit';
  const dialog = await commitFromWizard('matters');
  await page.getByTestId('take-on-commit-confirm').click();
  const results = await readResults();
  L('[matters results]', results);
  await shot('a-03-matters-results');
  state.matters = { mapped, cards: v.cards, dialog, results }; saveState();
}

async function stepValidate() {
  await login('user_e2e_alice');
  currentLabel = 'debtors-upload';
  state.debtorsBatch = await upload('debtor-balances', 'OBS 658A debtors (11 rows)', `${FX}/debtors-658a.csv`, CUTOVER); saveState();
  currentLabel = 'debtors-mapping';
  const mapped = await readMapping();
  L('[debtors suggested mapping]', mapped);
  await ensureIsoDates();
  const mappingRaw = rawEnums(await mainText());
  L('[raw-enum scan, debtors mapping]', mappingRaw);
  await shot('b-01-debtors-mapping');
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'debtors-validation';
  const v = await validateAndRead();
  L('[debtors cards]', v.cards);
  L('[debtors table heads]', v.heads);
  v.rows.forEach(r => L('   ', JSON.stringify({ n: r.n, status: r.status, fn: r.feeNoteNumber, client: r.client, issue: r.issueDate, due: r.dueDate, dueDefault: r.dueDefault, out: r.outstanding, details: r.clientDetails, msgs: r.messages })));
  const validationRaw = rawEnums(await mainText());
  L('[raw-enum scan, debtors validation]', validationRaw);
  const pageText = norm(await mainText());
  const present = Object.fromEntries(Object.entries(EXPECT).map(([k, t]) => [k, pageText.includes(t)]));
  L('[exact texts present]', present);
  const stepper = norm(await page.getByTestId('take-on-wizard').locator('ol, nav').first().innerText().catch(() => ''));
  const commitVisible = await page.getByTestId('take-on-commit').isVisible().catch(() => false);
  const attestationButtons = await page.getByTestId('take-on-to-attestation').count();
  const cutoverShown = norm(await page.getByTestId('take-on-wizard').innerText()).match(/[Cc]utover[^.]{0,60}/g);
  L('[stepper]', stepper, '[commit visible on Validation]', commitVisible, '[attestation buttons]', attestationButtons, '[cutover text]', cutoverShown);
  await shot('b-02-debtors-validation');
  await page.locator('table[data-testid="take-on-rows"]').screenshot({ path: `${OUT}/b-03-debtors-validation-table.png` });
  currentLabel = 'debtors-filter-error';
  await page.getByTestId('take-on-filter-status').click();
  const opts = await page.getByRole('option').allInnerTexts();
  L('[status filter options]', opts);
  const errOpt = opts.find(o => /error/i.test(o));
  if (errOpt) await page.getByRole('option', { name: errOpt, exact: true }).click(); else await page.keyboard.press('Escape');
  await page.waitForTimeout(2000);
  const ve = await readCardsAndRows();
  ve.rows.forEach(r => L('   [error filter]', r.n, r.feeNoteNumber, r.status, r.messages.join(' / ')));
  await shot('b-04-debtors-filter-error');
  state.validate = { mapped, cards: v.cards, heads: v.heads, rows: v.rows, mappingRaw, validationRaw, present, stepper, commitVisible, attestationButtons, filterOptions: opts, errorRows: ve.rows.map(r => r.n) }; saveState();
}

async function stepCommit() {
  await login('user_e2e_alice');
  currentLabel = 'debtors-commit';
  await openBatch(state.debtorsBatch);
  L('[batch status]', norm(await page.getByTestId('take-on-batch-status').innerText().catch(() => null)));
  const before = await mailpitTotal();
  L('[mailpit before debtors commit]', before);
  const dialog = await commitFromWizard('debtors');
  await shot('c-01-debtors-commit-dialog');
  state.commitClickedAt = new Date().toISOString(); saveState();
  await page.getByTestId('take-on-commit-confirm').click();
  const results = await readResults();
  L('[debtors results]', results);
  const raw = rawEnums(await mainText());
  L('[raw-enum scan, debtors results]', raw);
  await shot('c-02-debtors-results');
  await page.waitForTimeout(15000);
  const after = await mailpitTotal();
  L('[mailpit after debtors commit (+15s)]', after);
  state.commit = { before, after, dialog, results, raw }; saveState();
}

// ---------------------------------------------------------------------------------------------
async function readFeeNoteList() {
  await page.goto(`${BASE}/org/${SLUG}/invoices`, { waitUntil: 'load' });
  await page.locator('main table tbody tr').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  return page.locator('main table tbody tr').evaluateAll(trs => trs.map(tr => {
    const a = tr.querySelector('a[href*="/invoices/"]');
    return {
      text: tr.innerText.replace(/\s+/g, ' ').trim(),
      href: a ? a.getAttribute('href') : null,
      number: a ? a.innerText.replace(/\s+/g, ' ').trim() : null,
      badge: tr.querySelector('[data-testid="fee-note-takeon-badge"]') ? tr.querySelector('[data-testid="fee-note-takeon-badge"]').innerText.trim() : null,
    };
  }));
}

const BUTTONS = ['Preview', 'Approve', 'Delete Draft', 'Send fee note', 'Void', 'Record Payment'];
async function readDetail(href, shotName) {
  await page.goto(`${BASE}${href}`, { waitUntil: 'load' });
  await page.locator('main h1').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(2000);
  const h1 = norm(await page.locator('main h1').first().innerText());
  const badge = await page.locator('main [data-testid="fee-note-takeon-badge"]').allInnerTexts();
  const buttons = {};
  for (const b of BUTTONS) buttons[b] = await page.locator('main').getByRole('button', { name: b, exact: true }).count();
  const text = norm(await page.locator('main').innerText());
  const paymentLinkSection = text.includes('Online Payment Link');
  const bfLine = (text.match(/Balance brought forward as at [^—]+— fee note [A-Z0-9-]+/g) || []);
  const audit = norm(await page.getByTestId('invoice-audit-section').innerText().catch(() => null));
  const raw = rawEnums(await mainText());
  const header = text.slice(0, 400);
  if (shotName) await shot(shotName);
  return { href, h1, badge, buttons, paymentLinkSection, bfLine, header, audit: audit ? audit.slice(0, 900) : null, raw };
}

async function stepFeeNotes() {
  await login('user_e2e_alice');
  currentLabel = 'fee-note-list';
  const list = await readFeeNoteList();
  list.forEach(r => L('   [list]', JSON.stringify(r)));
  const listRaw = rawEnums(await mainText());
  L('[raw-enum scan, fee-note list]', listRaw);
  await shot('d-01-fee-note-list');
  const byNumber = Object.fromEntries(list.filter(r => r.number).map(r => [r.number, r]));
  state.feeNoteHrefs = Object.fromEntries(list.filter(r => r.href).map(r => [r.number || 'draft', r.href]));
  saveState();
  const details = {};
  for (const [num, shotName] of [['PS-1001', 'd-02-detail-ps-1001'], ['INV-0007', 'd-03-detail-inv-0007'], ['PS-1006', 'd-04-detail-ps-1006'], ['INV-0001', 'd-05-detail-inv-0001-ordinary']]) {
    currentLabel = `detail-${num}`;
    const r = byNumber[num];
    if (!r) { L('[missing in list]', num); continue; }
    details[num] = await readDetail(r.href, shotName);
    L(`[detail ${num}]`, details[num]);
  }
  state.feenotes = { list, listRaw, details }; saveState();
}

async function readNotifications(user, shotName) {
  await login(user);
  currentLabel = `${user}-notifications`;
  await page.waitForTimeout(3000);
  const bell = await page.getByRole('button', { name: 'Notifications' }).first().innerText().catch(() => null);
  const bellSr = norm(await page.locator('header').innerText().catch(() => ''));
  const unread = (bellSr.match(/(\d+|99\+) unread notifications/) || [null, '0'])[1];
  await page.goto(`${BASE}/org/${SLUG}/notifications`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  const text = norm(await page.locator('main').innerText());
  const feeNoteMentions = (text.match(/[^.]*(?:PS-10\d\d|INV-0007|brought forward|Fee Note)[^.]*/gi) || []).slice(0, 20);
  await shot(shotName);
  return { unread, bell: norm(bell), feeNoteMentions, excerpt: text.slice(0, 1200) };
}
async function stepNotify() {
  state.notify = { alice: await readNotifications('user_e2e_alice', 'e-01-alice-notifications'), bob: await readNotifications('user_e2e_bob', 'e-02-bob-notifications') };
  L('[notifications]', state.notify); saveState();
}

async function stepApprove() {
  await login('user_e2e_alice');
  currentLabel = 'approve-ordinary';
  const href = state.feeNoteHrefs && (state.feeNoteHrefs['Draft fee note'] || state.feeNoteHrefs['draft'] || Object.entries(state.feeNoteHrefs).find(([k]) => /draft/i.test(k))?.[1]);
  let target = href;
  if (!target) {
    const list = await readFeeNoteList();
    target = list.find(r => /draft/i.test(r.text))?.href;
  }
  L('[ordinary draft href]', target);
  const before = await readDetail(target, 'f-01-ordinary-draft-before-approve');
  L('[ordinary draft before]', { h1: before.h1, badge: before.badge, buttons: before.buttons });
  const mailBefore = await mailpitTotal();
  await page.locator('main').getByRole('button', { name: 'Approve', exact: true }).click();
  await page.waitForFunction(() => /INV-\d{4}/.test(document.querySelector('main h1')?.innerText || ''), null, { timeout: 60000 });
  await page.waitForTimeout(2000);
  const h1 = norm(await page.locator('main h1').first().innerText());
  const errors = await page.locator('main .text-destructive, main [role="alert"]').allInnerTexts();
  await shot('f-02-ordinary-after-approve');
  const after = await readDetail(target, null);
  const mailAfter = await mailpitTotal();
  L('[ordinary after approve]', { h1, errors, badge: after.badge, buttons: after.buttons, mailBefore: mailBefore.total, mailAfter: mailAfter.total });
  state.approve = { href: target, before: { h1: before.h1, badge: before.badge, buttons: before.buttons }, h1, errors, after: { h1: after.h1, badge: after.badge, buttons: after.buttons, header: after.header }, mailBefore, mailAfter }; saveState();
}

async function stepReports() {
  await login('user_e2e_alice');
  currentLabel = 'report-aging';
  await page.goto(`${BASE}/org/${SLUG}/reports/invoice-aging`, { waitUntil: 'load' });
  const runBtn = page.getByRole('button', { name: 'Run Report' }).filter({ visible: true }).first();
  await runBtn.waitFor({ timeout: 60000 });
  const asOf = page.locator('#param-asOfDate').filter({ visible: true }).first();
  if (await asOf.count()) await asOf.fill('2026-11-24');
  const title = norm(await page.locator('main h1').first().innerText().catch(() => null));
  await runBtn.click();
  await page.locator('main table tbody tr').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(2000);
  const heads = await page.locator('main table thead th').allInnerTexts();
  const rows = await page.locator('main table tbody tr').evaluateAll(trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim())));
  const summary = norm(await page.locator('main').innerText()).slice(0, 1500);
  const raw = rawEnums(await mainText());
  L('[aging report]', { title, heads, raw });
  rows.forEach(r => L('   [aging row]', r.join(' | ')));
  await shot('g-01-fee-note-aging-report');
  currentLabel = 'collections';
  await page.goto(`${BASE}/org/${SLUG}/invoices/collections`, { waitUntil: 'load' });
  await page.waitForTimeout(4000);
  const collText = norm(await page.locator('main').innerText());
  const collRows = await page.locator('main table tbody tr').evaluateAll(trs => trs.map(tr => tr.innerText.replace(/\s+/g, ' ').trim())).catch(() => []);
  collRows.forEach(r => L('   [debtor book]', r));
  await shot('g-02-collections-debtor-book');
  state.reports = { title, heads, rows, summary, raw, collRows, collExcerpt: collText.slice(0, 1500) }; saveState();
}

async function stepVoid() {
  await login('user_e2e_alice');
  currentLabel = 'void-ps-1003';
  const href = state.feeNoteHrefs['PS-1003'];
  const before = await readDetail(href, 'h-01-ps-1003-before-void');
  L('[PS-1003 before void]', { h1: before.h1, badge: before.badge, buttons: before.buttons });
  const mailBefore = await mailpitTotal();
  state.voidClickedAt = new Date().toISOString(); saveState();
  page.once('dialog', d => { L('[confirm dialog]', d.message()); d.accept(); });
  await page.locator('main').getByRole('button', { name: 'Void', exact: true }).click();
  await page.waitForFunction(() => /void/i.test(document.querySelector('main h1')?.parentElement?.innerText || ''), null, { timeout: 60000 });
  await page.waitForTimeout(3000);
  const after = await readDetail(href, 'h-02-ps-1003-after-void');
  await page.waitForTimeout(10000);
  const mailAfter = await mailpitTotal();
  L('[PS-1003 after void]', { h1: after.h1, header: after.header, badge: after.badge, buttons: after.buttons, audit: after.audit, mailBefore: mailBefore.total, mailAfter: mailAfter.total });
  state.void = { before: { h1: before.h1, buttons: before.buttons }, after, mailBefore, mailAfter }; saveState();
}

async function stepReimport() {
  await login('user_e2e_alice');
  currentLabel = 'reimport-upload';
  state.reimportBatch = await upload('debtor-balances', 'OBS 658A debtors re-import (same file)', `${FX}/debtors-658a.csv`, CUTOVER); saveState();
  await readMapping();
  await ensureIsoDates();
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'reimport-validation';
  const v = await validateAndRead();
  L('[re-import cards]', v.cards);
  v.rows.forEach(r => L('   ', JSON.stringify({ n: r.n, status: r.status, fn: r.feeNoteNumber, dup: r.duplicate, msgs: r.messages })));
  const pageText = norm(await mainText());
  const present = { duplicate: pageText.includes(EXPECT.duplicate), numberExistsVoided: pageText.includes(EXPECT.numberExistsVoided), continuesSeq: pageText.includes(EXPECT.continuesSeq) };
  const raw = rawEnums(await mainText());
  L('[re-import texts present]', present, '[raw]', raw);
  await shot('i-01-reimport-validation');
  await page.locator('table[data-testid="take-on-rows"]').screenshot({ path: `${OUT}/i-02-reimport-validation-table.png` });
  currentLabel = 'reimport-cancel';
  let cancelled = null;
  if (await page.getByTestId('take-on-cancel').isVisible().catch(() => false)) {
    await page.getByTestId('take-on-cancel').click();
    await page.getByTestId('take-on-cancel-confirm').click();
    await page.getByTestId('take-on-cancelled').waitFor({ timeout: 60000 }).catch(() => {});
    cancelled = norm(await page.getByTestId('take-on-batch-status').innerText().catch(() => null));
    await shot('i-03-reimport-cancelled');
  }
  L('[re-import batch after cancel]', cancelled);
  state.reimport = { cards: v.cards, rows: v.rows, present, raw, cancelled }; saveState();
}

async function stepCarol() {
  await login('user_e2e_carol');
  currentLabel = 'carol-take-on-tab';
  const r1 = await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  const res = { status: r1 && r1.status(), url: page.url(), text: norm(await page.locator('main').innerText()).slice(0, 600), takeOnTab: await page.getByTestId('take-on-tab').count(), picker: await page.getByTestId('take-on-dataset-picker').count(), tabs: await page.getByRole('tab').allInnerTexts().catch(() => []) };
  await shot('j-01-carol-take-on-tab');
  const r2 = await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on&batch=${state.debtorsBatch}`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  res.deepLink = { status: r2 && r2.status(), wizard: await page.getByTestId('take-on-wizard').count(), rows: await page.locator('table[data-testid="take-on-rows"]').count(), text: norm(await page.locator('main').innerText()).slice(0, 300) };
  await shot('j-02-carol-debtors-batch-deeplink');
  L('[carol]', res);
  state.carol = res; saveState();
}

(async () => {
  const browser = await chromium.launch();
  context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  page.on('response', r => { if (r.status() >= 400) { const s = `[http ${r.status()} @${currentLabel}] ${r.request().method()} ${r.url()}`; network.push(s); L(s); } });
  const steps = { clients: stepClients, matters: stepMatters, validate: stepValidate, commit: stepCommit, feenotes: stepFeeNotes, notify: stepNotify, approve: stepApprove, reports: stepReports, void: stepVoid, reimport: stepReimport, carol: stepCarol };
  L(`[step ${STEP}] start`, new Date().toISOString());
  try {
    if (!steps[STEP]) throw new Error('unknown STEP');
    await steps[STEP]();
  } catch (e) {
    L('[script error]', e.stack);
    await shot(`zz-failure-step-${STEP}`).catch(() => {});
    process.exitCode = 1;
  }
  L(`[step ${STEP}] end`, new Date().toISOString(), 'console/page errors:', consoleErrors.length, 'http>=400:', network.length);
  fs.appendFileSync(`${OUT}/obs-log.txt`, log.join('\n') + '\n');
  await browser.close();
})();
