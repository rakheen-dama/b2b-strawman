// 656A OBS — matters take-on (client resolution, orphan error, lead warning, commit from Validation,
// matters linked to clients, lead, no template tasks) and the go-live checklist, observed in a real
// browser (e2e mock stack :3001). Modelled on 655A/655B's obs.cjs.
// Usage: STEP=a|b|c|d|bob|e|carol node obs.cjs   (state shared via state.json in this dir)
//   a      clients take-on (12 clients, previous-system IDs C-001..C-012): upload → map → validate →
//          attest → commit → results → Create portal access (Mailpit before/after)
//   b      matters take-on (15 rows): upload → map (File room parked) → validate; validation table,
//          client column, orphan error, lead warning, raw-enum scan
//   c      commit the matters batch from the Validation step; results; Mailpit before/after
//   d      3+ clients' Matters tab; matter detail (Team = lead, Tasks = none, Details work type);
//          Matters list raw-enum scan
//   bob    Bob's notifications ("You were added to matter …")
//   e      go-live checklist: texts; acknowledge with a note, acknowledge without, withdraw; reload
//   carol  Carol (member): the Take-on tab / go-live card
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-656A/frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const BASE = 'http://localhost:3001';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
const CLIENTS_FIXTURE = `${D}/fixtures/clients-656a.csv`;
const MATTERS_FIXTURE = `${D}/fixtures/matters-656a.csv`;
const OUT = D;
const STATE_FILE = `${OUT}/state.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

// The X3 texts, copied verbatim from backend GoLiveItem.java @ 580ae0388.
const X3 = {
  'trust-records-retained': 'Trust records from the previous system will be kept for at least seven years from the last entry, secured and immediately accessible to the firm and the Council (Rules 54.9.1 and 54.9.2).',
  'fica-records-retained': 'FICA records from the previous system will be kept for at least five years after each business relationship ends (section 23).',
  'archive-hosting-considered': 'If the previous vendor keeps hosting the archive, or its location changes, we have considered whether Council consent (Rule 54.9.2) or a notification to the FIC (section 24(3)) is needed. Kazi does not file either.',
  'auditor-informed': 'Our trust auditor has been told the cutover date and will receive the take-on report.',
};
const ORPHAN_TEXT = 'No client matches C-099. Take the client on first, or correct the reference.';
const LEAD_UNKNOWN_TEXT = 'No member of the firm has this email address. The member who commits the take-on will be the matter\'s lead.';

// Raw-enum scan: any SCREAMING_SNAKE token, or a bare coded value that has a label.
const RAW_ENUM_RE = /\b[A-Z]{2,}(?:_[A-Z]{2,})+\b|\b(?:LOW|MEDIUM|HIGH|CONVEYANCING|LABOUR|WILLS|TRUSTS|TAX|LICENSING|COMMERCIAL|COLLECTIONS|ACTIVE|CLIENTS|MATTERS|ERROR|VALID|CREATED|VALIDATED|COMPLETED)\b/g;
const rawEnums = text => [...new Set((text.match(RAW_ENUM_RE) || []))];
// Text nodes joined with spaces (plain textContent glues adjacent elements, e.g. "ActiveCOMMERCIAL_LITIGATION",
// so a \b-anchored token was missed in the first pass of step d — see README run notes). textContent, not
// innerText, so CSS text-transform: uppercase headers are not mistaken for raw enums.
const mainText = async () => page.locator('main').evaluate(m => { const w = document.createTreeWalker(m, NodeFilter.SHOW_TEXT, { acceptNode: n => (n.parentElement && n.parentElement.closest('script,style') ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT) }); const out = []; while (w.nextNode()) out.push(w.currentNode.nodeValue); return out.join(' '); });

let page, currentLabel = 'init';
const consoleErrors = [];
const network = [];
const shot = async n => { await page.screenshot({ path: `${OUT}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };
const norm = s => (s || '').replace(/\s+/g, ' ').trim();

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

async function selectOption(trigger, optionName) {
  await trigger.click();
  await page.getByRole('option', { name: optionName, exact: true }).click();
}

async function openTakeOnTab() {
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on`, { waitUntil: 'load' });
  await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
}

async function upload(datasetKebab, batchName, fixture) {
  await openTakeOnTab();
  await page.getByTestId(`take-on-start-${datasetKebab}`).click();
  await page.getByTestId('take-on-new-upload').waitFor();
  await page.getByTestId('take-on-batch-name').fill(batchName);
  await page.getByTestId('take-on-file-input').setInputFiles(fixture);
  await page.getByTestId('take-on-upload-submit').click();
  await page.waitForFunction(() => location.search.includes('batch=') || document.querySelector('[data-testid="take-on-upload-warnings"]') || document.querySelector('[data-testid="take-on-upload-server-error"]'), null, { timeout: 60000 });
  if (await page.getByTestId('take-on-upload-server-error').isVisible().catch(() => false)) {
    L('[upload server error]', await page.getByTestId('take-on-upload-server-error').innerText());
    throw new Error('upload failed');
  }
  if (await page.getByTestId('take-on-upload-warnings').isVisible().catch(() => false)) {
    L('[upload warnings]', await page.getByTestId('take-on-upload-warnings').innerText());
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
    mapped[(await t.getAttribute('data-testid')).replace('take-on-map-', '')] = (await t.innerText()).trim();
  }
  return mapped;
}

async function ensureIsoDates() {
  const df = (await page.getByTestId('take-on-date-format').innerText()).trim();
  if (!df.startsWith('2025-12-31')) await selectOption(page.getByTestId('take-on-date-format'), '2025-12-31 (yyyy-mm-dd)');
}

async function validateAndRead() {
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  if (await page.getByTestId('take-on-validate').isVisible().catch(() => false)) {
    await page.getByTestId('take-on-validate').click();
  }
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  await page.waitForTimeout(1500);
  return readCardsAndRows();
}

async function readCardsAndRows() {
  const card = async id => Number((await page.getByTestId(`take-on-card-${id}`).innerText()).replace(/[^0-9]/g, '') || 'NaN');
  const cards = { total: await card('total'), valid: await card('valid'), error: await card('error'), duplicate: await card('duplicate'), blocks: await card('blocks') };
  const heads = await page.locator('table[data-testid="take-on-rows"] thead th').evaluateAll(ths => ths.map(th => th.innerText.replace(/\s+/g, ' ').trim()));
  const rows = await page.locator('table[data-testid="take-on-rows"] tbody tr[data-testid^="take-on-row-"]').evaluateAll(trs => trs.map(tr => {
    const id = tr.getAttribute('data-testid');
    const n = id.replace('take-on-row-', '');
    const q = s => { const e = tr.querySelector(`[data-testid="take-on-row-${n}-${s}"]`); return e ? e.innerText.replace(/\s+/g, ' ').trim() : null; };
    const st = tr.querySelector(`[data-testid="take-on-row-status-${n}"]`);
    return {
      n,
      status: st ? st.innerText.replace(/\s+/g, ' ').trim() : null,
      name: q('name'), ref: q('referenceNumber'), client: q('client'), lead: q('leadMemberEmail'), leadWarning: q('lead-warning'),
      cells: [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim()),
    };
  }));
  return { cards, heads, rows };
}

// ---------------------------------------------------------------------------------------------
async function stepA() {
  await login('user_e2e_alice');
  currentLabel = 'clients-upload';
  state.clientsBatch = await upload('clients', 'OBS 656A clients (12 rows)', CLIENTS_FIXTURE); saveState();
  currentLabel = 'clients-mapping';
  const mapped = await readMapping();
  L('[clients suggested mapping]', mapped);
  await ensureIsoDates();
  await shot('a-01-clients-mapping');
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'clients-validation';
  const v = await validateAndRead();
  L('[clients cards]', v.cards);
  v.rows.forEach(r => L('   ', r.cells.join(' | ')));
  await shot('a-02-clients-validation');
  currentLabel = 'clients-attestation';
  await page.getByTestId('take-on-to-attestation').click();
  await page.getByTestId('take-on-attestation').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-attestation-accept').click();
  await page.getByTestId('take-on-attest').click();
  await page.getByTestId('take-on-attestation-live').waitFor({ timeout: 60000 });
  L('[live attestation]', norm(await page.getByTestId('take-on-attestation-live').innerText()));
  currentLabel = 'clients-commit';
  await page.getByTestId('take-on-commit').click();
  await page.getByTestId('take-on-commit-dialog').waitFor();
  L('[clients commit dialog]', norm(await page.getByTestId('take-on-commit-body').innerText()));
  await page.getByTestId('take-on-commit-confirm').click();
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 180000 });
  const fig = async id => norm(await page.getByTestId(id).innerText());
  const results = { created: await fig('take-on-result-created'), skipped: await fig('take-on-result-skipped'), failed: await fig('take-on-result-failed') };
  L('[clients results]', results);
  await shot('a-03-clients-results');
  currentLabel = 'clients-portal-access';
  const before = await mailpitTotal();
  await page.getByTestId('take-on-portal-access').click();
  await page.getByTestId('take-on-portal-result').or(page.getByTestId('take-on-portal-error')).waitFor({ timeout: 60000 });
  const portalResult = await page.getByTestId('take-on-portal-result').innerText().catch(() => null);
  const portalError = await page.getByTestId('take-on-portal-error').innerText().catch(() => null);
  await page.waitForTimeout(5000);
  const after = await mailpitTotal();
  L('[portal access]', { portalResult, portalError, mailpitBefore: before, mailpitAfter: after });
  await shot('a-04-clients-portal-access');
  state.a = { mapped, cards: v.cards, results, portalResult, portalError, mailpitBefore: before, mailpitAfter: after }; saveState();
}

// ---------------------------------------------------------------------------------------------
async function stepB() {
  await login('user_e2e_alice');
  currentLabel = 'matters-upload';
  state.mattersBatch = await upload('matters', 'OBS 656A matters (15 rows)', MATTERS_FIXTURE); saveState();
  currentLabel = 'matters-mapping';
  const mapped = await readMapping();
  L('[matters suggested mapping]', mapped);
  const leftovers = await page.getByTestId('take-on-leftovers').innerText().catch(() => null);
  L('[leftovers before]', norm(leftovers));
  // "File room" is the extra column: keep it as a note on the record (parked).
  const leftoverTriggers = page.locator('[data-testid^="take-on-leftover-"]').filter({ hasNot: page.locator('[id^="take-on-leftover-label-"]') });
  const leftoverIds = await page.locator('[data-testid^="take-on-leftover-"]').evaluateAll(es => es.map(e => e.getAttribute('data-testid')));
  L('[leftover triggers]', leftoverIds);
  for (const id of leftoverIds) {
    await selectOption(page.getByTestId(id), 'Keep as a note on the record');
  }
  await ensureIsoDates();
  L('[leftovers after]', norm(await page.getByTestId('take-on-leftovers').innerText().catch(() => null)));
  const mappingRaw = rawEnums(await mainText());
  L('[raw-enum scan, matters mapping]', mappingRaw);
  await shot('b-01-matters-mapping');
  if (await page.getByTestId('take-on-mapping-issues').isVisible().catch(() => false)) L('[mapping issues]', await page.getByTestId('take-on-mapping-issues').innerText());
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'matters-validation';
  const v = await validateAndRead();
  L('[matters cards]', v.cards);
  L('[matters table heads]', v.heads);
  v.rows.forEach(r => L('   ', JSON.stringify({ n: r.n, status: r.status, name: r.name, ref: r.ref, client: r.client, lead: r.lead, leadWarning: r.leadWarning }), '||', r.cells.join(' | ')));
  const validationRaw = rawEnums(await mainText());
  L('[raw-enum scan, matters validation]', validationRaw);
  const pageText = norm(await mainText());
  const orphanShown = pageText.includes(ORPHAN_TEXT);
  const leadUnknownShown = pageText.includes(LEAD_UNKNOWN_TEXT);
  L('[exact texts present]', { orphanShown, leadUnknownShown });
  await shot('b-02-matters-validation');
  await page.locator('table[data-testid="take-on-rows"]').screenshot({ path: `${OUT}/b-03-matters-validation-table.png` });
  // Errors filter.
  currentLabel = 'matters-filter-error';
  await page.getByTestId('take-on-filter-status').click();
  const opts = await page.getByRole('option').allInnerTexts();
  L('[status filter options]', opts);
  const errOpt = opts.find(o => /error/i.test(o));
  if (errOpt) await page.getByRole('option', { name: errOpt, exact: true }).click(); else await page.keyboard.press('Escape');
  await page.waitForTimeout(2000);
  const ve = await readCardsAndRows();
  ve.rows.forEach(r => L('   [error filter]', r.cells.join(' | ')));
  await shot('b-04-matters-filter-error');
  const commitVisible = await page.getByTestId('take-on-commit').isVisible().catch(() => false);
  const attestationButton = await page.getByTestId('take-on-to-attestation').count();
  const stepper = norm(await page.getByTestId('take-on-wizard').locator('ol, nav').first().innerText().catch(() => ''));
  L('[commit button visible on Validation]', commitVisible, '[attestation button count]', attestationButton, '[stepper]', stepper);
  state.b = { mapped, cards: v.cards, heads: v.heads, rows: v.rows, mappingRaw, validationRaw, orphanShown, leadUnknownShown, errorRows: ve.rows, commitVisible, attestationButton, stepper }; saveState();
}

// ---------------------------------------------------------------------------------------------
async function stepC() {
  await login('user_e2e_alice');
  currentLabel = 'matters-commit';
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on&batch=${state.mattersBatch}`, { waitUntil: 'load' });
  await page.getByTestId('take-on-wizard').waitFor({ timeout: 60000 });
  L('[batch status]', await page.getByTestId('take-on-batch-status').innerText());
  await page.getByTestId('take-on-commit').waitFor({ timeout: 60000 });
  const before = await mailpitTotal();
  L('[mailpit before matters commit]', before);
  await page.getByTestId('take-on-commit').click();
  await page.getByTestId('take-on-commit-dialog').waitFor();
  const body = norm(await page.getByTestId('take-on-commit-body').innerText());
  L('[matters commit dialog]', body);
  await shot('c-01-matters-commit-dialog');
  await page.getByTestId('take-on-commit-confirm').click();
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 180000 });
  const fig = async id => norm(await page.getByTestId(id).innerText());
  const results = { created: await fig('take-on-result-created'), skipped: await fig('take-on-result-skipped'), failed: await fig('take-on-result-failed') };
  const failedNote = await page.getByTestId('take-on-result-failed-note').innerText().catch(() => null);
  const status = await page.getByTestId('take-on-batch-status').innerText();
  const resultsText = norm(await page.getByTestId('take-on-results').last().innerText().catch(() => ''));
  L('[matters results]', results, '[failed note]', failedNote, '[batch status]', status);
  L('[results text]', resultsText);
  const raw = rawEnums(await mainText());
  L('[raw-enum scan, matters results]', raw);
  await shot('c-02-matters-results');
  await page.waitForTimeout(8000);
  const after = await mailpitTotal();
  L('[mailpit after matters commit (+8s)]', after);
  // The rows' outcomes, back on the Validation step.
  currentLabel = 'matters-rows-after-commit';
  const valStep = page.getByRole('button', { name: /^Validation/ }).or(page.getByRole('link', { name: /^Validation/ })).first();
  let rowsAfter = null;
  if (await valStep.isVisible().catch(() => false)) {
    await valStep.click();
    await page.getByTestId('take-on-cards').waitFor({ timeout: 30000 }).catch(() => {});
    await page.waitForTimeout(1500);
    rowsAfter = await readCardsAndRows().catch(() => null);
    if (rowsAfter) rowsAfter.rows.forEach(r => L('   [after commit]', r.cells.join(' | ')));
    await shot('c-03-matters-rows-after-commit');
  } else {
    L('[no Validation stepper control visible]');
  }
  state.c = { before, after, body, results, failedNote, status, resultsText, raw, rowsAfter }; saveState();
}

// ---------------------------------------------------------------------------------------------
const CLIENTS_TO_OPEN = ['Lindiwe Mthembu', 'Pieter van der Merwe', 'Highveld Logistics (Pty) Ltd', 'Nomvula Dlamini', 'Cape Vines Wholesale (Pty) Ltd'];
const MATTERS_TO_OPEN = ['Supply contract dispute', 'Transfer of Erf 412 Hatfield', 'Liquor licence renewal'];
async function stepD() {
  await login('user_e2e_alice');
  state.d = { clients: {}, matters: {} };
  for (const name of CLIENTS_TO_OPEN) {
    currentLabel = `client-${name}`;
    await page.goto(`${BASE}/org/${SLUG}/clients?search=${encodeURIComponent(name)}`, { waitUntil: 'load' });
    const link = page.locator('table tbody tr a', { hasText: name }).first();
    await link.waitFor({ timeout: 60000 });
    const href = await link.getAttribute('href');
    const url = `${BASE}${href.split('?')[0]}`;
    await page.goto(`${url}?tab=matters`, { waitUntil: 'load' });
    await page.getByRole('heading', { level: 1, name }).waitFor({ timeout: 60000 });
    await page.waitForTimeout(2500);
    const matterLinks = await page.locator('main a[href*="/matters/"]').evaluateAll(as => [...new Set(as.map(a => a.innerText.replace(/\s+/g, ' ').trim()).filter(Boolean))]);
    const raw = rawEnums(await mainText());
    L(`[client ${name} matters tab]`, { url, matterLinks, raw });
    await shot(`d-0${CLIENTS_TO_OPEN.indexOf(name) + 1}-client-matters-${name.split(' ')[0].toLowerCase()}`);
    state.d.clients[name] = { url, matterLinks, raw };
  }
  // Matter detail.
  currentLabel = 'matters-list';
  await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: 'load' });
  await page.locator('main a[href*="/matters/"]').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(2000);
  const listRaw = rawEnums(await mainText());
  const listText = norm(await page.locator('main').innerText());
  L('[matters list raw-enum scan]', listRaw);
  await shot('d-10-matters-list');
  state.d.listRaw = listRaw;
  for (const [i, mname] of MATTERS_TO_OPEN.entries()) {
    currentLabel = `matter-${mname}`;
    await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: 'load' });
    const link = page.locator('main a[href*="/matters/"]', { hasText: mname }).first();
    await link.waitFor({ timeout: 60000 });
    const href = await link.getAttribute('href');
    const url = `${BASE}${href.split('?')[0]}`;
    // Team (lead).
    await page.goto(`${url}?tab=team`, { waitUntil: 'load' });
    const panel = page.getByTestId('matter-team-panel').filter({ visible: true }).first();
    await panel.waitFor({ timeout: 30000 });
    await page.waitForTimeout(1500);
    const team = norm(await panel.innerText());
    const teamRows = await panel.locator('tr, li').evaluateAll(rs => rs.map(r => r.innerText.replace(/\s+/g, ' ').trim()).filter(Boolean));
    L(`[matter ${mname} team]`, { url, team, teamRows });
    await shot(`d-2${i}-matter-team-${i}`);
    // Tasks.
    await page.goto(`${url}?tab=tasks`, { waitUntil: 'load' });
    await page.waitForTimeout(3000);
    const tasksText = norm(await page.locator('main').innerText());
    const taskRows = await page.locator('main table tbody tr').count();
    const tasksIdx = tasksText.indexOf('Tasks');
    L(`[matter ${mname} tasks]`, { taskRows, excerpt: tasksText.slice(Math.max(0, tasksText.length - 600)) });
    await shot(`d-3${i}-matter-tasks-${i}`);
    // Details (work type etc.).
    await page.goto(`${url}?tab=details`, { waitUntil: 'load' });
    await page.waitForTimeout(2500);
    const detailsText = norm(await page.locator('main').innerText());
    const raw = rawEnums(await mainText());
    L(`[matter ${mname} details]`, { raw, excerpt: detailsText.slice(0, 1500) });
    await shot(`d-4${i}-matter-details-${i}`);
    state.d.matters[mname] = { url, team, teamRows, taskRows, raw, detailsExcerpt: detailsText.slice(0, 1500) };
  }
  saveState();
}

// ---------------------------------------------------------------------------------------------
async function stepBob() {
  await login('user_e2e_bob');
  currentLabel = 'bob-notifications';
  await page.goto(`${BASE}/org/${SLUG}/notifications`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  const text = norm(await page.locator('main').innerText());
  const added = (text.match(/[^.]*added to matter[^.]*/gi) || []).slice(0, 10);
  L('[bob notifications]', { added, excerpt: text.slice(0, 1500) });
  await shot('bob-01-notifications');
  state.bob = { added, excerpt: text.slice(0, 1500) }; saveState();
}

// ---------------------------------------------------------------------------------------------
async function readGoLive() {
  const card = page.getByTestId('take-on-go-live-card');
  await card.waitFor({ timeout: 60000 });
  const items = {};
  for (const k of Object.keys(X3)) {
    const item = page.getByTestId(`take-on-go-live-item-${k}`);
    const text = norm(await page.getByTestId(`take-on-go-live-text-${k}`).innerText().catch(() => null));
    const ack = await page.getByTestId(`take-on-go-live-acknowledged-${k}`).innerText().then(norm).catch(() => null);
    items[k] = {
      present: await item.count(),
      text, textMatchesX3: text === X3[k],
      acknowledged: ack,
      hasAcknowledgeButton: await page.getByTestId(`take-on-go-live-acknowledge-${k}`).count(),
      hasWithdrawButton: await page.getByTestId(`take-on-go-live-withdraw-${k}`).count(),
    };
  }
  const order = await page.locator('[data-testid^="take-on-go-live-item-"]').evaluateAll(es => es.map(e => e.getAttribute('data-testid').replace('take-on-go-live-item-', '')));
  return { title: norm(await card.locator('h3, [data-slot="card-title"]').first().innerText().catch(() => null)), description: norm(await card.locator('[data-slot="card-description"]').first().innerText().catch(() => null)), order, items };
}
async function stepE() {
  await login('user_e2e_alice');
  currentLabel = 'go-live-initial';
  await openTakeOnTab();
  const initial = await readGoLive();
  L('[go-live initial]', initial);
  await page.getByTestId('take-on-go-live-card').screenshot({ path: `${OUT}/e-01-go-live-initial.png` });
  await shot('e-01b-take-on-tab-full');
  // 1. Acknowledge TRUST_RECORDS_RETAINED with a note.
  currentLabel = 'go-live-ack-note';
  const NOTE = 'Previous system archive kept in Archive room E; checked by A. Owner.';
  await page.getByTestId('take-on-go-live-note-trust-records-retained').fill(NOTE);
  await page.getByTestId('take-on-go-live-acknowledge-trust-records-retained').click();
  await page.getByTestId('take-on-go-live-acknowledged-trust-records-retained').waitFor({ timeout: 30000 });
  // 2. Acknowledge FICA_RECORDS_RETAINED without a note.
  currentLabel = 'go-live-ack-plain';
  await page.getByTestId('take-on-go-live-acknowledge-fica-records-retained').click();
  await page.getByTestId('take-on-go-live-acknowledged-fica-records-retained').waitFor({ timeout: 30000 });
  // 3. Acknowledge AUDITOR_INFORMED, then withdraw it.
  currentLabel = 'go-live-ack-then-withdraw';
  await page.getByTestId('take-on-go-live-acknowledge-auditor-informed').click();
  await page.getByTestId('take-on-go-live-acknowledged-auditor-informed').waitFor({ timeout: 30000 });
  const afterAck = await readGoLive();
  L('[go-live after three acknowledgements]', afterAck);
  await page.getByTestId('take-on-go-live-card').screenshot({ path: `${OUT}/e-02-go-live-three-acknowledged.png` });
  await page.getByTestId('take-on-go-live-withdraw-auditor-informed').click();
  await page.getByTestId('take-on-go-live-acknowledge-auditor-informed').waitFor({ timeout: 30000 });
  const afterWithdraw = await readGoLive();
  L('[go-live after withdraw]', afterWithdraw);
  await page.getByTestId('take-on-go-live-card').screenshot({ path: `${OUT}/e-03-go-live-after-withdraw.png` });
  const errorCount = await page.getByTestId('take-on-go-live-error').count();
  // Reload.
  currentLabel = 'go-live-reload';
  await page.reload({ waitUntil: 'load' });
  const afterReload = await readGoLive();
  L('[go-live after reload]', afterReload);
  await page.getByTestId('take-on-go-live-card').screenshot({ path: `${OUT}/e-04-go-live-after-reload.png` });
  const raw = rawEnums(await page.getByTestId('take-on-go-live-card').evaluate(e => e.textContent));
  L('[raw-enum scan, go-live card]', raw, '[error elements]', errorCount);
  state.e = { initial, afterAck, afterWithdraw, afterReload, raw, errorCount, note: NOTE }; saveState();
}

// ---------------------------------------------------------------------------------------------
async function pageSummary() {
  const h1 = await page.locator('h1').allInnerTexts();
  const text = norm(await page.locator('main').innerText().catch(() => ''));
  return { url: page.url(), h1, text: text.slice(0, 900) };
}
async function stepCarol() {
  await login('user_e2e_carol');
  const res = {};
  currentLabel = 'carol-take-on-tab';
  const r1 = await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  res.takeOnTab = { status: r1 && r1.status(), ...(await pageSummary()), goLiveCards: await page.getByTestId('take-on-go-live-card').count(), takeOnTab: await page.getByTestId('take-on-tab').count(), picker: await page.getByTestId('take-on-dataset-picker').count() };
  L('[carol take-on tab]', res.takeOnTab);
  await shot('f-01-carol-take-on-tab');
  currentLabel = 'carol-matters-settings';
  const r2 = await page.goto(`${BASE}/org/${SLUG}/settings/matters`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  const tabs = await page.getByRole('tab').allInnerTexts().catch(() => []);
  const links = await page.locator('main a').evaluateAll(as => as.map(a => a.innerText.trim()).filter(t => /take-on/i.test(t)));
  res.mattersSettings = { status: r2 && r2.status(), ...(await pageSummary()), tabs, takeOnLinks: links, goLiveCards: await page.getByTestId('take-on-go-live-card').count() };
  L('[carol matters settings]', res.mattersSettings);
  await shot('f-02-carol-matters-settings');
  currentLabel = 'carol-batch-deeplink';
  const r3 = await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on&batch=${state.mattersBatch}`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  res.batchDeepLink = { status: r3 && r3.status(), ...(await pageSummary()), wizard: await page.getByTestId('take-on-wizard').count(), goLiveCards: await page.getByTestId('take-on-go-live-card').count() };
  L('[carol batch deep link]', res.batchDeepLink);
  await shot('f-03-carol-batch-deeplink');
  state.carol = res; saveState();
}

// ---------------------------------------------------------------------------------------------
// uiworktype: control for OBS-656A-1 — Alice creates a matter in the ordinary New Matter dialog (not
// take-on) with the free-text work type "Commercial litigation" and reads the matter header.
async function selectNative(select, label) {
  const option = select.locator('option', { hasText: label }).first();
  await option.waitFor({ state: 'attached', timeout: 15000 });
  await select.selectOption(await option.getAttribute('value'));
}
const UI_MATTER = 'OBS 656A UI-created matter (work type control)';
async function stepUiWorkType() {
  await login('user_e2e_alice');
  currentLabel = 'ui-new-matter';
  await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: 'load' });
  await page.getByRole('button', { name: 'New Matter' }).click();
  const dialog = page.getByRole('dialog', { name: 'Create Matter' });
  await dialog.waitFor();
  await dialog.getByLabel('Name', { exact: true }).fill(UI_MATTER);
  await selectNative(dialog.getByLabel(/^Client/), 'Lindiwe Mthembu');
  await dialog.getByLabel(/^Work Type/).fill('Commercial litigation');
  await shot('g-01-ui-new-matter-dialog');
  await dialog.getByRole('button', { name: 'Create Matter' }).click();
  await dialog.waitFor({ state: 'hidden', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: 'load' });
  const link = page.locator('main a[href*="/matters/"]', { hasText: UI_MATTER }).first();
  await link.waitFor({ timeout: 30000 });
  await link.click();
  await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 30000 });
  await page.waitForTimeout(2000);
  const head = norm(await page.locator('main').innerText()).slice(0, 400);
  const raw = rawEnums(await mainText());
  L('[ui-created matter header]', { url: page.url(), head, raw });
  await shot('g-02-ui-created-matter-header');
  state.uiWorkType = { url: page.url(), head, raw }; saveState();
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  page.on('response', r => { if (r.status() >= 400) { const s = `[http ${r.status()} @${currentLabel}] ${r.request().method()} ${r.url()}`; network.push(s); L(s); } });
  const steps = { a: stepA, b: stepB, c: stepC, d: stepD, bob: stepBob, e: stepE, carol: stepCarol, uiworktype: stepUiWorkType };
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
