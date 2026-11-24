// 655B OBS — FICA attestation projection, review queue, attestation card, widget, review intervals,
// observed in a real browser (e2e mock stack :3001). Modelled on 655A's obs.cjs.
// Usage: STEP=0|a|b|c|d|e|f|g|matter|carol node obs.cjs   (state shared via state.json in this dir)
//   0       Alice creates an onboarding client through the normal UI (New Client → Start Onboarding);
//           records its FICA status (list + detail) and the client list before the take-on
//   a       take-on wizard: upload → map (records-location default) → validate → attest → commit → results
//   b       client list: FICA badges, reasons, risk, records location; raw-enum scan
//   c       "FICA review due" system view: rows + order, aria-sort, first Client header click
//   d       client detail of every imported client: attestation card fields + hero FICA tile
//   e       dashboard "FICA outstanding" widget
//   f       the onboarding client's FICA status after the take-on
//   g       Firm Settings → Compliance: review intervals 36/24/12; Medium → 18 (clear + type), save,
//           reload; 0 and 121 refused inline; back to 24
//   matter  Alice opens a matter for an attested (review-due) client; matter overview FICA card
//   carol   Carol (member): clients page, ?view=system:fica-review-due, client detail, matter overview
const { chromium } = require('/Users/rakheendama/Projects/2026/worktree-epic-655B/frontend/node_modules/@playwright/test');
const fs = require('fs');
const D = __dirname;
const BASE = 'http://localhost:3001';
const SLUG = 'e2e-test-org';
const STEP = process.env.STEP;
const FIXTURE = `${D}/fixtures/clients-655b.csv`;
const OUT = process.env.OUT_DIR ? `${D}/${process.env.OUT_DIR}` : D;
fs.mkdirSync(OUT, { recursive: true });
const STATE_FILE = `${OUT}/state.json`;
const state = fs.existsSync(STATE_FILE) ? JSON.parse(fs.readFileSync(STATE_FILE, 'utf8')) : {};
const saveState = () => fs.writeFileSync(STATE_FILE, JSON.stringify(state, null, 2));
const log = [];
const L = (...a) => { const s = a.map(x => (typeof x === 'string' ? x : JSON.stringify(x))).join(' '); console.log(s); log.push(s); };

// The fixture's clients (file order) and what 655B should project for them (intervals 36/24/12, today 2026-11-24).
const FIXTURE_CLIENTS = [
  { ref: 'OLD-B01', name: 'Busisiwe Ngcobo', expect: 'clean' },
  { ref: 'OLD-B02', name: 'Umhlanga Ridge Holdings (Pty) Ltd', expect: 'clean' },
  { ref: 'OLD-B03', name: 'Hendrik Joubert', expect: 'clean' },
  { ref: 'OLD-B04', name: 'Thabo Mashaba', expect: 'review', reasons: ['Not risk-rated'] },
  { ref: 'OLD-B05', name: 'Mokopane Mining Supplies (Pty) Ltd', expect: 'review', reasons: ['Beneficial ownership outstanding'] },
  { ref: 'OLD-B06', name: 'Refilwe Moloi', expect: 'review', reasons: ['Source of funds outstanding'] },
  { ref: 'OLD-B07', name: 'Andile Khumalo', expect: 'review', reasons: ['Not screened'] },
  { ref: 'OLD-B08', name: 'Gerrit Coetzee', expect: 'review', reasons: ['Review date passed'] },
];
const ONBOARDING_CLIENT = { name: 'Palesa Dube (OBS 655B onboarding)', email: 'palesa.dube.obs655b@example.test' };
const RECORDS_DEFAULT = 'Archive room D, Sandton office';
const LABEL_ATTESTED = 'Verified in prior system (attested)';
const LABEL_REVIEW = 'Review due (attested)';

// Raw-enum scan: any SCREAMING_SNAKE token, or a bare coded value that has a label.
const RAW_ENUM_RE = /\b[A-Z]{2,}(?:_[A-Z]{2,})+\b|\b(?:ATTESTED|UNRATED|OUTSTANDING|LOW|MEDIUM|HIGH|DONE|REVIEW|TRUST|COMPANY)\b/g;
const rawEnums = text => [...new Set((text.match(RAW_ENUM_RE) || []))];
// Scan the DOM text (textContent, so CSS text-transform: uppercase headers such as "Trust balance"
// are not mistaken for raw enums), excluding <script>/<style>.
const mainText = async () => page.locator('main').evaluate(m => { const c = m.cloneNode(true); c.querySelectorAll('script,style').forEach(e => e.remove()); return c.textContent; });

let page, currentLabel = 'init';
const consoleErrors = [];
const network = [];
const shot = async n => { await page.screenshot({ path: `${OUT}/${n}.png`, fullPage: true }); L('[shot]', `${n}.png`); };
const norm = s => (s || '').replace(/\s+/g, ' ').trim();

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

async function selectNative(select, label) {
  const option = select.locator('option', { hasText: label }).first();
  await option.waitFor({ state: 'attached', timeout: 15000 });
  await select.selectOption(await option.getAttribute('value'));
}

async function readClientTable() {
  await page.locator('table').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(1000);
  const heads = await page.locator('table thead th').evaluateAll(ths => ths.map(th => ({ text: th.innerText.replace(/\s+/g, ' ').trim(), ariaSort: th.getAttribute('aria-sort') })));
  const rows = await page.locator('table tbody tr').evaluateAll(trs => trs.map(tr => {
    const q = id => { const e = tr.querySelector(`[data-testid="${id}"]`); return e ? e.innerText.replace(/\s+/g, ' ').trim() : null; };
    const link = tr.querySelector('a[href*="/clients/"]');
    return {
      name: link ? link.innerText.trim() : null,
      href: link ? link.getAttribute('href') : null,
      ficaStatus: q('client-fica-status'),
      reasons: q('client-fica-review-reasons'),
      recordsLocation: q('client-fica-records-location'),
      recordsLocationTitle: (tr.querySelector('[data-testid="client-fica-records-location"]') || {}).title || null,
      cells: [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim()),
    };
  }));
  return { heads, rows };
}

async function clientDetailUrlByName(name) {
  return state.clientUrls && state.clientUrls[name];
}

// ---------------------------------------------------------------------------------------------
async function step0() {
  await login('user_e2e_alice');
  currentLabel = 'clients-list-before';
  await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: 'load' });
  const before = await readClientTable();
  L('[client list headers]', before.heads.map(h => h.text));
  before.rows.forEach(r => L('   ', r.name, '|', r.ficaStatus, '|', r.reasons));
  state.listBeforeOnboardingClient = before.rows.map(r => ({ name: r.name, ficaStatus: r.ficaStatus })); saveState();
  await shot('s0-01-client-list-before');
  // Create the onboarding client through the New Client dialog (not take-on).
  currentLabel = 'new-client';
  await page.getByRole('button', { name: 'New Client' }).click();
  const dialog = page.getByRole('dialog', { name: /^(Create Client|Additional Information)$/ });
  await dialog.waitFor();
  await dialog.getByLabel('Name', { exact: true }).fill(ONBOARDING_CLIENT.name);
  await selectNative(dialog.getByLabel('Type', { exact: true }), 'Individual');
  await dialog.getByLabel('Email', { exact: true }).fill(ONBOARDING_CLIENT.email);
  await dialog.locator('input[name="addressLine1"]').fill('12 Rivonia Road');
  await dialog.locator('input[name="city"]').fill('Sandton');
  await dialog.locator('input[name="postalCode"]').fill('2196');
  await dialog.getByLabel(/^Country/).selectOption('ZA');
  await dialog.getByRole('button', { name: 'Next' }).click();
  await dialog.getByText('Step 2 of 2').waitFor();
  await dialog.getByRole('button', { name: 'Create Client' }).click();
  await page.getByRole('heading', { level: 1, name: ONBOARDING_CLIENT.name }).waitFor({ timeout: 30000 });
  await page.waitForURL(/\/clients\/[0-9a-f-]{36}/, { timeout: 30000 });
  state.onboardingClientUrl = page.url().split('?')[0]; saveState();
  L('[onboarding client created]', state.onboardingClientUrl, 'lifecycle:', await page.getByTestId('lifecycle-badge').innerText());
  // PROSPECT → ONBOARDING (starts the onboarding checklist).
  currentLabel = 'start-onboarding';
  await page.getByRole('button', { name: 'Start Onboarding' }).first().click();
  const confirm = page.getByRole('alertdialog', { name: 'Start Onboarding' });
  await confirm.getByRole('button', { name: 'Start Onboarding' }).click();
  await confirm.waitFor({ state: 'hidden', timeout: 15000 });
  await page.waitForFunction(() => (document.querySelector('[data-testid="lifecycle-badge"]') || {}).innerText === 'Onboarding', null, { timeout: 15000 });
  await page.reload({ waitUntil: 'load' });
  await page.waitForTimeout(2000);
  const tiles = norm(await page.locator('[data-slot="detail-hero-tiles"]').innerText().catch(() => null));
  const lifecycle = await page.getByTestId('lifecycle-badge').innerText();
  const attestationCards = await page.getByTestId('client-fica-attestation-card').count();
  L('[onboarding client detail before take-on]', { lifecycle, tiles, attestationCards });
  await shot('s0-02-onboarding-client-detail-before');
  currentLabel = 'clients-list-onboarding-before';
  await page.goto(`${BASE}/org/${SLUG}/clients?search=${encodeURIComponent('Palesa Dube')}`, { waitUntil: 'load' });
  const t = await readClientTable();
  const row = t.rows.find(r => r.name === ONBOARDING_CLIENT.name);
  L('[onboarding client list row before take-on]', row);
  await shot('s0-03-onboarding-client-list-before');
  state.onboardingBefore = { lifecycle, tiles, attestationCards, listFica: row && row.ficaStatus, listCells: row && row.cells }; saveState();
}

// ---------------------------------------------------------------------------------------------
async function stepA() {
  await login('user_e2e_alice');
  currentLabel = 'take-on-tab';
  await page.goto(`${BASE}/org/${SLUG}/settings/matters?tab=take-on`, { waitUntil: 'load' });
  await page.getByTestId('take-on-tab').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-start-clients').click();
  await page.getByTestId('take-on-new-upload').waitFor();
  await page.getByTestId('take-on-batch-name').fill('OBS 655B clients (8 rows)');
  await page.getByTestId('take-on-file-input').setInputFiles(FIXTURE);
  await page.getByTestId('take-on-upload-submit').click();
  await page.waitForFunction(() => location.search.includes('batch=') || document.querySelector('[data-testid="take-on-upload-warnings"]') || document.querySelector('[data-testid="take-on-upload-server-error"]'), null, { timeout: 60000 });
  if (await page.getByTestId('take-on-upload-server-error').isVisible().catch(() => false)) {
    L('[upload server error]', await page.getByTestId('take-on-upload-server-error').innerText());
    throw new Error('clients upload failed');
  }
  if (await page.getByTestId('take-on-upload-warnings').isVisible().catch(() => false)) {
    L('[upload warnings]', await page.getByTestId('take-on-upload-warnings').innerText());
    await page.getByTestId('take-on-continue').click();
    await page.waitForURL(/batch=/, { timeout: 60000 });
  }
  state.batch = new URL(page.url()).searchParams.get('batch'); saveState();
  L('[batch]', state.batch);
  currentLabel = 'mapping';
  await page.getByTestId('take-on-mapping').waitFor({ timeout: 60000 });
  const mapTriggers = page.locator('[data-testid^="take-on-map-"]');
  const mapped = {};
  for (let i = 0; i < await mapTriggers.count(); i++) {
    const t = mapTriggers.nth(i);
    mapped[(await t.getAttribute('data-testid')).replace('take-on-map-', '')] = (await t.innerText()).trim();
  }
  L('[suggested mapping]', mapped);
  // Row 8 (Gerrit Coetzee) has a blank records location: the batch default fills it.
  await page.getByTestId('take-on-default-cddRecordsLocation').fill(RECORDS_DEFAULT);
  const df = (await page.getByTestId('take-on-date-format').innerText()).trim();
  if (!df.startsWith('2025-12-31')) await selectOption(page.getByTestId('take-on-date-format'), '2025-12-31 (yyyy-mm-dd)');
  await shot('a-01-mapping');
  if (await page.getByTestId('take-on-mapping-issues').isVisible().catch(() => false)) L('[mapping issues]', await page.getByTestId('take-on-mapping-issues').innerText());
  await page.getByTestId('take-on-save-mapping').click();
  currentLabel = 'validation';
  await page.getByTestId('take-on-validation').waitFor({ timeout: 60000 });
  await page.getByTestId('take-on-validate').click();
  await page.getByTestId('take-on-cards').waitFor({ timeout: 90000 });
  await page.waitForTimeout(1500);
  const card = async id => Number((await page.getByTestId(`take-on-card-${id}`).innerText()).replace(/[^0-9]/g, '') || 'NaN');
  const cards = { total: await card('total'), valid: await card('valid'), error: await card('error'), duplicate: await card('duplicate'), blocks: await card('blocks') };
  L('[cards]', cards);
  const rows = await page.locator('table[data-testid="take-on-rows"] tbody tr[data-testid^="take-on-row-"]').evaluateAll(trs => trs.map(tr => [...tr.querySelectorAll('td')].map(td => td.innerText.replace(/\s+/g, ' ').trim()).join(' | ')));
  rows.forEach(r => L('   ', r));
  await shot('a-02-validation');
  currentLabel = 'attestation';
  await page.getByTestId('take-on-to-attestation').click();
  await page.getByTestId('take-on-attestation').waitFor({ timeout: 60000 });
  L('[defaults]', norm(await page.getByTestId('take-on-attestation-defaults').innerText()));
  L('[fica summary]', norm(await page.getByTestId('take-on-attestation-fica-summary').innerText()));
  await page.getByTestId('take-on-attestation-accept').click();
  await page.getByTestId('take-on-attest').click();
  await page.getByTestId('take-on-attestation-live').waitFor({ timeout: 60000 });
  const live = norm(await page.getByTestId('take-on-attestation-live').innerText());
  L('[live attestation]', live);
  await shot('a-03-attested');
  currentLabel = 'commit';
  await page.getByTestId('take-on-commit').click();
  await page.getByTestId('take-on-commit-dialog').waitFor();
  const body = norm(await page.getByTestId('take-on-commit-body').innerText());
  L('[commit dialog]', body);
  await page.getByTestId('take-on-commit-confirm').click();
  await page.getByTestId('take-on-result-cards').waitFor({ timeout: 180000 });
  const fig = async id => norm(await page.getByTestId(id).innerText());
  const results = { created: await fig('take-on-result-created'), skipped: await fig('take-on-result-skipped'), failed: await fig('take-on-result-failed') };
  L('[results]', results);
  L('[batch status]', await page.getByTestId('take-on-batch-status').innerText());
  await shot('a-04-results');
  state.a = { mapped, cards, rows, live, body, results }; saveState();
}

// ---------------------------------------------------------------------------------------------
async function stepB() {
  await login('user_e2e_alice');
  currentLabel = 'clients-list-after';
  await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: 'load' });
  const t = await readClientTable();
  L('[client list headers]', t.heads.map(h => h.text));
  L('[client list rows]', t.rows.length);
  t.rows.forEach(r => L('   ', r.name, '|', r.ficaStatus, '|', r.reasons, '|', r.recordsLocation, '|', r.cells.join(' ¦ ')));
  await shot('b-01-client-list-after');
  state.clientUrls = state.clientUrls || {};
  const checks = [];
  for (const fc of FIXTURE_CLIENTS) {
    const r = t.rows.find(x => x.name === fc.name);
    if (r && r.href) state.clientUrls[fc.name] = `${BASE}${r.href.split('?')[0]}`;
    const wantStatus = fc.expect === 'clean' ? LABEL_ATTESTED : LABEL_REVIEW;
    const ficaCell = r ? r.cells.find(c => c.includes(r.ficaStatus || '@@')) : null;
    const ok = !!r && r.ficaStatus === wantStatus
      && (fc.expect === 'clean' ? r.reasons === null : fc.reasons.every(x => (r.reasons || '').includes(x)))
      && !!r.recordsLocation;
    checks.push({ name: fc.name, expect: fc.expect, ficaStatus: r && r.ficaStatus, reasons: r && r.reasons, recordsLocation: r && r.recordsLocation, ficaCell, ok });
  }
  checks.forEach(c => L('[badge check]', c));
  const raw = rawEnums(await mainText());
  L('[raw-enum scan, client list main]', raw);
  // Search narrows to one review-due row so its cell is legible in a screenshot.
  await page.goto(`${BASE}/org/${SLUG}/clients?search=${encodeURIComponent('Mokopane')}`, { waitUntil: 'load' });
  await page.locator('table').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(800);
  await shot('b-02-review-due-row-mokopane');
  await page.goto(`${BASE}/org/${SLUG}/clients?search=${encodeURIComponent('Busisiwe')}`, { waitUntil: 'load' });
  await page.locator('table').first().waitFor({ timeout: 60000 });
  await page.waitForTimeout(800);
  await shot('b-03-attested-row-busisiwe');
  const others = t.rows.filter(r => !FIXTURE_CLIENTS.some(f => f.name === r.name)).map(r => ({ name: r.name, ficaStatus: r.ficaStatus }));
  state.b = { checks, raw, others, allPass: checks.every(c => c.ok) && raw.length === 0 }; saveState();
  L('[b allPass]', state.b.allPass);
}

// ---------------------------------------------------------------------------------------------
async function stepC() {
  await login('user_e2e_alice');
  currentLabel = 'review-due-view';
  await page.goto(`${BASE}/org/${SLUG}/clients?view=system:fica-review-due`, { waitUntil: 'load' });
  const t = await readClientTable();
  L('[url]', page.url());
  L('[headers + aria-sort]', t.heads);
  t.rows.forEach((r, i) => L(`   ${i + 1}.`, r.name, '|', r.ficaStatus, '|', r.reasons, '|', r.recordsLocation, '|', r.cells.join(' ¦ ')));
  const viewSelector = norm(await page.locator('main').innerText()).slice(0, 400);
  L('[page top text]', viewSelector);
  await shot('c-01-review-due-view');
  const order = t.rows.map(r => r.name);
  const activeSort = t.heads.filter(h => h.ariaSort === 'ascending' || h.ariaSort === 'descending');
  const raw = rawEnums(await mainText());
  L('[raw-enum scan, review view]', raw);
  // First click on the Client header.
  currentLabel = 'review-due-sort-click';
  const clientHeader = page.locator('table thead th').filter({ hasText: /^Client/ }).first().locator('a').first();
  const href = await clientHeader.getAttribute('href').catch(() => null);
  L('[Client header href]', href);
  await clientHeader.click();
  await page.waitForURL(/sortBy=/, { timeout: 30000 });
  const t2 = await readClientTable();
  L('[after click url]', page.url());
  L('[after click headers + aria-sort]', t2.heads);
  t2.rows.forEach((r, i) => L(`   ${i + 1}.`, r.name));
  await shot('c-02-review-due-after-client-click');
  const names2 = t2.rows.map(r => r.name);
  const sortedAsc = [...names2].sort((a, b) => a.localeCompare(b, 'en', { sensitivity: 'base' }));
  const clientHead2 = t2.heads.find(h => /^Client/.test(h.text));
  state.c = { order, rowsDetail: t.rows.map(r => ({ name: r.name, ficaStatus: r.ficaStatus, reasons: r.reasons, recordsLocation: r.recordsLocation })), heads: t.heads, activeSort, raw, afterClick: { url: page.url(), names: names2, clientHeaderAriaSort: clientHead2 && clientHead2.ariaSort, isNameAsc: JSON.stringify(names2) === JSON.stringify(sortedAsc) } };
  saveState();
  L('[c summary]', state.c);
}

// ---------------------------------------------------------------------------------------------
async function stepD() {
  await login('user_e2e_alice');
  state.d = {};
  for (const [i, fc] of FIXTURE_CLIENTS.entries()) {
    currentLabel = `detail-${fc.ref}`;
    const url = await clientDetailUrlByName(fc.name);
    if (!url) { L('[no url for]', fc.name); continue; }
    await page.goto(url, { waitUntil: 'load' });
    await page.getByRole('heading', { level: 1, name: fc.name }).waitFor({ timeout: 60000 });
    await page.getByTestId('client-fica-attestation-card').waitFor({ timeout: 30000 }).catch(() => {});
    const cardCount = await page.getByTestId('client-fica-attestation-card').count();
    const fields = await page.locator('[data-testid^="fica-attestation-field-"]').evaluateAll(els => Object.fromEntries(els.map(e => [e.getAttribute('data-testid').replace('fica-attestation-field-', ''), e.querySelector('dd').innerText.replace(/\s+/g, ' ').trim()])));
    const status = await page.getByTestId('fica-attestation-status').innerText().catch(() => null);
    const note = await page.getByTestId('fica-attestation-note').innerText().catch(() => null);
    const reasons = await page.getByTestId('fica-attestation-review-reasons').innerText().then(norm).catch(() => null);
    const tiles = norm(await page.locator('[data-slot="detail-hero-tiles"]').innerText().catch(() => null));
    const raw = rawEnums(await mainText());
    const r = { url, cardCount, status, note, reasons, fields, tiles, raw };
    L(`[detail ${fc.ref} ${fc.name}]`, r);
    if (cardCount) await page.getByTestId('client-fica-attestation-card').scrollIntoViewIfNeeded();
    await shot(`d-0${i + 1}-detail-${fc.ref}`);
    if (cardCount) await page.getByTestId('client-fica-attestation-card').screenshot({ path: `${OUT}/d-card-${fc.ref}.png` });
    state.d[fc.ref] = r;
  }
  saveState();
}

// ---------------------------------------------------------------------------------------------
async function stepE() {
  await login('user_e2e_alice');
  currentLabel = 'dashboard';
  await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: 'load' });
  const w = page.getByTestId('fica-outstanding-widget');
  await w.waitFor({ timeout: 60000 });
  await page.waitForTimeout(1500);
  const text = await w.innerText();
  L('[fica widget]', text.replace(/\n/g, ' | '));
  const items = await w.locator('a, li').allInnerTexts();
  const included = FIXTURE_CLIENTS.map(fc => ({ name: fc.name, expect: fc.expect, inWidget: text.includes(fc.name) }));
  included.forEach(x => L('   ', x));
  const onboardingInWidget = text.includes(ONBOARDING_CLIENT.name);
  L('[onboarding client in widget]', onboardingInWidget);
  await w.scrollIntoViewIfNeeded();
  await w.screenshot({ path: `${OUT}/e-01-fica-widget.png` });
  await shot('e-02-dashboard');
  state.e = { text, included, onboardingInWidget, raw: rawEnums(text), pass: included.every(x => x.inWidget === (x.expect === 'review')) }; saveState();
  L('[e pass]', state.e.pass, 'raw', state.e.raw);
}

// e2: the widget lists at most 5 (`/api/dashboard/fica-outstanding?limit=5`, pre-existing); its
// "View all" link opens the Clients "FICA outstanding" view, which lists every outstanding client.
async function stepE2() {
  await login('user_e2e_alice');
  currentLabel = 'dashboard-view-all';
  await page.goto(`${BASE}/org/${SLUG}/dashboard`, { waitUntil: 'load' });
  // Right after navigation the dashboard briefly holds two copies of the widget (<500 ms, then one;
  // probed separately) — wait for one before clicking.
  await page.waitForFunction(() => document.querySelectorAll('[data-testid="fica-outstanding-widget"]').length === 1, null, { timeout: 30000 });
  const w = page.getByTestId('fica-outstanding-widget');
  await w.getByRole('link', { name: 'View all' }).click();
  await page.waitForURL(/view=system(%3A|:)fica-outstanding/, { timeout: 30000 });
  const t = await readClientTable();
  L('[url]', page.url());
  t.rows.forEach((r, i) => L(`   ${i + 1}.`, r.name, '|', r.ficaStatus, '|', r.reasons));
  await shot('e-03-fica-outstanding-view-all');
  const names = t.rows.map(r => r.name);
  const included = FIXTURE_CLIENTS.map(fc => ({ name: fc.name, expect: fc.expect, listed: names.includes(fc.name) }));
  included.forEach(x => L('   ', x));
  state.e2 = { url: page.url(), rows: t.rows.map(r => ({ name: r.name, ficaStatus: r.ficaStatus })), included, onboardingListed: names.includes(ONBOARDING_CLIENT.name), pass: included.every(x => x.listed === (x.expect === 'review')) };
  saveState();
  L('[e2 pass]', state.e2.pass, 'onboarding listed', state.e2.onboardingListed);
}

// ---------------------------------------------------------------------------------------------
async function stepF() {
  await login('user_e2e_alice');
  currentLabel = 'onboarding-client-after';
  await page.goto(state.onboardingClientUrl, { waitUntil: 'load' });
  await page.getByRole('heading', { level: 1, name: ONBOARDING_CLIENT.name }).waitFor({ timeout: 60000 });
  await page.waitForTimeout(2000);
  const tiles = norm(await page.locator('[data-slot="detail-hero-tiles"]').innerText().catch(() => null));
  const lifecycle = await page.getByTestId('lifecycle-badge').innerText();
  const attestationCards = await page.getByTestId('client-fica-attestation-card').count();
  await shot('f-01-onboarding-client-detail-after');
  await page.goto(`${BASE}/org/${SLUG}/clients?search=${encodeURIComponent('Palesa Dube')}`, { waitUntil: 'load' });
  const t = await readClientTable();
  const row = t.rows.find(r => r.name === ONBOARDING_CLIENT.name);
  await shot('f-02-onboarding-client-list-after');
  const after = { lifecycle, tiles, attestationCards, listFica: row && row.ficaStatus, listCells: row && row.cells };
  L('[onboarding before]', state.onboardingBefore);
  L('[onboarding after]', after);
  const same = state.onboardingBefore && after.lifecycle === state.onboardingBefore.lifecycle && after.tiles === state.onboardingBefore.tiles && after.listFica === state.onboardingBefore.listFica && after.attestationCards === 0;
  // The seeded clients' FICA status before the take-on vs now.
  await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: 'load' });
  const all = await readClientTable();
  const seededNow = (state.listBeforeOnboardingClient || []).map(b => ({ name: b.name, before: b.ficaStatus, after: (all.rows.find(r => r.name === b.name) || {}).ficaStatus }));
  seededNow.forEach(s => L('   [seeded]', s));
  state.f = { after, same, seededNow, seededSame: seededNow.every(s => s.before === s.after) }; saveState();
  L('[f same]', same, 'seededSame', state.f.seededSame);
}

// ---------------------------------------------------------------------------------------------
async function readIntervals() {
  return {
    low: await page.getByTestId('fica-review-months-low').inputValue(),
    medium: await page.getByTestId('fica-review-months-medium').inputValue(),
    high: await page.getByTestId('fica-review-months-high').inputValue(),
  };
}
async function openCompliance() {
  await page.goto(`${BASE}/org/${SLUG}/settings/firm?tab=compliance`, { waitUntil: 'load' });
  await page.getByTestId('fica-review-intervals').waitFor({ timeout: 60000 });
  await page.waitForTimeout(800);
}
async function saveAndRead() {
  await page.getByRole('button', { name: 'Save Settings' }).click();
  await page.waitForTimeout(2500);
  const msg = await page.locator('[data-testid="fica-review-intervals"] ~ div p').allInnerTexts().catch(() => []);
  return msg.map(norm);
}
async function stepG() {
  await login('user_e2e_alice');
  currentLabel = 'compliance-settings';
  const posts = [];
  page.on('request', r => { if (r.method() === 'POST' && currentLabel.startsWith('compliance')) posts.push({ t: new Date().toISOString(), url: r.url() }); });
  await openCompliance();
  const initial = await readIntervals();
  L('[intervals initial]', initial);
  L('[section text]', norm(await page.getByTestId('fica-review-intervals').innerText()));
  await page.getByTestId('fica-review-intervals').scrollIntoViewIfNeeded();
  await shot('g-01-intervals-initial');
  // Medium → 18: clear the field and type.
  const med = page.getByTestId('fica-review-months-medium');
  await med.click();
  await med.press('ControlOrMeta+a');
  await med.press('Backspace');
  const cleared = await med.inputValue();
  await med.pressSequentially('18');
  const typed = await med.inputValue();
  L('[medium cleared → typed]', { cleared, typed });
  await med.blur();
  const errAfterTyping = await page.getByTestId('fica-review-months-medium-error').count();
  const savedMsg = await saveAndRead();
  L('[save 18 message]', savedMsg);
  await shot('g-02-medium-18-saved');
  await openCompliance();
  const afterReload = await readIntervals();
  L('[intervals after reload]', afterReload);
  await page.getByTestId('fica-review-intervals').scrollIntoViewIfNeeded();
  await shot('g-03-after-reload');
  // 0 and 121 refused inline.
  const refusals = {};
  for (const bad of ['0', '121']) {
    currentLabel = `compliance-refuse-${bad}`;
    await openCompliance();
    const postsBefore = posts.length;
    await med.click();
    await med.press('ControlOrMeta+a');
    await med.press('Backspace');
    await med.pressSequentially(bad);
    const v = await med.inputValue();
    await med.blur();
    const errBlur = await page.getByTestId('fica-review-months-medium-error').innerText().catch(() => null);
    const msgs = await saveAndRead();
    const errSave = await page.getByTestId('fica-review-months-medium-error').innerText().catch(() => null);
    const ariaInvalid = await med.getAttribute('aria-invalid');
    await shot(`g-04-refused-${bad}`);
    await openCompliance();
    const reread = await readIntervals();
    refusals[bad] = { typed: v, errBlur, errSave, ariaInvalid, msgs, postsDuringSave: posts.length - postsBefore, reread };
    L(`[refuse ${bad}]`, refusals[bad]);
  }
  // Back to 24.
  currentLabel = 'compliance-restore';
  await openCompliance();
  await med.click();
  await med.press('ControlOrMeta+a');
  await med.press('Backspace');
  await med.pressSequentially('24');
  await med.blur();
  const restoreMsg = await saveAndRead();
  await openCompliance();
  const restored = await readIntervals();
  L('[restore 24]', { restoreMsg, restored });
  await page.getByTestId('fica-review-intervals').scrollIntoViewIfNeeded();
  await shot('g-05-restored-24');
  state.g = { initial, cleared, typed, errAfterTyping, savedMsg, afterReload, refusals, restoreMsg, restored, posts }; saveState();
}

// ---------------------------------------------------------------------------------------------
const MATTER_NAME = 'Mashaba — OBS 655B attested-client matter';
async function stepMatter() {
  await login('user_e2e_alice');
  currentLabel = 'new-matter';
  await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: 'load' });
  await page.getByRole('button', { name: 'New Matter' }).click();
  const dialog = page.getByRole('dialog', { name: 'Create Matter' });
  await dialog.waitFor();
  await dialog.getByLabel('Name', { exact: true }).fill(MATTER_NAME);
  await selectNative(dialog.getByLabel(/^Client/), 'Thabo Mashaba');
  await dialog.getByLabel(/^Work Type/).fill('LITIGATION');
  await dialog.getByRole('button', { name: 'Create Matter' }).click();
  await dialog.waitFor({ state: 'hidden', timeout: 30000 });
  await page.waitForTimeout(1500);
  await page.goto(`${BASE}/org/${SLUG}/matters`, { waitUntil: 'load' });
  const link = page.getByRole('link', { name: new RegExp('OBS 655B attested-client matter') }).first();
  await link.waitFor({ timeout: 30000 });
  await link.click();
  await page.waitForURL(/\/matters\/[0-9a-f-]{36}/, { timeout: 30000 });
  state.matterUrl = page.url().split('?')[0]; saveState();
  currentLabel = 'matter-overview-alice';
  await page.getByTestId('fica-status-card').waitFor({ timeout: 30000 }).catch(() => {});
  const cardCount = await page.getByTestId('fica-status-card').count();
  const badge = await page.getByTestId('fica-status-badge').innerText().catch(() => null);
  const reasons = await page.getByTestId('fica-review-reasons').innerText().then(norm).catch(() => null);
  const link2 = await page.getByTestId('fica-attestation-link').getAttribute('href').catch(() => null);
  const cardText = norm(await page.getByTestId('fica-status-card').innerText().catch(() => null));
  L('[matter overview (Alice)]', { url: state.matterUrl, cardCount, badge, reasons, attestationLink: link2, cardText });
  if (cardCount) await page.getByTestId('fica-status-card').scrollIntoViewIfNeeded();
  await shot('h-01-matter-overview-alice');
  state.matter = { cardCount, badge, reasons, attestationLink: link2, cardText, raw: rawEnums(cardText || '') }; saveState();
}

// team: Alice adds Carol to the matter's team (Details → Team → Add Member), so Carol can open it.
async function stepTeam() {
  await login('user_e2e_alice');
  currentLabel = 'matter-team';
  await page.goto(state.matterUrl, { waitUntil: 'load' });
  const group = page.getByTestId('tab-group-details').filter({ visible: true });
  const item = page.getByTestId('tab-item-team').filter({ visible: true });
  await group.waitFor({ timeout: 30000 });
  for (let i = 0; i < 3; i++) { await group.click(); if (await item.waitFor({ state: 'visible', timeout: 2000 }).then(() => true, () => false)) { await item.click(); break; } }
  const panel = page.getByTestId('matter-team-panel').first();
  await panel.waitFor({ timeout: 15000 });
  await panel.getByRole('button', { name: 'Add Member' }).click();
  const dialog = page.getByRole('dialog', { name: 'Add Member' });
  await dialog.waitFor();
  await dialog.getByRole('option', { name: /Carol Member/ }).click();
  await dialog.waitFor({ state: 'hidden', timeout: 15000 });
  await panel.getByRole('row', { name: /Carol Member/ }).first().waitFor({ timeout: 15000 });
  L('[team]', norm(await panel.innerText()));
  await shot('h-06-matter-team-carol-added');
}

// carolmatter: Carol (now on the team) opens the matter overview of the attested client.
async function stepCarolMatter() {
  await login('user_e2e_carol');
  currentLabel = 'carol-matter-on-team';
  const r = await page.goto(state.matterUrl, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  const res = { status: r && r.status(), ...(await pageSummary()), ficaCards: await page.getByTestId('fica-status-card').count(), errorBoundary: await page.getByText(/something went wrong|application error/i).count() };
  L('[carol matter (on team)]', res);
  await shot('h-07-carol-matter-overview');
  state.carolMatter = res; saveState();
}

// ---------------------------------------------------------------------------------------------
async function pageSummary() {
  const h1 = await page.locator('h1').allInnerTexts();
  const text = norm(await page.locator('main').innerText().catch(() => ''));
  return { url: page.url(), h1, text: text.slice(0, 700) };
}
async function stepCarol() {
  await login('user_e2e_carol');
  const res = {};
  currentLabel = 'carol-clients';
  await page.goto(`${BASE}/org/${SLUG}/clients`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  res.clients = { ...(await pageSummary()), tables: await page.locator('table').count() };
  L('[carol clients]', res.clients);
  await shot('h-02-carol-clients');
  currentLabel = 'carol-review-view';
  const resp = await page.goto(`${BASE}/org/${SLUG}/clients?view=system:fica-review-due`, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  res.reviewView = { status: resp && resp.status(), ...(await pageSummary()), tables: await page.locator('table').count() };
  L('[carol review view]', res.reviewView);
  await shot('h-03-carol-review-view');
  currentLabel = 'carol-client-detail';
  const detailUrl = state.clientUrls && state.clientUrls['Thabo Mashaba'];
  const r2 = await page.goto(detailUrl, { waitUntil: 'load' });
  await page.waitForTimeout(3000);
  res.clientDetail = {
    status: r2 && r2.status(), ...(await pageSummary()),
    attestationCards: await page.getByTestId('client-fica-attestation-card').count(),
    tiles: norm(await page.locator('[data-slot="detail-hero-tiles"]').innerText().catch(() => null)),
  };
  res.clientDetail.ficaTile = /FICA/.test(res.clientDetail.tiles || '');
  L('[carol client detail]', res.clientDetail);
  await shot('h-04-carol-client-detail');
  if (state.matterUrl) {
    currentLabel = 'carol-matter';
    const r3 = await page.goto(state.matterUrl, { waitUntil: 'load' });
    await page.waitForTimeout(3000);
    res.matter = { status: r3 && r3.status(), ...(await pageSummary()), ficaCards: await page.getByTestId('fica-status-card').count() };
    L('[carol matter]', res.matter);
    await shot('h-05-carol-matter');
  }
  state.carol = res; saveState();
}

(async () => {
  const browser = await chromium.launch();
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, acceptDownloads: true });
  page = await context.newPage();
  page.on('console', m => { if (m.type() === 'error') { const s = `[console.error @${currentLabel}] ${m.text()}`; consoleErrors.push(s); L(s); } });
  page.on('pageerror', e => { const s = `[pageerror @${currentLabel}] ${e.message}`; consoleErrors.push(s); L(s); });
  page.on('response', r => { if (r.status() >= 400) { const s = `[http ${r.status()} @${currentLabel}] ${r.request().method()} ${r.url()}`; network.push(s); L(s); } });
  const steps = { 0: step0, a: stepA, b: stepB, c: stepC, d: stepD, e: stepE, e2: stepE2, f: stepF, g: stepG, matter: stepMatter, team: stepTeam, carol: stepCarol, carolmatter: stepCarolMatter };
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
